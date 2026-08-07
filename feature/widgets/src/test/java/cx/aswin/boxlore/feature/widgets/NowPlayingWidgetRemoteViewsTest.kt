package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NowPlayingWidgetRemoteViewsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun emptySnapshotUsesStandardLayout() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViews(
                context = context,
                appWidgetId = 1,
                snapshot = NowPlayingWidgetSnapshot(),
                options = defaultOptions(),
            )

        assertEquals(R.layout.now_playing_widget_standard, views.layoutId)
    }

    @Test
    fun playingSnapshotUsesStandardLayoutForDefaultOptions() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViews(
                context = context,
                appWidgetId = 2,
                snapshot =
                    NowPlayingWidgetSnapshot(
                        episodeId = "ep-1",
                        episodeTitle = "Episode",
                        podcastTitle = "Podcast",
                        isPlaying = true,
                    ),
                options = defaultOptions(),
            )

        assertEquals(R.layout.now_playing_widget_standard, views.layoutId)
    }

    @Test
    fun standardLayoutCanBeInflatedByRemoteViews() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViews(
                context = context,
                appWidgetId = 2,
                snapshot = playingSnapshot(),
                options = defaultOptions(),
            )

        views.apply(context, FrameLayout(context))
    }

    @Test
    fun responsiveSizesUseLauncherSizeList() {
        val options =
            defaultOptions().apply {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(245f, 110f), SizeF(300f, 150f)),
                )
            }

        NowPlayingWidgetRenderer.buildRemoteViews(
            context = context,
            appWidgetId = 2,
            snapshot = playingSnapshot(),
            options = options,
        )
    }

    @Test
    fun barVariantUsesDedicatedHorizontalLayout() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViews(
                context = context,
                appWidgetId = 3,
                snapshot = playingSnapshot(),
                options = defaultOptions(),
                variant = WidgetVariant.BAR,
            )

        assertEquals(R.layout.now_playing_widget_bar, views.layoutId)
    }

    @Test
    fun barVariantPrefersMaxWidthWhenWiderThanMin() {
        val options =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 245)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 400)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 56)
            }

        val views =
            NowPlayingWidgetRenderer.buildRemoteViews(
                context = context,
                appWidgetId = 5,
                snapshot = playingSnapshot(),
                options = options,
                variant = WidgetVariant.BAR,
            )

        assertEquals(R.layout.now_playing_widget_bar, views.layoutId)
        views.apply(context, FrameLayout(context))
    }

    @Test
    fun controlsVariantUsesDedicatedGridLayout() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViews(
                context = context,
                appWidgetId = 4,
                snapshot = playingSnapshot(),
                options = defaultOptions(),
                variant = WidgetVariant.CONTROLS,
            )

        assertEquals(R.layout.playback_controls_widget, views.layoutId)
    }

    private fun playingSnapshot() =
        NowPlayingWidgetSnapshot(
            episodeId = "ep-1",
            episodeTitle = "A long episode title that needs careful fitting",
            podcastTitle = "Podcast",
            isPlaying = true,
        )

    private fun defaultOptions(): Bundle =
        Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
        }
}
