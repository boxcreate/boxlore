package cx.aswin.boxlore.share

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
import cx.aswin.boxlore.core.catalog.EpisodeShareRegistrar
import cx.aswin.boxlore.core.catalog.EpisodeShareRegistrationOutcome
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.network.model.V2EpisodeRegisterRequest
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Registers a shared episode with the API in the background after the share itself could not reach
 * it in time (offline, a timeout, no API session yet), so older builds and web previews can open the
 * link that already went out. Retries with WorkManager's exponential backoff, at most [MAX_ATTEMPTS]
 * runs; an answer the API can't take ends it.
 *
 * The unique work name ([workName], prefix [WORK_NAME_PREFIX]) is a stable identity: work enqueued by
 * one build is found again by the next.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
 * COMPAT(own-ids): shared links stay openable on every build. Permanent: never remove (MIGRATION.md §5).
 */
class EpisodeShareRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    internal var register: suspend (V2EpisodeRegisterRequest) -> EpisodeShareRegistrationOutcome = { request ->
        EpisodeShareRegistrar.registerOnce(SharedAppDependenciesHolder.require().podcastRepository.apiV2, request)
    }

    override suspend fun doWork(): Result {
        val request = requestFrom(inputData) ?: return Result.failure()
        return when (register(request)) {
            EpisodeShareRegistrationOutcome.DONE, EpisodeShareRegistrationOutcome.DROPPED -> Result.success()
            EpisodeShareRegistrationOutcome.RETRY -> if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        /** Permanent unique-work name prefix; the rest is a hash of the show and catalog key. Never rename. */
        const val WORK_NAME_PREFIX = "episode-share-registration-"

        /** Runs per shared episode, the first included. */
        const val MAX_ATTEMPTS = 8

        private const val BACKOFF_SECONDS = 30L
        private const val KEY_SHOW = "show"
        private const val KEY_GUID = "guid"
        private const val KEY_ENCLOSURE_URL = "enclosureUrl"
        private const val NAME_HASH_CHARS = 32

        /** One name per (show, key): sharing the same episode again while a retry waits keeps that retry. */
        fun workName(request: V2EpisodeRegisterRequest): String {
            val identity = "${request.show}\u0000${request.guid.orEmpty()}\u0000${request.enclosureUrl.orEmpty()}"
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            return WORK_NAME_PREFIX + digest.joinToString("") { "%02x".format(it) }.take(NAME_HASH_CHARS)
        }

        fun workRequest(request: V2EpisodeRegisterRequest): OneTimeWorkRequest {
            val input = Data.Builder().putLong(KEY_SHOW, request.show)
            request.guid?.let { input.putString(KEY_GUID, it) }
            request.enclosureUrl?.let { input.putString(KEY_ENCLOSURE_URL, it) }
            return OneTimeWorkRequestBuilder<EpisodeShareRegistrationWorker>()
                .setInputData(input.build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        fun enqueue(context: Context, request: V2EpisodeRegisterRequest) {
            WorkManager.getInstance(context).enqueueUniqueWork(workName(request), ExistingWorkPolicy.KEEP, workRequest(request))
        }

        /** The request a work item carries, or null when its input is not a valid registration. */
        internal fun requestFrom(data: Data): V2EpisodeRegisterRequest? = try {
            V2EpisodeRegisterRequest(
                show = data.getLong(KEY_SHOW, 0L),
                guid = data.getString(KEY_GUID),
                enclosureUrl = data.getString(KEY_ENCLOSURE_URL),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
