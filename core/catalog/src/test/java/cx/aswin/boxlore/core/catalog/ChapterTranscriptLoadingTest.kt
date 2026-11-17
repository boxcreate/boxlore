package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Chapters and transcripts: a stalled read gives up, callers loading one address share one read,
 * the transcript cache is bounded, and related-episode IDs follow what this phone holds on every
 * return, including a downloaded episode's saved chapters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterTranscriptLoadingTest {
    private val recorded = requireNotNull(javaClass.classLoader?.getResource("fixtures/v2/briefing-chapters.json")) { "fixture" }.readText()
    private val chapterReader = ChapterRepository.readRemote
    private val chapterTimeout = ChapterRepository.remoteTimeoutMs
    private val transcriptReader = TranscriptRepository.readRemote
    private val transcriptTimeout = TranscriptRepository.remoteTimeoutMs
    private val ids = MutableIds()

    @Before
    fun setUp() {
        ChapterRepository.clearCache()
        TranscriptRepository.clearCache()
        DiscoveryRouting.install({ true }, ids)
    }

    @After
    fun tearDown() {
        ChapterRepository.readRemote = chapterReader
        ChapterRepository.remoteTimeoutMs = chapterTimeout
        TranscriptRepository.readRemote = transcriptReader
        TranscriptRepository.remoteTimeoutMs = transcriptTimeout
        ChapterRepository.clearCache()
        TranscriptRepository.clearCache()
        DiscoveryRouting.resetForTests()
    }

    @Test
    fun aStalledChaptersReadGivesUpAndIsNotKept() = runBlocking {
        ChapterRepository.remoteTimeoutMs = 50L
        ChapterRepository.readRemote = { awaitCancellation() }

        val stalled = withTimeout(5_000L) { ChapterRepository.getChapters(CHAPTERS_URL) }
        assertTrue(stalled.isEmpty())

        ChapterRepository.readRemote = { recorded }
        assertTrue(ChapterRepository.getChapters(CHAPTERS_URL).isNotEmpty())
    }

    @Test
    fun aStalledTranscriptReadGivesUp() = runBlocking {
        TranscriptRepository.remoteTimeoutMs = 50L
        TranscriptRepository.readRemote = { awaitCancellation() }

        assertTrue(withTimeout(5_000L) { TranscriptRepository.getTranscript(transcriptUrl(1)) }.isEmpty())
    }

    @Test
    fun callersLoadingOneAddressAtOnceShareOneRead() = runBlocking {
        val reads = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        ChapterRepository.readRemote = {
            reads.incrementAndGet()
            release.await()
            recorded
        }
        TranscriptRepository.readRemote = {
            reads.incrementAndGet()
            release.await()
            SRT
        }

        val chapters = List(4) { async(Dispatchers.Default) { ChapterRepository.getChapters(CHAPTERS_URL) } }
        val transcripts = List(4) { async(Dispatchers.Default) { TranscriptRepository.getTranscript(transcriptUrl(1)) } }
        withTimeout(5_000L) { while (reads.get() < 2) delay(10L) }
        delay(100L)
        release.complete(Unit)

        val chapterAnswers = chapters.awaitAll()
        val transcriptAnswers = transcripts.awaitAll()
        assertEquals(2, reads.get())
        assertTrue(chapterAnswers.all { it == chapterAnswers.first() && it.isNotEmpty() })
        assertTrue(transcriptAnswers.all { it == transcriptAnswers.first() && it.isNotEmpty() })
    }

    @Test
    fun theTranscriptCacheKeepsOnlyTheMostRecentFew() = runBlocking {
        val reads = AtomicInteger()
        TranscriptRepository.readRemote = {
            reads.incrementAndGet()
            SRT
        }

        for (n in 1..9) TranscriptRepository.getTranscript(transcriptUrl(n))
        assertEquals(9, reads.get())

        TranscriptRepository.getTranscript(transcriptUrl(9))
        assertEquals(9, reads.get())
        TranscriptRepository.getTranscript(transcriptUrl(1))
        assertEquals(10, reads.get())
    }

    @Test
    fun anEpisodeStoredAfterTheFirstReadIsShownUnderItsOwnIdNextTime() = runBlocking {
        val reads = AtomicInteger()
        ChapterRepository.readRemote = {
            reads.incrementAndGet()
            recorded
        }

        val before = ChapterRepository.getChapters(CHAPTERS_URL)
        assertEquals(RELATED.toString(), before.first().relatedEpisodes!!.first().id)

        ids.owners[RELATED] = DiscoveryEpisodeOwner("6200", RELATED_SHOW)
        val after = ChapterRepository.getChapters(CHAPTERS_URL)

        assertEquals("6200", after.first().relatedEpisodes!!.first().id)
        assertEquals(1, reads.get())
    }

    @Test
    fun aDownloadedEpisodesSavedChaptersAreTranslated() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChapterOfflineStorage.saveOfflineChapters(context, "downloaded", recorded)
        ids.owners[RELATED] = DiscoveryEpisodeOwner("6200", RELATED_SHOW)
        try {
            val chapters = ChapterRepository.getOfflineChapters(context, "downloaded")
            assertEquals("6200", chapters.first().relatedEpisodes!!.first().id)
        } finally {
            ChapterOfflineStorage.deleteOfflineChapters(context, "downloaded")
        }
    }

    private class MutableIds : DiscoveryEpisodeIds {
        val owners = java.util.concurrent.ConcurrentHashMap<Long, DiscoveryEpisodeOwner>()

        override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> = emptyMap()

        override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, DiscoveryEpisodeOwner> =
            refs.mapNotNull { ref -> owners[ref.recipeId]?.takeIf { ref.podcastId == RELATED_SHOW }?.let { ref to it } }.toMap()
    }

    private fun transcriptUrl(n: Int) = "https://cdn.example/transcripts/$n.srt"

    private companion object {
        const val CHAPTERS_URL = "https://api.aswin.cx/v2/briefings/chapters/us?d=2026-10-25&v=25f44a9c&exp=0&sig=redacted"
        const val RELATED_SHOW = "7488327"
        const val RELATED = 7_504_489_678_679_256L
        val SRT =
            """
            1
            00:00:01,000 --> 00:00:04,000
            Hello and welcome
            """.trimIndent() + "\n\n"
    }
}
