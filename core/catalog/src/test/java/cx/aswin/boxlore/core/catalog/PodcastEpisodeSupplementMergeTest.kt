package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.rss.EpisodeSupplementListMerge
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastEpisodeSupplementMergeTest {
    @Test
    fun `rss library rows skip extras`() {
        val pi = listOf(TestFixtures.episode(id = "1", title = "PI"))
        val extras = listOf(TestFixtures.episode(id = "-9", title = "Feed only"))
        val merged =
            PodcastEpisodeSupplementMerge.mergePage(
                podcastId = "rss:feed",
                piEpisodes = pi,
                supplements = extras,
                sort = EpisodeSupplementListMerge.Sort.NEWEST,
                injectExtras = true,
            )
        assertEquals(pi, merged)
    }

    @Test
    fun `later pages do not inject extras`() {
        val pi = listOf(TestFixtures.episode(id = "1", title = "PI", publishedDate = 100L))
        val extras =
            listOf(
                TestFixtures.episode(
                    id = "-9",
                    title = "Feed only",
                    audioUrl = "https://cdn.example/feed-only.mp3",
                    publishedDate = 200L,
                ),
            )
        val merged =
            PodcastEpisodeSupplementMerge.mergePage(
                podcastId = "1258562",
                piEpisodes = pi,
                supplements = extras,
                sort = EpisodeSupplementListMerge.Sort.NEWEST,
                injectExtras = false,
            )
        assertEquals(listOf("1"), merged.map { it.id })
    }

    @Test
    fun `offset zero injects feed-only extras newest first`() {
        val pi = listOf(TestFixtures.episode(id = "10", title = "PI latest", publishedDate = 100L))
        val extras =
            listOf(
                TestFixtures.episode(
                    id = "-203",
                    title = "Feed only",
                    audioUrl = "https://cdn.example/feed-only.mp3",
                    publishedDate = 200L,
                ),
            )
        val merged =
            PodcastEpisodeSupplementMerge.mergePage(
                podcastId = "1258562",
                piEpisodes = pi,
                supplements = extras,
                sort = EpisodeSupplementListMerge.Sort.NEWEST,
                injectExtras = true,
            )
        assertEquals(listOf("-203", "10"), merged.map { it.id })
    }

    @Test
    fun `unionSearch skips rss ids`() {
        val network = listOf(TestFixtures.episode(id = "n1", title = "Net"))
        val extras = listOf(TestFixtures.episode(id = "-1", title = "Supp"))
        val union =
            PodcastEpisodeSupplementMerge.unionSearch(
                podcastId = "rss:feed",
                networkResults = network,
                supplementMatches = extras,
            )
        assertEquals(network, union)
    }
}
