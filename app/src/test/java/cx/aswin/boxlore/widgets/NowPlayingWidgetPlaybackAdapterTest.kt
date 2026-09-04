package cx.aswin.boxlore.widgets

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlayerState
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NowPlayingWidgetPlaybackAdapterTest {
    @Test
    fun `restore work switches to the MediaController dispatcher`() = runBlocking {
        val dispatcher =
            Executors
                .newSingleThreadExecutor { runnable -> Thread(runnable, "widget-media-main") }
                .asCoroutineDispatcher()
        try {
            val threadName =
                withWidgetPlaybackDispatcher(dispatcher) {
                    Thread.currentThread().name
                }

            assertEquals("widget-media-main", threadName.substringBefore(" "))
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun `maps episode artwork preferring episode image over podcast`() {
        val state =
            PlayerState(
                currentEpisode =
                Episode(
                    id = "ep",
                    title = "Title",
                    description = "",
                    audioUrl = "https://example.com/a.mp3",
                    imageUrl = "https://example.com/ep.jpg",
                    podcastImageUrl = "https://example.com/show.jpg",
                    podcastTitle = "Show",
                ),
            )

        val mapped = state.toWidgetPlaybackState()
        assertEquals("ep", mapped.episodeId)
        assertEquals("https://example.com/ep.jpg", mapped.artworkUrl)
        assertEquals("Show", mapped.podcastTitle)
    }

    @Test
    fun `maps empty player state without episode`() {
        val mapped = PlayerState().toWidgetPlaybackState()
        assertNull(mapped.episodeId)
        assertNull(mapped.artworkUrl)
    }
}
