package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val CookieLobes = 9
private const val CookieDepth = 0.06f
private const val ContourSteps = 144

internal val MiniPlayerArtworkSize = 48.dp
internal val MiniPlayerCompactArtworkSize = 40.dp
internal val MiniPlayerCompactProgressStroke = 3.5.dp

/** Shared contour for the compact artwork clip, surface and progress outline. */
internal fun compactPlayerContourPoint(size: Size, progress: Float, cookieFraction: Float, inset: Float = 0f, rotation: Float = 0f): Offset {
    val depth = CookieDepth * cookieFraction.coerceIn(0f, 1f)
    val radius = (size.minDimension / 2f - inset).coerceAtLeast(0f) / (1f + depth)
    val angle = (progress * 2f * PI - PI / 2f).toFloat()
    val scallopedRadius = radius * (1f + depth * cos(CookieLobes * (angle + rotation)))
    return Offset(size.width / 2f + scallopedRadius * cos(angle), size.height / 2f + scallopedRadius * sin(angle))
}

internal fun compactPlayerPath(size: Size, cookieFraction: Float, inset: Float = 0f, rotation: Float = 0f): Path = Path().apply {
    for (step in 0..ContourSteps) {
        val point = compactPlayerContourPoint(size, step.toFloat() / ContourSteps, cookieFraction, inset, rotation)
        if (step == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

/** The same cookie artwork keeps its contour as the regular bar contracts into compact mode. */
internal class MiniPlayerArtworkShape(private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(compactPlayerPath(size, cookieFraction = 1f, rotation = rotation))
}

/** Morph the regular sheet's perimeter into the cookie without changing its hit bounds. */
internal class AdaptiveMiniPlayerSurfaceShape(
    private val topRadius: Dp,
    private val bottomRadius: Dp,
    private val cookieFraction: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.minDimension <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val amount = cookieFraction.coerceIn(0f, 1f)
        if (amount == 0f) {
            return RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
                .createOutline(size, layoutDirection, density)
        }
        val top = with(density) { topRadius.toPx() }.coerceIn(0f, size.minDimension / 2f)
        val bottom = with(density) { bottomRadius.toPx() }.coerceIn(0f, size.minDimension / 2f)
        val measure = PathMeasure().apply { setPath(roundedPlayerPath(size, top, bottom), true) }
        val path = Path()
        for (step in 0..ContourSteps) {
            val fraction = step.toFloat() / ContourSteps
            val normal = measure.getPosition(measure.length * fraction)
            val cookie = compactPlayerContourPoint(size, fraction, 1f, rotation = rotation)
            val point = normal + (cookie - normal) * amount
            if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

private fun roundedPlayerPath(size: Size, top: Float, bottom: Float): Path = Path().apply {
    // Start at twelve o'clock and travel clockwise, matching the progress contour.
    moveTo(size.width / 2f, 0f)
    lineTo(size.width - top, 0f)
    arcTo(Rect(size.width - top * 2f, 0f, size.width, top * 2f), -90f, 90f, false)
    lineTo(size.width, size.height - bottom)
    arcTo(Rect(size.width - bottom * 2f, size.height - bottom * 2f, size.width, size.height), 0f, 90f, false)
    lineTo(bottom, size.height)
    arcTo(Rect(0f, size.height - bottom * 2f, bottom * 2f, size.height), 90f, 90f, false)
    lineTo(0f, top)
    arcTo(Rect(0f, 0f, top * 2f, top * 2f), 180f, 90f, false)
    close()
}
