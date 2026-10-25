package com.sarvesh173.talos.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Zero-config core discovery (the "unmounted" pairing flow).
 *
 * Broadcasts TALOS_DISCOVER on the LAN; talos-core's UDP responder
 * (port 8089) answers with its service endpoint. The user never types or
 * sees an IP - the app auto-detects, saves the URL, and pairs a device key.
 */
object MeshDiscovery {
    private const val PROBE_PORT = 8089
    private const val PROBE = "TALOS_DISCOVER"

    data class CoreEndpoint(
        val baseUrl: String,   // http://<ip>:<port>
        val nodeName: String,
        val version: String,
    )

    /**
     * Probe the LAN for a talos-core. Returns the first responder or null.
     * Never throws - unreachable networks simply return null.
     */
    suspend fun discover(timeoutMs: Long = 3500): CoreEndpoint? = withContext(Dispatchers.IO) {
        runCatching {
            var result: CoreEndpoint? = null
            val socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 250
                reuseAddress = true
            }
            socket.use { s ->
                val probeBytes = PROBE.toByteArray(Charsets.US_ASCII)
                val targets = listOf(
                    InetAddress.getByName("255.255.255.255"),
                    InetAddress.getByName("127.0.0.1"), // same-device core (desktop/emulator)
                )
                val deadline = System.currentTimeMillis() + timeoutMs
                var sent = false

                probe@ while (System.currentTimeMillis() < deadline && result == null) {
                    if (!sent) {
                        for (target in targets) {
                            runCatching {
                                s.send(DatagramPacket(probeBytes, probeBytes.size, target, PROBE_PORT))
                            }
                        }
                        sent = true
                    }
                    val buf = ByteArray(1024)
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(packet)
                        val reply = String(packet.data, 0, packet.length).trim()
                        if (reply.contains("talos-core")) {
                            result = parseReply(
                                reply,
                                packet.address.hostAddress ?: continue@probe,
                            )
                        }
                    } catch (_: SocketTimeoutException) {
                        // Re-probe until the deadline.
                        sent = false
                    }
                }
            }
            result
        }.getOrNull()
    }

    private fun parseReply(json: String, ip: String): CoreEndpoint? = runCatching {
        val obj = JSONObject(json)
        val port = obj.optInt("port", 8000)
        CoreEndpoint(
            baseUrl = "http://$ip:$port",
            nodeName = obj.optString("node", "talos-node"),
            version = obj.optString("version", ""),
        )
    }.getOrNull()
}
