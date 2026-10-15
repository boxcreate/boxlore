package cx.aswin.boxlore.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * The holo card's foil: theme-tinted chrome, the travelling rainbow band, glitter and glare. Kept
 * apart from the card's layout so each file stays focused.
 */

/** The foil's tones, from the theme so the card belongs to it, plus the solid panels' colours. */
@Immutable
internal data class EchoFoil(
    val light: Color,
    val mid: Color,
    val shade: Color,
    val panel: Color,
    /** Where the panel's tint ends, at its bottom. */
    val panelTint: Color,
    /** The panels' fine edge. */
    val edge: Color,
    val onPanel: Color,
    val onPanelVariant: Color,
    val accent: Color,
    val onAccent: Color,
    val back: Color,
    val sparkle: Color,
    val glare: Float,
)

@Composable
internal fun echoFoil(): EchoFoil {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    return if (dark) {
        EchoFoil(
            light = lerp(colors.primary, Color.White, 0.45f),
            mid = lerp(colors.primaryContainer, colors.primary, 0.45f),
            shade = colors.primaryContainer,
            panel = colors.surfaceContainerHigh,
            panelTint = lerp(colors.surfaceContainerHigh, colors.primaryContainer, 0.45f),
            edge = colors.primary.copy(alpha = 0.35f),
            onPanel = colors.onSurface,
            onPanelVariant = colors.onSurfaceVariant,
            accent = colors.primary,
            onAccent = colors.onPrimary,
            back = colors.primaryContainer,
            sparkle = Color.White,
            glare = 0.12f,
        )
    } else {
        EchoFoil(
            light = lerp(colors.primaryContainer, Color.White, 0.7f),
            mid = lerp(colors.primary, colors.primaryContainer, 0.45f),
            shade = lerp(colors.primary, Color.Black, 0.12f),
            panel = colors.surfaceContainerLowest,
            panelTint = lerp(colors.surfaceContainerLowest, colors.primaryContainer, 0.35f),
            edge = colors.primary.copy(alpha = 0.28f),
            onPanel = colors.onSurface,
            onPanelVariant = colors.onSurfaceVariant,
            accent = colors.primary,
            onAccent = colors.onPrimary,
            back = colors.primary,
            sparkle = Color.White,
            glare = 0.22f,
        )
    }
}

/** Printed holo foil's spectrum; an effect, not a theme colour. */
private val EchoHoloSpectrum = listOf(
    Color(0xFFFF6B8B),
    Color(0xFFFFC46B),
    Color(0xFFF4F77A),
    Color(0xFF7DFFC4),
    Color(0xFF6BD3FF),
    Color(0xFF9D8CFF),
    Color(0xFFFF8CE6),
    Color(0xFFFF6B8B),
)

/** Polished metal: light and shade bands along a diagonal that slides as the light moves. */
internal fun DrawScope.drawChrome(foil: EchoFoil, light: Offset) {
    val shift = (light.x - 0.5f) * size.width * 1.4f + (light.y - 0.4f) * size.height * 0.3f
    drawRect(
        Brush.linearGradient(
            0f to foil.shade,
            0.18f to foil.mid,
            0.34f to foil.light,
            0.46f to foil.mid,
            0.62f to foil.shade,
            0.8f to foil.light,
            1f to foil.mid,
            start = Offset(-size.width * 0.2f + shift, 0f),
            end = Offset(size.width * 0.9f + shift, size.height),
            tileMode = TileMode.Mirror,
        ),
    )
}

/** The rainbow band; it travels faster than the light, which is what makes foil look alive. */
internal fun DrawScope.drawHolo(light: Offset, alpha: Float, blendMode: BlendMode = BlendMode.Overlay) {
    val period = size.width * 0.9f
    val start = (light.x * 2.2f - 0.6f) * size.width + (light.y - 0.4f) * size.height * 0.6f
    drawRect(
        Brush.linearGradient(EchoHoloSpectrum, Offset(start, 0f), Offset(start + period, period * 1.2f), TileMode.Mirror),
        alpha = alpha,
        blendMode = blendMode,
    )
}

internal fun DrawScope.drawGlare(light: Offset, alpha: Float) {
    val centre = Offset(light.x * size.width, light.y * size.height)
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent), centre, max(size.width, size.height) * 0.55f))
}

/** A sparkle: where it sits (0-1) and the angle at which it catches the light. */
internal data class EchoSparkle(val x: Float, val y: Float, val phase: Float, val size: Float)

internal fun echoSparkles(seed: Long, count: Int): List<EchoSparkle> {
    val random = Random(seed)
    return List(count) { EchoSparkle(random.nextFloat(), random.nextFloat(), random.nextFloat() * 2f * PI.toFloat(), 0.6f + random.nextFloat() * 0.8f) }
}

/** Each glint flashes only near its own angle, so turning the card makes them twinkle in turn. */
internal fun echoSparkleAlpha(phase: Float, lean: EchoCardLean): Float =
    sin(phase + lean.y * 0.45f + lean.x * 0.3f).coerceAtLeast(0f).pow(6)

internal fun DrawScope.drawSparkles(sparkles: List<EchoSparkle>, lean: EchoCardLean, color: Color) {
    val arm = 3.dp.toPx()
    val stroke = 1.dp.toPx()
    sparkles.forEach { sparkle ->
        val alpha = echoSparkleAlpha(sparkle.phase, lean)
        if (alpha < 0.05f) return@forEach
        val centre = Offset(sparkle.x * size.width, sparkle.y * size.height)
        val reach = arm * sparkle.size * (0.6f + alpha * 0.6f)
        drawCircle(color.copy(alpha = alpha * 0.35f), reach, centre)
        drawLine(color.copy(alpha = alpha), centre - Offset(reach, 0f), centre + Offset(reach, 0f), stroke)
        drawLine(color.copy(alpha = alpha), centre - Offset(0f, reach), centre + Offset(0f, reach), stroke)
    }
}

/** Fine diagonal etching across the foil, built once per size. */
internal fun echoEtching(width: Float, height: Float, spacing: Float): Path = Path().apply {
    var x = -height
    while (x < width) {
        moveTo(x, height)
        lineTo(x + height, 0f)
        x += spacing
    }
}
