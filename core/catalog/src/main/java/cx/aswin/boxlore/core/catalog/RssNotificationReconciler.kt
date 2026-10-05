package cx.aswin.boxlore.core.catalog

import com.google.android.gms.tasks.Task
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import cx.aswin.boxlore.core.database.PodcastEntity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

interface RssNotificationRemote {
    suspend fun publish(record: RssNotificationRegistration, payload: Map<String, String>)
    suspend fun remove(record: RssNotificationRegistration)
}

/** Completion is acknowledged for both RTDB and FCM; journal entries survive either failure. */
class FirebaseRssNotificationRemote : RssNotificationRemote {
    override suspend fun publish(record: RssNotificationRegistration, payload: Map<String, String>) {
        FirebaseDatabase.getInstance().getReference("tracked_podcasts").child(record.key).setValue(payload).awaitResult()
        FirebaseMessaging.getInstance().subscribeToTopic(record.topic).awaitResult()
    }

    override suspend fun remove(record: RssNotificationRegistration) {
        FirebaseDatabase.getInstance().getReference("tracked_podcasts").child(record.key).removeValue().awaitResult()
        FirebaseMessaging.getInstance().unsubscribeFromTopic(record.topic).awaitResult()
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase notification operation failed"))
            }
        }
    }
}

/** The publication journal also owns cleanup after disable, deletion, URL change, or process death. */
class RssNotificationReconciler(
    private val store: RssNotificationRegistrationStore,
    private val getShow: suspend (String) -> PodcastEntity?,
    private val consent: RssNotificationConsent,
    private val remote: RssNotificationRemote,
) {
    suspend fun reconcile(): Boolean {
        var succeeded = true
        // Retire the legacy topic first; obsolete URL scopes are also removed in this pass.
        for (record in store.records().sortedBy { it.feedFingerprint.isNotEmpty() }) {
            try {
                val completed = withTimeoutOrNull(30_000L) { reconcile(record) } ?: false
                if (!completed) succeeded = false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                succeeded = false
            }
        }
        return succeeded
    }

    private suspend fun reconcile(record: RssNotificationRegistration): Boolean {
        val show = getShow(record.podcastId)
        val url = TrackedPodcastRtdbLogic.httpsFeedUrl(show?.feedUrl)
        val accepted = isActive(record, show, url)
        if (accepted && url != null && show != null) {
            remote.publish(record, TrackedPodcastRtdbLogic.payload(show.title, show.imageUrl, url))
            // A disable during publication still leaves the obligation available to the next pass.
            val current = getShow(record.podcastId)
            if (isActive(record, current, TrackedPodcastRtdbLogic.httpsFeedUrl(current?.feedUrl))) return true
        }
        remote.remove(record)
        return store.forget(record)
    }

    private fun isActive(record: RssNotificationRegistration, show: PodcastEntity?, url: String?): Boolean {
        if (show?.isSubscribed != true || !show.notificationsEnabled || url == null) return false
        return record.feedFingerprint == TrackedPodcastRtdbLogic.feedFingerprint(url) && consent.isAccepted(record.podcastId, url)
    }
}
