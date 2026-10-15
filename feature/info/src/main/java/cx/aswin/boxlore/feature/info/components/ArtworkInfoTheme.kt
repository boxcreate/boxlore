package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.designsystem.theme.ArtworkColorTransitionMillis
import cx.aswin.boxlore.core.designsystem.theme.ArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkColorsEnabled
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkLoaderColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationBaseColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationEntryId
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.artworkColorScheme
import cx.aswin.boxlore.core.designsystem.theme.artworkLoadingColorScheme
import cx.aswin.boxlore.core.designsystem.theme.artworkSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.cachedArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.loadArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.rememberAnimatedArtworkColors
import cx.aswin.boxlore.feature.info.logic.DefaultInfoBackdropStrength
import cx.aswin.boxlore.feature.info.logic.artworkBackdropStrength
import cx.aswin.boxlore.feature.info.logic.loadArtworkSeed

private data class ArtworkResult(val urls: List<String>, val palette: ArtworkPalette?, val complete: Boolean)

/**
 * The page and navbar share the same animated palette, read from the cover by the shared artwork
 * extractor (the player reads the same one); saved app colors stay independent. The blurred-cover
 * backdrop's strength follows the cover too, easing on the same clock as the colours.
 */
@Composable
internal fun ArtworkInfoTheme(imageUrls: List<String?>, isLoading: Boolean = false, content: @Composable () -> Unit) {
    val navigationEntryId = LocalArtworkNavigationEntryId.current
    val enabled = LocalArtworkColorsEnabled.current
    val urls = imageUrls.filterNotNull().map(String::trim).filter(String::isNotEmpty).distinct()
    val result = rememberArtworkResult(urls, enabled)
    val palette = result.palette.takeIf { result.urls == urls && enabled }
    val base = LocalArtworkNavigationBaseColors.current ?: MaterialTheme.colorScheme
    val dark = LocalEffectiveDarkTheme.current
    val style = artworkSurfaceStyle(LocalSurfaceStyle.current)
    val navigationColors = LocalArtworkNavigationColors.current
    val inherited = navigationColors?.initialColorsFor(navigationEntryId)
    val handoffKey = navigationEntryId to navigationColors?.handoffKeyFor(navigationEntryId)
    val initial = remember(inherited, base, enabled, dark, style) {
        if (enabled) artworkLoadingColorScheme(base, inherited, dark, style) else base
    }
    val waiting = isLoading || result.urls != urls || !result.complete
    val target = remember(palette, enabled, dark, style, base, initial, waiting) {
        when {
            enabled && palette != null -> artworkColorScheme(palette, dark, style)
            enabled && waiting -> initial
            else -> base
        }
    }
    val colors = rememberAnimatedArtworkColors(target, initialColors = initial, animationKey = handoffKey)
    val backdropStrength = rememberBackdropStrength(palette, target)
    SideEffect {
        if (navigationEntryId != null) navigationColors?.update(navigationEntryId, colors)
    }
    DisposableEffect(navigationColors, navigationEntryId) {
        onDispose { if (navigationEntryId != null) navigationColors?.update(navigationEntryId, null) }
    }
    CompositionLocalProvider(LocalArtworkLoaderColors provides colors, LocalInfoBackdropStrength provides backdropStrength) {
        MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
                // Surface propagates its minimum size. Keep that on one page container,
                // rather than forcing sibling header overlays to fill the whole viewport.
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}

/** The backdrop's strength eases on the same clock as the colours it is chosen against. */
@Composable
private fun rememberBackdropStrength(palette: ArtworkPalette?, target: ColorScheme): State<Float> = animateFloatAsState(
    targetValue = palette?.let { artworkBackdropStrength(Color(it.average), target.background, target.onSurface) } ?: DefaultInfoBackdropStrength,
    animationSpec = tween(durationMillis = ArtworkColorTransitionMillis, easing = FastOutSlowInEasing),
    label = "artworkBackdropStrength",
)

@Composable
private fun rememberArtworkResult(urls: List<String>, enabled: Boolean): ArtworkResult {
    val context = LocalContext.current
    val cached = if (enabled) urls.firstOrNull()?.let(::cachedArtworkPalette) else null
    val initial = ArtworkResult(urls, cached, cached != null || urls.isEmpty() || !enabled)
    val result by produceState(initialValue = initial, urls, enabled, context) {
        value = initial
        if (enabled) {
            val palette = loadArtworkSeed(urls, cached = ::cachedArtworkPalette, fetch = { loadArtworkPalette(context, it) })
            value = ArtworkResult(urls, palette, complete = true)
        }
    }
    return result
}
