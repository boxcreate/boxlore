package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Exercises the production Home collector rather than only the edition-key flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelBriefingTest {
    private val existing = brief("in")
    private val chapters = listOf(Chapter(0.0, "Saved headline"))
    private val briefingState = MutableStateFlow<Briefing?>(existing)
    private val chaptersState = MutableStateFlow(chapters)

    @Test
    fun `failed same edition refresh retains the loaded Brief and its chapters`() = runTest {
        collectHomeBriefings(flowOf("in"), briefingState, chaptersState, { null }, { error("must not fetch") })
        assertEquals(existing, briefingState.value)
        assertEquals(chapters, chaptersState.value)
    }

    @Test
    fun `failed changed edition does not display previous region content`() = runTest {
        collectHomeBriefings(flowOf("gb"), briefingState, chaptersState, { null }, { error("must not fetch") })
        assertNull(briefingState.value)
        assertEquals(emptyList<Chapter>(), chaptersState.value)
    }

    @Test
    fun `successful metadata and chapters replace previous edition`() = runTest {
        val fresh = listOf(Chapter(0.0, "UK headline"))
        collectHomeBriefings(flowOf("gb"), briefingState, chaptersState, { brief(it) }, { fresh })
        assertEquals(brief("gb"), briefingState.value)
        assertEquals(fresh, chaptersState.value)
    }

    @Test
    fun `new date clears old chapters even when chapter request fails`() = runTest {
        val tomorrow = existing.copy(date = "2026-10-12")
        collectHomeBriefings(flowOf("in"), briefingState, chaptersState, { tomorrow }, { error("offline") })
        assertEquals(tomorrow, briefingState.value)
        assertEquals(emptyList<Chapter>(), chaptersState.value)
    }

    @Test
    fun `same date chapter failure retains usable headlines`() = runTest {
        collectHomeBriefings(flowOf("in"), briefingState, chaptersState, { existing }, { error("offline") })
        assertEquals(existing, briefingState.value)
        assertEquals(chapters, chaptersState.value)
    }

    @Test
    fun `a cancelled older metadata response cannot publish after region changes`() = runTest {
        val regions = MutableSharedFlow<String>()
        val ready = CompletableDeferred<Unit>()
        val job = launch {
            collectHomeBriefings(regions, briefingState, chaptersState, { region ->
                if (region == "in") withContext(NonCancellable) { ready.await() }
                brief(region)
            }, { listOf(Chapter(0.0, it.region)) })
        }
        runCurrent()
        regions.emit("in")
        runCurrent()
        regions.emit("gb")
        runCurrent()
        ready.complete(Unit)
        runCurrent()
        assertEquals("gb", briefingState.value?.region)
        assertEquals(listOf(Chapter(0.0, "gb")), chaptersState.value)
        job.cancel()
    }

    private fun brief(region: String) = Briefing("2026-10-11", region, "Daily news", "", "https://example.invalid/audio", "")
}
