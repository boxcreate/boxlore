package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The show page's next page after a failed request (429, 5xx, offline): asked again a few times, never read as the end of the list. */
class ShowPageRetryTest {
    @Test
    fun `a failed page is asked for again until it loads, waiting longer each time`() = runTest {
        val waits = mutableListOf<Long>()
        var asked = 0
        val page =
            ShowPageRetry.load(
                load = {
                    when (++asked) {
                        1 -> PodcastRepository.EpisodePage(emptyList(), hasMore = false, failed = true)
                        2 -> throw IOException("offline")
                        else -> PodcastRepository.EpisodePage(listOf(TestFixtures.episode(id = "e1")), hasMore = true)
                    }
                },
                waitMs = { 0L },
                sleep = { waits += it },
            )

        assertFalse(page.failed)
        assertEquals(listOf("e1"), page.episodes.map { it.id })
        assertEquals(listOf(ShowPageRetry.FIRST_WAIT_MS, 2 * ShowPageRetry.FIRST_WAIT_MS), waits)
    }

    @Test
    fun `a route paused by a 429 waits for its Retry-After`() = runTest {
        val waits = mutableListOf<Long>()
        var asked = 0
        ShowPageRetry.load(
            load = { PodcastRepository.EpisodePage(emptyList(), hasMore = false, failed = ++asked == 1) },
            waitMs = { 60_000L },
            sleep = { waits += it },
        )
        assertEquals(listOf(60_000L), waits)
    }

    @Test
    fun `a page that keeps failing gives up after a few tries instead of spinning forever`() = runTest {
        val waits = mutableListOf<Long>()
        var asked = 0
        val result =
            runCatching {
                ShowPageRetry.load(
                    load = {
                        asked++
                        PodcastRepository.EpisodePage(emptyList(), hasMore = false, failed = true)
                    },
                    waitMs = { 0L },
                    sleep = { waits += it },
                )
            }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(ShowPageRetry.MAX_ATTEMPTS, asked)
        assertEquals(ShowPageRetry.MAX_ATTEMPTS - 1, waits.size)
    }

    @Test
    fun `the wait stops growing at its cap`() {
        assertEquals(ShowPageRetry.MAX_WAIT_MS, ShowPageRetry.backoffMs(20))
    }
}
