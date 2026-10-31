package cx.aswin.boxlore.feature.info.branded

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.feature.info.R

/** The mark's width for its height: the B with the microphone is 82 by 64. */
private const val MARK_ASPECT = 82f / 64f

/** How far the tag's pointed end reaches in from its start. */
internal val BrandedTagPoint = 9.dp

/** The hole punched at the base of the point. */
internal val BrandedTagHoleRadius = 2.5.dp

/** Room from the tag's start to the mark: past the point and the hole, with a little air. */
internal val BrandedTagStartPadding = 17.dp

/** A soft shadow under a tag, sized like a CSS box-shadow: [blur] is the CSS blur radius. */
internal data class BrandedTagShadow(val color: Color, val offsetY: Dp, val blur: Dp)

/** One show's tag: its colours, its ring or shadow, and how its far end is rounded. */
internal data class BrandedTagStyle(
    val fill: Color,
    val mark: Color,
    val text: Color,
    val textStyle: TextStyle,
    val height: Dp = 28.dp,
    val corner: Dp = 6.dp,
    val endPadding: Dp = 12.dp,
    val markHeight: Dp = 14.dp,
    val ring: Color? = null,
    val shadow: BrandedTagShadow? = null,
)

/**
 * A show's top-bar tag, shaped like a label tag: its start cut to a point with a hole punched at the
 * point's base, in the show's colours. Inside, boxlore's mark, a line splitting the tag in two, then
 * "Made for:", which reads on into the show's name below.
 */
@Composable
internal fun BrandedTag(style: BrandedTagStyle, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(style.height)
            .brandedTagBackground(style)
            .padding(start = BrandedTagStartPadding, end = style.endPadding)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_boxlore_app_mark),
            contentDescription = stringResource(R.string.branded_tag_brand),
            tint = style.mark,
            modifier = Modifier.size(width = style.markHeight * MARK_ASPECT, height = style.markHeight),
        )
        Spacer(Modifier.width(7.dp))
        // The split runs the tag's full height, so the mark and the words sit in two parts of one tag.
        Box(Modifier.width(1.dp).fillMaxHeight().background(style.text.copy(alpha = BRANDED_TAG_SPLIT_ALPHA)))
        Spacer(Modifier.width(7.dp))
        Text(stringResource(R.string.branded_pick_tag), style = style.textStyle, color = style.text, maxLines = 1)
    }
}

/** The split line is the text's colour, faded: a divider, not another word. */
internal const val BRANDED_TAG_SPLIT_ALPHA = 0.35f

private fun Modifier.brandedTagBackground(style: BrandedTagStyle): Modifier = drawBehind {
    val path = brandedTagPath(size, layoutDirection, BrandedTagPoint.toPx(), BrandedTagHoleRadius.toPx(), style.corner.toPx())
    style.shadow?.let {
        // CSS blurs by a sigma of half the radius; the platform shadow layer takes radius = (sigma - 0.5) / 0.57735.
        val radius = ((it.blur.toPx() / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.fill.toArgb()
            setShadowLayer(radius, 0f, it.offsetY.toPx(), it.color.toArgb())
        }
        drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
    drawPath(path, style.fill)
    // The ring sits just inside the edge, and around the hole too.
    style.ring?.let { ring -> clipPath(path) { drawPath(path, ring, style = Stroke(2.dp.toPx())) } }
}

/** Where the hole sits: at the point's base, halfway down, on the start side in either direction. */
internal fun brandedTagHoleCentre(size: Size, layoutDirection: LayoutDirection, point: Float): Offset =
    Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - point else point, size.height / 2f)

/** The tag's outline with the hole cut through it: pointed at the start, the far corners rounded by [corner]. */
internal fun brandedTagPath(size: Size, layoutDirection: LayoutDirection, point: Float, holeRadius: Float, corner: Float): Path {
    val rtl = layoutDirection == LayoutDirection.Rtl
    fun x(value: Float) = if (rtl) size.width - value else value
    val w = size.width
    val h = size.height
    val r = corner.coerceAtMost(h / 2f)
    return Path().apply {
        fillType = PathFillType.EvenOdd
        moveTo(x(0f), h / 2f)
        lineTo(x(point), 0f)
        lineTo(x(w - r), 0f)
        quadraticTo(x(w), 0f, x(w), r)
        lineTo(x(w), h - r)
        quadraticTo(x(w), h, x(w - r), h)
        lineTo(x(point), h)
        close()
        addOval(Rect(brandedTagHoleCentre(size, layoutDirection, point), holeRadius))
    }
}
