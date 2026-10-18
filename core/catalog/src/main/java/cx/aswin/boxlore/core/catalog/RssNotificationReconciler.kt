package cx.aswin.boxlore.core.catalog

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
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
class FirebaseRssNotificationRemote internal constructor(private val firebase: TrackedPodcastFirebase) : RssNotificationRemote {
    constructor() : this(LiveTrackedPodcastFirebase)

    override suspend fun publish(record: RssNotificationRegistration, payload: Map<String, String>) {
        firebase.set(record.key, payload)
        firebase.subscribe(record.topic)
    }

    /**
     * Live rules refuse deleting a tracked node, so cleanup drops only `feedUrl`, which stops the
     * new-episode checker for this scope. The rules refuse that delete only when no node is left
     * to hold it (never published, or already gone), so a refusal means there is nothing to stop.
     */
    override suspend fun remove(record: RssNotificationRegistration) {
        try {
            firebase.delete("${record.key}/feedUrl")
        } catch (_: TrackedPodcastWriteRefused) {
            // Nothing is published under this key.
        }
        firebase.unsubscribe(record.topic)
    }
}

/** RTDB `tracked_podcasts` writes and FCM topic changes, each returning once the server acknowledges it. */
internal interface TrackedPodcastFirebase {
    suspend fun set(key: String, payload: Map<String, String>)

    /** Deletes `tracked_podcasts/<path>`; throws [TrackedPodcastWriteRefused] when the rules refuse it. */
    suspend fun delete(path: String)
    suspend fun subscribe(topic: String)
    suspend fun unsubscribe(topic: String)
}

internal class TrackedPodcastWriteRefused : Exception("Tracked podcast rules refused the write")

private object LiveTrackedPodcastFirebase : TrackedPodcastFirebase {
    override suspend fun set(key: String, payload: Map<String, String>) = acknowledged { node(key).setValue(payload, it) }

    override suspend fun delete(path: String) = acknowledged { node(path).removeValue(it) }

    override suspend fun subscribe(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic).awaitResult()
    }

    override suspend fun unsubscribe(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).awaitResult()
    }

    private fun node(path: String) = FirebaseDatabase.getInstance().getReference("tracked_podcasts").child(path)

    private suspend fun acknowledged(write: (DatabaseReference.CompletionListener) -> Unit): Unit = suspendCancellableCoroutine { continuation ->
        write(
            DatabaseReference.CompletionListener { error, _ ->
                when {
                    error == null -> continuation.resume(Unit)
                    error.code == DatabaseError.PERMISSION_DENIED -> continuation.resumeWithException(TrackedPodcastWriteRefused())
                    else -> continuation.resumeWithException(error.toException())
                }
            },
        )
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
