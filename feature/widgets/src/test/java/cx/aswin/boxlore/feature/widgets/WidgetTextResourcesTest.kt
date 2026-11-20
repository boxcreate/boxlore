package cx.aswin.boxlore.feature.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.text.resolve
import cx.aswin.boxlore.feature.widgets.logic.WidgetSemantics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widget's screen-reader sentence and time left, read from resources in English. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetTextResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val minute = 60_000L
    private val snapshot = NowPlayingWidgetSnapshot(
        episodeId = "ep-1",
        episodeTitle = "Episode",
        podcastTitle = "Show",
        isPlaying = true,
        positionMs = 24 * minute,
        durationMs = 60 * minute,
    )

    @Test
    fun screenReaderSentenceReadsAsBefore() {
        fun said(s: NowPlayingWidgetSnapshot) = WidgetSemantics.contentDescription(s).resolve(context)
        assertEquals("Episode from Show, playing, 36 minutes left", said(snapshot))
        assertEquals("Episode from Show, paused, 1 minute left", said(snapshot.copy(isPlaying = false, positionMs = 59 * minute + 30_000L)))
        assertEquals("Episode from Show, playing", said(snapshot.copy(durationMs = 0L)))
        assertEquals("Episode, paused", said(snapshot.copy(isPlaying = false, podcastTitle = "", durationMs = 0L)))
        assertEquals("boxlore now playing widget, no episode", said(NowPlayingWidgetSnapshot()))
    }

    @Test
    fun timeLeftUsesShortUnits() {
        assertEquals("36m left", NowPlayingWidgetRenderer.timeLeftLabel(context, snapshot))
        assertEquals("1h 5m left", NowPlayingWidgetRenderer.timeLeftLabel(context, snapshot.copy(positionMs = 0L, durationMs = 65 * minute)))
        assertEquals("2h left", NowPlayingWidgetRenderer.timeLeftLabel(context, snapshot.copy(positionMs = 0L, durationMs = 120 * minute)))
    }
}
