package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance

/** Entry-scoped ownership prevents an exiting detail page from recoloring the current route. */
class ArtworkNavigationColors {
    private data class EntryStart(val colors: ColorScheme, val generation: Long)

    private val entries = mutableStateMapOf<String, ColorScheme>()
    private val snapshots = linkedMapOf<String, ColorScheme>()
    private val startingColors = mutableStateMapOf<String, EntryStart>()
    private var generation = 0L
    private var activeEntryId: String? = null
    private var appColors: ColorScheme? = null
    private var artworkEnabled = true
    var transitionColors by mutableStateOf<ColorScheme?>(null)
        private set

    /** Native Back can reveal a saved-theme page before the current entry changes. */
    var appThemePreviewEntryId by mutableStateOf<String?>(null)
        private set

    fun previewAppTheme(entryId: String?) {
        appThemePreviewEntryId = entryId
    }

    /** Called synchronously by the navigation shell before the incoming page's first frame. */
    fun beginEntry(entryId: String, base: ColorScheme, enabled: Boolean) {
        if (appColors !== base || artworkEnabled != enabled) {
            entries.clear()
            snapshots.clear()
            startingColors.clear()
            activeEntryId = null
            appColors = base
            artworkEnabled = enabled
        }
        if (entryId == activeEntryId) return
        appThemePreviewEntryId = null
        val source = if (enabled) activeEntryId?.let { entries[it] ?: snapshots[it] } ?: base else base
        transitionColors = source
        // Forward and Back both start with the departing page; the destination owns its final palette.
        startingColors[entryId] = EntryStart(source, ++generation)
        entries[entryId] = source
        rememberSnapshot(entryId, source)
        activeEntryId = entryId
    }

    fun colorsFor(entryId: String?): ColorScheme? = entryId?.let(entries::get)
    fun initialColorsFor(entryId: String?): ColorScheme? = entryId?.let { startingColors[it]?.colors }
    fun handoffKeyFor(entryId: String?): Long? = entryId?.let { startingColors[it]?.generation }
    fun update(entryId: String, colors: ColorScheme?) {
        if (colors == null) {
            entries.remove(entryId)
        } else {
            entries[entryId] = colors
            rememberSnapshot(entryId, colors)
        }
    }

    private fun rememberSnapshot(entryId: String, colors: ColorScheme) {
        snapshots.remove(entryId)
        snapshots[entryId] = colors
        while (snapshots.size > 80) {
            val oldest = snapshots.keys.first()
            snapshots.remove(oldest)
            startingColors.remove(oldest)
            entries.remove(oldest)
        }
    }
}

val LocalArtworkNavigationColors = staticCompositionLocalOf<ArtworkNavigationColors?> { null }

val LocalArtworkNavigationEntryId = staticCompositionLocalOf<String?> { null }

/** Stable saved theme beneath temporary navigation colors, so failed artwork can restore it. */
val LocalArtworkNavigationBaseColors = staticCompositionLocalOf<ColorScheme?> { null }

/** Loading indicators can inherit the source without partially recoloring a regular app page. */
val LocalArtworkLoaderColors = compositionLocalOf<ColorScheme?> { null }

/** A restored navigation snapshot must still respect current mode and Pure surfaces. */
fun artworkLoadingColorScheme(base: ColorScheme, inherited: ColorScheme?, dark: Boolean, surfaceStyle: String): ColorScheme {
    if (inherited == null || (inherited.background.luminance() < 0.5f) != dark) return base
    return applySurfaceStyle(inherited, dark, artworkSurfaceStyle(surfaceStyle))
}

@Composable
fun ArtworkNavigationEntry(entryId: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalArtworkNavigationEntryId provides entryId, content = content)
}

/** Inherit the page's restrained neutral palette, including its Pure surface override. */
fun navigationSchemeWithArtwork(base: ColorScheme, artwork: ColorScheme): ColorScheme = artwork.copy(
    error = base.error,
    onError = base.onError,
    errorContainer = base.errorContainer,
    onErrorContainer = base.onErrorContainer,
    scrim = base.scrim,
)

@Composable
fun ArtworkNavigationTheme(artwork: ColorScheme?, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = artwork?.let { navigationSchemeWithArtwork(base, it) } ?: base, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}
