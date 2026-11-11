package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansTypefaces
import cx.aswin.boxlore.core.prefs.WidgetShowsArtwork
import cx.aswin.boxlore.feature.widgets.logic.WidgetSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gives the launcher's widget picker previews drawn by the live renderers, in the listener's own widget
 * colours and lettering. Android 15 and later show them. Older versions, and any preview the system
 * turns down, fall back to the static `widget_preview_*` layouts, which can only use system colours.
 */
object WidgetPickerPreviews {
    /** One picker entry: the widget it belongs to, the size it is drawn for, and how to draw it. */
    internal class Preview(
        val key: String,
        val receiver: Class<out AppWidgetProvider>,
        /**
         * A little under the widget's usual size, so a preview never outgrows the picker's frame. At
         * this size the library widgets show whole rows only.
         */
        val size: WidgetSize,
        val build: (Context) -> RemoteViews,
    )

    /**
     * Publishes every preview whose look changed since it was last accepted. Cheap to call often: an
     * unchanged look publishes nothing, and a preview the system rate-limits is tried again next time.
     */
    suspend fun refresh(context: Context) = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return@withContext
        val app = context.applicationContext
        val manager = app.getSystemService(AppWidgetManager::class.java) ?: return@withContext
        val look = lookSignature(app)
        val published = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        for (preview in previews()) {
            if (published.getString(preview.key, null) == look) continue
            val accepted =
                runCatching {
                    manager.setWidgetPreview(
                        ComponentName(app, preview.receiver),
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        preview.build(app),
                    )
                }.getOrDefault(false)
            if (accepted) published.edit { putString(preview.key, look) }
        }
    }

    internal fun previews(): List<Preview> = listOf(
        playback("full", NowPlayingWidgetReceiver::class.java, WidgetVariant.NOW_PLAYING, FULL_WIDTH_DP, FULL_HEIGHT_DP),
        playback("compact", NowPlayingBarWidgetReceiver::class.java, WidgetVariant.BAR, FULL_WIDTH_DP, BAR_HEIGHT_DP),
        playback("round", PlaybackControlsWidgetReceiver::class.java, WidgetVariant.CONTROLS, ROUND_SIDE_DP, ROUND_SIDE_DP),
        playback("round_next", PlaybackNextControlsWidgetReceiver::class.java, WidgetVariant.CONTROLS_NEXT, ROUND_SIDE_DP, ROUND_SIDE_DP),
        library("shows", SubscriptionsWidgetReceiver::class.java, LibraryWidgetKind.SUBSCRIPTIONS, SHOWS_HEIGHT_DP),
        library("new_episodes", NewEpisodesWidgetReceiver::class.java, LibraryWidgetKind.NEW_EPISODES, EPISODES_HEIGHT_DP),
    )

    private fun playback(
        key: String,
        receiver: Class<out AppWidgetProvider>,
        variant: WidgetVariant,
        widthDp: Int,
        heightDp: Int,
    ) = Preview(key, receiver, WidgetSize(widthDp, heightDp)) { context ->
        NowPlayingWidgetRenderer.buildRemoteViewsAt(
            context = context,
            appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
            snapshot = samplePlayback(context),
            options =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            },
            variant = variant,
        )
    }

    private fun library(
        key: String,
        receiver: Class<out AppWidgetProvider>,
        kind: LibraryWidgetKind,
        heightDp: Int,
    ) = Preview(key, receiver, WidgetSize(FULL_WIDTH_DP, heightDp)) { context ->
        val sample = sampleLibrary(context)
        // The picker cannot bind the widget's list service, so the preview carries its rows with it.
        val factory = LibraryWidgetRemoteViewsFactory(context, kind, preset = sample).apply { onDataSetChanged() }
        val rows =
            RemoteViews.RemoteCollectionItems
                .Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
                .apply { for (position in 0 until factory.count) addItem(factory.getItemId(position), factory.getViewAt(position)) }
                .build()
        LibraryWidgetRenderer.build(
            context = context,
            appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
            snapshot = sample,
            kind = kind,
            collection = rows,
        )
    }

    /**
     * Everything that changes how a preview looks: the widget colours, the lettering, the Your Shows
     * cover style and the installed build. A preview is published again only when this changes.
     */
    internal fun lookSignature(context: Context): String {
        val chrome = WidgetChrome.resolve(context)
        val colours =
            listOf(
                WidgetPalette.surface,
                WidgetPalette.onSurface,
                WidgetPalette.primary,
                WidgetPalette.primaryContainer,
                WidgetPalette.tertiary,
                WidgetPalette.tertiaryContainer,
            ).joinToString(",") { role -> Integer.toHexString(chrome.argb(role)) }
        val updatedAt = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        return listOf(
            PREVIEW_VERSION,
            updatedAt,
            chrome.usesAppTheme,
            chrome.followsSystemDayNight,
            colours,
            GoogleSansTypefaces.cachedRoundness(context),
            WidgetShowsArtwork.cached(context),
        ).joinToString("|")
    }

    internal fun samplePlayback(context: Context) = NowPlayingWidgetSnapshot(
        episodeId = SAMPLE_ID,
        episodeTitle = context.getString(R.string.widget_preview_episode),
        podcastTitle = context.getString(R.string.widget_preview_podcast),
        artworkCachePath = sampleCover(context, 0),
        isPlaying = true,
        positionMs = SAMPLE_POSITION_MS,
        durationMs = SAMPLE_DURATION_MS,
        seekForwardMs = SAMPLE_SEEK_FORWARD_MS,
        seekBackwardMs = SAMPLE_SEEK_BACK_MS,
    )

    internal fun sampleLibrary(context: Context): LibraryWidgetSnapshot {
        val episodes =
            listOf(
                R.string.widget_preview_episode to R.string.widget_preview_podcast,
                R.string.widget_preview_episode_2 to R.string.widget_preview_episode_2_show,
                R.string.widget_preview_episode_3 to R.string.widget_preview_episode_3_show,
            )
        return LibraryWidgetSnapshot(
            subscriptions =
            List(SAMPLE_SHOWS) { index ->
                WidgetShowRow(
                    podcastId = "$SAMPLE_ID-show-$index",
                    title = context.getString(episodes[index % episodes.size].second),
                    artworkCachePath = sampleCover(context, index),
                    deepLinkUri = SAMPLE_LINK,
                    isNew = index in SAMPLE_NEW_SHOWS,
                )
            },
            newEpisodes =
            episodes.mapIndexed { index, (episode, show) ->
                WidgetEpisodeRow(
                    episodeId = "$SAMPLE_ID-episode-$index",
                    episodeTitle = context.getString(episode),
                    podcastId = "$SAMPLE_ID-show-$index",
                    podcastTitle = context.getString(show),
                    artworkCachePath = sampleCover(context, index),
                    deepLinkUri = SAMPLE_LINK,
                )
            },
            counts = WidgetLibraryCounts(subscriptions = SAMPLE_SHOW_COUNT, newEpisodes = SAMPLE_EPISODE_COUNT),
        )
    }

    /**
     * The path of sample cover [index], drawn from the same pictures the static previews use and kept
     * in the widget artwork cache. Null when it cannot be written; the widget then shows its placeholder.
     */
    private fun sampleCover(context: Context, index: Int): String? {
        val drawableRes = SAMPLE_COVERS[index.mod(SAMPLE_COVERS.size)]
        val directory = File(context.cacheDir, WidgetArtworkLoader.CACHE_DIR_NAME)
        val file = File(directory, "preview_cover_${PREVIEW_VERSION}_${index.mod(SAMPLE_COVERS.size)}.png")
        if (file.exists() && file.length() > 0L) return file.absolutePath
        return runCatching {
            directory.mkdirs()
            val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
            val bitmap = createBitmap(SAMPLE_COVER_PX, SAMPLE_COVER_PX)
            drawable.setBounds(0, 0, SAMPLE_COVER_PX, SAMPLE_COVER_PX)
            drawable.draw(Canvas(bitmap))
            // Several previews are built at once and share covers: write beside the file, then move it into
            // place, so a reader never finds half a picture and a write cut short leaves nothing behind.
            val unfinished = File(directory, "${file.name}.${System.nanoTime()}.tmp")
            unfinished.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            if (!unfinished.renameTo(file)) {
                unfinished.delete()
                if (!file.exists()) return null
            }
            file.absolutePath
        }.getOrNull()
    }

    private val SAMPLE_COVERS =
        listOf(
            R.drawable.widget_preview_cover_a,
            R.drawable.widget_preview_cover_b,
            R.drawable.widget_preview_cover_c,
            R.drawable.widget_preview_cover_d,
            R.drawable.widget_preview_cover_e,
            R.drawable.widget_preview_cover_f,
        )

    internal const val PREFS_NAME = "boxlore_widget_picker_previews"

    /** Bump when the sample content or the way previews are built changes. */
    private const val PREVIEW_VERSION = 1

    // A little under a typical 4-column grid, so a preview never outgrows the picker's frame.
    private const val FULL_WIDTH_DP = 320
    private const val FULL_HEIGHT_DP = 170
    private const val BAR_HEIGHT_DP = 80

    // Two whole rows of covers and three whole episode rows at the preview's width.
    private const val SHOWS_HEIGHT_DP = 290
    private const val EPISODES_HEIGHT_DP = 296
    private const val ROUND_SIDE_DP = 160

    private const val SAMPLE_ID = "widget-preview"
    private const val SAMPLE_LINK = "boxlore://home"
    private const val SAMPLE_POSITION_MS = 24 * 60_000L
    private const val SAMPLE_DURATION_MS = 60 * 60_000L
    private const val SAMPLE_SEEK_FORWARD_MS = 30_000L
    private const val SAMPLE_SEEK_BACK_MS = 10_000L
    private const val SAMPLE_SHOWS = 8
    private val SAMPLE_NEW_SHOWS = setOf(0, 2, 5)
    private const val SAMPLE_SHOW_COUNT = 24
    private const val SAMPLE_EPISODE_COUNT = 12
    private const val SAMPLE_COVER_PX = 256
    private const val PNG_QUALITY = 100
}
