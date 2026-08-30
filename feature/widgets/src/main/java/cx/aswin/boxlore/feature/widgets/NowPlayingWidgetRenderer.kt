package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.text.Layout
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import cx.aswin.boxlore.feature.widgets.actions.WidgetActionIntents
import cx.aswin.boxlore.feature.widgets.logic.WidgetSemantics

@Suppress("TooManyFunctions")
object NowPlayingWidgetRenderer {
    fun updateAll(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: NowPlayingWidgetSnapshot,
        optionsById: Map<Int, android.os.Bundle> = emptyMap(),
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
        options: android.os.Bundle = appWidgetManager.getAppWidgetOptions(appWidgetId),
        variant: WidgetVariant = WidgetVariant.NOW_PLAYING,
    ) {
        val remoteViews = buildRemoteViews(context, appWidgetId, snapshot, options, variant)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    internal fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        snapshot: NowPlayingWidgetSnapshot,
        options: android.os.Bundle,
        variant: WidgetVariant = WidgetVariant.NOW_PLAYING,
    ): RemoteViews {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
        var widthDp = maxOf(minWidth, maxWidth)
        var heightDp = maxOf(minHeight, maxHeight)

        // Prefer the widest reported size. A SizeF→RemoteViews map often left the host
        // displaying a narrow title layout inside a much wider bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    options.getParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        SizeF::class.java,
                    )
                } else {
                    options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                }
            val widest = sizes?.maxByOrNull { it.width }
            if (widest != null) {
                widthDp = widest.width.toInt().coerceAtLeast(widthDp)
                heightDp = widest.height.toInt().coerceAtLeast(1)
            }
        }

        return buildForSize(context, appWidgetId, snapshot, variant, widthDp, heightDp)
    }

    private fun buildForSize(
        context: Context,
        appWidgetId: Int,
        snapshot: NowPlayingWidgetSnapshot,
        variant: WidgetVariant,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val layoutId =
            when (variant) {
                WidgetVariant.NOW_PLAYING -> R.layout.now_playing_widget_standard
                WidgetVariant.BAR -> R.layout.now_playing_widget_bar
                WidgetVariant.CONTROLS -> R.layout.playback_controls_widget
                WidgetVariant.CONTROLS_NEXT -> R.layout.playback_controls_widget
            }
        return RemoteViews(context.packageName, layoutId).also { views ->
            bind(
                context = context,
                views = views,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                variant = variant,
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }
    }

    private fun bind(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        snapshot: NowPlayingWidgetSnapshot,
        variant: WidgetVariant,
        widthDp: Int,
        heightDp: Int,
    ) {
        views.setContentDescription(R.id.widget_root, WidgetSemantics.contentDescription(snapshot))
        configureGridCardSize(views, variant, widthDp, heightDp)
        configureCompactCardHeight(views, variant)

        val chrome = WidgetChrome.resolve(context)
        val artwork =
            snapshot.artworkCachePath
                ?.takeIf(String::isNotBlank)
                ?.let { path -> decodeArtwork(path, context, variant, widthDp, heightDp) }
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_surface_background,
            WidgetPalette.surface,
            chrome,
        )

        if (!snapshot.hasEpisode) {
            bindEmptyState(context, views, widthDp, variant, chrome)
            return
        }

        views.setViewVisibility(R.id.widget_empty_container, View.GONE)
        views.setViewVisibility(R.id.widget_playing_container, View.VISIBLE)
        if (artwork == null) {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.widget_art_placeholder)
        } else {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
        }

        if (!variant.isControlsGrid()) {
            bindMetadata(views, snapshot, chrome)
        }
        bindTransport(context, views, appWidgetId, snapshot, variant, chrome)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetActionIntents.openApp(context))
    }

    private fun bindEmptyState(
        context: Context,
        views: RemoteViews,
        widthDp: Int,
        variant: WidgetVariant,
        chrome: WidgetChrome,
    ) {
        views.setViewVisibility(R.id.widget_playing_container, View.GONE)
        views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
        if (!variant.isControlsGrid()) {
            WidgetRemoteViewsColors.setColorFilter(
                views,
                R.id.widget_empty_surface_background,
                WidgetPalette.surface,
                chrome,
            )
        }
        val appIcon = context.applicationInfo.icon
        if (appIcon != 0) {
            views.setImageViewResource(R.id.widget_empty_icon, appIcon)
        }
        views.setOnClickPendingIntent(R.id.widget_empty_container, WidgetActionIntents.openApp(context))

        when (variant) {
            WidgetVariant.NOW_PLAYING -> bindStandardEmptyState(context, views, widthDp, chrome)
            WidgetVariant.BAR -> bindBarEmptyState(context, views, widthDp, chrome)
            WidgetVariant.CONTROLS,
            WidgetVariant.CONTROLS_NEXT,
            -> bindControlsEmptyState(context, views, widthDp, chrome)
        }
    }

    private fun bindStandardEmptyState(
        context: Context,
        views: RemoteViews,
        widthDp: Int,
        chrome: WidgetChrome,
    ) {
        val metadataWidth = (widthDp - STANDARD_EMPTY_METADATA_INSETS_DP).coerceAtLeast(MIN_TEXT_WIDTH_DP)
        views.setImageViewBitmap(
            R.id.widget_empty_title,
            WidgetTextBitmapRenderer.renderColor(
                context = context,
                spec =
                    WidgetTextBitmapRenderer.Spec(
                        text = context.getString(R.string.widget_empty_title),
                        widthDp = metadataWidth,
                        heightDp = 40,
                        preferredSizeSp = 17f,
                        minSizeSp = 13f,
                        weight = TITLE_WEIGHT,
                        maxLines = 2,
                        alignment = Layout.Alignment.ALIGN_CENTER,
                    ),
                color = chrome.argb(WidgetPalette.onSurface),
            ),
        )
        views.setImageViewBitmap(
            R.id.widget_empty_subtitle,
            WidgetTextBitmapRenderer.renderColor(
                context = context,
                spec =
                    WidgetTextBitmapRenderer.Spec(
                        text = context.getString(R.string.widget_empty_subtitle),
                        widthDp = metadataWidth,
                        heightDp = 18,
                        preferredSizeSp = 11f,
                        minSizeSp = 9f,
                        weight = BODY_WEIGHT,
                        maxLines = 1,
                        alignment = Layout.Alignment.ALIGN_CENTER,
                    ),
                color = chrome.argb(WidgetPalette.onSurfaceVariant),
            ),
        )
    }

    private fun bindBarEmptyState(
        context: Context,
        views: RemoteViews,
        widthDp: Int,
        chrome: WidgetChrome,
    ) {
        val textWidth = (widthDp - BAR_EMPTY_CONTENT_INSETS_DP).coerceAtLeast(MIN_TEXT_WIDTH_DP)
        views.setImageViewBitmap(
            R.id.widget_empty_title,
            WidgetTextBitmapRenderer.renderColor(
                context = context,
                spec =
                    WidgetTextBitmapRenderer.Spec(
                        text = context.getString(R.string.widget_empty_bar_title),
                        widthDp = textWidth,
                        heightDp = 24,
                        preferredSizeSp = 14f,
                        minSizeSp = 11f,
                        weight = TITLE_WEIGHT,
                        maxLines = 1,
                        alignment = Layout.Alignment.ALIGN_CENTER,
                    ),
                color = chrome.argb(WidgetPalette.onSurface),
            ),
        )
    }

    private fun bindControlsEmptyState(
        context: Context,
        views: RemoteViews,
        widthDp: Int,
        chrome: WidgetChrome,
    ) {
        val textWidth =
            (widthDp - GRID_EMPTY_HORIZONTAL_PADDING_DP)
                .coerceAtLeast(GRID_MIN_TEXT_WIDTH_DP)
        views.setImageViewBitmap(
            R.id.widget_empty_title,
            WidgetTextBitmapRenderer.renderColor(
                context = context,
                spec =
                    WidgetTextBitmapRenderer.Spec(
                        text = context.getString(R.string.widget_empty_title),
                        widthDp = textWidth,
                        heightDp = 28,
                        preferredSizeSp = 11f,
                        minSizeSp = 9f,
                        weight = TITLE_WEIGHT,
                        maxLines = 2,
                        alignment = Layout.Alignment.ALIGN_CENTER,
                    ),
                color = chrome.argb(WidgetPalette.onSurface),
            ),
        )
    }

    private fun bindMetadata(
        views: RemoteViews,
        snapshot: NowPlayingWidgetSnapshot,
        chrome: WidgetChrome,
    ) {
        views.setTextViewText(R.id.widget_episode_title, snapshot.episodeTitle)
        WidgetRemoteViewsColors.setTextColor(
            views,
            R.id.widget_episode_title,
            WidgetPalette.onSurface,
            chrome,
        )
        views.setTextViewText(R.id.widget_podcast_title, snapshot.podcastTitle)
        WidgetRemoteViewsColors.setTextColor(
            views,
            R.id.widget_podcast_title,
            WidgetPalette.onSurfaceVariant,
            chrome,
        )
    }

    private fun bindTransport(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        snapshot: NowPlayingWidgetSnapshot,
        variant: WidgetVariant,
        chrome: WidgetChrome,
    ) {
        val playBackground =
            if (variant.isControlsGrid() || snapshot.isPlaying) {
                R.drawable.widget_button_pause
            } else {
                R.drawable.widget_button_play
            }
        views.setImageViewResource(R.id.widget_play_pause_background, playBackground)
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_play_pause_background,
            WidgetPalette.primary,
            chrome,
        )
        views.setImageViewResource(
            R.id.widget_play_pause_icon,
            if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_play_pause_icon,
            WidgetPalette.onPrimary,
            chrome,
        )
        views.setContentDescription(
            R.id.widget_play_pause_icon,
            context.getString(if (snapshot.isPlaying) R.string.widget_pause else R.string.widget_play),
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            WidgetActionIntents.broadcast(context, appWidgetId, WidgetControl.TOGGLE),
        )

        val leadingControl =
            if (variant == WidgetVariant.CONTROLS_NEXT) {
                WidgetControl.NEXT
            } else {
                WidgetControl.SKIP_BACK
            }
        views.setImageViewResource(
            R.id.widget_skip_back_icon,
            if (leadingControl == WidgetControl.NEXT) {
                R.drawable.ic_widget_next
            } else {
                R.drawable.ic_widget_skip_back
            },
        )
        views.setContentDescription(
            R.id.widget_skip_back_icon,
            context.getString(
                if (leadingControl == WidgetControl.NEXT) {
                    R.string.widget_next
                } else {
                    R.string.widget_skip_back
                },
            ),
        )

        // Compact surfaces use seek-back by default; the alternate grid uses next.
        bindSecondaryButton(
            context = context,
            views = views,
            appWidgetId = appWidgetId,
            containerId = R.id.widget_skip_back,
            backgroundId = R.id.widget_skip_back_background,
            iconId = R.id.widget_skip_back_icon,
            control = leadingControl,
            chrome = chrome,
        )
        bindSecondaryButton(
            context = context,
            views = views,
            appWidgetId = appWidgetId,
            containerId = R.id.widget_skip_forward,
            backgroundId = R.id.widget_skip_forward_background,
            iconId = R.id.widget_skip_forward_icon,
            control = WidgetControl.SKIP_FORWARD,
            chrome = chrome,
        )

        if (variant == WidgetVariant.NOW_PLAYING) {
            bindSecondaryButton(
                context = context,
                views = views,
                appWidgetId = appWidgetId,
                containerId = R.id.widget_previous,
                backgroundId = R.id.widget_previous_background,
                iconId = R.id.widget_previous_icon,
                control = WidgetControl.PREVIOUS,
                chrome = chrome,
            )
            bindSecondaryButton(
                context = context,
                views = views,
                appWidgetId = appWidgetId,
                containerId = R.id.widget_next,
                backgroundId = R.id.widget_next_background,
                iconId = R.id.widget_next_icon,
                control = WidgetControl.NEXT,
                chrome = chrome,
            )
        }
    }

    private fun bindSecondaryButton(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        containerId: Int,
        backgroundId: Int,
        iconId: Int,
        control: WidgetControl,
        chrome: WidgetChrome,
    ) {
        WidgetRemoteViewsColors.setColorFilter(
            views,
            backgroundId,
            WidgetPalette.secondaryContainer,
            chrome,
        )
        WidgetRemoteViewsColors.setColorFilter(
            views,
            iconId,
            WidgetPalette.onSecondaryContainer,
            chrome,
        )
        views.setOnClickPendingIntent(
            containerId,
            WidgetActionIntents.broadcast(context, appWidgetId, control),
        )
    }

    private fun configureGridCardSize(
        views: RemoteViews,
        variant: WidgetVariant,
        widthDp: Int,
        heightDp: Int,
    ) {
        if (!variant.isControlsGrid()) return
        val sideDp = minOf(widthDp, heightDp).coerceAtLeast(GRID_MIN_SIDE_DP)
        views.setViewLayoutWidth(R.id.widget_grid_card, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.widget_grid_card, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        setSquareSize(views, R.id.widget_skip_back_icon, sideDp * GRID_CONTROL_ICON_RATIO)
        setSquareSize(views, R.id.widget_skip_forward_icon, sideDp * GRID_CONTROL_ICON_RATIO)
        setSquareSize(views, R.id.widget_play_pause_icon, sideDp * GRID_PLAY_ICON_RATIO)
    }

    /**
     * Pin the beige card to content height so launcher hosts cannot stretch a
     * match_parent surface into tall empty bands inside a 2-row cell.
     */
    private fun configureCompactCardHeight(
        views: RemoteViews,
        variant: WidgetVariant,
    ) {
        val heightDp =
            when (variant) {
                // 48 metadata + 4 gap + 38 controls + 10 vertical padding
                WidgetVariant.NOW_PLAYING -> STANDARD_CARD_HEIGHT_DP
                // 40 art + 8 vertical padding
                WidgetVariant.BAR -> BAR_CARD_HEIGHT_DP
                WidgetVariant.CONTROLS,
                WidgetVariant.CONTROLS_NEXT,
                -> return
            }
        views.setViewLayoutHeight(
            R.id.widget_playing_container,
            heightDp.toFloat(),
            TypedValue.COMPLEX_UNIT_DIP,
        )
    }

    private fun setSquareSize(
        views: RemoteViews,
        viewId: Int,
        sizeDp: Float,
    ) {
        views.setViewLayoutWidth(viewId, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(viewId, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
    }

    private fun decodeArtwork(
        path: String,
        context: Context,
        variant: WidgetVariant,
        widthDp: Int,
        heightDp: Int,
    ): Bitmap? {
        val density = context.resources.displayMetrics.density
        val targetSizeDp =
            when (variant) {
                WidgetVariant.NOW_PLAYING -> 48
                WidgetVariant.BAR -> 40
                WidgetVariant.CONTROLS,
                WidgetVariant.CONTROLS_NEXT,
                ->
                    ((minOf(widthDp, heightDp) - GRID_INSETS_DP) / 2)
                        .coerceAtLeast(GRID_MIN_ART_DP)
            }
        val targetSizePx = (targetSizeDp * density).toInt().coerceAtLeast(1)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetSizePx &&
            bounds.outHeight / (sampleSize * 2) >= targetSizePx
        ) {
            sampleSize *= 2
        }
        val source =
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return null
        val sourceRatio = source.width.toFloat() / source.height
        val crop =
            if (sourceRatio > 1f) {
                val cropWidth = source.height
                val left = (source.width - cropWidth) / 2
                Rect(left, 0, left + cropWidth, source.height)
            } else {
                val cropHeight = source.width
                val top = (source.height - cropHeight) / 2
                Rect(0, top, source.width, top + cropHeight)
            }
        return Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(
                source,
                crop,
                Rect(0, 0, targetSizePx, targetSizePx),
                null,
            )
            source.recycle()
        }
    }

    private const val TITLE_WEIGHT = 600
    private const val BODY_WEIGHT = 400
    private const val MIN_TEXT_WIDTH_DP = 72
    private const val GRID_MIN_TEXT_WIDTH_DP = 48
    private const val STANDARD_EMPTY_METADATA_INSETS_DP = 104
    private const val BAR_EMPTY_CONTENT_INSETS_DP = 70
    private const val GRID_EMPTY_HORIZONTAL_PADDING_DP = 20
    private const val GRID_MIN_SIDE_DP = 110
    private const val GRID_INSETS_DP = 38
    private const val GRID_MIN_ART_DP = 36
    private const val GRID_CONTROL_ICON_RATIO = 0.14f
    private const val GRID_PLAY_ICON_RATIO = 0.16f
    private const val STANDARD_CARD_HEIGHT_DP = 100
    private const val BAR_CARD_HEIGHT_DP = 48

    private fun WidgetVariant.isControlsGrid(): Boolean = this == WidgetVariant.CONTROLS || this == WidgetVariant.CONTROLS_NEXT
}
