package cx.aswin.boxlore.feature.info.components

import android.app.Activity
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
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
import cx.aswin.boxlore.feature.info.branded.ActiveBrandedPage
import cx.aswin.boxlore.feature.info.branded.BrandedHeaderMotion
import cx.aswin.boxlore.feature.info.branded.BrandedShowPage
import cx.aswin.boxlore.feature.info.branded.LocalBrandedHeaderMotion
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
import cx.aswin.boxlore.feature.info.logic.DefaultInfoBackdropStrength
import cx.aswin.boxlore.feature.info.logic.artworkBackdropStrength
import cx.aswin.boxlore.feature.info.logic.loadArtworkSeed

private data class ArtworkResult(val urls: List<String>, val palette: ArtworkPalette?, val complete: Boolean)

/**
 * The page and navbar share the same animated palette: a Material scheme built from the seed the
 * shared artwork picker reads from the cover (the player and Home's hero cards use the same seed);
 * saved app colors stay independent. The blurred-cover backdrop's strength follows the cover's mean
 * colour, easing on the same clock as the colours. A [branded] show page uses its own colours instead
 * of the cover's, in both the page and the navbar.
 */
@Composable
internal fun ArtworkInfoTheme(
    imageUrls: List<String?>,
    isLoading: Boolean = false,
    branded: BrandedShowPage? = null,
    content: @Composable () -> Unit,
) {
    val navigationEntryId = LocalArtworkNavigationEntryId.current
    val base = LocalArtworkNavigationBaseColors.current ?: MaterialTheme.colorScheme
    val systemDark = LocalEffectiveDarkTheme.current
    val dark = pageDark(systemDark, branded)
    val active = branded?.let { page -> remember(page, base, dark) { ActiveBrandedPage(page, page.style(base, dark)) } }
    val brandedColors = active?.style?.colors
    val enabled = LocalArtworkColorsEnabled.current && active == null
    val urls = imageUrls.filterNotNull().map(String::trim).filter(String::isNotEmpty).distinct()
    val result = rememberArtworkResult(urls, enabled)
    val palette = result.palette.takeIf { result.urls == urls && enabled }
    val style = artworkSurfaceStyle(LocalSurfaceStyle.current)
    val navigationColors = LocalArtworkNavigationColors.current
    val inherited = navigationColors?.initialColorsFor(navigationEntryId)
    val handoffKey = navigationEntryId to navigationColors?.handoffKeyFor(navigationEntryId)
    val initial = remember(inherited, base, enabled, dark, style, brandedColors) {
        brandedColors ?: artworkInitial(enabled, base, inherited, dark, style)
    }
    val waiting = isLoading || result.urls != urls || !result.complete
    val target = remember(palette, enabled, dark, style, base, initial, waiting, brandedColors) {
        brandedColors ?: artworkTarget(palette.takeIf { enabled }, enabled && waiting, dark, style, initial, base)
    }
    val colors = rememberAnimatedArtworkColors(target, initialColors = initial, animationKey = handoffKey)
    // A branded page tints the navbar with its own navbar colours, on the same clock.
    val barColors = active?.let { rememberAnimatedArtworkColors(it.style.navigationColors, initialColors = initial, animationKey = handoffKey) } ?: colors
    val backdropStrength = rememberBackdropStrength(palette, target)
    SideEffect {
        if (navigationEntryId != null) navigationColors?.update(navigationEntryId, barColors)
    }
    DisposableEffect(navigationColors, navigationEntryId) {
        onDispose { if (navigationEntryId != null) navigationColors?.update(navigationEntryId, null) }
    }
    val headerMotion = rememberBrandedHeaderMotion(branded)
    DarkPageStatusBar(enabled = dark != systemDark, systemDark = systemDark)
    CompositionLocalProvider(
        LocalArtworkLoaderColors provides colors,
        LocalInfoBackdropStrength provides backdropStrength,
        LocalBrandedPage provides active,
        LocalBrandedHeaderMotion provides headerMotion,
        LocalEffectiveDarkTheme provides dark,
    ) {
        MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
                // Surface propagates its minimum size. Keep that on one page container,
                // rather than forcing sibling header overlays to fill the whole viewport.
                Box(modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Reading).fillMaxSize()) {
                    content()
                }
            }
        }
    }
}

/** Before the cover's palette is in: the departing page's colours when artwork colours are on. */
private fun artworkInitial(enabled: Boolean, base: ColorScheme, inherited: ColorScheme?, dark: Boolean, style: String): ColorScheme =
    if (enabled) artworkLoadingColorScheme(base, inherited, dark, style) else base

/** The cover's scheme once its palette is in, the loading scheme while it loads, otherwise the app's. */
private fun artworkTarget(palette: ArtworkPalette?, waiting: Boolean, dark: Boolean, style: String, initial: ColorScheme, base: ColorScheme): ColorScheme = when {
    palette != null -> artworkColorScheme(palette, dark, style)
    waiting -> initial
    else -> base
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

/** A dark-only show keeps its dark page in light mode too. */
private fun pageDark(systemDark: Boolean, branded: BrandedShowPage?): Boolean = systemDark || branded?.darkOnly == true

@Composable
private fun rememberBrandedHeaderMotion(branded: BrandedShowPage?): BrandedHeaderMotion? = remember(branded) { branded?.let { BrandedHeaderMotion() } }

/** White status bar icons over a dark-only page; the app's own setting returns when the page leaves. */
@Composable
private fun DarkPageStatusBar(enabled: Boolean, systemDark: Boolean) {
    if (!enabled) return
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    val lightIconsOutside by rememberUpdatedState(!systemDark)
    DisposableEffect(view) {
        onDispose { controller.isAppearanceLightStatusBars = lightIconsOutside }
    }
    SideEffect { controller.isAppearanceLightStatusBars = false }
}
