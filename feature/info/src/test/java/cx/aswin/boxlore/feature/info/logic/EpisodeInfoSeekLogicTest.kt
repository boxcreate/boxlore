package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class EpisodeInfoSeekLogicTest {
    @Test
    fun `progressSaveInputForSeek copies episode and podcast identity before play`() {
        val episode =
            TestFixtures.episode(id = "ep-42").copy(
                title = "Deep Dive",
                description = "Notes",
                audioUrl = "https://cdn.example/a.mp3",
                imageUrl = "https://cdn.example/ep.png",
                podcastImageUrl = "https://cdn.example/pod.png",
                enclosureType = "audio/mpeg",
            )

        val input =
            EpisodeInfoSeekLogic.progressSaveInputForSeek(
                podcastId = "pod-7",
                podcastTitle = "Show Title",
                episode = episode,
                positionMs = 12_345L,
                durationMs = 60_000L,
                isLiked = true,
                lastPlayedAt = 1_700_000_000_000L,
            )

        assertEquals("pod-7", input.podcastId)
        assertEquals("ep-42", input.episodeId)
        assertEquals(12_345L, input.positionMs)
        assertEquals(60_000L, input.durationMs)
        assertEquals("Deep Dive", input.episodeTitle)
        assertEquals("https://cdn.example/ep.png", input.episodeImageUrl)
        assertEquals("https://cdn.example/pod.png", input.podcastImageUrl)
        assertEquals("https://cdn.example/a.mp3", input.episodeAudioUrl)
        assertEquals("Show Title", input.podcastName)
        assertFalse(input.isCompleted)
        assertEquals(true, input.isLiked)
        assertEquals(1_700_000_000_000L, input.lastPlayedAt)
        assertEquals("audio/mpeg", input.enclosureType)
        assertEquals("Notes", input.episodeDescription)
    }
}
