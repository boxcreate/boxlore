package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackHistorySeedPolicyTest {
    @Test
    fun `ordered source title takes precedence over snapshot and telemetry`() {
        val history =
            PlaybackHistorySeedPolicy.build(
                snapshot = snapshot(episodeTitle = "Player title"),
                sources =
                listOf(
                    PlaybackHistorySeedSource(episodeTitle = "  "),
                    PlaybackHistorySeedSource(
                        podcastId = "podcast",
                        episodeTitle = "Catalog title",
                    ),
                ),
                podcast = null,
                telemetry = PlaybackHistorySeedSource(episodeTitle = "Telemetry title"),
                nowMs = 123L,
            )

        assertEquals("Catalog title", history?.episodeTitle)
        assertEquals("podcast", history?.podcastId)
    }

    @Test
    fun `first positive source duration fills an unknown player duration`() {
        val history =
            PlaybackHistorySeedPolicy.build(
                snapshot = snapshot(episodeTitle = null, durationMs = 0L),
                sources =
                listOf(
                    PlaybackHistorySeedSource(durationMs = 0L),
                    PlaybackHistorySeedSource(
                        episodeTitle = "Downloaded title",
                        durationMs = 3_600_000L,
                    ),
                ),
                podcast = null,
                telemetry = null,
                nowMs = 123L,
            )

        assertEquals(3_600_000L, history?.durationMs)
    }

    @Test
    fun `missing title in every source skips history seed`() {
        assertNull(
            PlaybackHistorySeedPolicy.build(
                snapshot = snapshot(episodeTitle = " "),
                sources = listOf(PlaybackHistorySeedSource(episodeTitle = null)),
                podcast = null,
                telemetry = PlaybackHistorySeedSource(episodeTitle = ""),
                nowMs = 123L,
            ),
        )
    }

    @Test
    fun `blank snapshot and source podcastName falls back to telemetry`() {
        val history =
            PlaybackHistorySeedPolicy.build(
                snapshot =
                snapshot(episodeTitle = "Test Episode").copy(
                    podcastName = "",
                ),
                sources =
                listOf(
                    PlaybackHistorySeedSource(podcastName = "  ", podcastId = " "),
                ),
                podcast = null,
                telemetry =
                PlaybackHistorySeedSource(
                    podcastName = "Telemetry Show",
                    podcastId = "telemetry-pod",
                ),
                nowMs = 123L,
            )

        assertEquals("Telemetry Show", history?.podcastName)
        assertEquals("telemetry-pod", history?.podcastId)
    }

    @Test
    fun `blank snapshot podcastName falls back to podcast entity`() {
        val history =
            PlaybackHistorySeedPolicy.build(
                snapshot =
                snapshot(episodeTitle = "Test Episode").copy(
                    podcastName = "   ",
                ),
                sources =
                listOf(
                    PlaybackHistorySeedSource(
                        podcastName = "   ",
                        podcastImageUrl = " ",
                    ),
                ),
                podcast =
                PlaybackHistorySeedSource(
                    podcastName = "Entity Show",
                    podcastImageUrl = "https://example.com/pod.png",
                ),
                telemetry = null,
                nowMs = 123L,
            )

        assertEquals("Entity Show", history?.podcastName)
        assertEquals("https://example.com/pod.png", history?.podcastImageUrl)
    }

    private fun snapshot(episodeTitle: String?, durationMs: Long = 1_000L,) = PlaybackProgressSnapshot(
        sequence = 1L,
        episodeId = "episode",
        positionMs = 500L,
        durationMs = durationMs,
        hasBeenPlayingFor10s = true,
        allowZeroPosition = false,
        episodeTitle = episodeTitle,
        episodeImageUrl = null,
        episodeAudioUrl = "https://example.com/audio.mp3",
        podcastName = "Podcast",
        enclosureType = "audio/mpeg",
    )
}
