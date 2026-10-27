package cx.aswin.boxlore.fcm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.catalog.NotificationRegistrationOutcome
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Registers a Podcast Index show's new-episode notifications again after its saved feed address
 * changed, and retries until Firebase acknowledges it: the same `tracked_podcasts/<id>` node and
 * topic as turning notifications on ([cx.aswin.boxlore.core.catalog.PodcastNotificationRegistrar]).
 * Network-gated, exponential backoff from 30 s, at most [MAX_ATTEMPTS] runs, so it never retries
 * forever like the `rss:` journal worker ([RssNotificationSyncWorker]).
 *
 * The unique work name ([workName], prefix [WORK_NAME_PREFIX]) is a stable identity. REPLACE: the
 * worker reads the show's row when it runs, so a newer address change restarts it with fresh attempts.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): notifications follow a corrected address. Permanent: never remove (MIGRATION.md §5).
 */
class PodcastNotificationRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    internal var register: suspend (podcastId: String) -> NotificationRegistrationOutcome = { podcastId ->
        SharedAppDependenciesHolder.require().subscriptionRepository.registerPodcastNotifications(podcastId)
    }

    override suspend fun doWork(): Result {
        val podcastId = inputData.getString(KEY_PODCAST_ID)?.takeIf { it.isNotBlank() && !it.startsWith("rss:") } ?: return Result.failure()
        val outcome =
            try {
                register(podcastId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                NotificationRegistrationOutcome.RETRY
            }
        return when (outcome) {
            NotificationRegistrationOutcome.DONE, NotificationRegistrationOutcome.DROPPED -> Result.success()
            NotificationRegistrationOutcome.RETRY -> if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        /** Permanent unique-work name prefix; the rest is the show ID. Never rename. */
        const val WORK_NAME_PREFIX = "podcast-notification-registration-"

        /** Runs per address change, the first included. */
        const val MAX_ATTEMPTS = 8

        private const val BACKOFF_SECONDS = 30L
        private const val KEY_PODCAST_ID = "podcastId"

        fun workName(podcastId: String): String = WORK_NAME_PREFIX + podcastId

        fun workRequest(podcastId: String): OneTimeWorkRequest = OneTimeWorkRequestBuilder<PodcastNotificationRegistrationWorker>()
            .setInputData(Data.Builder().putString(KEY_PODCAST_ID, podcastId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Asks for [podcastId]'s notifications to be registered again; `rss:` shows have their own journal. */
        fun enqueue(context: Context, podcastId: String) {
            if (podcastId.isBlank() || podcastId.startsWith("rss:")) return
            WorkManager.getInstance(context).enqueueUniqueWork(workName(podcastId), ExistingWorkPolicy.REPLACE, workRequest(podcastId))
        }
    }
}
