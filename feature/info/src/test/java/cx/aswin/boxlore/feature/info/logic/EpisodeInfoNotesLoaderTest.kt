package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.ShowNotes
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EpisodeInfoNotesLoaderTest {
    @Test
    fun `description fallback publishes while remote chapters fail independently of promotion`() = runTest {
        val published = mutableListOf<List<Chapter>>()
        var promotionFinished = false
        val loader = EpisodeInfoNotesLoader(this, { throw java.io.IOException("offline") }, { _, _, _, _ -> null }, StandardTestDispatcher(testScheduler))
        val episode = TestFixtures.episode(description = "00:00 Intro\n00:30 Interview").copy(chaptersUrl = "https://example.org/chapters.json")
        loader.load(episode, "host", "Host", {}, published::add, { promotionFinished = true })
        advanceUntilIdle()
        assertEquals(listOf(0.0, 30.0), published.single().map { it.startTime })
        assertTrue(promotionFinished)
    }

    @Test
    fun `remote chapter list replaces fallback after validation sorting and deduplication`() = runTest {
        val published = mutableListOf<List<Chapter>>()
        val loader = EpisodeInfoNotesLoader(this, {
            listOf(Chapter(45.0, "Later"), Chapter(0.0, "Start"), Chapter(45.0, "Duplicate"), Chapter(-1.0, "Invalid"), Chapter(Double.NaN, "Invalid"), Chapter(100.0, "End"))
        }, { _, _, _, _ -> null }, StandardTestDispatcher(testScheduler))
        loader.load(TestFixtures.episode(duration = 100, description = "00:00 Intro\n00:30 Interview").copy(chaptersUrl = "https://example.org/chapters"), "host", "Host", {}, published::add, {})
        advanceUntilIdle()
        assertEquals(2, published.size)
        assertEquals(listOf("Start", "Later"), published.last().map { it.title })
    }

    @Test
    fun `late result from previous episode cannot overwrite current notes or chapters`() = runTest {
        val oldRemote = CompletableDeferred<List<Chapter>>()
        val publishedNotes = mutableListOf<ShowNotes>()
        val publishedChapters = mutableListOf<List<Chapter>>()
        val loader = EpisodeInfoNotesLoader(this, { withContext(NonCancellable) { oldRemote.await() } }, { _, _, _, _ -> null }, StandardTestDispatcher(testScheduler))
        loader.load(TestFixtures.episode(id = "old", description = "Old notes").copy(chaptersUrl = "https://example.org/old"), "host", "Host", publishedNotes::add, publishedChapters::add, {})
        runCurrent()
        loader.load(TestFixtures.episode(id = "new", description = "New notes"), "host", "Host", publishedNotes::add, publishedChapters::add, {})
        runCurrent()
        oldRemote.complete(listOf(Chapter(0.0, "Stale")))
        advanceUntilIdle()
        assertEquals("New notes", publishedNotes.last().plainText)
        assertTrue(publishedChapters.flatten().isEmpty())
    }

    @Test
    fun `metadata refresh for same episode also invalidates old promotion publication`() = runTest {
        val oldPromotion = CompletableDeferred<Unit>()
        val completed = mutableListOf<String>()
        val loader = EpisodeInfoNotesLoader(this, { emptyList() }, { episode, _, _, _ ->
            if (episode.description == "Old") withContext(NonCancellable) { oldPromotion.await() }
            null
        }, StandardTestDispatcher(testScheduler))
        loader.load(TestFixtures.episode(id = "same", description = "Old"), "host", "Host", {}, {}, { completed.add("old") })
        runCurrent()
        loader.load(TestFixtures.episode(id = "same", description = "New"), "host", "Host", {}, {}, { completed.add("new") })
        runCurrent()
        oldPromotion.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("new"), completed)
    }

    @Test
    fun `explicit cancellation publishes no pending result`() = runTest {
        var notesPublished = false
        val loader = EpisodeInfoNotesLoader(this, { emptyList() }, { _, _, _, _ -> null }, StandardTestDispatcher(testScheduler))
        loader.load(TestFixtures.episode(), "host", "Host", { notesPublished = true }, {}, {})
        loader.cancel()
        advanceUntilIdle()
        assertEquals(false, notesPublished)
    }

    @Test
    fun `identical and unrelated metadata refreshes do not restart parsing or promotion`() = runTest {
        var started = 0
        var promotions = 0
        var notes = 0
        val loader = EpisodeInfoNotesLoader(this, { emptyList() }, { _, _, _, _ ->
            promotions++
            null
        }, StandardTestDispatcher(testScheduler))
        val episode = TestFixtures.episode(description = "Notes")
        fun load(value: cx.aswin.boxlore.core.model.Episode) = loader.load(value, "host", "Host", { notes++ }, {}, {}, { started++ })
        load(episode)
        load(episode.copy(imageUrl = "https://example.org/new.png"))
        advanceUntilIdle()
        load(episode)
        advanceUntilIdle()
        assertEquals(1, started)
        assertEquals(1, promotions)
        assertEquals(1, notes)
        loader.cancel()
        load(episode)
        advanceUntilIdle()
        assertEquals(2, promotions)
    }

    @Test
    fun `promotion inputs and remote chapter URL changes trigger fresh requests`() = runTest {
        var started = 0
        val loader = EpisodeInfoNotesLoader(this, { emptyList() }, { _, _, _, _ -> null }, StandardTestDispatcher(testScheduler))
        val episode = TestFixtures.episode(description = "Original notes")
        val inputs = listOf(episode, episode.copy(title = "Changed"), episode.copy(description = "Changed"), episode.copy(duration = 90), episode.copy(episodeType = "trailer"), episode.copy(chaptersUrl = "https://example.org/chapters"))
        inputs.forEach { value ->
            loader.load(value, "host", "Host", {}, {}, {}, { started++ })
            advanceUntilIdle()
        }
        loader.load(inputs.last(), "host", "Changed host", {}, {}, {}, { started++ })
        advanceUntilIdle()
        assertEquals(inputs.size + 1, started)
    }
}
