package cx.aswin.boxlore.feature.explore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoreArtworkAccentsTest {
    @Test
    fun `blank episode artwork does not hide valid show artwork`() {
        assertEquals(listOf("feed"), loreArtworkSources("  ", " feed "))
        assertEquals(listOf("same"), loreArtworkSources("same", "same"))
        assertEquals(emptyList<String>(), loreArtworkSources(null, ""))
    }

    @Test
    fun `proxy failure tries original artwork before the show fallback`() = runTest {
        val attempts = mutableListOf<String>()
        val color = resolveLoreArtworkAccent(listOf("episode", "feed"), { "proxy:$it" }) {
            attempts += it
            if (it == "episode") 12 else null
        }
        assertEquals(12, color)
        assertEquals(listOf("proxy:episode", "episode"), attempts)
    }

    @Test
    fun `unavailable episode artwork falls back to the show and tolerates failed requests`() = runTest {
        val attempts = mutableListOf<String>()
        val color = resolveLoreArtworkAccent(listOf("episode", "feed"), { "proxy:$it" }) {
            attempts += it
            if (it == "proxy:episode") error("Unavailable proxy")
            if (it == "proxy:feed") 24 else null
        }
        assertEquals(24, color)
        assertEquals(listOf("proxy:episode", "episode", "proxy:feed"), attempts)
    }

    @Test
    fun `cancellation cannot fall through to another artwork or publish a false failure`() = runTest {
        val attempts = mutableListOf<String>()
        var cancelled = false
        try {
            resolveLoreArtworkAccent(listOf("episode", "feed"), { it }) {
                attempts += it
                throw CancellationException("Card left the deck")
            }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(listOf("episode"), attempts)
    }

    @Test
    fun `page and repeated cards share one request and the next card is already cached`() = runTest {
        val requests = mutableListOf<List<String>>()
        val ready = CompletableDeferred<Unit>()
        val first = listOf("first")
        val next = listOf("next")
        val store = LoreArtworkAccentStore(this) {
            requests += it
            ready.await()
            if (it == first) 11 else 22
        }
        store.prefetch(listOf(first, next, first))
        runCurrent()
        store.prefetch(listOf(first, next))
        runCurrent()
        assertEquals(listOf(first, next), requests)
        ready.complete(Unit)
        advanceUntilIdle()
        store.prefetch(listOf(next))
        advanceUntilIdle()
        assertEquals(22, store.colors.value[next])
        assertEquals(11, store.colors.value[first])
        assertEquals(2, requests.size)
    }

    @Test
    fun `cancelled old work cannot change the current card colour even if its loader completes late`() = runTest {
        val old = listOf("old")
        val current = listOf("current")
        val store = LoreArtworkAccentStore(this) {
            if (it == old) withContext(NonCancellable) { delay(100) }
            if (it == old) 1 else 2
        }
        store.prefetch(listOf(old))
        runCurrent()
        store.prefetch(listOf(current))
        advanceUntilIdle()
        assertEquals(2, store.colors.value[current])
        assertFalse(old in store.colors.value)
    }

    @Test
    fun `failed palette requests remain retryable`() = runTest {
        var attempts = 0
        val key = listOf("artwork")
        val store = LoreArtworkAccentStore(this) {
            attempts++
            if (attempts == 1) null else 7
        }
        store.prefetch(listOf(key))
        advanceUntilIdle()
        assertNull(store.colors.value[key])
        store.prefetch(listOf(key))
        advanceUntilIdle()
        assertEquals(7, store.colors.value[key])
        assertEquals(2, attempts)
    }

    @Test
    fun `palette cache is bounded and retains recently reused artwork`() = runTest {
        val first = listOf("first")
        val second = listOf("second")
        val third = listOf("third")
        val store = LoreArtworkAccentStore(this, capacity = 2) { it.first().length }
        store.prefetch(listOf(first, second))
        advanceUntilIdle()
        store.prefetch(listOf(second, third))
        advanceUntilIdle()
        assertEquals(setOf(second, third), store.colors.value.keys)
    }

    @Test
    fun `lookahead is bounded and at most two palettes load together`() = runTest {
        val ready = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        var requests = 0
        val store = LoreArtworkAccentStore(this) {
            requests++
            active++
            peak = maxOf(peak, active)
            ready.await()
            active--
            3
        }
        store.prefetch((1..8).map { listOf("artwork:$it") })
        runCurrent()
        assertEquals(2, active)
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, peak)
        assertEquals(4, requests)
    }

    @Test
    fun `colour selection favours artwork hues over neutral backgrounds and tiny accents`() {
        val grey = LoreArtworkSwatch(1, 5000, 0f, 0.5f)
        val teal = LoreArtworkSwatch(2, 400, 0.55f, 0.45f)
        val tinyRed = LoreArtworkSwatch(3, 1, 0.95f, 0.45f)
        assertEquals(2, selectLoreArtworkSwatch(listOf(grey, teal, tinyRed)))
        assertNull(selectLoreArtworkSwatch(listOf(grey)))
        assertNull(selectLoreArtworkSwatch(listOf(LoreArtworkSwatch(4, 100, 0.5f, 0.99f))))
    }
}
