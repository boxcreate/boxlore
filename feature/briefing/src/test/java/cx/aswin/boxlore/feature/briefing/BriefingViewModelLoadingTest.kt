package cx.aswin.boxlore.feature.briefing

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.core.prefs.BriefingEdition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests the actual load coordinator wired by BriefingViewModel, with no live repository or media service. */
@OptIn(ExperimentalCoroutinesApi::class)
class BriefingViewModelLoadingTest {
    private val states = mutableListOf<BriefingLoadState>()
    private val requests = mutableListOf<String>()
    private val chapters = listOf(Chapter(0.0, "First story"))
    private val session = PlaybackSession("briefing_in", "briefing_in_2026-10-11", 1200, 9000, 1, "Brief", "Brief", null, null, null)

    private fun loader(
        scope: CoroutineScope,
        metadata: suspend (String) -> Briefing? = {
            requests.add(it)
            brief(it)
        },
        chapterFetch: suspend (Briefing) -> List<Chapter> = { chapters },
        sessionFetch: suspend (Briefing) -> PlaybackSession? = { session },
    ) = BriefingLoader(scope, metadata, chapterFetch, sessionFetch, states::add)

    @Test
    fun `saved edition resolves before the first request and success includes chapters and progress`() = runTest {
        loader(this).initialize(flowOf("in"), flowOf("us"), null)
        advanceUntilIdle()
        assertEquals(listOf("in"), requests)
        assertEquals(BriefingLoadState("in", brief("in"), chapters, session, isLoading = false), states.last())
        assertTrue(states.first().isLoading)
    }

    @Test
    fun `explicit route wins over default and app region`() = runTest {
        loader(this).initialize(flowOf("in"), flowOf("us"), "UK")
        advanceUntilIdle()
        assertEquals(listOf("gb"), requests)
    }

    @Test
    fun `follow app maps unsupported markets to global`() = runTest {
        loader(this).initialize(flowOf(BriefingEdition.FOLLOW_APP), flowOf("de"), null)
        advanceUntilIdle()
        assertEquals(listOf("global"), requests)
    }

    @Test
    fun `a selection during delayed preferences is not overwritten by initialization`() = runTest {
        val ready = CompletableDeferred<Unit>()
        val loader = loader(this)
        loader.initialize(
            flow {
                ready.await()
                emit("in")
            },
            flowOf("us"),
            null,
        )
        loader.load("gb")
        advanceUntilIdle()
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("gb"), requests)
        assertEquals("gb", states.last().region)
    }

    @Test
    fun `null metadata ends loading and retrying the same edition succeeds`() = runTest {
        var available = false
        val loader = loader(this, metadata = { if (available) brief(it) else null })
        loader.load("us")
        advanceUntilIdle()
        assertTrue(states.last().failed)
        assertFalse(states.last().isLoading)
        available = true
        loader.load("us")
        advanceUntilIdle()
        assertFalse(states.last().failed)
        assertEquals(brief("us"), states.last().briefing)
    }

    @Test
    fun `unexpected metadata failure produces a recoverable error`() = runTest {
        loader(this, metadata = { error("offline") }).load("in")
        advanceUntilIdle()
        assertTrue(states.last().failed)
        assertFalse(states.last().isLoading)
    }

    @Test
    fun `chapter failure leaves briefing and saved progress usable`() = runTest {
        loader(this, chapterFetch = { error("chapters unavailable") }).load("in")
        advanceUntilIdle()
        assertEquals(BriefingLoadState("in", brief("in"), emptyList(), session, isLoading = false), states.last())
    }

    @Test
    fun `cancelled chapter request is never converted into successful or failed content`() = runTest {
        loader(this, chapterFetch = { throw CancellationException("cancelled") }).load("in")
        advanceUntilIdle()
        assertEquals(1, states.size)
        assertTrue(states.last().isLoading)
    }

    @Test
    fun `slow noncooperative metadata cannot replace a newer selection`() = runTest {
        val ready = CompletableDeferred<Unit>()
        val loader = loader(this, metadata = { region ->
            if (region == "in") withContext(NonCancellable) { ready.await() }
            brief(region)
        })
        loader.load("in")
        advanceUntilIdle()
        loader.load("us")
        advanceUntilIdle()
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals("us", states.last().briefing?.region)
        assertEquals(1, states.count { !it.isLoading })
    }

    @Test
    fun `slow noncooperative chapters cannot leak into another edition`() = runTest {
        val ready = CompletableDeferred<Unit>()
        val loader = loader(this, chapterFetch = { briefing ->
            if (briefing.region == "in") withContext(NonCancellable) { ready.await() }
            listOf(Chapter(0.0, briefing.region))
        })
        loader.load("in")
        advanceUntilIdle()
        loader.load("gb")
        advanceUntilIdle()
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals("gb", states.last().briefing?.region)
        assertEquals(listOf(Chapter(0.0, "gb")), states.last().chapters)
        assertEquals(1, states.count { !it.isLoading })
    }

    @Test
    fun `region changes start loading with no prior chapters or saved session`() = runTest {
        val loader = loader(this)
        loader.load("in")
        advanceUntilIdle()
        loader.load("gb")
        advanceUntilIdle()
        assertEquals(BriefingLoadState(region = "gb"), states[2])
    }

    private fun brief(region: String) = Briefing("2026-10-11", region, "Daily news", "", "https://example.invalid/audio", "")
}
