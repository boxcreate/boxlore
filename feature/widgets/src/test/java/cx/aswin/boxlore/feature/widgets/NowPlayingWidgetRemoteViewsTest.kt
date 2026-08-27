package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun longEpisodeTitleKeepsStandardControlsInsideMinimumHeight() {
        val root =
            renderAtMinimumSize(
                variant = WidgetVariant.NOW_PLAYING,
                widthDp = 245,
                heightDp = 100,
                snapshot =
                    playingSnapshot().copy(
                        episodeTitle =
                            "An exceptionally long episode title that would occupy several lines " +
                                "without a strict metadata boundary",
                    ),
            )

        assertEquals(2, root.findViewById<TextView>(R.id.widget_episode_title).maxLines)
        assertFullyInside(root, R.id.widget_previous)
        assertFullyInside(root, R.id.widget_skip_back)
        assertFullyInside(root, R.id.widget_play_pause)
        assertFullyInside(root, R.id.widget_skip_forward)
        assertFullyInside(root, R.id.widget_next)
    }

    @Test
    fun barControlsStayInsideMinimumHeight() {
        val root =
            renderAtMinimumSize(
                variant = WidgetVariant.BAR,
                widthDp = 245,
                heightDp = 48,
                snapshot = playingSnapshot(),
            )

        assertFullyInside(root, R.id.widget_skip_back)
        assertFullyInside(root, R.id.widget_play_pause)
        assertFullyInside(root, R.id.widget_skip_forward)
    }

    @Test
    fun emptyBarContentStaysInsideMinimumHeight() {
        val root =
            renderAtMinimumSize(
                variant = WidgetVariant.BAR,
                widthDp = 245,
                heightDp = 48,
                snapshot = NowPlayingWidgetSnapshot(),
            )

        assertFullyInside(root, R.id.widget_empty_title)
        assertFullyInside(root, R.id.widget_empty_cta)
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

    private fun renderAtMinimumSize(
        variant: WidgetVariant,
        widthDp: Int,
        heightDp: Int,
        snapshot: NowPlayingWidgetSnapshot,
    ): View {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViews(
                context = context,
                appWidgetId = 6,
                snapshot = snapshot,
                options =
                    Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                    },
                variant = variant,
            )
        return views.apply(context, FrameLayout(context)).also { root ->
            val density = context.resources.displayMetrics.density
            root.measure(
                View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun assertFullyInside(
        root: View,
        viewId: Int,
    ) {
        val view = root.findViewById<View>(viewId)
        var top = view.top
        var parent = view.parent
        while (parent is View && parent !== root) {
            top += parent.top
            parent = parent.parent
        }
        assertTrue("view $viewId starts above its widget", top >= 0)
        assertTrue("view $viewId ends below its widget", top + view.height <= root.height)
    }

    private fun defaultOptions(): Bundle =
        Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
        }
}
