package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.feature.widgets.logic.WidgetPresentation
import cx.aswin.boxlore.feature.widgets.logic.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NowPlayingWidgetRemoteViewsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun emptySnapshotUsesStandardLayout() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
                context = context,
                appWidgetId = 1,
                snapshot = NowPlayingWidgetSnapshot(),
                options = defaultOptions(),
            )

        assertEquals(R.layout.now_playing_widget_standard, views.layoutId)
    }

    @Test
    fun standardEmptyStateUsesThemedBoxloreIdentity() {
        val root =
            renderAtMinimumSize(
                variant = WidgetVariant.NOW_PLAYING,
                widthDp = 245,
                heightDp = 100,
                snapshot = NowPlayingWidgetSnapshot(),
            )

        assertFullyInside(root, R.id.widget_empty_icon)
        assertFullyInside(root, R.id.widget_empty_title)
        assertFullyInside(root, R.id.widget_empty_subtitle)
    }

    @Test
    fun playingSnapshotUsesStandardLayoutForDefaultOptions() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
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
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
                context = context,
                appWidgetId = 2,
                snapshot = playingSnapshot(),
                options = defaultOptions(),
            )

        views.apply(context, FrameLayout(context))
    }

    @Test
    fun longEpisodeTitleEllipsizesWithoutDisplacingStandardControls() {
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

        assertEquals(1, root.findViewById<TextView>(R.id.widget_episode_title).maxLines)
        val metadata = root.findViewById<View>(R.id.widget_metadata_container)
        assertEquals(1, root.findViewById<TextView>(R.id.widget_episode_title).lineCount)
        assertFullyInside(metadata, R.id.widget_episode_title)
        assertFullyInside(metadata, R.id.widget_podcast_title)
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
                snapshot =
                playingSnapshot().copy(
                    episodeTitle = "A long episode title that must truncate cleanly",
                    podcastTitle = "A long publisher name that must also truncate cleanly",
                ),
            )

        val metadata = root.findViewById<View>(R.id.widget_metadata_container)
        assertFullyInside(metadata, R.id.widget_episode_title)
        assertFullyInside(metadata, R.id.widget_podcast_title)
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

        assertFullyInside(root, R.id.widget_empty_icon)
        assertFullyInside(root, R.id.widget_empty_title)
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

        NowPlayingWidgetRenderer.buildRemoteViewsAt(
            context = context,
            appWidgetId = 2,
            snapshot = playingSnapshot(),
            options = options,
        )
    }

    @Test
    fun barVariantUsesDedicatedHorizontalLayout() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
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
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
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
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
                context = context,
                appWidgetId = 4,
                snapshot = playingSnapshot(),
                options = defaultOptions(),
                variant = WidgetVariant.CONTROLS,
            )

        assertEquals(R.layout.playback_controls_widget, views.layoutId)
    }

    @Test
    fun emptyControlsGridUsesCompactBoxloreIdentity() {
        val root =
            renderAtMinimumSize(
                variant = WidgetVariant.CONTROLS,
                widthDp = 110,
                heightDp = 110,
                snapshot = NowPlayingWidgetSnapshot(),
            )

        assertFullyInside(root, R.id.widget_empty_icon)
        assertFullyInside(root, R.id.widget_empty_title)
    }

    @Test
    fun nextControlsVariantPutsSeekForwardLeftAndNextEpisodeRight() {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
                context = context,
                appWidgetId = 7,
                snapshot = playingSnapshot(),
                options = defaultOptions(),
                variant = WidgetVariant.CONTROLS_NEXT,
            )
        val root = views.apply(context, FrameLayout(context))

        assertEquals(R.layout.playback_controls_widget, views.layoutId)
        assertEquals(
            context.getString(R.string.widget_skip_forward),
            root.findViewById<View>(R.id.widget_skip_back_icon).contentDescription,
        )
        assertEquals(
            context.getString(R.string.widget_next),
            root.findViewById<View>(R.id.widget_skip_forward_icon).contentDescription,
        )
        assertTrue(
            WidgetProviders.all.any {
                it.receiverClass == PlaybackNextControlsWidgetReceiver::class.java &&
                    it.variant == WidgetVariant.CONTROLS_NEXT
            },
        )
    }

    @Test
    fun metaLinePutsTheTimeLeftFirstSoALongShowNameIsWhatGetsCut() {
        val minute = 60_000L
        val snapshot = playingSnapshot().copy(positionMs = 24 * minute, durationMs = 60 * minute)
        val both = WidgetPresentation.Meta.TIME_AND_SHOW

        assertEquals("36m left · ${snapshot.podcastTitle}", NowPlayingWidgetRenderer.metaLine(context, snapshot, both))
        assertEquals("36m left", NowPlayingWidgetRenderer.metaLine(context, snapshot, WidgetPresentation.Meta.TIME))
        assertEquals("", NowPlayingWidgetRenderer.metaLine(context, snapshot, WidgetPresentation.Meta.NONE))
        assertEquals("36m left", NowPlayingWidgetRenderer.metaLine(context, snapshot.copy(podcastTitle = ""), both))
        // No length yet: just the show, never "0m left".
        assertEquals(snapshot.podcastTitle, NowPlayingWidgetRenderer.metaLine(context, snapshot.copy(durationMs = 0L), both))
        assertEquals("", NowPlayingWidgetRenderer.metaLine(context, snapshot.copy(durationMs = 0L), WidgetPresentation.Meta.TIME))
    }

    @Test
    fun timeLeftReadsInHoursAndMinutes() {
        val minute = 60_000L
        val snapshot = playingSnapshot()
        assertEquals("1h 12m left", NowPlayingWidgetRenderer.timeLeftLabel(context, snapshot.copy(positionMs = 14 * minute, durationMs = 86 * minute)))
        assertEquals("2h left", NowPlayingWidgetRenderer.timeLeftLabel(context, snapshot.copy(positionMs = 0L, durationMs = 120 * minute)))
        assertEquals("1m left", NowPlayingWidgetRenderer.timeLeftLabel(context, snapshot.copy(positionMs = 59 * minute + 30_000L, durationMs = 60 * minute)))
        assertEquals(null, NowPlayingWidgetRenderer.timeLeftLabel(context, snapshot.copy(positionMs = 60 * minute, durationMs = 60 * minute)))
    }

    @Test
    fun fullControlsGivesTheTitleMoreLinesAsItGrowsAndFewerAsItShrinks() {
        val minute = 60_000L
        val snapshot = playingSnapshot().copy(positionMs = 24 * minute, durationMs = 60 * minute)

        fun titleAt(heightDp: Int): TextView = renderAtMinimumSize(WidgetVariant.NOW_PLAYING, widthDp = 350, heightDp = heightDp, snapshot = snapshot)
            .findViewById(R.id.widget_episode_title)

        val regular = renderAtMinimumSize(WidgetVariant.NOW_PLAYING, widthDp = 350, heightDp = 182, snapshot = snapshot)
        assertEquals("36m left · ${snapshot.podcastTitle}", regular.findViewById<TextView>(R.id.widget_podcast_title).text.toString())
        assertEquals(2, titleAt(182).maxLines)
        assertEquals(3, titleAt(278).maxLines)
        assertEquals(1, titleAt(100).maxLines)
        assertEquals(2, titleAt(182).maxLines)
    }

    @Test
    fun compactPlayerKeepsTheEpisodeNameAndLeavesTheShowToTheArtwork() {
        val minute = 60_000L
        val snapshot = playingSnapshot().copy(positionMs = 24 * minute, durationMs = 60 * minute)

        val roomy = renderAtMinimumSize(WidgetVariant.BAR, widthDp = 350, heightDp = 86, snapshot = snapshot)
        assertEquals(snapshot.episodeTitle, roomy.findViewById<TextView>(R.id.widget_episode_title).text.toString())
        assertEquals("36m left", roomy.findViewById<TextView>(R.id.widget_podcast_title).text.toString())

        // A slim bar has room for the episode name only.
        val slim = renderAtMinimumSize(WidgetVariant.BAR, widthDp = 350, heightDp = 52, snapshot = snapshot)
        assertEquals(View.GONE, slim.findViewById<View>(R.id.widget_podcast_title).visibility)
        assertEquals(1, slim.findViewById<TextView>(R.id.widget_episode_title).maxLines)
    }

    @Test
    fun aWidgetIsNeverClippedWhenTheLauncherReportsTheWrongSize() {
        val long = playingSnapshot().copy(episodeTitle = "An exceptionally long episode title that would occupy several lines if it could")
        // What the launcher said, then what the widget really got: smaller, larger, and turned on its side.
        val reported = mapOf(WidgetVariant.NOW_PLAYING to (350 to 182), WidgetVariant.BAR to (350 to 86), WidgetVariant.CONTROLS to (170 to 182))
        val actual = listOf(200 to 96, 245 to 100, 300 to 60, 320 to 240, 140 to 140, 420 to 120, 120 to 260)
        val playing = listOf(R.id.widget_play_pause, R.id.widget_skip_back, R.id.widget_skip_forward, R.id.widget_previous, R.id.widget_next, R.id.widget_episode_title, R.id.widget_artwork)
        val resting = listOf(R.id.widget_empty_mark, R.id.widget_empty_title)
        val cases = reported.flatMap { (variant, size) -> actual.flatMap { drawn -> listOf(long, NowPlayingWidgetSnapshot()).map { Triple(variant, size, drawn) to it } } }
        for ((case, snapshot) in cases) {
            val (variant, size, drawn) = case
            val root = renderAt(variant, reportedDp = size, actualDp = drawn, snapshot = snapshot)
            val where = "$variant reported ${size.first}x${size.second}, drawn ${drawn.first}x${drawn.second}"
            val shown = (if (snapshot.hasEpisode) playing else resting).filter { root.findViewById<View>(it)?.visibility == View.VISIBLE }
            for (id in shown) {
                val view = root.findViewById<View>(id)
                assertFullyInside(root, id, where)
                assertTrue("$where: ${context.resources.getResourceEntryName(id)} has no size", view.width > 0 && view.height > 0)
            }
        }
    }

    @Test
    fun theRoundWidgetIsACircleThatFitsAnyCell() {
        for ((widthDp, heightDp) in listOf(110 to 110, 170 to 182, 182 to 170, 350 to 182, 120 to 300)) {
            val root = renderAtMinimumSize(WidgetVariant.CONTROLS, widthDp = widthDp, heightDp = heightDp, snapshot = playingSnapshot())
            val card = root.findViewById<View>(R.id.widget_grid_card)
            assertEquals("a circle at ${widthDp}x$heightDp", card.width, card.height)
            assertEquals("as wide as the shorter side", minOf(root.width, root.height), card.width)
            assertFullyInside(root, R.id.widget_grid_card)
            // The play button never covers the smaller buttons beside it.
            val play = root.findViewById<View>(R.id.widget_play_pause)
            val back = root.findViewById<View>(R.id.widget_skip_back)
            assertTrue("play sits below and between the side buttons", play.width > back.width && play.height > back.height)
        }
    }

    @Test
    fun theLauncherIsGivenOneLayoutForEverySizeStepAndPicksTheSameOneWeDo() {
        val pick = RemoteViews::class.java.getMethod("getRemoteViewsToApply", Context::class.java, SizeF::class.java)
        val snapshot = playingSnapshot().copy(positionMs = 60_000L, durationMs = 3_600_000L)
        for (variant in listOf(WidgetVariant.NOW_PLAYING, WidgetVariant.BAR)) {
            // The report is deliberately wrong: what is shown must follow the space the launcher really has.
            val sent = NowPlayingWidgetRenderer.buildRemoteViews(context, 4, snapshot, sizeOptions(350, 182), variant)
            for (widthDp in 160..640 step 40) {
                for (heightDp in 40..320 step 20) {
                    val launchers = (pick.invoke(sent, context, SizeF(widthDp.toFloat(), heightDp.toFloat())) as RemoteViews).apply(context, FrameLayout(context))
                    val ours =
                        NowPlayingWidgetRenderer
                            .buildRemoteViewsAt(context, 4, snapshot, sizeOptions(350, 182), variant, drawn = WidgetSize(widthDp, heightDp))
                            .apply(context, FrameLayout(context))
                    val where = "$variant drawn ${widthDp}x$heightDp"
                    assertEquals(where, ours.sourceLayoutResId, launchers.sourceLayoutResId)
                    assertEquals(where, ours.findViewById<TextView>(R.id.widget_episode_title).maxLines, launchers.findViewById<TextView>(R.id.widget_episode_title).maxLines)
                    assertEquals(where, ours.findViewById<TextView>(R.id.widget_podcast_title).text.toString(), launchers.findViewById<TextView>(R.id.widget_podcast_title).text.toString())
                }
            }
        }
    }

    @Test
    fun whatAWidgetShowsFollowsTheSpaceItReallyHasNotTheReport() {
        val long = playingSnapshot().copy(episodeTitle = "An exceptionally long episode title that would occupy several lines", positionMs = 60_000L, durationMs = 3_600_000L)
        // Reported as a roomy 4x2, really drawn short: one title line and no show name, all of it readable.
        val short = renderAt(WidgetVariant.NOW_PLAYING, reportedDp = 350 to 182, actualDp = 300 to 110, snapshot = long, pickedByLauncher = true)
        assertEquals(1, short.findViewById<TextView>(R.id.widget_episode_title).maxLines)
        assertEquals("59m left", short.findViewById<TextView>(R.id.widget_podcast_title).text.toString())
        // Reported short, really drawn roomy: the second line and the show name come back.
        val roomy = renderAt(WidgetVariant.NOW_PLAYING, reportedDp = 300 to 110, actualDp = 350 to 182, snapshot = long, pickedByLauncher = true)
        assertEquals(2, roomy.findViewById<TextView>(R.id.widget_episode_title).maxLines)
        assertEquals("59m left · Podcast", roomy.findViewById<TextView>(R.id.widget_podcast_title).text.toString())
        // The round widget is one layout: it shows the same thing at every size.
        assertEquals(1, NowPlayingWidgetRenderer.stepsFor(WidgetVariant.CONTROLS).size)
    }

    // Real text measuring: the default test fonts do not have real line heights.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun textIsNeverCutThroughALineAtAnySizeTheLauncherCanDraw() {
        val long = playingSnapshot().copy(episodeTitle = "An exceptionally long episode title that would occupy several lines if it could", positionMs = 60_000L, durationMs = 3_600_000L)
        val heights = mapOf(WidgetVariant.NOW_PLAYING to (100..320 step 10), WidgetVariant.BAR to (48..200 step 8))
        val sizes = heights.flatMap { (variant, range) -> listOf(245, 300, 350, 420, 620).flatMap { widthDp -> range.map { heightDp -> Triple(variant, widthDp, heightDp) } } }
        for ((variant, widthDp, heightDp) in sizes) {
            val root = renderAt(variant, reportedDp = 350 to 182, actualDp = widthDp to heightDp, snapshot = long, pickedByLauncher = true)
            val texts = listOf(R.id.widget_episode_title, R.id.widget_podcast_title).map { root.findViewById<TextView>(it) }.filter { it.visibility == View.VISIBLE }
            for (text in texts) {
                val shownLines = minOf(text.layout.lineCount, text.maxLines)
                val needed = text.layout.getLineBottom(shownLines - 1) + text.paddingTop + text.paddingBottom
                assertTrue(
                    "$variant ${widthDp}x$heightDp: ${context.resources.getResourceEntryName(text.id)} needs ${needed}px for $shownLines lines, has ${text.height}px",
                    needed <= text.height + 1,
                )
            }
        }
    }

    /** Builds for one size and lays out at another, the way a launcher with a stale report would. */
    private fun renderAt(
        variant: WidgetVariant,
        reportedDp: Pair<Int, Int>,
        actualDp: Pair<Int, Int>,
        snapshot: NowPlayingWidgetSnapshot,
        pickedByLauncher: Boolean = false,
    ): View {
        // A launcher picks the layout for the space it has. Left to the report, this draws the wrong pick.
        val drawn = if (pickedByLauncher) WidgetSize(actualDp.first, actualDp.second) else WidgetSize(reportedDp.first, reportedDp.second)
        val views = NowPlayingWidgetRenderer.buildRemoteViewsAt(context, 8, snapshot, sizeOptions(reportedDp.first, reportedDp.second), variant, drawn)
        return views.apply(context, FrameLayout(context)).also { root ->
            val density = context.resources.displayMetrics.density
            root.measure(
                View.MeasureSpec.makeMeasureSpec((actualDp.first * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((actualDp.second * density).toInt(), View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun sizeOptions(widthDp: Int, heightDp: Int): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
    }

    private fun playingSnapshot() = NowPlayingWidgetSnapshot(
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
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
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

    /** Fails when any edge of the view lies outside [root], on either axis. */
    private fun assertFullyInside(
        root: View,
        viewId: Int,
        where: String = "",
    ) {
        val view = root.findViewById<View>(viewId)
        val name = "$where ${context.resources.getResourceEntryName(viewId)}".trim()
        var top = view.top
        var left = view.left
        var parent = view.parent
        while (parent is View && parent !== root) {
            top += parent.top
            left += parent.left
            parent = parent.parent
        }
        assertTrue("$name starts above its widget", top >= 0)
        assertTrue("$name ends below its widget", top + view.height <= root.height)
        assertTrue("$name starts before its widget", left >= 0)
        assertTrue("$name ends past its widget", left + view.width <= root.width)
    }

    private fun defaultOptions(): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
    }
}
