package com.sarvesh173.talos.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives notification replies and approval decisions.
 *
 * A notification action must not do network I/O on the main thread, and the
 * process may be started cold just to handle this, so the work runs on an
 * application scope rather than an Activity's.
 *
 * The reply text is sent into the same room the notification came from, which
 * means an answer typed in the shade shows up in the thread exactly as if it had
 * been typed there. Nothing about the transport is different.
 */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val roomId = intent.getStringExtra(AgentNotifications.EXTRA_ROOM_ID).orEmpty()
        if (roomId.isBlank()) return
        val approvalId = intent.getStringExtra(AgentNotifications.EXTRA_APPROVAL_ID).orEmpty()

        val decision = if (intent.hasExtra(AgentNotifications.EXTRA_APPROVE)) {
            intent.getBooleanExtra(AgentNotifications.EXTRA_APPROVE, false)
        } else {
            null
        }

        val typed = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AgentNotifications.KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = TalosClient.shared(context)
                if (client == null) {
                    Log.w(TAG, "No paired client; dropping notification reply")
                    return@launch
                }

                when {
                    decision != null && approvalId.isNotBlank() -> {
                        client.resolveApproval(approvalId, decision)
                        // A decision often comes with a clarifying sentence.
                        if (typed.isNotBlank()) {
                            client.sendMessageTo(roomId, typed)
                        }
                    }
                    typed.isNotBlank() -> client.sendMessageTo(roomId, typed)
                }

                // The decision is consumed; the approval notification must not
                // linger and invite a second, contradictory tap.
                if (decision != null && approvalId.isNotBlank()) {
                    AgentNotifications.cancelForRoom(context, roomId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle notification reply: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TalosReplyReceiver"
    }
}
