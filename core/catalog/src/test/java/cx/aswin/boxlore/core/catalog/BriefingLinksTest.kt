package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The briefing links the app builds itself and the briefing related episodes: chapters follow the
 * v2 discovery switch, the transcript keeps its path, and related episodes show under local IDs.
 */
class BriefingLinksTest {
    @AfterEach
    fun tearDown() {
        DiscoveryRouting.resetForTests()
    }

    @Test
    fun `chapters move to the v2 path and the transcript keeps its path`() {
        assertEquals(
            "https://api.example/v2/briefings/chapters/us?d=2026-10-24&v=3",
            BriefingLinks.chaptersUrl("us", "2026-10-24", "3", v2 = true, baseUrl = BASE),
        )
        assertEquals(
            "https://api.example/briefings/chapters/in?d=2026-10-24",
            BriefingLinks.chaptersUrl("in", "2026-10-24", null, v2 = false, baseUrl = BASE),
        )
        assertEquals(
            "https://api.example/briefings/transcript/us?d=2026-10-24&v=3",
            BriefingLinks.transcriptUrl("us", "2026-10-24", "3", baseUrl = BASE),
        )
    }

    @Test
    fun `the chapters link follows the installed v2 discovery switch`() {
        assertTrue(BriefingLinks.chaptersUrl("us", "2026-10-24", null, baseUrl = BASE).contains("/v2/briefings/"))

        var v2 = false
        DiscoveryRouting.install({ v2 }, DiscoveryEpisodeIds.Unmapped)
        assertEquals("https://api.example/briefings/chapters/us?d=2026-10-24", BriefingLinks.chaptersUrl("us", "2026-10-24", null, baseUrl = BASE))
        v2 = true
        assertEquals("https://api.example/v2/briefings/chapters/us?d=2026-10-24", BriefingLinks.chaptersUrl("us", "2026-10-24", null, baseUrl = BASE))
    }

    @Test
    fun `briefing related episodes show under the IDs this phone holds`() = runTest {
        val owners = mapOf(DiscoveryEpisodeRef("920666", RECIPE_A) to DiscoveryEpisodeOwner("7777", "920666"))
        val ids =
            object : DiscoveryEpisodeIds {
                override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> = emptyMap()

                override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, DiscoveryEpisodeOwner> =
                    refs.mapNotNull { ref -> owners[ref]?.let { ref to it } }.toMap()
            }
        DiscoveryRouting.install({ true }, ids)
        val chapters =
            listOf(
                Chapter(0.0, "Markets", relatedEpisodes = listOf(episode(RECIPE_A.toString(), "920666"), episode(RECIPE_E.toString(), "75075"))),
                Chapter(60.0, "Weather"),
            )

        val mapped = DiscoveryRouting.episodeIds.relatedToLocalOwners(chapters)

        assertEquals(listOf("7777", RECIPE_E.toString()), mapped.first().relatedEpisodes!!.map { it.id })
        assertEquals(listOf(RECIPE_A, RECIPE_E), mapped.first().relatedEpisodes!!.map { it.recipeId })
        assertEquals(chapters[1], mapped[1])
        // Nothing to place: the IDs stay, each keeping the recipe ID it named; a list with no related episodes comes back as it is.
        assertEquals(chapters.first().relatedEpisodes!!.map { it.id }, DiscoveryEpisodeIds.Unmapped.relatedToLocalOwners(chapters).first().relatedEpisodes!!.map { it.id })
        val noRelated = listOf(Chapter(0.0, "Weather"))
        assertSame(noRelated, DiscoveryEpisodeIds.Unmapped.relatedToLocalOwners(noRelated))
    }

    @Test
    fun `an alias table that fails leaves recipe IDs as they are`() = runTest {
        val failing =
            object : DiscoveryEpisodeIds {
                override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> = error("database closed")

                override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, DiscoveryEpisodeOwner> = error("database closed")
            }
        val episodes = listOf(episode(RECIPE_A.toString(), "920666"))

        assertEquals(episodes.map { it.id }, failing.toLocalOwners(episodes).map { it.id })
        assertEquals(mapOf(RECIPE_A.toString() to RECIPE_A), failing.recipeIdsOrOwn(listOf(RECIPE_A.toString(), "7777")))
    }

    private fun episode(id: String, show: String) = Episode(id = id, title = "Episode", description = "", audioUrl = "", podcastId = show)

    private companion object {
        const val BASE = "https://api.example"
        const val RECIPE_A = 4_503_599_627_370_497L
        const val RECIPE_E = 4_503_599_627_370_501L
    }
}
