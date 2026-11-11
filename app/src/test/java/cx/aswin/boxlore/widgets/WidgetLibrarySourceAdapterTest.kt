package cx.aswin.boxlore.widgets

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.widgets.WidgetShowRow
import cx.aswin.boxlore.feature.widgets.logic.LibraryWidgetLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLibrarySourceAdapterTest {
    @Test
    fun enrichMarksCompletedFromHistory() {
        val podcast =
            Podcast(
                id = "p1",
                title = "Show",
                artist = "Host",
                imageUrl = "https://example.com/a.jpg",
                latestEpisode =
                Episode(
                    id = "e1",
                    title = "Ep",
                    description = "",
                    audioUrl = "https://example.com/a.mp3",
                    publishedDate = 1_700_000_000L,
                ),
            )
        val history =
            listOf(
                ListeningHistoryEntity(
                    episodeId = "e1",
                    podcastId = "p1",
                    episodeTitle = "Ep",
                    episodeImageUrl = null,
                    podcastImageUrl = null,
                    episodeAudioUrl = null,
                    podcastName = "Show",
                    progressMs = 10L,
                    durationMs = 10L,
                    isCompleted = true,
                    lastPlayedAt = 1L,
                ),
            )
        val enriched = WidgetLibrarySourceAdapter.enrichWithHistory(listOf(podcast), history)
        assertEquals(EpisodeStatus.COMPLETED, enriched.single().episodeStatus)
    }

    @Test
    fun deepLinksUseBoxloreScheme() {
        assertEquals(
            "boxlore://podcast/p%201",
            WidgetLibrarySourceAdapter.podcastDeepLink("p 1"),
        )
        assertEquals(
            "boxlore://library/subscriptions?tab=1",
            WidgetLibrarySourceAdapter.subscriptionsDeepLink(1),
        )
        val episode =
            WidgetLibrarySourceAdapter.episodeDeepLink(
                episodeId = "e1",
                podcastId = "p1",
                podcastTitle = "Cold War",
            )
        assertTrue(episode.startsWith("boxlore://episode/e1?"))
        assertTrue(episode.contains("autoplay=false"))
        assertTrue(episode.contains("podcastId=p1"))
    }

    @Test
    fun countsCoverTheWholeLibraryWhileTheRowsStayCapped() {
        val total = LibraryWidgetLogic.MAX_ROWS + 12
        val finished = 5
        val library =
            List(total) { index ->
                Podcast(
                    id = "p$index",
                    title = "Show $index",
                    artist = "Host",
                    imageUrl = "https://example.com/$index.jpg",
                    latestEpisode = Episode(id = "e$index", title = "Ep $index", description = "", audioUrl = "https://example.com/$index.mp3", publishedDate = 1_700_000_000L + index),
                    episodeStatus = if (index < finished) EpisodeStatus.COMPLETED else EpisodeStatus.UNPLAYED,
                )
            }

        val everyEpisode = WidgetLibrarySourceAdapter.latestEpisodePool(library, hideCompleted = false)
        val counts = WidgetLibrarySourceAdapter.libraryCounts(library, everyEpisode)
        assertEquals(total, counts.subscriptions)
        assertEquals(total, counts.newEpisodes)
        // The widget still scrolls a capped list.
        assertEquals(LibraryWidgetLogic.MAX_ROWS, LibraryWidgetLogic.truncateShows(library.map { WidgetShowRow(podcastId = it.id, title = it.title, deepLinkUri = "") }).size)

        // Hiding finished episodes shrinks the New Episodes count, not the show count.
        val unfinished = WidgetLibrarySourceAdapter.latestEpisodePool(library, hideCompleted = true)
        val hidden = WidgetLibrarySourceAdapter.libraryCounts(library, unfinished)
        assertEquals(total, hidden.subscriptions)
        assertEquals(total - finished, hidden.newEpisodes)

        // A show without a latest episode is a show, but not a New Episodes row.
        val withoutEpisode = library + Podcast(id = "bare", title = "Bare", artist = "Host", imageUrl = "")
        assertEquals(total, WidgetLibrarySourceAdapter.latestEpisodePool(withoutEpisode, hideCompleted = false).size)
    }
}
