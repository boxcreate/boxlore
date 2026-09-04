package cx.aswin.boxlore.widgets

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
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
}
