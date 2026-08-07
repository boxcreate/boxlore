package cx.aswin.boxlore.feature.widgets.logic

import cx.aswin.boxlore.feature.widgets.WidgetEpisodeRow
import cx.aswin.boxlore.feature.widgets.WidgetShowRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryWidgetLogicTest {
    @Test
    fun truncateShowsKeepsAtMostMaxRows() {
        val rows =
            (1..LibraryWidgetLogic.MAX_ROWS + 10).map {
                WidgetShowRow(
                    podcastId = "p$it",
                    title = "Show $it",
                    deepLinkUri = "boxlore://podcast/p$it",
                )
            }
        assertEquals(LibraryWidgetLogic.MAX_ROWS, LibraryWidgetLogic.truncateShows(rows).size)
        assertEquals("p1", LibraryWidgetLogic.truncateShows(rows).first().podcastId)
    }

    @Test
    fun truncateEpisodesKeepsAtMostMaxRows() {
        val rows =
            (1..LibraryWidgetLogic.MAX_ROWS + 5).map {
                WidgetEpisodeRow(
                    episodeId = "e$it",
                    episodeTitle = "Ep $it",
                    podcastId = "p$it",
                    podcastTitle = "Show $it",
                    deepLinkUri = "boxlore://episode/e$it",
                )
            }
        assertEquals(LibraryWidgetLogic.MAX_ROWS, LibraryWidgetLogic.truncateEpisodes(rows).size)
    }

    @Test
    fun mergeArtworkPathsReusesCacheForSamePodcast() {
        val previous =
            listOf(
                WidgetShowRow(
                    podcastId = "p1",
                    title = "Show",
                    artworkUrl = "https://example.com/a.jpg",
                    artworkCachePath = "/cache/a.jpg",
                    deepLinkUri = "boxlore://podcast/p1",
                ),
            )
        val next =
            listOf(
                WidgetShowRow(
                    podcastId = "p1",
                    title = "Show",
                    artworkUrl = "https://example.com/a.jpg",
                    deepLinkUri = "boxlore://podcast/p1",
                ),
            )
        val merged = LibraryWidgetLogic.mergeArtworkPaths(previous, next)
        assertEquals("/cache/a.jpg", merged.single().artworkCachePath)
    }

    @Test
    fun emptyTruncateIsEmpty() {
        assertTrue(LibraryWidgetLogic.truncateShows(emptyList()).isEmpty())
        assertTrue(LibraryWidgetLogic.truncateEpisodes(emptyList()).isEmpty())
    }
}
