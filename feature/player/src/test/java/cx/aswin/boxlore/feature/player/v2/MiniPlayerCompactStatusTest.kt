package cx.aswin.boxlore.feature.player.v2

import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.player.R
import cx.aswin.boxlore.feature.player.v2.logic.episode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MiniPlayerCompactStatusTest {
    private val content = MiniPlayerContent(
        episode = episode(),
        podcastTitle = "Test Podcast",
        podcastImageUrl = null,
        isPlaying = false,
        isLoading = false,
        position = 10_000L,
        duration = 60_000L,
    )

    @Test
    fun compactArtworkReportsWhetherPlaybackIsPausedOrPlaying() {
        assertEquals(UiText.res(R.string.player_paused), miniPlayerCompactStateDescription(content))
        assertEquals(UiText.res(R.string.player_playing), miniPlayerCompactStateDescription(content.copy(isPlaying = true)))
    }

    @Test
    fun loadingTakesPrecedenceOverTransportState() {
        assertEquals(UiText.res(R.string.player_loading), miniPlayerCompactStateDescription(content.copy(isPlaying = true, isLoading = true)))
    }

    @Test
    fun castAccessibilityNamesTheReceiverWhilePreservingTransportState() {
        assertEquals(
            UiText.res(R.string.player_state_casting_to, UiText.res(R.string.player_paused), UiText.raw("Living room")),
            miniPlayerCompactStateDescription(content.copy(isCasting = true, castDeviceName = "Living room")),
        )
        assertEquals(
            UiText.res(
                R.string.player_state_casting_to,
                UiText.res(R.string.player_playing),
                UiText.res(R.string.player_cast_device_fallback),
            ),
            miniPlayerCompactStateDescription(content.copy(isPlaying = true, isCasting = true)),
        )
    }

    @Test
    fun progressUsesPlaybackDurationWhenAvailable() {
        assertEquals(1f / 6f, miniPlayerCompactProgress(content.copy(episode = episode().copy(duration = 120))), 0.001f)
        assertEquals(0.5f, miniPlayerCompactProgress(content.copy(position = 30_000L)), 0.001f)
    }

    @Test
    fun progressFallsBackToEpisodeSecondsBeforePlaybackDurationArrives() {
        val restored = content.copy(duration = 0L, episode = episode().copy(duration = 60))
        assertEquals(60_000L, miniPlayerCompactDuration(restored))
        assertEquals(1f / 6f, miniPlayerCompactProgress(restored), 0.001f)
        assertEquals(1f / 6f, miniPlayerCompactProgress(restored.copy(isLoading = true)), 0.001f)
        assertEquals(1f / 6f, miniPlayerCompactProgress(restored.copy(isPlaying = true)), 0.001f)
    }

    @Test
    fun progressHandlesUnknownDurationAndTimelineBounds() {
        assertEquals(0f, miniPlayerCompactProgress(content.copy(duration = 0L, episode = episode().copy(duration = 0))))
        assertEquals(0f, miniPlayerCompactProgress(content.copy(position = -1L)))
        assertEquals(1f, miniPlayerCompactProgress(content.copy(position = 120_000L)))
    }
}
