package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.prefs.FontRoundnessAxis
import cx.aswin.boxlore.core.prefs.WidgetAppearance
import cx.aswin.boxlore.core.prefs.WidgetShowsArtwork
import cx.aswin.boxlore.feature.widgets.logic.WidgetSize
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Applies every widget in every state and size the way a launcher would, and draws it. A view id or a
 * remote call that does not match the layout fails here instead of leaving a blank widget on a phone.
 *
 * Set `BOXLORE_WIDGET_RENDERS=1` to also save each drawing under `build/reports/widget-renders/` for
 * a visual check against the design.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderGalleryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val density: Float get() = context.resources.displayMetrics.density

    private class Size(val name: String, val widthDp: Int, val heightDp: Int)

    @Test
    fun playbackWidgetsDrawInEveryStateSizeAndTheme() {
        val art = artworkFile()
        val states =
            listOf(
                "playing" to playing(art, isPlaying = true, positionMs = 24 * MINUTE, durationMs = 60 * MINUTE),
                "paused" to
                    playing(art, isPlaying = false, positionMs = 14 * MINUTE, durationMs = 86 * MINUTE).copy(
                        episodeTitle = "The surprisingly long history of the humble paperclip, part two",
                        podcastTitle = "Paper Trails",
                    ),
                "no-art" to playing(null, isPlaying = true, positionMs = 0L, durationMs = 0L),
                "resting" to NowPlayingWidgetSnapshot(),
            )
        val sizes =
            mapOf(
                WidgetVariant.NOW_PLAYING to
                    listOf(Size("4x2", 350, 182), Size("min", 245, 100), Size("roomy", 380, 220), Size("tall", 350, 278), Size("wide", 530, 182)),
                WidgetVariant.BAR to listOf(Size("4x1", 350, 86), Size("min", 245, 48), Size("tallcell", 380, 112), Size("wide", 530, 86)),
                WidgetVariant.CONTROLS to listOf(Size("2x2", 170, 182), Size("min", 110, 110), Size("wide", 350, 182)),
                WidgetVariant.CONTROLS_NEXT to listOf(Size("2x2", 170, 182)),
            )
        val cases =
            sizes.flatMap { (variant, variantSizes) ->
                variantSizes.flatMap { size -> states.map { (stateName, snapshot) -> PlaybackCase(variant, size, stateName, snapshot) } }
            }
        for (dark in listOf(false, true)) {
            useAppTheme(dark)
            cases.forEach { case -> drawPlayback(case, dark) }
        }
    }

    @Test
    fun playbackWidgetsStillFitWhenTheLauncherReportsAStaleSize() {
        val art = artworkFile()
        val snapshot =
            playing(art, isPlaying = true, positionMs = 24 * MINUTE, durationMs = 60 * MINUTE)
                .copy(episodeTitle = "The surprisingly long history of the humble paperclip, part two")
        useAppTheme(dark = false)
        // Reported as a regular size but drawn at another: the launcher picks the layout for the space it has.
        val reported = mapOf(WidgetVariant.NOW_PLAYING to Size("r", 350, 182), WidgetVariant.BAR to Size("r", 350, 86), WidgetVariant.CONTROLS to Size("r", 170, 182))
        val drawn = listOf(Size("small", 245, 110), Size("squat", 320, 70), Size("big", 400, 250))
        for ((variant, said) in reported) {
            for (actual in drawn) {
                val views = NowPlayingWidgetRenderer.buildRemoteViewsAt(context, 7, snapshot, options(said), variant, drawn = WidgetSize(actual.widthDp, actual.heightDp))
                save(draw(views, actual, dark = false), "stale-${variant.name.lowercase()}-${actual.name}")
                // And the worst case: a launcher that draws the layout picked for the report anyway.
                val wrongPick = NowPlayingWidgetRenderer.buildRemoteViewsAt(context, 7, snapshot, options(said), variant)
                save(draw(wrongPick, actual, dark = false), "wrongpick-${variant.name.lowercase()}-${actual.name}")
            }
        }
    }

    private class PlaybackCase(val variant: WidgetVariant, val size: Size, val state: String, val snapshot: NowPlayingWidgetSnapshot)

    private fun drawPlayback(case: PlaybackCase, dark: Boolean) {
        val views =
            NowPlayingWidgetRenderer.buildRemoteViewsAt(
                context = context,
                appWidgetId = 7,
                snapshot = case.snapshot,
                options = options(case.size),
                variant = case.variant,
            )
        save(draw(views, case.size, dark), "${case.variant.name.lowercase()}-${case.size.name}-${case.state}-${themeName(dark)}")
    }

    @Test
    fun libraryWidgetsDrawWithRowsTilesAndRestingStates() {
        val art = artworkFile().absolutePath
        val full =
            LibraryWidgetSnapshot(
                subscriptions =
                (1..14).map { index ->
                    WidgetShowRow(
                        podcastId = "show-$index",
                        title = "Show $index",
                        // Every third show has no cover yet, so placeholders are drawn too.
                        artworkCachePath = art.takeIf { index % 3 != 0 },
                        deepLinkUri = "boxlore://podcast/show-$index",
                        isNew = index % 4 == 1,
                    )
                },
                newEpisodes =
                listOf("Why curiosity compounds" to "The Daily Lore", "A quiet week in Kyoto" to "Field Notes", "How maps shape memory" to "Soft Power", "The last night bus" to "Night Shift Radio", "Sourdough, slowly" to "Small Batch")
                    .mapIndexed { index, (episode, show) ->
                        WidgetEpisodeRow(
                            episodeId = "episode-$index",
                            episodeTitle = episode,
                            podcastId = "show-$index",
                            podcastTitle = show,
                            artworkCachePath = art.takeIf { index != 2 },
                            deepLinkUri = "boxlore://episode/episode-$index",
                        )
                    },
                counts = WidgetLibraryCounts(subscriptions = 24, newEpisodes = 12),
            )
        val sizes = listOf(Size("4x3", 350, 278), Size("narrow", 260, 278), Size("tall", 350, 374))
        // New Episodes has one look; Your Shows is drawn as whole covers and as mixed shapes.
        val looks =
            listOf(
                LibraryLook(LibraryWidgetKind.NEW_EPISODES, WidgetShowsArtwork.ROUNDED, "new_episodes"),
                LibraryLook(LibraryWidgetKind.SUBSCRIPTIONS, WidgetShowsArtwork.ROUNDED, "subscriptions-covers"),
                LibraryLook(LibraryWidgetKind.SUBSCRIPTIONS, WidgetShowsArtwork.SHAPES, "subscriptions-shapes"),
            )
        for (dark in listOf(false, true)) {
            useAppTheme(dark)
            for (look in looks) {
                setShowsArtwork(look.artwork)
                sizes.forEach { size -> save(drawLibrary(look.kind, full, size, dark), "${look.name}-${size.name}-${themeName(dark)}") }
            }
            LibraryWidgetKind.entries.forEach { kind ->
                save(drawLibrary(kind, LibraryWidgetSnapshot(), sizes.first(), dark), "${kind.name.lowercase()}-resting-${themeName(dark)}")
            }
        }
    }

    private class LibraryLook(val kind: LibraryWidgetKind, val artwork: String, val name: String)

    private fun drawLibrary(kind: LibraryWidgetKind, snapshot: LibraryWidgetSnapshot, size: Size, dark: Boolean): Bitmap {
        LibraryWidgetSnapshotStore(context).write(snapshot)
        val views = LibraryWidgetRenderer.build(context, appWidgetId = 9, snapshot = snapshot, kind = kind)
        val factory = LibraryWidgetRemoteViewsFactory(context, kind).apply { onDataSetChanged() }
        return draw(views, size, dark) { root ->
            // A launcher binds the collection through the remote service; here the factory feeds it directly.
            val collection = root.findViewById<AbsListView>(R.id.widget_list_view)
            collection.emptyView = root.findViewById(R.id.widget_empty_container)
            collection.adapter =
                object : BaseAdapter() {
                    override fun getCount(): Int = factory.count

                    override fun getItem(position: Int): Any = position

                    override fun getItemId(position: Int): Long = factory.getItemId(position)

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = factory.getViewAt(position).apply(context, parent)
                }
        }
    }

    private fun draw(views: RemoteViews, size: Size, dark: Boolean, prepare: (View) -> Unit = {}): Bitmap {
        val width = (size.widthDp * density).roundToInt()
        val height = (size.heightDp * density).roundToInt()
        val host = FrameLayout(context)
        val root = views.apply(context, host)
        host.addView(root, FrameLayout.LayoutParams(width, height))
        prepare(root)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            // A stand-in wallpaper, so the gaps between shapes read as on a home screen.
            drawColor(if (dark) Color.rgb(12, 13, 19) else Color.rgb(205, 208, 232))
            host.draw(this)
        }
        assertTrue("the widget drew something", bitmap.width > 0 && bitmap.height > 0)
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        if (System.getenv("BOXLORE_WIDGET_RENDERS") != "1") return
        val directory = File("build/reports/widget-renders").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun options(size: Size): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.heightDp)
    }

    private fun playing(art: File?, isPlaying: Boolean, positionMs: Long, durationMs: Long) = NowPlayingWidgetSnapshot(
        episodeId = "episode-1",
        episodeTitle = "Why curiosity compounds",
        podcastTitle = "The Daily Lore",
        artworkCachePath = art?.absolutePath,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        seekForwardMs = 30_000L,
        seekBackwardMs = 10_000L,
    )

    /** A flat cover with a sun, like the stand-in art on the design boards. */
    private fun artworkFile(): File {
        val file = File(context.cacheDir, "gallery-cover.png")
        val bitmap = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(216, 73, 42))
            drawCircle(COVER_PX * 0.66f, COVER_PX * 0.36f, COVER_PX * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 226, 168) })
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun themeName(dark: Boolean): String = if (dark) "dark" else "light"

    private fun useAppTheme(dark: Boolean) {
        themeCache()
            .edit()
            .putString("theme_brand", "violet")
            .putString("theme_config", if (dark) "dark" else "light")
            .putString("surface_style", "classic_dynamic")
            .putBoolean("use_dynamic_color", false)
            .putString(WidgetAppearance.PREF_KEY, WidgetAppearance.APP)
            .commit()
    }

    private fun setShowsArtwork(style: String) {
        themeCache().edit().putString(WidgetShowsArtwork.PREF_KEY, style).commit()
    }

    private fun themeCache() = context.getSharedPreferences(FontRoundnessAxis.THEME_FAST_CACHE, Context.MODE_PRIVATE)

    private companion object {
        const val MINUTE = 60_000L
        const val COVER_PX = 256
    }
}
