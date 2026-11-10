package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import cx.aswin.boxlore.feature.widgets.actions.WidgetActionIntents
import cx.aswin.boxlore.feature.widgets.logic.WidgetPresentation
import cx.aswin.boxlore.feature.widgets.logic.WidgetProgress
import cx.aswin.boxlore.feature.widgets.logic.WidgetSemantics
import cx.aswin.boxlore.feature.widgets.logic.WidgetSize
import cx.aswin.boxlore.feature.widgets.logic.WidgetSizes
import kotlin.math.roundToInt

/**
 * Draws the four playback widgets. The layouts scale themselves to the space the launcher gives them,
 * so this only decides what to show ([WidgetPresentation]), supplies the pictures and tints every part
 * through [WidgetChrome]. The size a launcher reports is used for those choices and for how sharp to
 * draw the pictures, never to lay anything out.
 */
@Suppress("TooManyFunctions")
object NowPlayingWidgetRenderer {
    fun updateAll(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: NowPlayingWidgetSnapshot,
        optionsById: Map<Int, Bundle> = emptyMap(),
        variant: WidgetVariant = WidgetVariant.NOW_PLAYING,
    ) {
        appWidgetIds.forEach { id ->
            val options = optionsById[id] ?: appWidgetManager.getAppWidgetOptions(id)
            updateOne(context, appWidgetManager, id, snapshot, options, variant)
        }
    }

    fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: NowPlayingWidgetSnapshot,
        options: Bundle = appWidgetManager.getAppWidgetOptions(appWidgetId),
        variant: WidgetVariant = WidgetVariant.NOW_PLAYING,
    ) {
        val remoteViews = buildRemoteViews(context, appWidgetId, snapshot, options, variant)
        // Over Android 17's bitmap cap, fall back to the placeholder artwork rather than an empty widget.
        appWidgetManager.pushWidgetUpdate(appWidgetId, remoteViews) {
            buildRemoteViews(context, appWidgetId, snapshot.copy(artworkCachePath = null), options, variant)
        }
    }

    /**
     * What a widget shows depends on its size, and launchers report sizes late, loosely or not at all.
     * So the widget is built once for every size at which what it shows changes, and the launcher
     * itself draws the one that fits the space it really has, also while the widget is being resized.
     * The pictures are the same objects in each, so they are sent once.
     */
    internal fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        snapshot: NowPlayingWidgetSnapshot,
        options: Bundle,
        variant: WidgetVariant = WidgetVariant.NOW_PLAYING,
    ): RemoteViews {
        val steps = stepsFor(variant)
        val update = Update(context, appWidgetId, snapshot, variant, WidgetChrome.resolve(context), reportedSizes(options, variant).largest)
        if (steps.size == 1) return buildStep(update, steps.single())
        return RemoteViews(steps.associate { step -> SizeF(step.widthDp.toFloat(), step.heightDp.toFloat()) to buildStep(update, step) })
    }

    /** The one layout a launcher shows for a widget really drawn at [drawn]: for previews and tests. */
    internal fun buildRemoteViewsAt(
        context: Context,
        appWidgetId: Int,
        snapshot: NowPlayingWidgetSnapshot,
        options: Bundle,
        variant: WidgetVariant = WidgetVariant.NOW_PLAYING,
        drawn: WidgetSize = reportedSizes(options, variant).portrait,
    ): RemoteViews {
        val update = Update(context, appWidgetId, snapshot, variant, WidgetChrome.resolve(context), reportedSizes(options, variant).largest)
        return buildStep(update, WidgetPresentation.stepFor(stepsFor(variant), drawn))
    }

    /** The sizes at which what [variant] shows changes, smallest first. The round widget never changes. */
    internal fun stepsFor(variant: WidgetVariant): List<WidgetSize> = when (variant) {
        WidgetVariant.NOW_PLAYING -> WidgetPresentation.FULL_CONTROLS_STEPS
        WidgetVariant.BAR -> WidgetPresentation.BAR_STEPS
        WidgetVariant.CONTROLS,
        WidgetVariant.CONTROLS_NEXT,
        -> listOf(WidgetSize(0, 0))
    }

    private fun buildStep(update: Update, shown: WidgetSize): RemoteViews {
        val frame = Frame(update, shown)
        return RemoteViews(update.context.packageName, layoutFor(update.variant, shown)).also { views ->
            when (update.variant) {
                WidgetVariant.NOW_PLAYING -> bindFullControls(frame, views)
                WidgetVariant.BAR -> bindBar(frame, views)
                WidgetVariant.CONTROLS,
                WidgetVariant.CONTROLS_NEXT,
                -> bindRound(frame, views)
            }
        }
    }

    internal fun layoutFor(variant: WidgetVariant, shown: WidgetSize): Int = when (variant) {
        WidgetVariant.NOW_PLAYING ->
            if (WidgetPresentation.fullControls(shown).tall) {
                R.layout.now_playing_widget_standard_tall
            } else {
                R.layout.now_playing_widget_standard
            }
        WidgetVariant.BAR -> R.layout.now_playing_widget_bar
        WidgetVariant.CONTROLS,
        WidgetVariant.CONTROLS_NEXT,
        -> R.layout.playback_controls_widget
    }

    internal fun reportedSizes(options: Bundle, variant: WidgetVariant): WidgetSizes = WidgetSizes.of(
        minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
        maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
        minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
        maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
        fallback =
        when (variant) {
            WidgetVariant.NOW_PLAYING -> WidgetSize(FALLBACK_WIDE_DP, FALLBACK_TWO_ROWS_DP)
            WidgetVariant.BAR -> WidgetSize(FALLBACK_WIDE_DP, FALLBACK_ONE_ROW_DP)
            WidgetVariant.CONTROLS,
            WidgetVariant.CONTROLS_NEXT,
            -> WidgetSize(FALLBACK_TWO_ROWS_DP, FALLBACK_TWO_ROWS_DP)
        },
    )

    /** Everything one widget update needs, whatever size it is drawn at. */
    private class Update(
        val context: Context,
        val appWidgetId: Int,
        val snapshot: NowPlayingWidgetSnapshot,
        val variant: WidgetVariant,
        val chrome: WidgetChrome,
        /** The most space the launcher says the widget may get, so pictures stay sharp at any size. */
        val largest: WidgetSize,
    )

    /** One update drawn for one size step. */
    private class Frame(
        update: Update,
        /** The size what is shown is chosen for. */
        val shown: WidgetSize,
    ) {
        val context: Context = update.context
        val appWidgetId: Int = update.appWidgetId
        val snapshot: NowPlayingWidgetSnapshot = update.snapshot
        val variant: WidgetVariant = update.variant
        val chrome: WidgetChrome = update.chrome
        val largest: WidgetSize = update.largest
        private val density: Float = context.resources.displayMetrics.density

        /** Pixels for a picture about [dp] across, drawn sharp enough for the largest size reported. */
        fun px(dp: Number): Int = (dp.toFloat() * density).roundToInt().coerceIn(MIN_PICTURE_PX, MAX_PICTURE_PX)
    }

    // region Now Playing · Full Controls

    private fun bindFullControls(frame: Frame, views: RemoteViews) {
        val show = WidgetPresentation.fullControls(frame.shown)
        bindRoot(frame, views)

        if (!frame.snapshot.hasEpisode) {
            bindRest(frame, views, showSubtitle = true)
            return
        }
        showPlaying(views)
        frame.chrome.setColorFilter(views, R.id.widget_surface_background, WidgetPalette.primaryContainer)
        val tallestDp = frame.largest.heightDp
        bindDisc(frame, views, frame.px(if (show.tall) tallestDp * TALL_DISC_SHARE else tallestDp / 2f), DISC_ART_INSET, DISC_RING_STROKE)
        bindMetadata(frame, views, show.titleLines, show.meta)

        frame.chrome.setColorFilter(views, R.id.widget_controls_start_background, WidgetPalette.surface)
        frame.chrome.setColorFilter(views, R.id.widget_controls_end_background, WidgetPalette.surface)
        bindPlayPause(frame, views)
        val seekColors = ControlColors(WidgetPalette.primaryContainer, WidgetPalette.onPrimaryContainer)
        bindControl(frame, views, Slot.LEADING, WidgetControl.SKIP_BACK, seekColors)
        bindControl(frame, views, Slot.TRAILING, WidgetControl.SKIP_FORWARD, seekColors)
        val edgeColors = ControlColors(background = null, icon = WidgetPalette.onSurface)
        bindControl(frame, views, Slot.PREVIOUS, WidgetControl.PREVIOUS, edgeColors)
        bindControl(frame, views, Slot.NEXT, WidgetControl.NEXT, edgeColors)
    }

    // endregion

    // region Now Playing · Compact

    private fun bindBar(frame: Frame, views: RemoteViews) {
        val show = WidgetPresentation.bar(frame.shown)
        bindRoot(frame, views)

        if (!frame.snapshot.hasEpisode) {
            bindRest(frame, views, showSubtitle = show.showTimeLeft)
            return
        }
        showPlaying(views)
        frame.chrome.setColorFilter(views, R.id.widget_surface_background, WidgetPalette.primaryContainer)
        bindDisc(frame, views, frame.px(frame.largest.heightDp.coerceAtMost(BAR_MAX_DISC_DP)), DISC_ART_INSET, DISC_RING_STROKE)
        // The artwork already says which show this is, so the narrow title area keeps the episode name.
        bindMetadata(frame, views, show.titleLines, if (show.showTimeLeft) WidgetPresentation.Meta.TIME else WidgetPresentation.Meta.NONE)

        bindPlayPause(frame, views)
        val miniColors = ControlColors(WidgetPalette.surface, WidgetPalette.onSurface)
        bindControl(frame, views, Slot.LEADING, WidgetControl.SKIP_BACK, miniColors)
        bindControl(frame, views, Slot.TRAILING, WidgetControl.SKIP_FORWARD, miniColors)
    }

    // endregion

    // region Now Playing · Round

    private fun bindRound(frame: Frame, views: RemoteViews) {
        bindRoot(frame, views)
        frame.chrome.setColorFilter(views, R.id.widget_surface_background, WidgetPalette.primaryContainer)
        if (!frame.snapshot.hasEpisode) {
            bindRest(frame, views, showSubtitle = false)
            return
        }
        showPlaying(views)
        bindDisc(frame, views, frame.px(frame.largest.shortSide), ROUND_ART_INSET, ROUND_RING_STROKE)
        bindPlayPause(frame, views)
        val miniColors = ControlColors(WidgetPalette.surface, WidgetPalette.onSurface)
        // Seek has back and forward. Next keeps forward on the left and puts next episode on the right.
        val next = frame.variant == WidgetVariant.CONTROLS_NEXT
        bindControl(frame, views, Slot.LEADING, if (next) WidgetControl.SKIP_FORWARD else WidgetControl.SKIP_BACK, miniColors)
        bindControl(frame, views, Slot.TRAILING, if (next) WidgetControl.NEXT else WidgetControl.SKIP_FORWARD, miniColors)
    }

    // endregion

    // region Shared parts

    private fun bindRoot(frame: Frame, views: RemoteViews) {
        views.setContentDescription(R.id.widget_root, WidgetSemantics.contentDescription(frame.snapshot))
        views.setOnClickPendingIntent(R.id.widget_root, WidgetActionIntents.openApp(frame.context))
    }

    private fun showPlaying(views: RemoteViews) {
        views.setViewVisibility(R.id.widget_empty_container, View.GONE)
        views.setViewVisibility(R.id.widget_playing_container, View.VISIBLE)
    }

    /** Nothing playing: the scalloped mark and a line that opens boxlore. */
    private fun bindRest(frame: Frame, views: RemoteViews, showSubtitle: Boolean) {
        views.setViewVisibility(R.id.widget_playing_container, View.GONE)
        views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
        // The round widget rests on its own circle; the others have a pill of their own.
        if (frame.variant == WidgetVariant.NOW_PLAYING || frame.variant == WidgetVariant.BAR) {
            frame.chrome.setColorFilter(views, R.id.widget_empty_surface_background, WidgetPalette.primaryContainer)
            views.setViewVisibility(R.id.widget_empty_subtitle, if (showSubtitle) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_empty_subtitle, frame.context.getString(R.string.widget_empty_subtitle))
            frame.chrome.setTextColor(views, R.id.widget_empty_subtitle, WidgetPalette.onPrimaryContainer)
            views.setInt(
                R.id.widget_empty_title,
                METHOD_GRAVITY,
                if (showSubtitle) Gravity.BOTTOM or Gravity.START else Gravity.CENTER_VERTICAL or Gravity.START,
            )
        }
        frame.chrome.setColorFilter(views, R.id.widget_empty_mark_background, WidgetPalette.primary)
        frame.chrome.setColorFilter(views, R.id.widget_empty_icon, WidgetPalette.onPrimary)
        views.setTextViewText(R.id.widget_empty_title, frame.context.getString(R.string.widget_empty_title))
        frame.chrome.setTextColor(views, R.id.widget_empty_title, WidgetPalette.onPrimaryContainer)
        views.setOnClickPendingIntent(R.id.widget_empty_container, WidgetActionIntents.openApp(frame.context))
    }

    /** Round artwork inside the progress ring: what has played, a gap, then what is left. */
    private fun bindDisc(frame: Frame, views: RemoteViews, discPx: Int, artInset: Float, ringStroke: Float) {
        val snapshot = frame.snapshot
        val ring = WidgetBitmapCache.progressRing(discPx, WidgetProgress.ringStep(snapshot.positionMs, snapshot.durationMs), ringStroke)
        views.setMaskOrHide(R.id.widget_progress, ring.played)
        frame.chrome.setColorFilter(views, R.id.widget_progress, WidgetPalette.primary)
        views.setMaskOrHide(R.id.widget_progress_track, ring.remaining)
        frame.chrome.setColorFilter(views, R.id.widget_progress_track, WidgetPalette.onPrimaryContainer)
        views.setInt(R.id.widget_progress_track, "setImageAlpha", TRACK_ALPHA)

        val artwork =
            snapshot.artworkCachePath
                ?.takeIf(String::isNotBlank)
                ?.let { path -> WidgetBitmapCache.artwork(path, discPx, WidgetArtShape.CIRCLE, artInset) }
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
            views.setViewVisibility(R.id.widget_artwork, View.VISIBLE)
            views.setViewVisibility(R.id.widget_artwork_placeholder, View.GONE)
            views.setViewVisibility(R.id.widget_artwork_mark, View.GONE)
            return
        }
        // No artwork yet: a tinted circle with the boxlore mark, in the same place.
        views.setViewVisibility(R.id.widget_artwork, View.GONE)
        views.setImageViewBitmap(R.id.widget_artwork_placeholder, WidgetBitmapCache.mask(WidgetArtShape.CIRCLE, discPx, artInset))
        views.setViewVisibility(R.id.widget_artwork_placeholder, View.VISIBLE)
        frame.chrome.setColorFilter(views, R.id.widget_artwork_placeholder, WidgetPalette.secondaryContainer)
        views.setViewVisibility(R.id.widget_artwork_mark, View.VISIBLE)
        frame.chrome.setColorFilter(views, R.id.widget_artwork_mark, WidgetPalette.onSecondaryContainer)
    }

    /**
     * The episode title, then whatever there is room for under it. The title always comes first: when
     * the line under it goes, the title is centred in the space instead.
     */
    private fun bindMetadata(frame: Frame, views: RemoteViews, titleLines: Int, meta: WidgetPresentation.Meta) {
        val line = metaLine(frame.context, frame.snapshot, meta)
        views.setTextViewText(R.id.widget_episode_title, frame.snapshot.episodeTitle)
        views.setInt(R.id.widget_episode_title, "setMaxLines", titleLines)
        views.setInt(
            R.id.widget_episode_title,
            METHOD_GRAVITY,
            if (line.isEmpty()) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.BOTTOM or Gravity.START,
        )
        frame.chrome.setTextColor(views, R.id.widget_episode_title, WidgetPalette.onPrimaryContainer)
        views.setViewVisibility(R.id.widget_podcast_title, if (line.isEmpty()) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_podcast_title, line)
        frame.chrome.setTextColor(views, R.id.widget_podcast_title, WidgetPalette.onPrimaryContainer)
    }

    /**
     * "36m left · The Daily Lore": the time first, so a long show name is what gets cut off. With no
     * time to show yet, the show name stands alone wherever there is room for it.
     */
    internal fun metaLine(context: Context, snapshot: NowPlayingWidgetSnapshot, meta: WidgetPresentation.Meta): String {
        val timeLeft = timeLeftLabel(context, snapshot)
        val show = snapshot.podcastTitle
        return when (meta) {
            WidgetPresentation.Meta.NONE -> ""
            WidgetPresentation.Meta.TIME -> timeLeft.orEmpty()
            WidgetPresentation.Meta.TIME_AND_SHOW ->
                when {
                    timeLeft == null -> show
                    show.isBlank() -> timeLeft
                    else -> context.getString(R.string.widget_meta_time_and_show, timeLeft, show)
                }
        }
    }

    internal fun timeLeftLabel(context: Context, snapshot: NowPlayingWidgetSnapshot): String? {
        val left = WidgetProgress.timeLeft(snapshot.positionMs, snapshot.durationMs) ?: return null
        return when {
            left.hours == 0 -> context.getString(R.string.widget_time_left_minutes, left.minutes)
            left.minutes == 0 -> context.getString(R.string.widget_time_left_hours, left.hours)
            else -> context.getString(R.string.widget_time_left_hours_minutes, left.hours, left.minutes)
        }
    }

    private fun bindPlayPause(frame: Frame, views: RemoteViews) {
        bindPlayPauseState(frame.context, views, frame.chrome, frame.snapshot.isPlaying)
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            WidgetActionIntents.broadcast(frame.context, frame.appWidgetId, WidgetControl.TOGGLE),
        )
    }

    /** The play button: a circle at rest, a scalloped cookie while playing. */
    private fun bindPlayPauseState(context: Context, views: RemoteViews, chrome: WidgetChrome, playing: Boolean) {
        views.setImageViewResource(
            R.id.widget_play_pause_background,
            if (playing) R.drawable.widget_cookie else R.drawable.widget_circle,
        )
        chrome.setColorFilter(views, R.id.widget_play_pause_background, WidgetPalette.primary)
        views.setImageViewResource(
            R.id.widget_play_pause_icon,
            if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        chrome.setColorFilter(views, R.id.widget_play_pause_icon, WidgetPalette.onPrimary)
        views.setContentDescription(
            R.id.widget_play_pause_icon,
            context.getString(if (playing) R.string.widget_pause else R.string.widget_play),
        )
    }

    /** The views of one control. Leading and trailing hold whichever control the widget puts there. */
    private enum class Slot(val container: Int, val background: Int?, val icon: Int) {
        LEADING(R.id.widget_skip_back, R.id.widget_skip_back_background, R.id.widget_skip_back_icon),
        TRAILING(R.id.widget_skip_forward, R.id.widget_skip_forward_background, R.id.widget_skip_forward_icon),
        PREVIOUS(R.id.widget_previous, null, R.id.widget_previous_icon),
        NEXT(R.id.widget_next, null, R.id.widget_next_icon),
    }

    private class ControlColors(@ColorRes val background: Int?, @ColorRes val icon: Int)

    private fun bindControl(
        frame: Frame,
        views: RemoteViews,
        slot: Slot,
        control: WidgetControl,
        colors: ControlColors,
    ) {
        val context = frame.context
        if (slot.background != null && colors.background != null) {
            frame.chrome.setColorFilter(views, slot.background, colors.background)
        }
        val description: Int
        when (control) {
            WidgetControl.SKIP_BACK -> {
                views.setImageViewBitmap(slot.icon, WidgetBitmapCache.seekIcon(context, seconds(frame.snapshot.seekBackwardMs), forward = false))
                description = R.string.widget_skip_back
            }
            WidgetControl.SKIP_FORWARD -> {
                views.setImageViewBitmap(slot.icon, WidgetBitmapCache.seekIcon(context, seconds(frame.snapshot.seekForwardMs), forward = true))
                description = R.string.widget_skip_forward
            }
            WidgetControl.NEXT -> {
                views.setImageViewResource(slot.icon, R.drawable.ic_widget_next)
                description = R.string.widget_next
            }
            WidgetControl.PREVIOUS -> {
                views.setImageViewResource(slot.icon, R.drawable.ic_widget_previous)
                description = R.string.widget_previous
            }
            WidgetControl.TOGGLE,
            WidgetControl.OPEN_APP,
            -> return
        }
        frame.chrome.setColorFilter(views, slot.icon, colors.icon)
        views.setContentDescription(slot.icon, context.getString(description))
        views.setOnClickPendingIntent(
            slot.container,
            WidgetActionIntents.broadcast(context, frame.appWidgetId, control),
        )
    }

    private fun seconds(ms: Long): Int = (ms / MS_PER_SECOND).toInt().coerceAtLeast(1)

    // endregion

    private fun RemoteViews.setMaskOrHide(viewId: Int, mask: Bitmap?) {
        if (mask == null) {
            setViewVisibility(viewId, View.GONE)
        } else {
            setImageViewBitmap(viewId, mask)
            setViewVisibility(viewId, View.VISIBLE)
        }
    }

    private const val FALLBACK_WIDE_DP = 250
    private const val FALLBACK_TWO_ROWS_DP = 110
    private const val FALLBACK_ONE_ROW_DP = 48

    private const val METHOD_GRAVITY = "setGravity"
    private const val MIN_PICTURE_PX = 96
    private const val MAX_PICTURE_PX = 512
    private const val MS_PER_SECOND = 1000L
    private const val TRACK_ALPHA = 56
    private const val BAR_MAX_DISC_DP = 110
    private const val TALL_DISC_SHARE = 0.66f

    /** The artwork sits inside the ring: 8dp clear on a 78dp disc, 10dp on the 170dp round widget. */
    private const val DISC_ART_INSET = 0.105f
    private const val DISC_RING_STROKE = 3.4f / 78f
    private const val ROUND_ART_INSET = 10f / 170f
    private const val ROUND_RING_STROKE = 5f / 170f
}
