package com.sarvesh173.talos.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.sarvesh173.talos.MainActivity
import com.sarvesh173.talos.R

/**
 * Agent notifications, and the reply path back into the room.
 *
 * Two channels, because they demand different attention:
 *  - **Approvals** — high importance. The agent is blocked and will not move
 *    until a human answers, so this must interrupt.
 *  - **Activity** — default importance. "Task finished", "tool ran", "error".
 *    Useful, never urgent.
 *
 * Every notification carries an inline reply action wired through
 * [ReplyReceiver], so the user can answer an approval or nudge the agent from
 * the shade without opening the app. Bubble support (Android 11+) is declared on
 * the approval channel so the OS can float a conversation bubble for it — that
 * is the "mini view" affordance: tap the bubble, get the thread, not the whole
 * app.
 */
object AgentNotifications {

    const val CHANNEL_APPROVAL = "talos_approvals"
    const val CHANNEL_ACTIVITY = "talos_activity"

    /** Bubbles are per-conversation; the agent id is the conversation. */
    private const val SHORTCUT_PREFIX = "agent_"

    /** Remote-input key used by the reply action. */
    const val KEY_REPLY = "talos_reply_text"

    /** Intent extras the receiver reads to route a reply. */
    const val EXTRA_ROOM_ID = "room_id"
    const val EXTRA_AGENT_NAME = "agent_name"
    const val EXTRA_APPROVAL_ID = "approval_id"
    const val EXTRA_APPROVE = "approve"

    /** Group key so all agent chatter collapses into one stack. */
    private const val GROUP_AGENT = "talos.agent.group"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val approvals = NotificationChannel(
            CHANNEL_APPROVAL,
            "Agent approvals",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "The agent is blocked and needs a decision"
            enableVibration(true)
            // Declaring bubbles makes the OS eligible to float this thread as a
            // conversation bubble; without it no bubble can ever appear.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setAllowBubbles(true)
            }
        }

        val activity = NotificationChannel(
            CHANNEL_ACTIVITY,
            "Agent activity",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Task completions, tool runs and errors"
            setShowBadge(true)
        }

        manager.createNotificationChannel(approvals)
        manager.createNotificationChannel(activity)
    }

    // -- public surface ----------------------------------------------------

    /**
     * An agent needs a decision. High priority, inline reply, and Approve/Deny
     * actions so the user never has to open the app to unblock it.
     */
    fun notifyApproval(
        context: Context,
        agentName: String,
        roomId: String,
        approvalId: String,
        summary: String,
        command: String = ""
    ) {
        ensureChannels(context)
        val notification = buildApproval(
            context = context,
            agentName = agentName,
            roomId = roomId,
            approvalId = approvalId,
            summary = summary,
            command = command
        )
        post(context, approvalTag(roomId), notification)
    }

    /**
     * General agent activity. Silent when the room is already on screen — the
     * app knows that, the caller passes [suppressIfActive].
     */
    fun notifyActivity(
        context: Context,
        agentName: String,
        roomId: String,
        title: String,
        body: String,
        suppressIfActive: Boolean = false
    ) {
        if (suppressIfActive) return
        ensureChannels(context)
        val notification = buildActivity(context, agentName, roomId, title, body)
        post(context, activityTag(roomId), notification)
    }

    fun cancelForRoom(context: Context, roomId: String) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(approvalTag(roomId), 0)
        manager.cancel(activityTag(roomId), 0)
    }

    // -- builders ----------------------------------------------------------

    private fun buildApproval(
        context: Context,
        agentName: String,
        roomId: String,
        approvalId: String,
        summary: String,
        command: String
    ): Notification {
        val openIntent = openRoomIntent(context, roomId, agentName)

        val approveIntent = PendingIntent.getBroadcast(
            context,
            (roomId + approvalId + "yes").hashCode(),
            replyActionIntent(context, roomId, agentName, approvalId).apply {
                putExtra(EXTRA_APPROVE, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val denyIntent = PendingIntent.getBroadcast(
            context,
            (roomId + approvalId + "no").hashCode(),
            replyActionIntent(context, roomId, agentName, approvalId).apply {
                putExtra(EXTRA_APPROVE, false)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Inline reply: a typed answer is delivered as a normal room message, so
        // "why?" or "use the other file" reaches the agent without the app open.
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Reply to $agentName")
            .build()
        val replyPending = PendingIntent.getBroadcast(
            context,
            (roomId + approvalId + "reply").hashCode(),
            replyActionIntent(context, roomId, agentName, approvalId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification_reply,
            "Reply",
            replyPending
        ).addRemoteInput(remoteInput).build()

        return NotificationCompat.Builder(context, CHANNEL_APPROVAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$agentName needs approval")
            .setContentText(summary)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(summary)
                        if (command.isNotBlank()) append("\n\n$command")
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_AGENT)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_notification, "Approve", approveIntent)
            .addAction(R.drawable.ic_notification, "Deny", denyIntent)
            .addAction(replyAction)
            .apply {
                // Bubble metadata: makes this conversation eligible to float as a
                // bubble instead of living only in the shade.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setShortcutId(shortcutId(roomId))
                    setBubbleMetadata(
                        Notification.BubbleMetadata.Builder(
                            openRoomIntent(context, roomId, agentName),
                            android.graphics.drawable.Icon.createWithResource(
                                context, R.drawable.ic_notification
                            )
                        )
                            .setDesiredHeight(620)
                            .setAutoExpandBubble(false)
                            .setSuppressNotification(true)
                            .build()
                    )
                }
            }
            .build()
    }

    private fun buildActivity(
        context: Context,
        agentName: String,
        roomId: String,
        title: String,
        body: String
    ): Notification {
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Message $agentName")
            .build()
        val replyPending = PendingIntent.getBroadcast(
            context,
            (roomId + "activity_reply").hashCode(),
            replyActionIntent(context, roomId, agentName, ""),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ACTIVITY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_AGENT)
            .setAutoCancel(true)
            .setContentIntent(openRoomIntent(context, roomId, agentName))
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification_reply, "Reply", replyPending
                ).addRemoteInput(remoteInput).build()
            )
            .build()
    }

    // -- plumbing ----------------------------------------------------------

    private fun replyActionIntent(
        context: Context,
        roomId: String,
        agentName: String,
        approvalId: String
    ): Intent = Intent(context, ReplyReceiver::class.java).apply {
        putExtra(EXTRA_ROOM_ID, roomId)
        putExtra(EXTRA_AGENT_NAME, agentName)
        putExtra(EXTRA_APPROVAL_ID, approvalId)
    }

    private fun openRoomIntent(context: Context, roomId: String, agentName: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_AGENT_NAME, agentName)
        }
        return PendingIntent.getActivity(
            context,
            (roomId + "open").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun post(context: Context, tag: String, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Not granted: dropping silently is correct. The in-app surface is
            // still the source of truth; asking again here would be nagging.
            return
        }
        NotificationManagerCompat.from(context).notify(tag, 0, notification)
    }

    private fun shortcutId(roomId: String) = SHORTCUT_PREFIX + roomId
    private fun approvalTag(roomId: String) = "approval:$roomId"
    private fun activityTag(roomId: String) = "activity:$roomId"
}
