package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What one `GET /podcast` said about a show, for jobs that must tell "the server has no such show"
 * apart from "the server couldn't answer now" ([PodcastRepository.getPodcastDetails] answers null
 * for both).
 */
sealed interface PodcastDetailsAnswer {
    data class Found(val podcast: Podcast) : PodcastDetailsAnswer

    /** A 404, or a 2xx without a show. */
    data object NotFound : PodcastDetailsAnswer

    /** A 429: ask again after [retryAfterMs] (null when the answer didn't say). */
    data class RateLimited(val retryAfterMs: Long?) : PodcastDetailsAnswer

    /** Another failed answer, or no connection. */
    data object Unavailable : PodcastDetailsAnswer
}

/**
 * [PodcastRepository.getPodcastDetails] with the failure kept: a renumbered show is reported the
 * same way, and a 429 also pauses the `/podcast` route in [PodcastRepository.routeBudget].
 */
suspend fun PodcastRepository.getPodcastDetailsAnswer(feedId: String): PodcastDetailsAnswer = withContext(Dispatchers.IO) {
    if (feedId.startsWith("rss:")) {
        return@withContext getPodcastDetails(feedId)?.let { PodcastDetailsAnswer.Found(it) } ?: PodcastDetailsAnswer.NotFound
    }
    val answer =
        try {
            podcastDetailsAnswerOf(executeGetPodcastRequest(feedId))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            PodcastDetailsAnswer.Unavailable
        }
    when (answer) {
        is PodcastDetailsAnswer.Found -> reportRenumbering(feedId, answer.podcast)
        is PodcastDetailsAnswer.RateLimited -> routeBudget.rateLimited(ApiRouteBudget.PODCAST, answer.retryAfterMs)
        else -> Unit
    }
    answer
}

private fun PodcastRepository.podcastDetailsAnswerOf(response: retrofit2.Response<cx.aswin.boxlore.core.network.model.PodcastResponse>): PodcastDetailsAnswer {
    val feed = if (response.isSuccessful) response.body()?.feed else null
    return when {
        feed != null -> PodcastDetailsAnswer.Found(mapPodcastResponseFeed(feed))
        response.isSuccessful || response.code() == HTTP_NOT_FOUND -> PodcastDetailsAnswer.NotFound
        response.code() == HTTP_TOO_MANY_REQUESTS -> PodcastDetailsAnswer.RateLimited(retryAfterMs(response))
        else -> PodcastDetailsAnswer.Unavailable
    }
}

/** A response's `Retry-After` in milliseconds (the API sends whole seconds); null when absent. */
internal fun retryAfterMs(response: retrofit2.Response<*>): Long? = response.headers()["Retry-After"]?.trim()?.toLongOrNull()?.takeIf { it >= 0L }?.times(MS_PER_SECOND)

/**
 * `GET /podcast` for library-wide jobs, through [budget] (one per process,
 * [PodcastRepository.routeBudget]): [whenRoom] asks only when the route has room now (null
 * otherwise), [waiting] waits for room first.
 */
class PacedPodcastLookups(
    private val budget: ApiRouteBudget,
    private val lookup: suspend (String) -> PodcastDetailsAnswer,
) {
    constructor(repository: PodcastRepository) : this(repository.routeBudget, repository::getPodcastDetailsAnswer)

    suspend fun whenRoom(podcastId: String): PodcastDetailsAnswer? {
        if (!budget.tryAcquire(ApiRouteBudget.PODCAST)) return null
        return lookup(podcastId).also { notePause(it) }
    }

    suspend fun waiting(podcastId: String): PodcastDetailsAnswer {
        budget.acquire(ApiRouteBudget.PODCAST)
        return lookup(podcastId).also { notePause(it) }
    }

    private suspend fun notePause(answer: PodcastDetailsAnswer) {
        if (answer is PodcastDetailsAnswer.RateLimited) budget.rateLimited(ApiRouteBudget.PODCAST, answer.retryAfterMs)
    }
}

/**
 * What a lookup of a followed show's missing https address learned. Only [Answered] is stamped, so
 * a 429, a server error, no connection or a full route budget ([NoAnswer]) is asked again on the
 * next pass instead of a day later.
 */
sealed interface FeedUrlLookup {
    /** The server answered: [httpsFeedUrl] is its https address, null when it has none; [httpOnly] when it lists only an http one. */
    data class Answered(val httpsFeedUrl: String?, val httpOnly: Boolean = false) : FeedUrlLookup {
        /**
         * The lookup stamp to save at [now]. Stamps are read as "asked at" against
         * [LOOKUP_INTERVAL_MS], so an http-only answer is stamped ahead and asked again only after
         * [HTTP_ONLY_BACKOFF_MS].
         */
        fun stampAt(now: Long): Long = if (httpsFeedUrl == null && httpOnly) now + HTTP_ONLY_BACKOFF_MS - LOOKUP_INTERVAL_MS else now
    }

    data object NoAnswer : FeedUrlLookup

    companion object {
        /** A show is asked again this long after its last answer. */
        const val LOOKUP_INTERVAL_MS = LocalEpisodeCatalogRepository.FEED_URL_LOOKUP_INTERVAL_MS

        /** How long a show the server lists only with an http address waits before it is asked again. */
        const val HTTP_ONLY_BACKOFF_MS = 7L * 24 * 60 * 60 * 1000

        fun of(answer: PodcastDetailsAnswer?): FeedUrlLookup = when (answer) {
            is PodcastDetailsAnswer.Found -> {
                val address = answer.podcast.feedUrl
                val https = TrackedPodcastRtdbLogic.httpsFeedUrl(address)
                Answered(https, httpOnly = https == null && address?.trim()?.startsWith("http://", ignoreCase = true) == true)
            }
            PodcastDetailsAnswer.NotFound -> Answered(null)
            is PodcastDetailsAnswer.RateLimited, PodcastDetailsAnswer.Unavailable, null -> NoAnswer
        }

        /** True when a show last stamped at [lastLookupAt] (0: never) is due at [now]; a stamp set ahead waits. */
        fun isDue(lastLookupAt: Long, now: Long): Boolean = lastLookupAt <= 0L || now - lastLookupAt >= LOOKUP_INTERVAL_MS
    }
}

/** One paced missing-address lookup for [podcastId], asked only when the `/podcast` route has room now. */
suspend fun PodcastRepository.lookupFeedUrlWhenRoom(podcastId: String): FeedUrlLookup = FeedUrlLookup.of(PacedPodcastLookups(this).whenRoom(podcastId))

private const val HTTP_NOT_FOUND = 404
private const val MS_PER_SECOND = 1000L
