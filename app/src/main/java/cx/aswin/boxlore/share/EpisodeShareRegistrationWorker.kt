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
 * failed runs; an answer the API can't take ends it. An answer that wants a better API session
 * ([EpisodeShareRegistrationOutcome.AWAIT_SESSION], which the app gets on screen) isn't a failed
 * run: the work comes back after [SESSION_WAIT_HOURS] as a fresh request that carries its count
 * of failed runs, at most [MAX_SESSION_WAITS] times.
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

    /** Queues the follow-up after a session wait; it runs once this work has finished. */
    internal var requeue: (V2EpisodeRegisterRequest, OneTimeWorkRequest) -> Unit = { request, next ->
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(workName(request), ExistingWorkPolicy.APPEND_OR_REPLACE, next)
    }

    override suspend fun doWork(): Result {
        val request = requestFrom(inputData) ?: return Result.failure()
        // Every retry of this request was a failed run; session waits start a new request instead.
        val failedRuns = inputData.getInt(KEY_FAILED_RUNS, 0) + runAttemptCount
        return when (register(request)) {
            EpisodeShareRegistrationOutcome.DONE, EpisodeShareRegistrationOutcome.DROPPED -> Result.success()
            EpisodeShareRegistrationOutcome.RETRY -> if (failedRuns + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
            EpisodeShareRegistrationOutcome.AWAIT_SESSION -> {
                val waits = inputData.getInt(KEY_SESSION_WAITS, 0) + 1
                if (waits > MAX_SESSION_WAITS) return Result.failure()
                requeue(request, workRequest(request, failedRuns, waits, delayHours = SESSION_WAIT_HOURS))
                Result.success()
            }
        }
    }

    companion object {
        /** Permanent unique-work name prefix; the rest is a hash of the show and catalog key. Never rename. */
        const val WORK_NAME_PREFIX = "episode-share-registration-"

        /** Failed runs per shared episode, the first included. */
        const val MAX_ATTEMPTS = 8

        /** Waits for a better API session per shared episode: four a day for a month. */
        const val MAX_SESSION_WAITS = 120
        const val SESSION_WAIT_HOURS = 6L

        private const val BACKOFF_SECONDS = 30L
        private const val KEY_SHOW = "show"
        private const val KEY_GUID = "guid"
        private const val KEY_ENCLOSURE_URL = "enclosureUrl"
        private const val KEY_FAILED_RUNS = "failedRuns"
        private const val KEY_SESSION_WAITS = "sessionWaits"
        private const val NAME_HASH_CHARS = 32

        /**
         * One name per (show, key): sharing the same episode again while a retry waits keeps that
         * retry. The key is the GUID, or the enclosure URL without one; the enclosure a GUID request
         * also carries isn't part of the name.
         */
        fun workName(request: V2EpisodeRegisterRequest): String {
            val enclosureKey = if (request.guid == null) request.enclosureUrl.orEmpty() else ""
            val identity = "${request.show}\u0000${request.guid.orEmpty()}\u0000$enclosureKey"
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            return WORK_NAME_PREFIX + digest.joinToString("") { "%02x".format(it) }.take(NAME_HASH_CHARS)
        }

        fun workRequest(request: V2EpisodeRegisterRequest, failedRuns: Int = 0, sessionWaits: Int = 0, delayHours: Long = 0L): OneTimeWorkRequest {
            val input = Data.Builder().putLong(KEY_SHOW, request.show)
            request.guid?.let { input.putString(KEY_GUID, it) }
            request.enclosureUrl?.let { input.putString(KEY_ENCLOSURE_URL, it) }
            if (failedRuns > 0) input.putInt(KEY_FAILED_RUNS, failedRuns)
            if (sessionWaits > 0) input.putInt(KEY_SESSION_WAITS, sessionWaits)
            return OneTimeWorkRequestBuilder<EpisodeShareRegistrationWorker>()
                .setInputData(input.build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .apply { if (delayHours > 0L) setInitialDelay(delayHours, TimeUnit.HOURS) }
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
