package cx.aswin.boxlore.feature.info.branded.crimejunkie

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The fingerprint behind the splatter mark: 26 ridge lines, a loop at the core inside arches that
 * open towards the bottom, leaning as prints do, with small breaks along each ridge. Drawn in the
 * splatter's colours and faded out well before any text, as on the approved board.
 */
internal object CrimeJunkieFingerprint {
    const val RIDGES = 26
    private const val GAP = 6.6f
    private const val TILT = -0.26f
    private const val CLOSED_LOOPS = 5
    const val STROKE_WIDTH = 1.6f

    /** Fully drawn out to a quarter of this radius from the centre, gone by 85% of it. */
    const val FADE_RADIUS = 190f

    /** The print's box on the board: 369 by 160dp, its centre 78dp down. */
    const val BOX_WIDTH = 369f
    const val BOX_HEIGHT = 160f
    const val CENTRE_Y = 78f

    /** Ridge [index]'s points in dp around the print's centre. */
    fun ridge(index: Int): List<Offset> {
        val r = 10f + index * GAP
        val rx = r * 0.92f
        val ry = r * 1.22f
        val (from, to) = if (index < CLOSED_LOOPS) {
            0.0 to 2 * PI
        } else {
            val open = min(1.15, 0.35 + index * 0.045)
            PI / 2 + open to PI / 2 - open + 2 * PI
        }
        val steps = 90 + index * 6
        return (0..steps).map { k ->
            val t = from + (to - from) * k / steps
            val wobble = 1 + 0.035 * sin(3 * t + index * 0.45) + 0.02 * sin(7 * t + index)
            val lean = 0.12 * sin(t) * (1 - index.toDouble() / RIDGES)
            val ex = rx * wobble * cos(t + lean)
            val ey = -ry * wobble * sin(t + lean)
            Offset((ex * cos(TILT) - ey * sin(TILT)).toFloat(), (ex * sin(TILT) + ey * cos(TILT)).toFloat())
        }
    }

    /** Each ridge's dashes and breaks in dp, as the board drew them. */
    val Breaks: List<IntArray> = listOf(
        intArrayOf(59, 4, 89, 3),
        intArrayOf(78, 5, 35, 6),
        intArrayOf(33, 6, 36, 4, 68, 8, 41, 4),
        intArrayOf(86, 9, 82, 5, 118, 3),
        intArrayOf(107, 5, 43, 4, 58, 8, 46, 6),
        intArrayOf(88, 5, 79, 3, 35, 4, 91, 6),
        intArrayOf(58, 7, 71, 5, 101, 7, 52, 6),
        intArrayOf(77, 8, 96, 5, 118, 4),
        intArrayOf(68, 8, 44, 6, 34, 7, 99, 6, 109, 5),
        intArrayOf(93, 7, 82, 6, 106, 9, 73, 7),
        intArrayOf(35, 7, 88, 9, 104, 5, 65, 7, 32, 6, 45, 4),
        intArrayOf(35, 8, 42, 4, 65, 8, 37, 6, 79, 8, 104, 8, 55, 5),
        intArrayOf(62, 8, 116, 4, 46, 4, 51, 6, 83, 5, 30, 6, 63, 6),
        intArrayOf(116, 7, 76, 7, 91, 3, 111, 8, 109, 8),
        intArrayOf(65, 5, 39, 7, 36, 3, 49, 4, 61, 3, 30, 4, 39, 5, 32, 8, 85, 4, 53, 5),
        intArrayOf(63, 4, 106, 9, 72, 6, 38, 4, 61, 5, 105, 4, 32, 9, 78, 4),
        intArrayOf(79, 3, 78, 9, 108, 7, 54, 5, 45, 8, 78, 8, 60, 4),
        intArrayOf(103, 9, 107, 8, 104, 7, 50, 6, 62, 3, 33, 5, 53, 7),
        intArrayOf(116, 6, 114, 9, 116, 5, 50, 4, 48, 4, 86, 8, 106, 6),
        intArrayOf(89, 8, 38, 7, 112, 8, 98, 6, 46, 8, 60, 8, 117, 5),
        intArrayOf(66, 9, 95, 4, 41, 4, 111, 8, 43, 8, 118, 7, 62, 6, 42, 3),
        intArrayOf(117, 7, 77, 9, 69, 8, 104, 4, 53, 5, 52, 7, 53, 6, 42, 8, 62, 6),
        intArrayOf(83, 8, 68, 9, 75, 6, 77, 3, 70, 4, 30, 8, 46, 6, 95, 6, 59, 6, 80, 8),
        intArrayOf(40, 6, 52, 5, 100, 6, 81, 8, 112, 6, 85, 6, 76, 7, 71, 6, 73, 9),
        intArrayOf(93, 8, 115, 5, 80, 9, 106, 4, 41, 6, 37, 4, 37, 7, 101, 8, 44, 7, 89, 4),
        intArrayOf(109, 9, 50, 9, 66, 6, 119, 8, 45, 6, 76, 5, 48, 5, 95, 3, 80, 6, 32, 5),
    )
}

/** One ridge ready to draw: its line in px, the box its colours run across, and its breaks. */
internal class FingerprintRidge(val path: Path, val bounds: Rect, val breaks: FloatArray)

/** The print's ridges centred on [centre] (px) at dp [unit]; build them once per size. */
internal fun fingerprintRidges(centre: Offset, unit: Float): List<FingerprintRidge> = (0 until CrimeJunkieFingerprint.RIDGES).map { i ->
    val path = Path()
    CrimeJunkieFingerprint.ridge(i).forEachIndexed { k, p ->
        val x = centre.x + p.x * unit
        val y = centre.y + p.y * unit
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    FingerprintRidge(path, path.getBounds(), CrimeJunkieFingerprint.Breaks[i].map { it * unit }.toFloatArray())
}

/**
 * Draws the print centred on [centre] (px) at dp [unit]: each ridge stroked with the splatter's
 * diagonal across its own bounds, then faded with a soft circle and towards the bottom of its
 * 160dp box. Draw it in its own layer, so the fades only take the print away.
 */
internal fun DrawScope.drawFingerprint(ridges: List<FingerprintRidge>, centre: Offset, unit: Float) {
    val boxTop = centre.y - CrimeJunkieFingerprint.CENTRE_Y * unit
    val boxHeight = CrimeJunkieFingerprint.BOX_HEIGHT * unit
    // On the board the print sits in its box, so the outer arches stop at the box's top edge.
    clipRect(top = boxTop, bottom = boxTop + boxHeight) {
        ridges.forEach { ridge ->
            drawPath(
                ridge.path,
                Brush.linearGradient(
                    0f to CrimeJunkieColors.SplatMagenta,
                    0.55f to CrimeJunkieColors.SplatViolet,
                    1f to CrimeJunkieColors.SplatBlue,
                    start = ridge.bounds.topLeft,
                    end = ridge.bounds.bottomRight,
                ),
                style = Stroke(width = CrimeJunkieFingerprint.STROKE_WIDTH * unit, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(ridge.breaks)),
            )
        }
    }
    val radius = CrimeJunkieFingerprint.FADE_RADIUS * unit
    drawRect(Brush.radialGradient(0.25f to Color.White, 0.85f to Color.Transparent, center = centre, radius = radius), blendMode = BlendMode.DstIn)
    drawRect(
        Brush.verticalGradient(0.55f to Color.White, 1f to Color.Transparent, startY = boxTop, endY = boxTop + boxHeight),
        blendMode = BlendMode.DstIn,
    )
}
