package com.sarvesh173.talos.ui.settings

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Closed set of service states. The app must never collapse these into a
 * generic "connection failed": [ServiceState.TailscaleOff] is the common
 * cause (phone tailnet goes offline regularly) and must be named plainly.
 */
enum class ServiceState {
    TailscaleOff,
    HostOffline,
    ServiceStopped,
    AuthRequired,
    Unauthorized,
    Online,
    Unknown
}

/**
 * Totally ordered dot colour for Apple HIG monochrome presentation.
 * Red only for [ServiceState.Unauthorized] and [ServiceState.Unknown],
 * neutral for [ServiceState.AuthRequired], green for [ServiceState.Online].
 */
enum class DotTone { GREEN, NEUTRAL, RED }

fun dotToneFor(state: ServiceState): DotTone = when (state) {
    ServiceState.Online -> DotTone.GREEN
    ServiceState.AuthRequired -> DotTone.NEUTRAL
    ServiceState.Unauthorized, ServiceState.Unknown -> DotTone.RED
    ServiceState.TailscaleOff, ServiceState.HostOffline, ServiceState.ServiceStopped -> DotTone.NEUTRAL
}

/** Human-readable result. Copy is plain, specific, no jargon, no exclamation marks. */
data class ServiceProbeResult(
    val state: ServiceState,
    val title: String,
    val body: String,
    val actionLabel: String? = null,
    val detail: String? = null,
    val hasTailnetAddress: Boolean = false,
    val httpCode: Int? = null
) {
    val dotTone: DotTone get() = dotToneFor(state)
}

/**
 * Pure classifier — no Android, no network, unit testable on the JVM.
 *
 * Order matters (first match wins):
 * 1. 200 -> Online, 302/401 -> AuthRequired, 403 -> Unauthorized.
 *    A host that answered at all proves part of the path works, so these
 *    trump the local tailnet check.
 * 2. !hasTailnetAddress -> TailscaleOff (no local 100.x address, which also
 *    covers "timeout with no tailnet interface at all").
 * 3. portRefused -> ServiceStopped (TCP refused means the host answered).
 * 4. threw != null -> HostOffline (tailnet up, peer did not answer).
 * 5. Everything else (other HTTP codes, or no code and no throw) -> Unknown.
 *
 * Invariant: TailscaleOff is never returned when [hasTailnetAddress] is true.
 * 403 maps to Unauthorized here; [ServiceStatusProbe.probe] only reaches this
 * branch on a 403 response, i.e. a token was sent and rejected.
 */
fun classify(
    httpCode: Int?,
    threw: Throwable?,
    hasTailnetAddress: Boolean,
    portRefused: Boolean
): ServiceState = when {
    httpCode == 200 -> ServiceState.Online
    httpCode == 302 || httpCode == 401 -> ServiceState.AuthRequired
    httpCode == 403 -> ServiceState.Unauthorized
    !hasTailnetAddress -> ServiceState.TailscaleOff
    portRefused -> ServiceState.ServiceStopped
    threw != null -> ServiceState.HostOffline
    else -> ServiceState.Unknown
}

/**
 * Probes the dashboard at [baseUrl] (for example http://100.75.107.79:9119)
 * and classifies the outcome with [classify]. Redirects are not followed so
 * a 302 from the dashboard stays visible as the healthy AuthRequired state.
 */
class ServiceStatusProbe(
    private val connectTimeoutMs: Int = 3000,
    private val readTimeoutMs: Int = 3000
) {

    /** True when any local interface holds a Tailscale 100.64.0.0/10 address. */
    fun hasTailnetAddress(): Boolean = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching false
        for (iface in interfaces) {
            if (!iface.isUp) continue
            for (addr in iface.inetAddresses) {
                val bytes = addr.address
                if (bytes.size == 4 && (bytes[0].toInt() and 0xFF) == 100) {
                    val second = bytes[1].toInt() and 0xFF
                    if (second in 64..127) return true
                }
            }
        }
        false
    }.getOrDefault(false)

    suspend fun probe(baseUrl: String, token: String?): ServiceProbeResult =
        withContext(Dispatchers.IO) {
            val hasTailnet = hasTailnetAddress()
            val host = runCatching { URL(baseUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "100.75.107.79"
            val port = runCatching { URL(baseUrl).port }.getOrNull()?.takeIf { it > 0 } ?: 9119

            var httpCode: Int? = null
            var threw: Throwable? = null
            var portRefused = false

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(baseUrl.trimEnd('/')).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    if (!token.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }
                httpCode = connection.responseCode
                connection.inputStream?.close()
            } catch (e: ConnectException) {
                portRefused = true
                threw = e
            } catch (e: SocketTimeoutException) {
                threw = e
            } catch (e: UnknownHostException) {
                threw = e
            } catch (e: IOException) {
                threw = e
            } finally {
                connection?.disconnect()
            }

            val state = classify(httpCode, threw, hasTailnet, portRefused)
            val rawDetail = threw?.let { "${it.javaClass.simpleName}: ${it.message}" }
                ?: httpCode?.let { "HTTP $it" }

            when (state) {
                ServiceState.TailscaleOff -> ServiceProbeResult(
                    state = state,
                    title = "Tailscale is off",
                    body = "This device is not on your tailnet, so $host cannot be reached",
                    actionLabel = "Open Tailscale",
                    detail = rawDetail,
                    hasTailnetAddress = hasTailnet,
                    httpCode = httpCode
                )
                ServiceState.HostOffline -> ServiceProbeResult(
                    state = state,
                    title = "Host is unreachable",
                    body = "Tailscale is connected but $host is not answering",
                    detail = rawDetail,
                    hasTailnetAddress = hasTailnet,
                    httpCode = httpCode
                )
                ServiceState.ServiceStopped -> ServiceProbeResult(
                    state = state,
                    title = "Dashboard is stopped",
                    body = "The host is reachable but nothing is listening on port $port",
                    actionLabel = "Start service",
                    detail = rawDetail,
                    hasTailnetAddress = hasTailnet,
                    httpCode = httpCode
                )
                ServiceState.AuthRequired -> ServiceProbeResult(
                    state = state,
                    title = "Sign in required",
                    body = "The dashboard is running and asking for credentials",
                    detail = rawDetail,
                    hasTailnetAddress = hasTailnet,
                    httpCode = httpCode
                )
                ServiceState.Unauthorized -> ServiceProbeResult(
                    state = state,
                    title = "Token rejected",
                    body = "The saved token is no longer valid",
                    detail = rawDetail,
                    hasTailnetAddress = hasTailnet,
                    httpCode = httpCode
                )
                ServiceState.Online -> ServiceProbeResult(
                    state = state,
                    title = "Online",
                    body = "The dashboard is reachable",
                    detail = rawDetail,
                    hasTailnetAddress = hasTailnet,
                    httpCode = httpCode
                )
                ServiceState.Unknown -> ServiceProbeResult(
                    state = state,
                    title = "Unexpected response",
                    body = "The dashboard returned a response that could not be classified",
                    detail = rawDetail,
                    hasTailnetAddress = hasTailnet,
                    httpCode = httpCode
                )
            }
        }
}
