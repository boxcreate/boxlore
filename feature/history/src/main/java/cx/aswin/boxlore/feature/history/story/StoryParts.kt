package cx.aswin.boxlore.feature.history.story

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.historyTextStyle
import cx.aswin.boxlore.feature.history.insights.DayPart
import cx.aswin.boxlore.feature.history.insights.InsightsWording
import cx.aswin.boxlore.feature.history.insights.PartShare
import cx.aswin.boxlore.feature.history.insights.partName
import kotlin.math.sqrt

/** The story boards' type: sizes in frame units (the frame scales as a whole, so text never reflows). */
internal object StoryType {
    @Composable
    fun of(size: Float, line: Float? = null, weight: FontWeight = GoogleSansWeight.regular, tracking: Float = 0f): TextStyle = historyTextStyle(size, line, weight, tracking)

    /** "Your month in listening": 14/20 at 85%. */
    @Composable
    fun eyebrow() = of(14f, 20f, GoogleSansWeight.semiBold)

    @Composable
    fun bubble() = of(14f, 19f, GoogleSansWeight.semiBold)
}

/**
 * A one-line headline in a box of the board's [line] height with the text centred in it, as the
 * boards' line boxes do even where the line is shorter than the type (14 h at 112/108).
 */
internal fun Modifier.storyLine(line: Float): Modifier = height(line.dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true)

/**
 * Echo's speech bubble: 18dp corners with one 4dp corner pointing at Echo, at most [maxWidth] as the
 * boards draw it. The app's Google Sans sets a little wider than the boards' preview font, so the
 * bubble gets [BUBBLE_ROOM] more, for its lines to break where the boards' do.
 */
@Composable
internal fun StoryBubble(text: String, background: Color, color: Color, shape: RoundedCornerShape, maxWidth: Float, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = StoryType.bubble(),
        color = color,
        modifier = modifier.widthIn(max = (maxWidth * BUBBLE_ROOM).dp).clip(shape).background(background).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private const val BUBBLE_ROOM = 1.12f

internal object StoryBubbles {
    /** Tail at the bottom left (Echo below left). */
    val TailBottomStart = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)

    /** Tail at the bottom right (Echo below right). */
    val TailBottomEnd = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
}

/**
 * The boards' soft blob: an ellipse-cornered square whose corners have uneven radii, as a share of
 * its width ([horizontal], from the top-left, clockwise) and height ([vertical]).
 */
internal class StoryBlobShape(private val horizontal: List<Float>, private val vertical: List<Float>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val tlX = horizontal[0] * w
        val trX = horizontal[1] * w
        val brX = horizontal[2] * w
        val blX = horizontal[3] * w
        val tlY = vertical[0] * h
        val trY = vertical[1] * h
        val brY = vertical[2] * h
        val blY = vertical[3] * h
        val path = Path().apply {
            moveTo(tlX, 0f)
            lineTo(w - trX, 0f)
            arcTo(Rect(w - 2 * trX, 0f, w, 2 * trY), -90f, 90f, false)
            lineTo(w, h - brY)
            arcTo(Rect(w - 2 * brX, h - 2 * brY, w, h), 0f, 90f, false)
            lineTo(blX, h)
            arcTo(Rect(0f, h - 2 * blY, 2 * blX, h), 90f, 90f, false)
            lineTo(0f, tlY)
            arcTo(Rect(0f, 0f, 2 * tlX, 2 * tlY), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }

    companion object {
        /** 44% 56% 48% 52% / 50% 46% 54% 50%: the story cards' blobs. */
        val Card = StoryBlobShape(listOf(0.44f, 0.56f, 0.48f, 0.52f), listOf(0.50f, 0.46f, 0.54f, 0.50f))

        /** 46% 54% 44% 56% / 52% 46% 54% 48%: the Insights story card's and the earlier stories' blobs. */
        val Entry = StoryBlobShape(listOf(0.46f, 0.54f, 0.44f, 0.56f), listOf(0.52f, 0.46f, 0.54f, 0.48f))
    }
}

/** Where the boards put a show's artwork: [size] square with [corner] corners at [x], [y], turned [rotation] degrees, with a flat [shadow] below when set. */
internal data class StoryCoverSpot(val x: Float, val y: Float, val size: Float, val corner: Float, val rotation: Float = 0f, val shadow: Float = 0f)

/** A show's artwork at [spot]; [motion] is its entrance. */
@Composable
internal fun StoryCover(url: String?, spot: StoryCoverSpot, modifier: Modifier = Modifier, motion: Modifier = Modifier) {
    val (x, y, size) = spot
    val corner = spot.corner
    val rotation = spot.rotation
    val shadow = spot.shadow
    val shape = RoundedCornerShape(corner.dp)
    Box(
        modifier
            .offset(x.dp, y.dp)
            .size(size.dp)
            .then(motion)
            .graphicsLayer { rotationZ = rotation }
            .drawBehind {
                if (shadow > 0f) {
                    val outline = shape.createOutline(this.size, layoutDirection, this)
                    val path = Path().apply { addOutline(outline) }
                    path.translate(Offset(0f, shadow.dp.toPx()))
                    drawPath(path, Color.Black.copy(alpha = 0.18f))
                }
            },
    ) {
        OptimizedImage(url = url, proxyWidth = if (size > 150f) 512 else 256, contentDescription = null, modifier = Modifier.size(size.dp).clip(shape))
    }
}

/**
 * The type card's listening clock (A10): a 250dp dial (the board's 200-unit drawing at 1.25), a
 * [disc] circle with a dotted [ring], and a quarter wedge per part of the day (night top-right,
 * morning bottom-right, afternoon bottom-left, evening top-left), 64 units × √(share / largest):
 * the largest in [top], the next in [second], the rest in [rest]; [hub] dot in the middle and the
 * four hours around it in [labels].
 */
@Composable
internal fun StoryListeningClock(parts: List<PartShare>, colors: StoryClockColors, modifier: Modifier = Modifier) {
    val description = parts.map { stringResource(R.string.history_ins_part_share, partName(it.part), InsightsWording.percent(it.share)) }.joinToString()
    val label = stringResource(R.string.history_story_clock, description)
    val hours = listOf(R.string.history_clock_12am, R.string.history_clock_6am, R.string.history_clock_12pm, R.string.history_clock_6pm).map { stringResource(it) }
    val style = StoryType.of(11f * CLOCK_SCALE)
    Box(modifier.size((200 * CLOCK_SCALE).dp).clearAndSetSemantics { contentDescription = label }) {
        Canvas(Modifier.size((200 * CLOCK_SCALE).dp)) {
            val unit = size.width / 200f
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(colors.disc, 78 * unit, center)
            drawCircle(colors.ring, 78 * unit, center, style = Stroke(2 * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * unit, 7.68f * unit))))
            val largest = parts.maxOfOrNull { it.share }?.coerceAtLeast(0.0001f) ?: return@Canvas
            parts.forEachIndexed { index, share ->
                val radius = 64 * unit * sqrt(share.share / largest)
                val start = when (share.part) {
                    DayPart.NIGHT -> -90f
                    DayPart.MORNING -> 0f
                    DayPart.AFTERNOON -> 90f
                    DayPart.EVENING -> 180f
                }
                val color = when (index) {
                    0 -> colors.top
                    1 -> colors.second
                    else -> colors.rest
                }
                drawArc(color, start, 90f, useCenter = true, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2))
            }
            drawCircle(colors.hub, 5 * unit, center)
        }
        // The board's labels: centred at the top and bottom, against the right and left edges, on the middle line.
        val at = { x: Float, y: Float -> Modifier.offset((x * CLOCK_SCALE).dp, (y * CLOCK_SCALE).dp) }
        Text(hours[0], style = style.copy(textAlign = TextAlign.Center), color = colors.labels, modifier = at(70f, 0f).size((60 * CLOCK_SCALE).dp, (14 * CLOCK_SCALE).dp))
        Text(hours[1], style = style.copy(textAlign = TextAlign.End), color = colors.labels, modifier = at(136f, 92f).size((60 * CLOCK_SCALE).dp, (14 * CLOCK_SCALE).dp))
        Text(hours[2], style = style.copy(textAlign = TextAlign.Center), color = colors.labels, modifier = at(70f, 184f).size((60 * CLOCK_SCALE).dp, (14 * CLOCK_SCALE).dp))
        Text(hours[3], style = style, color = colors.labels, modifier = at(4f, 92f).size((60 * CLOCK_SCALE).dp, (14 * CLOCK_SCALE).dp))
    }
}

internal data class StoryClockColors(val disc: Color, val ring: Color, val top: Color, val second: Color, val rest: Color, val hub: Color, val labels: Color)

private const val CLOCK_SCALE = 1.25f
