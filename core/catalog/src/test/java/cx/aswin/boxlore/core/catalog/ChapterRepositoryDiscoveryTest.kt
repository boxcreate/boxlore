package cx.aswin.boxlore.core.catalog

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Briefing chapters read through [ChapterRepository]: the related episodes of the recorded
 * `/v2/briefings/chapters` answer (`fixtures/v2/briefing-chapters.json`) come back under the IDs this
 * phone holds, whether the chapters come from the signed `/v2` link, a downloaded briefing's saved
 * file, or the cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterRepositoryDiscoveryTest {
    private val recorded = requireNotNull(javaClass.classLoader?.getResource("fixtures/v2/briefing-chapters.json")) { "fixture" }.readText()
    private val reads = AtomicInteger()
    private val requested = mutableListOf<String>()
    private val originalReader = ChapterRepository.readRemote

    @Before
    fun setUp() {
        ChapterRepository.clearCache()
        ChapterRepository.readRemote = { url ->
            reads.incrementAndGet()
            requested += url
            recorded
        }
        DiscoveryRouting.install({ true }, FakeIds)
    }

    @After
    fun tearDown() {
        ChapterRepository.readRemote = originalReader
        ChapterRepository.clearCache()
        DiscoveryRouting.resetForTests()
    }

    @Test
    fun relatedEpisodesFromTheSignedV2LinkComeBackUnderLocalIdsAndAreCached() = runTest {
        val first = ChapterRepository.getChapters(CHAPTERS_URL)
        val again = ChapterRepository.getChapters(CHAPTERS_URL)

        assertEquals(listOf("6200", OTHER_RELATED), first.first().relatedEpisodes!!.map { it.id })
        assertEquals(listOf(RELATED, OTHER_RELATED.toLong()), first.first().relatedEpisodes!!.map { it.recipeId })
        assertEquals(first, again)
        assertEquals(1, reads.get())
        assertEquals(listOf(CHAPTERS_URL), requested)
    }

    @Test
    fun aDownloadedBriefingsSavedChaptersAreTranslatedToo() = runTest {
        val file = File.createTempFile("briefing", ".json").apply { writeText(recorded) }
        try {
            for (path in listOf(file.absolutePath, "file:${file.absolutePath}", "file://${file.absolutePath}")) {
                ChapterRepository.clearCache()
                assertEquals(path, listOf("6200", OTHER_RELATED), ChapterRepository.getChapters(path).first().relatedEpisodes!!.map { it.id })
            }
            assertEquals(0, reads.get())
        } finally {
            file.delete()
        }
    }

    @Test
    fun chaptersThatNameNoRecipeIdsAreLeftAsTheyAre() = runTest {
        ChapterRepository.readRemote = { """{"chapters":[{"startTime":0,"title":"Old","relatedEpisodes":[{"id":"1234","podcastId":"7488327","title":"T"}]}]}""" }

        val chapters = ChapterRepository.getChapters("https://api.example/briefings/chapters/us?d=2026-10-20")

        assertEquals(listOf("1234"), chapters.single().relatedEpisodes!!.map { it.id })
        assertEquals(listOf(null), chapters.single().relatedEpisodes!!.map { it.recipeId })
    }

    private object FakeIds : DiscoveryEpisodeIds {
        override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> = emptyMap()

        override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, DiscoveryEpisodeOwner> =
            refs.filter { it.podcastId == RELATED_SHOW && it.recipeId == RELATED }.associateWith { DiscoveryEpisodeOwner("6200", RELATED_SHOW) }
    }

    private companion object {
        const val CHAPTERS_URL = "https://api.aswin.cx/v2/briefings/chapters/us?d=2026-10-25&v=25f44a9c&exp=0&sig=redacted"

        // The first chapter's related episodes in the recorded answer.
        const val RELATED_SHOW = "7488327"
        const val RELATED = 7_504_489_678_679_256L
        const val OTHER_RELATED = "5260337418277736"
    }
}
