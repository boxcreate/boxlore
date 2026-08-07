package cx.aswin.boxlore.feature.widgets

import cx.aswin.boxlore.feature.widgets.logic.WidgetOptimisticAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetOptimisticActionTest {
    @Test
    fun `skip back floors at zero`() {
        val snapshot =
            NowPlayingWidgetSnapshot(
                episodeId = "ep-1",
                positionMs = 5_000L,
                seekBackwardMs = 15_000L,
            )

        val result = WidgetOptimisticAction.apply(snapshot, WidgetControl.SKIP_BACK)

        assertEquals(0L, result.positionMs)
    }

    @Test
    fun `skip forward caps at duration`() {
        val snapshot =
            NowPlayingWidgetSnapshot(
                episodeId = "ep-1",
                positionMs = 80_000L,
                durationMs = 90_000L,
                seekForwardMs = 30_000L,
            )

        val result = WidgetOptimisticAction.apply(snapshot, WidgetControl.SKIP_FORWARD)

        assertEquals(90_000L, result.positionMs)
    }
}
