package cx.aswin.boxlore.feature.widgets.logic

import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetSnapshot
import cx.aswin.boxlore.feature.widgets.WidgetControl
import cx.aswin.boxlore.feature.widgets.WidgetPlaybackState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NowPlayingWidgetLogicTest {
    @Test
    fun `mapper copies playback fields into snapshot`() {
        val snapshot =
            NowPlayingWidgetMapper.fromPlayback(
                state =
                WidgetPlaybackState(
                    episodeId = "ep-1",
                    episodeTitle = "Episode title",
                    podcastTitle = "Podcast title",
                    artworkUrl = "https://example.com/art.jpg",
                    isPlaying = true,
                    positionMs = 12_000L,
                    durationMs = 60_000L,
                    seekForwardMs = 30_000L,
                    seekBackwardMs = 10_000L,
                ),
                nowMs = 1_000L,
            )

        assertEquals("ep-1", snapshot.episodeId)
        assertEquals("Episode title", snapshot.episodeTitle)
        assertEquals("Podcast title", snapshot.podcastTitle)
        assertTrue(snapshot.isPlaying)
        assertEquals(12_000L, snapshot.positionMs)
        assertEquals(30_000L, snapshot.seekForwardMs)
        assertEquals(10_000L, snapshot.seekBackwardMs)
    }

    @Test
    fun `update policy renders only presentation changes`() {
        val base =
            NowPlayingWidgetSnapshot(
                episodeId = "ep-1",
                isPlaying = true,
            )

        assertTrue(WidgetUpdatePolicy.shouldRender(null, base))
        assertTrue(
            WidgetUpdatePolicy.shouldRender(
                previous = base,
                next = base.copy(isPlaying = false),
            ),
        )
        assertFalse(
            WidgetUpdatePolicy.shouldRender(
                previous = base,
                next = base.copy(positionMs = 45_000L, updatedAtMs = 60_000L),
            ),
        )
    }

    @Test
    fun `optimistic toggle flips playing state`() {
        val snapshot =
            NowPlayingWidgetSnapshot(
                episodeId = "ep-1",
                isPlaying = false,
                updatedAtMs = 100L,
            )

        val optimistic =
            WidgetOptimisticAction.apply(
                snapshot = snapshot,
                control = WidgetControl.TOGGLE,
                nowMs = 200L,
            )

        assertTrue(optimistic.isPlaying)
        assertEquals(200L, optimistic.updatedAtMs)
    }

    @Test
    fun `optimistic seek adjusts position by configured amounts`() {
        val snapshot =
            NowPlayingWidgetSnapshot(
                episodeId = "ep-1",
                positionMs = 40_000L,
                durationMs = 90_000L,
                seekForwardMs = 30_000L,
                seekBackwardMs = 15_000L,
            )

        val back = WidgetOptimisticAction.apply(snapshot, WidgetControl.SKIP_BACK, nowMs = 100L)
        val forward = WidgetOptimisticAction.apply(snapshot, WidgetControl.SKIP_FORWARD, nowMs = 200L)

        assertEquals(25_000L, back.positionMs)
        assertEquals(70_000L, forward.positionMs)
    }
}
