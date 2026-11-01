package cx.aswin.boxlore.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import cx.aswin.boxlore.core.support.SupportLocalStore
import cx.aswin.boxlore.core.support.SupportPushTopics
import cx.aswin.boxlore.core.support.SupportRepository

/** [SupportLocalStore] in its own preferences file (`boxlore_support`). */
class SupportPrefsStore(context: Context) : SupportLocalStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var hasConversations: Boolean
        get() = prefs.getBoolean(KEY_HAS_CONVERSATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_CONVERSATIONS, value).apply()

    override var replyWaiting: Boolean
        get() = prefs.getBoolean(KEY_REPLY_WAITING, false)
        set(value) = prefs.edit().putBoolean(KEY_REPLY_WAITING, value).apply()

    override var subscribedTopics: Set<String>
        get() = prefs.getStringSet(KEY_TOPICS, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet(KEY_TOPICS, value).apply()

    override var lastCheckedAt: Long
        get() = prefs.getLong(KEY_LAST_CHECKED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECKED, value).apply()

    companion object {
        const val PREFS_NAME = "boxlore_support"
        private const val KEY_HAS_CONVERSATIONS = "has_conversations"
        private const val KEY_REPLY_WAITING = "reply_waiting"
        private const val KEY_TOPICS = "push_topics"
        private const val KEY_LAST_CHECKED = "last_checked_at"
    }
}

/** The team's reply as a notification in its own channel; a tap opens that conversation. */
object SupportReplyNotifications {
    const val CHANNEL_ID = "boxlore_feedback_replies_v1"
    private const val NOTIFICATION_ID_BASE = 7_300
    private const val SLOTS = 50
    private val THREAD_ID = Regex("[0-9a-f]{24}")

    /** Where a tap goes: Feedback, on the conversation when the id is one. */
    fun routeFor(threadId: String?): String = if (threadId != null && THREAD_ID.matches(threadId)) "feedback?thread=$threadId" else "feedback"

    fun show(context: Context, threadId: String?, title: String?, body: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Feedback replies", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Replies from the boxlore team to your feedback"
            },
        )
        val slot = Math.floorMod((threadId ?: "").hashCode(), SLOTS)
        val intent = NewEpisodeFcmLogic.createNormalizedPushIntent(context, routeFor(threadId), notificationType = "support_reply")
        val tap = runCatching {
            PendingIntent.getActivity(context, NOTIFICATION_ID_BASE + slot, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }.getOrNull()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(cx.aswin.boxlore.R.drawable.ic_notification_custom)
            .setColor(android.graphics.Color.parseColor("#5B5BD6"))
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: "New reply to your feedback")
            .setContentText(body.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.orEmpty()))
            .setAutoCancel(true)
            .apply { if (tap != null) setContentIntent(tap) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + slot, notification) }
            .onFailure { Log.w("SupportReplies", "Reply notification not shown", it) }
    }
}

/**
 * After each successful refresh, joins and leaves reply-push topics to match the server's list
 * (none for a phone without conversations), recording only what Firebase confirmed.
 */
class SupportPushTopicSync(private val repository: SupportRepository, private val topics: SupportPushTopics) {
    fun sync() {
        val plan = topics.planFor(repository.meta.value, repository.hasConversations)
        if (plan.isEmpty) return
        val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return
        plan.subscribe.forEach { topic -> messaging.subscribeToTopic(topic).addOnSuccessListener { topics.joined(topic) } }
        plan.unsubscribe.forEach { topic -> messaging.unsubscribeFromTopic(topic).addOnSuccessListener { topics.left(topic) } }
    }
}
