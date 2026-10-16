package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.designsystem.theme.ArtworkColorTransitionMillis
import cx.aswin.boxlore.core.designsystem.theme.ArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.artworkColorScheme
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade

/**
 * Block's card colours for the listener's [mode] ([HeroCoverShade]): the app theme as it is (what
 * Block always showed, and still shows until a colour is picked); a scheme built from the cover's
 * seed, the same way pod info colours its page (the theme for a cover with no meaningful colour);
 * or [BlockDarkColors], near black in light and dark mode alike. Block's card reads only a few
 * roles (the background's lightness, primary and primary container with their on colours, and
 * the high surface container), so swapping the scheme recolours the whole card.
 */
internal fun blockColorScheme(mode: String, palette: ArtworkPalette?, theme: ColorScheme, isDark: Boolean, surfaceStyle: String): ColorScheme =
    when (HeroCoverShade.sanitize(mode)) {
        HeroCoverShade.DARK -> BlockDarkColors
        HeroCoverShade.ARTWORK -> palette?.let { artworkColorScheme(it, isDark, surfaceStyle) } ?: theme
        else -> theme
    }

/**
 * Neutral, near-black Block colours. A dark background makes Block fill its card with the primary
 * container and write in its on colour; Play is the light primary on that container (both well
 * over 4.5:1), and Details sits a step lighter.
 */
internal val BlockDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFE6E6EA),
    onPrimary = Color(0xFF1C1C20),
    primaryContainer = Color(0xFF1C1C20),
    onPrimaryContainer = Color(0xFFF2F2F5),
    background = Color(0xFF121214),
    surface = Color(0xFF121214),
    surfaceContainerHigh = Color(0xFF34343A),
)

/**
 * Draws Block cards for the cover at [imageUrl] in the chosen colours. A cover already read colours
 * the card from its first frame; otherwise the card starts in the theme and eases into the cover's
 * colours on the page colours' clock once the picker returns.
 */
@Composable
internal fun BlockHeroColors(imageUrl: String?, content: @Composable () -> Unit) {
    val mode = HeroCoverShade.forStyle(HeroCardStyle.BLOCK, LocalHeroCoverShade.current)
    val theme = MaterialTheme.colorScheme
    val palette = rememberHeroArtworkPalette(imageUrl, mode)
    val dark = LocalEffectiveDarkTheme.current
    val surfaceStyle = LocalSurfaceStyle.current
    val target = remember(mode, palette, theme, dark, surfaceStyle) { blockColorScheme(mode, palette, theme, dark, surfaceStyle) }
    val spec = remember { tween<Color>(ArtworkColorTransitionMillis, easing = FastOutSlowInEasing) }
    val primary by animateColorAsState(target.primary, spec, label = "blockPrimary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "blockOnPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "blockPrimaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "blockOnPrimaryContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, spec, label = "blockSurfaceContainerHigh")
    val scheme = target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        surfaceContainerHigh = surfaceContainerHigh,
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}
