package cx.aswin.boxlore.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.prefs.NavigationBlurLevel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** One strength of the blur: its radius, how much page colour it lays over, and how far above the bar it reaches. */
internal data class FloatingNavigationBlurSpec(val radius: Dp, val tintAlpha: Float, val reach: Dp)

/**
 * The page behind the floating navigation bar softens into a blur as it scrolls toward it, so
 * sharp text never meets the bar at a hard line. The bar stays solid; only the page blurs, in
 * one pass, behind the bar (not behind the mini player). Floating style only, at the strength
 * picked in Settings (soft by default).
 */
internal object FloatingNavigationBlur {
    fun shows(style: NavigationStyle, navigationVisible: Boolean, level: String): Boolean =
        NavigationBlurLevel.sanitize(level) != NavigationBlurLevel.OFF && navigationVisible && style == NavigationStyle.Floating

    /**
     * Only the bar and the system inset below it. The mini player is left out, so the strip keeps
     * its size when playback starts instead of growing up behind the player.
     */
    fun barChrome(style: NavigationStyle, systemNavigationInset: Dp): Dp =
        appBottomChromeContentPadding(style, isMiniPlayerVisible = false, systemNavigationInset = systemNavigationInset)

    /** The strip covers the bar chrome plus the level's reach above it. */
    fun stripHeight(bottomChrome: Dp, reach: Dp): Dp = bottomChrome.coerceAtLeast(0.dp) + reach

    /** Soft is a faint frost that barely rises above the bar; strong is a full frosted fade. */
    fun spec(level: String): FloatingNavigationBlurSpec = when (NavigationBlurLevel.sanitize(level)) {
        NavigationBlurLevel.STRONG -> FloatingNavigationBlurSpec(radius = 22.dp, tintAlpha = 0.26f, reach = 56.dp)
        NavigationBlurLevel.MEDIUM -> FloatingNavigationBlurSpec(radius = 14.dp, tintAlpha = 0.14f, reach = 40.dp)
        else -> FloatingNavigationBlurSpec(radius = 8.dp, tintAlpha = 0.06f, reach = 28.dp)
    }

    /**
     * How visible the whole effect (blur and tint together) is across the reach, top to bottom:
     * an ease-in curve, so it is nothing at the top edge and barely there for the first stretch.
     */
    fun fadeStops(): List<Pair<Float, Float>> = FadeSteps.map { t -> t to t * t * t }

    private val FadeSteps = listOf(0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f, 1f)
}

/** The blur strip, drawn under the floating bar over a page marked as the [state]'s source. */
@Composable
internal fun FloatingNavigationBlurStrip(state: HazeState, level: String, bottomChrome: Dp, pageColor: Color, modifier: Modifier = Modifier) {
    val spec = FloatingNavigationBlur.spec(level)
    val height by animateDpAsState(FloatingNavigationBlur.stripHeight(bottomChrome, spec.reach), label = "Navigation blur height")
    val reach = with(LocalDensity.current) { spec.reach.toPx() }
    // The mask fades blur and tint in together over the reach, so the top edge never shows.
    val mask = remember(reach) {
        Brush.verticalGradient(
            *FloatingNavigationBlur.fadeStops().map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }.toTypedArray(),
            startY = 0f,
            endY = reach,
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(
                state = state,
                style = HazeStyle(backgroundColor = pageColor, tint = HazeTint(pageColor.copy(alpha = spec.tintAlpha)), blurRadius = spec.radius, noiseFactor = 0f),
            ) {
                this.mask = mask
                // A scaled-down copy is cheaper to blur and looks the same this soft.
                inputScale = HazeInputScale.Auto
            },
    )
}
