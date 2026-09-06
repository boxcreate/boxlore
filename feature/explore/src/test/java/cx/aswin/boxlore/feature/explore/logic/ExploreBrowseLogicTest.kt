package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreBrowseLogicTest {
    @Test
    fun `morning hour puts news moods first with home titles`() {
        val vibes = ExploreBrowseLogic.vibesForHour(8)
        assertEquals("morning_news", vibes.first().first)
        assertEquals("What's happening", vibes.first().second)
    }

    @Test
    fun `late night hour puts crime moods first`() {
        assertEquals("true_crime_sleep", ExploreBrowseLogic.vibesForHour(1).first().first)
        assertEquals("True crime after dark", ExploreBrowseLogic.vibesForHour(1).first().second)
    }

    @Test
    fun `substring filter matches title or artist case-insensitively`() {
        val podcasts =
            listOf(
                TestFixtures.podcast(id = "1", title = "Alpha Show", artist = "Zed"),
                TestFixtures.podcast(id = "2", title = "Beta", artist = "Alpha Artist"),
                TestFixtures.podcast(id = "3", title = "Gamma", artist = "Other"),
            )
        val matches = ExploreBrowseLogic.filterPodcastsBySubstring("alpha", podcasts)
        assertEquals(listOf("1", "2"), matches.map { it.id })
    }

    @Test
    fun `mergeUniqueById appends only new ids`() {
        val existing = listOf(TestFixtures.podcast(id = "a"), TestFixtures.podcast(id = "b"))
        val incoming = listOf(TestFixtures.podcast(id = "b"), TestFixtures.podcast(id = "c"))
        val merged = ExploreBrowseLogic.mergeUniqueById(existing, incoming) { it.id }
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun `episodeToSearchPodcast maps podcast fields`() {
        val episode =
            TestFixtures
                .episode(
                    id = "e",
                    podcastId = "p",
                    podcastTitle = "Show",
                ).copy(podcastArtist = "Host", podcastImageUrl = "img", podcastGenre = "News")
        val podcast = ExploreBrowseLogic.episodeToSearchPodcast(episode)
        assertEquals("p", podcast.id)
        assertEquals("Show", podcast.title)
        assertEquals("Host", podcast.artist)
        assertEquals("img", podcast.imageUrl)
        assertEquals("News", podcast.genre)
        assertTrue(podcast.latestEpisode === episode)
    }

    @Test
    fun `projectDisplayList removes duplicates and drops blank ids`() {
        val podcasts = listOf(
            TestFixtures.podcast(id = "show-1", title = "First"),
            TestFixtures.podcast(id = "show-1", title = "Duplicate First"),
            TestFixtures.podcast(id = "  ", title = "Blank ID"),
            TestFixtures.podcast(id = "show-2", title = "Second"),
        )
        val projected = ExploreBrowseLogic.projectDisplayList(podcasts)
        assertEquals(listOf("show-1", "show-2"), projected.map { it.id })
        assertEquals("First", projected[0].title)
    }

    @Test
    fun `projectGridItems drops hero when browsing without vibe`() {
        val displayList = listOf(
            TestFixtures.podcast(id = "hero"),
            TestFixtures.podcast(id = "grid-1"),
            TestFixtures.podcast(id = "grid-2"),
        )
        val gridItems = ExploreBrowseLogic.projectGridItems(
            displayList = displayList,
            isSearching = false,
            hasCurrentVibe = false,
        )
        assertEquals(listOf("grid-1", "grid-2"), gridItems.map { it.id })
    }

    @Test
    fun `projectGridItems retains hero when searching or when vibe is active`() {
        val displayList = listOf(
            TestFixtures.podcast(id = "hero"),
            TestFixtures.podcast(id = "grid-1"),
        )
        val searchingGrid = ExploreBrowseLogic.projectGridItems(
            displayList = displayList,
            isSearching = true,
            hasCurrentVibe = false,
        )
        assertEquals(listOf("hero", "grid-1"), searchingGrid.map { it.id })

        val vibeGrid = ExploreBrowseLogic.projectGridItems(
            displayList = displayList,
            isSearching = false,
            hasCurrentVibe = true,
        )
        assertEquals(listOf("hero", "grid-1"), vibeGrid.map { it.id })
    }

    @Test
    fun `filterAlsoFound drops items present in catalog with whitespace normalization`() {
        val catalog = listOf(
            TestFixtures.podcast(id = " show-1 "),
            TestFixtures.podcast(id = "show-2"),
        )
        val alsoFound = listOf(
            TestFixtures.podcast(id = "show-1"),
            TestFixtures.podcast(id = "show-3"),
            TestFixtures.podcast(id = "show-3"),
            TestFixtures.podcast(id = "   "),
        )
        val filtered = ExploreBrowseLogic.filterAlsoFound(alsoFound, catalog)
        assertEquals(listOf("show-3"), filtered.map { it.id })
    }
}
