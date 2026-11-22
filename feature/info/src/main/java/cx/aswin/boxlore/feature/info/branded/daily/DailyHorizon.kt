package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * First light: the cover's dawn sky behind the top of the page, the sun's top edge peeking over the
 * middle of the horizon, and the page rising into the sky as the curve of a planet with a sunlit rim.
 * Distances are in dp from the header's top, which sits [DailyPage.headerTop] below the status bar.
 */
internal object DailyHorizon {
    /** The masthead row, the gap and the wordmark: the white lockup at the top of the header. */
    val LockupHeight: Dp = 72.dp

    /** The top of the planet's curve, in the middle of the page. */
    val Apex: Dp = 110.dp

    /** Where the page's own text starts, below the curve at every width. */
    val PageTop: Dp = 152.dp

    /** How far the curve drops by the window's edges on the 369dp design board. */
    private const val BOARD_WIDTH = 369f
    private const val BOARD_DROP = 26f

    /** The curve stays gentle on wide windows and shows on narrow ones. */
    private const val MIN_DROP = 20f
    private const val MAX_DROP = 40f

    /** The sky's colours run from this far above the apex down to this far below it; above, it is the deep blue. */
    private const val SKY_ABOVE_APEX = 214f
    private const val SKY_BELOW_APEX = 46f

    const val SUN_RADIUS = 30f

    /** How much of the sun shows over the horizon. */
    const val SUN_PEEK = 14f

    /** The halo around the sunrise, as an ellipse this wide and tall. */
    private const val HALO_WIDTH = 340f
    private const val HALO_HEIGHT = 140f

    private const val SUN_GLOW = 33f
    private const val RIM_WIDTH = 1.5f
    private const val RIM_GLOW = 12f

    /** How far the sky reaches under the curve's lowest point, past the planet's anti-aliased edge. */
    private const val SKY_UNDER_CURVE = 2f

    /** How far the curve drops at the edges of a window [width] dp wide. */
    fun drop(width: Float): Float = (width * BOARD_DROP / BOARD_WIDTH).coerceIn(MIN_DROP, MAX_DROP)

    /** The radius of the circle whose top is the apex and which drops [drop] dp across [width] dp. */
    fun radius(width: Float, drop: Float = drop(width)): Float = (width * width / 4f + drop * drop) / (2f * drop)

    /** How far below the apex the curve is, [fromCentre] dp to either side of the middle. */
    fun depth(width: Float, fromCentre: Float): Float {
        val r = radius(width)
        val x = fromCentre.coerceAtMost(r)
        return r - sqrt(r * r - x * x)
    }

    /**
     * The cover's dawn with the blue held long, so the white lockup on it stays readable, and yellow
     * reaching the horizon under the sun. Positions run from [SKY_ABOVE_APEX] above the apex to
     * [SKY_BELOW_APEX] below it.
     */
    val Sky = arrayOf(
        0f to Color(0xFF0054B7),
        0.5f to Color(0xFF006AB8),
        0.68f to Color(0xFF0084C5),
        0.76f to Color(0xFF01A2D9),
        0.82f to Color(0xFF00C2EE),
        0.855f to Color(0xFF37D0CE),
        0.88f to Color(0xFF81DD9A),
        0.905f to Color(0xFFC0E86E),
        0.93f to Color(0xFFF2F050),
        1f to Color(0xFFF7F27A),
    )

    /** The sky's colour [aboveApex] dp above the apex: what the lockup sits on. */
    fun skyAt(aboveApex: Float): Color {
        val position = ((SKY_ABOVE_APEX - aboveApex) / (SKY_ABOVE_APEX + SKY_BELOW_APEX)).coerceIn(0f, 1f)
        val after = Sky.indexOfFirst { it.first >= position }.coerceAtLeast(1)
        val (startAt, start) = Sky[after - 1]
        val (endAt, end) = Sky[after]
        val t = if (endAt == startAt) 0f else (position - startAt) / (endAt - startAt)
        return Color(
            red = start.red + (end.red - start.red) * t,
            green = start.green + (end.green - start.green) * t,
            blue = start.blue + (end.blue - start.blue) * t,
        )
    }

    private val Halo = arrayOf(
        0f to Color(0xB3FFE878),
        0.32f to Color(0x61FAE864),
        0.72f to Color(0x00F2F050),
    )

    private val Sun = arrayOf(
        0f to Color(0xFFFFFFFF),
        0.42f to Color(0xFFFFF4C0),
        1f to Color(0xFFFFD25C),
    )

    private val SunGlow = arrayOf(
        0f to Color(0x6BFFCE50),
        1f to Color(0x00FFCE50),
    )

    private val Rim = Color(0xE6FFFCDE)
    private val RimGlow = Color(0xBFFFF8C4)

    /**
     * Draws the sky from [skyTop] (above the header, behind the status bar) down to the horizon, the
     * sun peeking over its middle, and the [page]-coloured planet rising into it with a sunlit rim.
     * Below the curve it only paints the page's own colour, as far down as the sky's light reaches.
     */
    fun DrawScope.drawFirstLight(skyTop: Float, page: Color) {
        val unit = 1.dp.toPx()
        val widthDp = size.width / unit
        val apex = Apex.toPx()
        val drop = drop(widthDp) * unit
        val radius = radius(widthDp) * unit
        val centreX = size.width / 2f
        // The planet covers everything below the curve that the sky, the halo and the sun's glow reach.
        val reach = maxOf(drop / unit + RIM_GLOW, HALO_HEIGHT / 2f, 2 * SUN_RADIUS + SUN_GLOW - SUN_PEEK)
        val bottom = apex + reach * unit
        // The sky stops just under the curve's lowest point, so the opaque planet hides its edge; ending it
        // at the planet's own lower edge let a sliver of its last colour show as a line across the text.
        val skyBottom = apex + drop + SKY_UNDER_CURVE * unit
        drawRect(
            Brush.verticalGradient(*Sky, startY = apex - SKY_ABOVE_APEX * unit, endY = apex + SKY_BELOW_APEX * unit),
            topLeft = Offset(0f, skyTop),
            size = Size(size.width, skyBottom - skyTop),
        )
        // The sunrise's halo, a wide ellipse centred on the apex.
        scale(scaleX = 1f, scaleY = HALO_HEIGHT / HALO_WIDTH, pivot = Offset(centreX, apex)) {
            val r = HALO_WIDTH / 2f * unit
            drawCircle(Brush.radialGradient(*Halo, center = Offset(centreX, apex), radius = r), radius = r, center = Offset(centreX, apex))
        }
        val sunCentre = Offset(centreX, apex - SUN_PEEK * unit + SUN_RADIUS * unit)
        val glow = (SUN_RADIUS + SUN_GLOW) * unit
        drawCircle(
            Brush.radialGradient(0f to SunGlow[0].second, SUN_RADIUS / (SUN_RADIUS + SUN_GLOW) to SunGlow[0].second, 1f to SunGlow[1].second, center = sunCentre, radius = glow),
            radius = glow,
            center = sunCentre,
        )
        // Lit from just below its middle, as on the board: white at the core, gold at the edge.
        val light = Offset(centreX, sunCentre.y + SUN_RADIUS * unit * 0.4f)
        drawCircle(Brush.radialGradient(*Sun, center = light, radius = SUN_RADIUS * unit * 1.72f), radius = SUN_RADIUS * unit, center = sunCentre)
        clipRect(top = skyTop, bottom = bottom) {
            val planet = Offset(centreX, apex + radius)
            val outer = radius + RIM_GLOW * unit
            drawCircle(
                Brush.radialGradient((radius - unit) / outer to RimGlow, 1f to RimGlow.copy(alpha = 0f), center = planet, radius = outer),
                radius = outer,
                center = planet,
            )
            drawCircle(page, radius = radius, center = planet)
            drawCircle(Rim, radius = radius, center = planet, style = Stroke(RIM_WIDTH * unit))
        }
    }
}
