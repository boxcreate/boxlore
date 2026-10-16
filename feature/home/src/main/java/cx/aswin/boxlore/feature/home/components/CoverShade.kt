package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ArtworkColorTransitionMillis
import cx.aswin.boxlore.core.designsystem.theme.ArtworkDuotone
import cx.aswin.boxlore.core.designsystem.theme.ArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.ArtworkShade
import cx.aswin.boxlore.core.designsystem.theme.cachedArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.loadArtworkPalette
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade

/** The hero card style the listener chose in Appearance settings ([HeroCardStyle]); Block by default. */
val LocalHeroCardStyle = staticCompositionLocalOf { HeroCardStyle.BLOCK }

/** The stored hero colours ([HeroCoverShade]), unset until picked; each style reads it through [HeroCoverShade.forStyle] or sanitize. */
val LocalHeroCoverShade = staticCompositionLocalOf { HeroCoverShade.UNSET }

/**
 * The colours a Cover card's art fades into behind its words. Dark is a plain near-black shade;
 * App theme is a deep shade of the theme's [primary]; Artwork is a deep shade of the cover's seed
 * (the colour the shared artwork picker also gives its pages and the player), and stays plain for a
 * cover with no meaningful colour.
 */
internal fun coverShade(mode: String, palette: ArtworkPalette?, primary: Color): ArtworkShade = when (HeroCoverShade.sanitize(mode)) {
    HeroCoverShade.DARK -> ArtworkShade.Neutral
    HeroCoverShade.THEME -> ArtworkShade.fromColor(primary)
    else -> palette?.let(ArtworkShade::fromPalette) ?: ArtworkShade.Neutral
}

/**
 * [coverShade] for the cover at [imageUrl] in the listener's chosen mode. A cover already read
 * colours the card from its first frame; otherwise the card starts plain and eases into the cover's
 * shade, on the page colours' clock, once the picker returns.
 */
@Composable
internal fun rememberCoverShade(imageUrl: String?): ArtworkShade {
    val mode = HeroCoverShade.sanitize(LocalHeroCoverShade.current)
    val primary = MaterialTheme.colorScheme.primary
    val palette = rememberHeroArtworkPalette(imageUrl, mode)
    return remember(mode, palette, primary) { coverShade(mode, palette, primary) }.animated()
}

/**
 * The two tones a Duotone card redraws a cover in, from the same choice as Cover's shade: Dark is
 * black and white, App theme the theme's [primary], Artwork the cover's seed (black and white for a
 * cover with no meaningful colour).
 */
internal fun duotoneTones(mode: String, palette: ArtworkPalette?, primary: Color): ArtworkDuotone = when (HeroCoverShade.sanitize(mode)) {
    HeroCoverShade.DARK -> ArtworkDuotone.Neutral
    HeroCoverShade.THEME -> ArtworkDuotone.fromColor(primary)
    else -> palette?.let(ArtworkDuotone::fromPalette) ?: ArtworkDuotone.Neutral
}

/** A Duotone card's look: the cover's two tones and the words and buttons drawn over them. */
internal data class DuotoneLook(val duotone: ArtworkDuotone, val shade: ArtworkShade)

/** [duotoneTones] and [coverShade] for the cover at [imageUrl], easing in like [rememberCoverShade]. */
@Composable
internal fun rememberDuotoneLook(imageUrl: String?): DuotoneLook {
    val mode = HeroCoverShade.sanitize(LocalHeroCoverShade.current)
    val primary = MaterialTheme.colorScheme.primary
    val palette = rememberHeroArtworkPalette(imageUrl, mode)
    val shade = remember(mode, palette, primary) { coverShade(mode, palette, primary) }.animated()
    val tones = remember(mode, palette, primary) { duotoneTones(mode, palette, primary) }
    val spec = remember { tween<Color>(ArtworkColorTransitionMillis, easing = FastOutSlowInEasing) }
    val shadow by animateColorAsState(tones.shadow, spec, label = "duotoneShadow")
    val highlight by animateColorAsState(tones.highlight, spec, label = "duotoneHighlight")
    return DuotoneLook(ArtworkDuotone(shadow, highlight), shade)
}

/** The cover's palette in Artwork mode (cached ones at once, others once read); null in the other modes. */
@Composable
internal fun rememberHeroArtworkPalette(imageUrl: String?, mode: String): ArtworkPalette? {
    val context = LocalContext.current
    val url = imageUrl?.trim()?.takeIf(String::isNotEmpty)
    val artwork = mode == HeroCoverShade.ARTWORK
    var palette by remember(url, artwork) { mutableStateOf(if (artwork) url?.let(::cachedArtworkPalette) else null) }
    LaunchedEffect(url, artwork) {
        if (artwork && url != null && palette == null) palette = runCatching { loadArtworkPalette(context, url) }.getOrNull()
    }
    return palette
}

@Composable
private fun ArtworkShade.animated(): ArtworkShade {
    val spec = remember { tween<Color>(ArtworkColorTransitionMillis, easing = FastOutSlowInEasing) }
    val deepColor by animateColorAsState(this.deep, spec, label = "coverShadeDeep")
    val containerColor by animateColorAsState(this.container, spec, label = "coverShadeContainer")
    val contentColor by animateColorAsState(this.content, spec, label = "coverShadeContent")
    val softColor by animateColorAsState(this.softContent, spec, label = "coverShadeSoftContent")
    val accentColor by animateColorAsState(this.accent, spec, label = "coverShadeAccent")
    val onAccentColor by animateColorAsState(this.onAccent, spec, label = "coverShadeOnAccent")
    return ArtworkShade(deepColor, containerColor, contentColor, softColor, accentColor, onAccentColor)
}

/**
 * Fades the art above this block into [color]: clear [fade] above the block's top edge, nearly solid
 * at that edge and solid [solid] below it, so the words always sit on the shade however long they
 * run. Draws outside the block's bounds; the card's shape clips it.
 */
internal fun Modifier.coverScrim(color: Color, fade: Dp = 150.dp, solid: Dp = 28.dp): Modifier = drawBehind {
    val fadePx = fade.toPx()
    val total = fadePx + size.height
    if (total <= 0f) return@drawBehind
    drawRect(
        brush = Brush.verticalGradient(
            0f to color.copy(alpha = 0f),
            fadePx * 0.5f / total to color.copy(alpha = 0.6f),
            fadePx / total to color.copy(alpha = 0.92f),
            ((fadePx + solid.toPx()) / total).coerceAtMost(1f) to color,
            1f to color,
            startY = -fadePx,
            endY = size.height,
        ),
        topLeft = Offset(0f, -fadePx),
        size = Size(size.width, total),
    )
}
