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
 * Receives notification replies, approval decisions and question answers.
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
        val questionId = intent.getStringExtra(AgentNotifications.EXTRA_QUESTION_ID).orEmpty()
        // Always-allow from the shade grants the narrowest useful scope. The
        // shade cannot show the three-way choice the in-app overlay offers, so
        // it must not silently grant permanent control either.
        val scope = intent.getStringExtra(AgentNotifications.EXTRA_SCOPE).orEmpty()

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
                    questionId.isNotBlank() && typed.isNotBlank() -> {
                        // A typed answer to an agent's question is the answer —
                        // sending it as a chat message would make the agent
                        // re-ask instead of continuing.
                        client.answerQuestion(questionId, typed)
                    }
                    decision != null && approvalId.isNotBlank() -> {
                        client.resolveApproval(
                            approvalId,
                            decision,
                            scope.ifBlank { "once" }
                        )
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
                    // Cancel the one approval, not the whole room's activity:
                    // answering this request must not erase an unrelated
                    // progress or chat notification.
                    AgentNotifications.cancelApproval(context, approvalId)
                }
                if (questionId.isNotBlank()) {
                    AgentNotifications.cancelApproval(context, questionId)
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
