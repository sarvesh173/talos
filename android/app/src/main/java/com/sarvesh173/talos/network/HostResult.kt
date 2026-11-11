package com.sarvesh173.talos.network

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The outcome of a read against the TALOS core.
 *
 * Every fetcher used to fold "the host said there is nothing" and "I could not
 * reach the host" into a single empty value. Screens then had no way to tell
 * them apart, so an unreachable core rendered as a tidy empty state — or worse,
 * as a zeroed-out dashboard that looked like a real measurement. This type is
 * the fix: a failure can never be mistaken for a successful empty answer.
 */
sealed interface HostResult<out T> {

    /** The host answered, and this is what it said. May legitimately be empty. */
    data class Ok<T>(val value: T) : HostResult<T>

    /**
     * The host did not answer, or answered with an error status.
     *
     * [reason] is what the screen should show the user: the server's own
     * message when it sent one, otherwise the transport failure in plain words.
     */
    data class Failed(val reason: String) : HostResult<Nothing>

    /** Just the value when the host answered, otherwise null. */
    fun valueOrNull(): T? = (this as? Ok)?.value

    val isOk: Boolean get() = this is Ok
}

/**
 * Run a blocking OkHttp call and classify the outcome.
 *
 * A non-2xx response is a failure with the status on it, not an empty result —
 * a 500 means the host is broken, not that the list is empty.
 */
internal fun <T> hostCall(
    client: OkHttpClient,
    request: Request,
    parse: (String) -> T,
): HostResult<T> {
    val resp = try {
        client.newCall(request).execute()
    } catch (e: Exception) {
        return HostResult.Failed(transportReason(e))
    }
    resp.use {
        val body = it.body?.string().orEmpty()
        if (!it.isSuccessful) {
            return HostResult.Failed(
                when (it.code) {
                    401, 403 -> "The host rejected this device's token."
                    404 -> "The host has no such endpoint."
                    in 500..599 -> "The host is failing (HTTP ${it.code})."
                    else -> "The host refused the request (HTTP ${it.code})."
                }
            )
        }
        return try {
            HostResult.Ok(parse(if (body.isBlank()) "[]" else body))
        } catch (e: Exception) {
            HostResult.Failed("The host sent a reply this app could not read.")
        }
    }
}

private fun transportReason(e: Exception): String = when (e) {
    is java.net.SocketTimeoutException -> "The host did not answer in time."
    is java.net.UnknownHostException -> "The host name does not resolve."
    is java.net.ConnectException -> "Couldn't reach the host."
    else -> "Couldn't reach the host."
}
