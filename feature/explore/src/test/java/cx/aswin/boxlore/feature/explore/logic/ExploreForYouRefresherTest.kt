package cx.aswin.boxlore.feature.explore.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Explore's For you refreshes, wired as the ViewModel wires them. The followed shows come from a
 * replaying shared flow, as Room re-emits the same rows after unrelated writes.
 *
 * A visible screen keeps a wake-up for its next stale refresh or retry, so these tests advance time
 * by bounded steps ([settle]) instead of running until idle while the screen is shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreForYouRefresherTest {
    /** Picks a request brought for [key]; not [fresh] when they are the last good picks kept from earlier. */
    private data class Picks(val key: ExploreForYouRefreshKey, val fresh: Boolean)

    private class Harness(test: TestScope) {
        // Not the test's background scope: time advances run this work too.
        val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler))
        val region = MutableStateFlow("us")
        val followed = MutableSharedFlow<Set<String>>(replay = 1)
        val languages = MutableStateFlow(listOf("en"))
        val pickedForYou = MutableStateFlow(true)
        val requests = mutableListOf<ExploreForYouRefreshKey>()
        val requestTimes = mutableListOf<Long>()
        val published = mutableListOf<ExploreForYouRefreshKey>()
        val loadingChanges = mutableListOf<Boolean>()
        var loading = false
        var fails = false
        var lastGoodOnly = false

        /** How a request behaves; by default it takes half a second and brings picks unless [fails]. */
        var request: suspend (ExploreForYouRefreshKey) -> Picks? = { key ->
            delay(REQUEST_MS)
            if (fails) null else Picks(key, fresh = !lastGoodOnly)
        }

        val refresher =
            ExploreForYouRefresher(
                scope = scope,
                clockMs = { test.testScheduler.currentTime },
                fetch = { key ->
                    requests += key
                    requestTimes += test.testScheduler.currentTime
                    request(key)
                },
                publish = { picks: Picks -> published += picks.key },
                onLoadingChanged = {
                    loading = it
                    loadingChanges += it
                },
                isFresh = { it.fresh },
            )

        init {
            scope.launch { refresher.follow(region, followed, languages, pickedForYou) }
        }
    }

    private fun runHarness(block: suspend TestScope.(Harness) -> Unit) = runTest {
        val h = Harness(this)
        try {
            block(h)
        } finally {
            h.scope.cancel()
        }
    }

    private fun shows(count: Int): Set<String> = (1..count).map { "pi-$it" }.toSet()

    private fun key(followed: Set<String>, region: String = "us", languages: List<String> = listOf("en")) =
        ExploreForYouRefreshKey(region, followed, languages)

    /** Lets running requests and slot waits finish without reaching a stale refresh or a retry. */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    @Test
    fun `going back to a region after another region's last good picks replaced its picks asks again`() = runHarness { h ->
        h.request = { key ->
            delay(REQUEST_MS)
            Picks(key, fresh = key.region != "gb")
        }
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        settle()
        h.region.value = "gb"
        settle()
        assertEquals(listOf(key(shows(3)), key(shows(3), region = "gb")), h.published)

        h.region.value = "us"
        settle()

        assertEquals(3, h.requests.size)
        assertEquals(key(shows(3)), h.requests.last())
        assertEquals(key(shows(3)), h.published.last())
    }

    @Test
    fun `going back to a region after another region's request published nothing asks nothing`() = runHarness { h ->
        h.request = { key ->
            delay(REQUEST_MS)
            if (key.region == "gb") null else Picks(key, fresh = true)
        }
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        settle()
        h.region.value = "gb"
        settle()
        assertEquals(listOf(key(shows(3))), h.published)

        h.region.value = "us"
        settle()

        assertEquals(2, h.requests.size)
        assertEquals(listOf(key(shows(3))), h.published)
    }

    @Test
    fun `the same followed shows again asks nothing`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        settle()
        assertEquals(1, h.requests.size)

        repeat(50) {
            h.followed.emit(shows(3))
            advanceTimeBy(100)
        }
        advanceTimeBy(5 * 60_000L)

        assertEquals(1, h.requests.size)
    }

    @Test
    fun `a sign-in restore asks at most three times and ends with every show`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)

        for (count in 1..25) {
            h.followed.emit(shows(count))
            advanceTimeBy(1_200)
        }
        settle()

        assertTrue(h.requests.size <= 3, "requests: ${h.requests.size}")
        assertEquals(shows(25), h.requests.last().followedIds)
        assertEquals(h.requests.last(), h.published.last())
        assertFalse(h.loading)
    }

    @Test
    fun `an OPML import asks at most once per 30 seconds`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)

        for (count in 1..300) {
            h.followed.emit(shows(count))
            advanceTimeBy(1_500)
        }
        settle()

        assertTrue(h.requests.size <= 16, "requests: ${h.requests.size}")
        assertEquals(shows(300), h.requests.last().followedIds)
        assertEquals(h.requests.last(), h.published.last())
    }

    @Test
    fun `hidden asks nothing, then once with the latest shows when shown`() = runHarness { h ->
        for (count in 1..300) {
            h.followed.emit(shows(count))
            advanceTimeBy(1_500)
        }
        // Hidden, nothing is scheduled at all.
        advanceUntilIdle()
        assertEquals(0, h.requests.size)

        h.refresher.onVisibilityChanged(true)
        settle()

        assertEquals(listOf(key(shows(300))), h.requests)
    }

    @Test
    fun `hiding and showing with nothing changed asks nothing`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        settle()
        assertEquals(1, h.requests.size)

        // A rotation, a resize across the tablet gates, a fold: each hides and shows the screen.
        repeat(4) {
            h.refresher.onVisibilityChanged(false)
            h.followed.emit(shows(3))
            h.refresher.onVisibilityChanged(true)
            advanceTimeBy(10)
        }
        settle()

        assertEquals(1, h.requests.size)
    }

    @Test
    fun `a configuration change while a request runs neither restarts nor drops it`() = runHarness { h ->
        h.request = { key ->
            delay(5_000)
            Picks(key, fresh = true)
        }
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()

        h.refresher.onVisibilityChanged(false)
        h.refresher.onVisibilityChanged(true)
        settle()

        assertEquals(1, h.requests.size)
        assertEquals(h.requests, h.published)
    }

    @Test
    fun `switching Picked for you off cancels at once and switching it on asks at once`() = runHarness { h ->
        h.request = { key ->
            delay(10_000)
            Picks(key, fresh = true)
        }
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        assertEquals(1, h.requests.size)
        assertTrue(h.loading)

        h.pickedForYou.value = false
        runCurrent()
        assertFalse(h.loading)
        h.followed.emit(shows(4))
        // Off, nothing is scheduled at all.
        advanceUntilIdle()
        assertEquals(1, h.requests.size)
        assertTrue(h.published.isEmpty())

        val switchedOnAt = currentTime
        h.pickedForYou.value = true
        runCurrent()
        assertEquals(2, h.requests.size)
        assertEquals(switchedOnAt, currentTime)
        settle()
        assertEquals(listOf(key(shows(4))), h.published)
    }

    @Test
    fun `a region change asks at once`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        advanceTimeBy(5_000)
        h.followed.emit(shows(4))
        advanceTimeBy(1_000)
        assertEquals(1, h.requests.size)

        h.region.value = "in"
        runCurrent()

        assertEquals(2, h.requests.size)
        assertEquals(key(shows(4), region = "in"), h.requests.last())
    }

    @Test
    fun `a language change while shown asks at once, even right after a refresh`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals(1, h.requests.size)

        // English switched off for Hindi: the list no longer carries English.
        h.languages.value = listOf("hi")
        runCurrent()

        assertEquals(2, h.requests.size)
        assertEquals(key(shows(3), languages = listOf("hi")), h.requests.last())
        settle()
        assertEquals(2, h.requests.size)
    }

    @Test
    fun `a language change while hidden asks once when shown`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        settle()
        h.refresher.onVisibilityChanged(false)

        h.languages.value = listOf("en", "hi")
        advanceTimeBy(60_000)
        assertEquals(1, h.requests.size)

        h.refresher.onVisibilityChanged(true)
        runCurrent()
        assertEquals(2, h.requests.size)
        assertEquals(key(shows(3), languages = listOf("en", "hi")), h.requests.last())
        settle()
        assertEquals(2, h.requests.size)
    }

    @Test
    fun `picks over 30 minutes old are asked for once when crossing the boundary while shown, never more often`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        // The first answer arrives half a second in.
        advanceTimeBy(STALE_MS - 1_000)
        assertEquals(1, h.requests.size)

        advanceTimeBy(2_000)
        assertEquals(2, h.requests.size)
        assertEquals(STALE_MS + REQUEST_MS, h.requestTimes.last())
        // A stale refresh keeps the picks on screen without the loading state.
        assertFalse(h.loading)
        runCurrent()

        advanceTimeBy(STALE_MS - 1_000)
        assertEquals(2, h.requests.size)
        advanceTimeBy(2_000)
        assertEquals(3, h.requests.size)
        assertEquals(listOf(key(shows(3))), h.requests.distinct())
    }

    @Test
    fun `picks over 30 minutes old are asked for exactly once when shown`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        settle()
        h.refresher.onVisibilityChanged(false)

        // Hidden, a stale refresh waits for the screen.
        advanceTimeBy(STALE_MS)
        assertEquals(1, h.requests.size)

        h.refresher.onVisibilityChanged(true)
        runCurrent()
        assertEquals(2, h.requests.size)

        repeat(3) {
            h.refresher.onVisibilityChanged(false)
            h.refresher.onVisibilityChanged(true)
            advanceTimeBy(10)
        }
        settle()
        assertEquals(2, h.requests.size)
        assertEquals(listOf(key(shows(3)), key(shows(3))), h.published)
    }

    @Test
    fun `going back to the shown picks cancels a refresh for an older change and keeps the stale refresh`() = runHarness { h ->
        h.request = { key ->
            delay(10_000)
            Picks(key, fresh = true)
        }
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        advanceTimeBy(11_000)
        // Following one more show waits for the 30-second slot, then runs.
        h.followed.emit(shows(4))
        advanceTimeBy(20_000)
        assertEquals(2, h.requests.size)
        assertTrue(h.loading)

        h.followed.emit(shows(3))
        runCurrent()
        assertFalse(h.loading)
        assertEquals(listOf(key(shows(3))), h.published)

        // The first answer came 10 seconds in; its stale refresh is still due.
        advanceTimeBy(STALE_MS)
        assertEquals(3, h.requests.size)
        assertEquals(key(shows(3)), h.requests.last())
        assertEquals(10_000L + STALE_MS, h.requestTimes.last())
    }

    @Test
    fun `failed requests back off 1, 2, 4 minutes and on up to 30, and a key change starts again`() = runHarness { h ->
        h.fails = true
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()

        advanceTimeBy(4 * 60 * 60_000L)

        // Each retry waits from the previous failure, which comes half a second after its start.
        val gaps = h.requestTimes.zipWithNext { a, b -> b - a }
        val expected = listOf(60_000L, 120_000L, 240_000L, 480_000L, 960_000L, MAX_RETRY_MS).map { it + REQUEST_MS }
        assertEquals(expected, gaps.take(6))
        assertTrue(gaps.size >= 7, "gaps: $gaps")
        assertTrue(gaps.drop(5).all { it == MAX_RETRY_MS + REQUEST_MS }, "gaps: $gaps")
        assertTrue(h.published.isEmpty())

        val before = h.requests.size
        val changedAt = currentTime
        h.region.value = "in"
        runCurrent()
        assertEquals(before + 1, h.requests.size)
        assertEquals(changedAt, h.requestTimes.last())
        advanceTimeBy(60_000L + REQUEST_MS - 1)
        assertEquals(before + 1, h.requests.size)
        advanceTimeBy(2)
        assertEquals(before + 2, h.requests.size)
    }

    @Test
    fun `asking again after a failure asks at once with the loading state, and nothing else`() = runHarness { h ->
        h.fails = true
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        advanceTimeBy(1_000)
        assertEquals(1, h.requests.size)

        // The quick check passed: the failed picks are asked for now, not a minute later, and show loading.
        h.fails = false
        h.loadingChanges.clear()
        h.refresher.askAgain()
        runCurrent()
        assertEquals(2, h.requests.size)
        assertEquals(listOf(true), h.loadingChanges)
        settle()
        assertEquals(listOf(key(shows(3))), h.published)
        assertFalse(h.loading)

        // Fresh picks are not asked for again.
        h.refresher.askAgain()
        runCurrent()
        assertEquals(2, h.requests.size)
    }

    @Test
    fun `a failure keeps the shown picks and is retried`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        settle()
        assertEquals(listOf(key(shows(3))), h.published)

        h.fails = true
        h.region.value = "in"
        runCurrent()
        advanceTimeBy(1_000)
        // Nothing published: For you keeps the picks it shows.
        assertEquals(listOf(key(shows(3))), h.published)

        h.fails = false
        advanceTimeBy(60_000)
        assertEquals(3, h.requests.size)
        runCurrent()
        assertEquals(listOf(key(shows(3)), key(shows(3), region = "in")), h.published)
    }

    @Test
    fun `last good picks are shown but asked for again with backoff`() = runHarness { h ->
        h.lastGoodOnly = true
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        advanceTimeBy(1_000)
        assertEquals(listOf(key(shows(3))), h.published)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, h.requests.size)
        assertEquals(2, h.published.size)

        // A fresh answer ends the backoff; the next refresh is the stale one.
        h.lastGoodOnly = false
        advanceTimeBy(2 * 60_000L)
        runCurrent()
        assertEquals(listOf(0L, 60_000L + REQUEST_MS, 60_000L + 2 * REQUEST_MS + 120_000L), h.requestTimes)
        advanceTimeBy(STALE_MS - 1_000)
        assertEquals(3, h.requests.size)
    }

    @Test
    fun `showing the screen within the backoff does not retry`() = runHarness { h ->
        h.fails = true
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        advanceTimeBy(1_000)

        h.refresher.onVisibilityChanged(false)
        advanceTimeBy(20_000)
        h.refresher.onVisibilityChanged(true)
        runCurrent()
        assertEquals(1, h.requests.size)

        // The retry comes a minute after the failure, half a second in.
        advanceTimeBy(39_000)
        assertEquals(1, h.requests.size)
        advanceTimeBy(1_000)
        assertEquals(2, h.requests.size)
        runCurrent()

        // Hidden past the next backoff, showing retries at once.
        h.refresher.onVisibilityChanged(false)
        advanceTimeBy(5 * 60_000L)
        assertEquals(2, h.requests.size)
        h.refresher.onVisibilityChanged(true)
        runCurrent()
        assertEquals(3, h.requests.size)
    }

    @Test
    fun `showing the screen within 30 seconds of a refresh waits for the slot when only the followed shows changed`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        advanceTimeBy(1_000)
        h.refresher.onVisibilityChanged(false)

        h.followed.emit(shows(4))
        advanceTimeBy(4_000)
        h.refresher.onVisibilityChanged(true)
        runCurrent()
        assertEquals(1, h.requests.size)

        advanceTimeBy(24_999)
        assertEquals(1, h.requests.size)
        advanceTimeBy(2)
        assertEquals(2, h.requests.size)
        assertEquals(key(shows(4)), h.requests.last())
        runCurrent()

        // Past the slot, showing asks at once.
        h.refresher.onVisibilityChanged(false)
        h.followed.emit(shows(5))
        advanceTimeBy(60_000)
        h.refresher.onVisibilityChanged(true)
        runCurrent()
        assertEquals(3, h.requests.size)
    }

    @Test
    fun `retries never bring the loading state back, a new key's first request does`() = runHarness { h ->
        h.fails = true
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        assertTrue(h.loading)
        advanceTimeBy(1_000)
        assertFalse(h.loading)

        h.loadingChanges.clear()
        advanceTimeBy(10 * 60_000L)
        assertTrue(h.requests.size >= 3, "requests: ${h.requests.size}")
        assertTrue(h.loadingChanges.none { it }, "loading: ${h.loadingChanges}")

        h.region.value = "in"
        runCurrent()
        assertTrue(h.loading)
    }

    @Test
    fun `a newer request cancels the older, which never publishes even past a non-cancellable call`() = runHarness { h ->
        h.request = { key ->
            // A blocking network call ignores cancellation.
            withContext(NonCancellable) { delay(10_000) }
            Picks(key, fresh = true)
        }
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        runCurrent()
        advanceTimeBy(1_000)

        h.region.value = "in"
        runCurrent()
        assertEquals(2, h.requests.size)
        advanceTimeBy(9_500)
        // The first request has returned; the second still runs.
        assertTrue(h.published.isEmpty())
        assertTrue(h.loading)

        advanceTimeBy(20_000)

        assertEquals(listOf(key(shows(3), region = "in")), h.published)
        assertFalse(h.loading)
    }

    @Test
    fun `following or unfollowing an rss show asks nothing`() = runHarness { h ->
        h.refresher.onVisibilityChanged(true)
        h.followed.emit(shows(3))
        settle()

        h.followed.emit(shows(3) + "rss:feed-1")
        advanceTimeBy(60_000)
        h.followed.emit(shows(3))
        settle()

        assertEquals(1, h.requests.size)
        assertEquals(shows(3), h.requests.single().followedIds)
    }

    @Test
    fun `the key leaves rss shows out, carries the languages and is null while Picked for you is off`() {
        assertEquals(
            ExploreForYouRefreshKey("us", setOf("pi-1"), listOf("en", "hi")),
            ExploreForYouRefreshKey.of("us", setOf("pi-1", "rss:feed-1"), listOf("en", "hi"), pickedForYou = true),
        )
        assertNull(ExploreForYouRefreshKey.of("us", setOf("pi-1"), listOf("en"), pickedForYou = false))
    }

    private companion object {
        const val REQUEST_MS = 500L
        const val SETTLE_MS = 40_000L
        const val STALE_MS = ExploreForYouRefreshPolicy.STALE_AFTER_MS
        const val MAX_RETRY_MS = ExploreForYouRefreshPolicy.MAX_RETRY_INTERVAL_MS
    }
}
