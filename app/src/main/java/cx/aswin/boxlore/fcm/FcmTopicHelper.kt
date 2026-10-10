package cx.aswin.boxlore.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.util.isInstalledFromPlayStore
import java.io.File

/** FCM topic subscribe helpers and post-restore reconciliation. */
object FcmTopicHelper {
    private const val TAG = "Firebase"
    private const val SENTINEL_NAME = "fcm_topics_synced"

    /** Subscribe to broadcast topics (all_users + debug/prod). */
    fun subscribeDefaultTopics(context: Context) {
        try {
            val messaging = FirebaseMessaging.getInstance()
            val topics = announcementTopics(BuildConfig.DEBUG, BuildConfig.BOXLORE_ISOLATED_TESTS, !BuildConfig.BOXLORE_DIRECT_UPDATES || context.isInstalledFromPlayStore())
            topics.first.forEach { messaging.subscribeToTopic(it) }
            topics.second.forEach { messaging.unsubscribeFromTopic(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed FCM init", e)
        }
    }

    /**
     * After a backup restore, re-subscribe per-podcast topics once (sentinel in noBackupFilesDir).
     */
    suspend fun reconcileAfterRestoreIfNeeded(context: Context, subscriptionRepository: SubscriptionRepository,) {
        subscriptionRepository.requestRssNotificationReconciliation()
        val sentinel = File(context.noBackupFilesDir, SENTINEL_NAME)
        if (!sentinel.exists()) {
            subscriptionRepository.reconcileFcmTopicSubscriptions()
            try {
                sentinel.createNewFile()
            } catch (e: Exception) {
                Log.e("FCM_Topic", "Failed to write sentinel", e)
            }
        }
    }
}

internal fun announcementTopics(debug: Boolean, isolated: Boolean, play: Boolean): Pair<Set<String>, Set<String>> {
    val all = setOf("all_users", "prod_users", "debug_users", "test_users", "play_users", "direct_users")
    val subscribed = when {
        isolated -> setOf("test_users")
        debug -> setOf("all_users", "debug_users")
        play -> setOf("all_users", "prod_users", "play_users")
        else -> setOf("all_users", "prod_users", "direct_users")
    }
    return subscribed to (all - subscribed)
}
