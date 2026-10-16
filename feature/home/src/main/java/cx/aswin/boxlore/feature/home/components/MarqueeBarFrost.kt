package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Whether a Marquee card draws its own frost under the top bar ([MarqueeTopFrost]). Home turns it
 * off and blurs the live page behind its bar instead ([MarqueeBarFrost]); the Settings preview and
 * tests keep the card's own.
 */
val LocalMarqueeCardFrost = staticCompositionLocalOf { true }

/**
 * Frosted glass behind Home's floating top bar over Marquee, blurring the live page under it: the
 * still cover, the next card sliding in, Home's body rising. Because it is the bar's, fixed to the
 * top of the screen at the bar's full height, it never moves with the cards, never splits mid-swipe
 * and never shows a card's cover sharp while its blur loads. It is soft (a 20dp blur and a light
 * tint toward the opposite of the bar's words), full behind the bar and fading on an S-curve (a mask
 * on its own layer) from just above its foot to [MarqueeBarFrostReach] below it; it fades away with [overArtwork] as Home's body passes under the bar,
 * which then fills with the body's own colour. Blur needs Android 12; older phones keep the tint.
 */
@Composable
internal fun MarqueeBarFrost(state: HazeState, barHeight: Dp, tone: MarqueeTopTone, overArtwork: () -> Float, modifier: Modifier = Modifier) {
    val veil = if (tone.content.luminance() > 0.5f) Color.Black else Color.White
    val total = barHeight + MarqueeBarFrostReach
    val density = LocalDensity.current
    val fadeFrom = with(density) { (barHeight - MarqueeBarFrostLead).coerceAtLeast(0.dp).toPx() }
    val end = with(density) { total.toPx() }
    val stops = remember(fadeFrom, end) { marqueeBarFrostStops(fadeFrom / end) }
    val ground = MaterialTheme.colorScheme.surface
    // The fade is a mask on a layer of its own, applied to whatever the blur and tint draw: on this
    // hardware Haze ignored both its own mask and its progressive blur, leaving a razor-sharp foot.
    Box(
        modifier
            .fillMaxWidth()
            .height(total)
            .graphicsLayer {
                alpha = overArtwork()
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(*stops.map { (at, strength) -> at to Color.Black.copy(alpha = strength) }.toTypedArray()), blendMode = BlendMode.DstIn)
            }
            .testTag("home_marquee_bar_frost"),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .hazeEffect(state = state, style = HazeStyle(backgroundColor = ground, tint = HazeTint(veil.copy(alpha = MarqueeBarFrostTint)), blurRadius = MarqueeBarFrostBlur, noiseFactor = 0f)) {
                    inputScale = HazeInputScale.Auto
                },
        )
    }
}

/**
 * The frost's strength down its height, as (position, alpha), full until [fadeFrom] (a little above
 * the bar's foot) and then an S-curve to nothing at the strip's foot: it eases away gently, thins
 * through the middle and settles gently, so neither end of the fade shows as a line.
 */
internal fun marqueeBarFrostStops(fadeFrom: Float): List<Pair<Float, Float>> {
    val from = fadeFrom.coerceIn(0f, 1f)
    return listOf(0f to 1f, from to 1f) + MarqueeFrostTail.map { t -> (from + (1f - from) * t) to 1f - t * t * (3f - 2f * t) }
}

private val MarqueeFrostTail = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f)
private val MarqueeBarFrostReach = 40.dp

/** The fade starts this far above the bar's foot, so the bar's edge and the frost's never line up. */
private val MarqueeBarFrostLead = 20.dp
private val MarqueeBarFrostBlur = 20.dp
private const val MarqueeBarFrostTint = 0.12f
