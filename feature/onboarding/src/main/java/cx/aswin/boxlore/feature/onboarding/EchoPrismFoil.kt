package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * The Prism card's look: a turning rainbow edge round a deep-space ground, a starburst behind the
 * art, glitter, a rainbow sweep and a glare. The card is the deck's one deliberate break from the
 * app theme (the listener asked for something extravagant), so its colours are fixed here rather
 * than taken from the colour scheme; every text colour is checked against the dark ground.
 */

/** Printed holo foil's spectrum. */
internal val PrismSpectrum = listOf(
    Color(0xFFFF6B8B),
    Color(0xFFFFC46B),
    Color(0xFFF4F77A),
    Color(0xFF7DFFC4),
    Color(0xFF6BD3FF),
    Color(0xFF9D8CFF),
    Color(0xFFFF8CE6),
    Color(0xFFFF6B8B),
)

internal object PrismColors {
    val GroundTop = Color(0xFF140F2E)
    val GroundMiddle = Color(0xFF0B0919)
    val GroundBottom = Color(0xFF07060F)
    val Pink = Color(0xFFFF5CAA)
    val Sky = Color(0xFF50C8FF)
    val Violet = Color(0xFF825FFF)
    val Glow = Color(0xFF9D8CFF)

    /** The pitch box and its text (15:1). */
    val HookGround = Color(0xFF141129)
    val HookInk = Color(0xFFECE9FB)

    /** The publisher line on the ground (9:1). */
    val Muted = Color(0xFFBDB7DF)

    /** The tag sticker's ink on its pastel rainbow (over 12:1 throughout). */
    val TagInk = Color(0xFF0B0A14)
    val ArtRing = Color(0xF2120E28)
    val Mark = Color(0xFFC8C0FF)

    val Tag = Brush.horizontalGradient(listOf(Color(0xFFFFD27A), Color(0xFF8DFFD0), Color(0xFF8FDCFF), Color(0xFFFFA3EC)))
    val Title = Brush.linearGradient(listOf(Color.White, Color(0xFFFFD9F3), Color(0xFFCBF4FF), Color(0xFFFFF6D0), Color.White))
    val HookEdge = Brush.linearGradient(PrismSpectrum.dropLast(1).map { it.copy(alpha = 0.6f) })
}

/** The rainbow edge, turning: a sweep gradient spun about the centre and clipped by the card. */
internal fun DrawScope.drawPrismEdge(degrees: Float) {
    rotate(degrees) {
        drawCircle(Brush.sweepGradient(PrismSpectrum, center), radius = size.maxDimension, center = center)
    }
}

/** The deep-space ground with three soft glows, inside the edge. */
internal fun DrawScope.drawPrismGround(corner: Float) {
    drawRoundRect(
        Brush.verticalGradient(0f to PrismColors.GroundTop, 0.6f to PrismColors.GroundMiddle, 1f to PrismColors.GroundBottom),
        cornerRadius = CornerRadius(corner),
    )
    glow(PrismColors.Pink, 0.32f, Offset(size.width * 0.12f, size.height * 0.08f), size.width * 0.6f)
    glow(PrismColors.Sky, 0.26f, Offset(size.width * 0.92f, size.height * 0.26f), size.width * 0.55f)
    glow(PrismColors.Violet, 0.42f, Offset(size.width * 0.5f, size.height * 1.08f), size.width * 0.8f)
}

private fun DrawScope.glow(color: Color, alpha: Float, centre: Offset, radius: Float) {
    drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), centre, radius))
}

/** The starburst: thin rays from [centre], fading out by [reach], slowly turning. */
internal fun DrawScope.drawPrismRays(rays: Path, centre: Offset, reach: Float, degrees: Float) {
    rotate(degrees, centre) {
        drawPath(rays, Brush.radialGradient(0.14f to Color.White.copy(alpha = 0.11f), 1f to Color.White.copy(alpha = 0f), center = centre, radius = reach))
    }
}

/** Rays every 9°, each 2° wide, as one path built once per size. */
internal fun prismRays(centre: Offset, reach: Float): Path = Path().apply {
    var angle = 0.0
    while (angle < 360.0) {
        val from = Math.toRadians(angle)
        val to = Math.toRadians(angle + PrismRayWidthDegrees)
        moveTo(centre.x, centre.y)
        lineTo(centre.x + cos(from).toFloat() * reach, centre.y + sin(from).toFloat() * reach)
        lineTo(centre.x + cos(to).toFloat() * reach, centre.y + sin(to).toFloat() * reach)
        close()
        angle += PrismRayStepDegrees
    }
}

private const val PrismRayWidthDegrees = 2.0
private const val PrismRayStepDegrees = 9.0

/** The rainbow sweep: it follows the light as the card turns and drifts with [sweep] (0-1). */
internal fun DrawScope.drawPrismHolo(light: Offset, sweep: Float) {
    val start = (light.x * 1.6f - 0.6f + (sweep - 0.5f) * 0.5f) * size.width
    val band = size.width * 0.9f
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.25f to Color(0xFFFFC46B).copy(alpha = 0.2f),
            0.42f to Color(0xFF7DFFC4).copy(alpha = 0.22f),
            0.58f to Color(0xFF6BD3FF).copy(alpha = 0.22f),
            0.75f to Color(0xFFFF8CE6).copy(alpha = 0.18f),
            1f to Color.Transparent,
            start = Offset(start, 0f),
            end = Offset(start + band, band * 0.5f),
        ),
        blendMode = BlendMode.Screen,
    )
}

/** The art's frame: a white edge, a dark ring and a faint outer ring, over a violet glow. */
internal fun DrawScope.drawPrismArtFrame(corner: Float) {
    val ring = 1.5.dp.toPx()
    drawRect(
        Brush.radialGradient(listOf(PrismColors.Glow.copy(alpha = 0.5f), PrismColors.Glow.copy(alpha = 0f)), center, size.maxDimension * 0.85f),
        topLeft = Offset(-size.width * 0.4f, -size.height * 0.4f),
        size = Size(size.width * 1.8f, size.height * 1.8f),
    )
    ring(5.75.dp.toPx(), ring, Color.White.copy(alpha = 0.22f), corner)
    ring(3.25.dp.toPx(), 3.5.dp.toPx(), PrismColors.ArtRing, corner)
    ring(0.75.dp.toPx(), ring, Color.White.copy(alpha = 0.92f), corner)
}

private fun DrawScope.ring(outset: Float, width: Float, color: Color, corner: Float) {
    drawRoundRect(
        color,
        topLeft = Offset(-outset, -outset),
        size = Size(size.width + outset * 2, size.height + outset * 2),
        cornerRadius = CornerRadius(corner + outset),
        style = Stroke(width),
    )
}

/** A light sheen across the art, moving with [sweep]. */
internal fun DrawScope.drawPrismSheen(sweep: Float) {
    val start = (sweep * 1.6f - 0.8f) * size.width
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.3f),
            1f to Color.Transparent,
            start = Offset(start, 0f),
            end = Offset(start + size.width * 0.6f, size.height * 0.3f),
        ),
        blendMode = BlendMode.Screen,
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

/** Dust on the ground: a faint dot always, and a four-point glint when it catches the light. */
internal fun DrawScope.drawSparkles(sparkles: List<EchoSparkle>, lean: EchoCardLean, color: Color) {
    val arm = 3.dp.toPx()
    val stroke = 1.dp.toPx()
    val dot = 0.8.dp.toPx()
    sparkles.forEach { sparkle ->
        val centre = Offset(sparkle.x * size.width, sparkle.y * size.height)
        drawCircle(color.copy(alpha = 0.35f), dot * sparkle.size, centre)
        val alpha = echoSparkleAlpha(sparkle.phase, lean)
        if (alpha < 0.05f) return@forEach
        val reach = arm * sparkle.size * (0.6f + alpha * 0.6f)
        drawCircle(color.copy(alpha = alpha * 0.35f), reach, centre)
        drawLine(color.copy(alpha = alpha), centre - Offset(reach, 0f), centre + Offset(reach, 0f), stroke)
        drawLine(color.copy(alpha = alpha), centre - Offset(0f, reach), centre + Offset(0f, reach), stroke)
    }
}
