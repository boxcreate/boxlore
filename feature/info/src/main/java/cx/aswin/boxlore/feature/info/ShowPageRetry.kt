package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * The show page's next page of episodes when a request fails (a 429, a server error, no
 * connection): the list must not end there, so the page is asked for again while the loading cards
 * stay up, up to [MAX_ATTEMPTS] times, waiting [FIRST_WAIT_MS] and doubling up to [MAX_WAIT_MS], or
 * longer while the route is paused after a 429 ([waitMs]). After the last try it throws, and the
 * page shows that the episodes couldn't load with a way to try again
 * ([PodcastInfoUiState.Success.pageLoadFailed]).
 */
internal object ShowPageRetry {
    const val FIRST_WAIT_MS = 2_000L
    const val MAX_WAIT_MS = 30_000L
    const val MAX_ATTEMPTS = 3

    suspend fun load(
        load: suspend () -> PodcastRepository.EpisodePage,
        waitMs: suspend () -> Long,
        sleep: suspend (Long) -> Unit,
        maxAttempts: Int = MAX_ATTEMPTS,
    ): PodcastRepository.EpisodePage {
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            if (attempt > 0) sleep(maxOf(waitMs(), backoffMs(attempt - 1)))
            val page =
                try {
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Treated like a page that came back failed: asked again after the wait.
                    lastError = e
                    null
                }
            if (page != null && !page.failed) return page
        }
        throw IOException("The episodes page couldn't be read after $maxAttempts tries", lastError)
    }

    fun backoffMs(attempt: Int): Long = (FIRST_WAIT_MS shl attempt.coerceIn(0, MAX_SHIFT)).coerceAtMost(MAX_WAIT_MS)

    private const val MAX_SHIFT = 5
}
