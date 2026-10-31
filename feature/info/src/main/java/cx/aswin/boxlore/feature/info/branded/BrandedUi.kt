package cx.aswin.boxlore.feature.info.branded

import android.graphics.Paint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily
import kotlin.math.roundToInt
import kotlin.math.tan

/** Even half-leading above and below each line, the way the design boards set type. */
internal val BrandedLineHeight = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

/** Text in [family] at an exact size and line height. */
internal fun brandedTextStyle(family: FontFamily, size: TextUnit, lineHeight: TextUnit, letterSpacing: TextUnit = 0.sp): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    lineHeightStyle = BrandedLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** boxlore's font at [weight], with its optical size matched to the text size. */
@Composable
internal fun brandedSansStyle(size: TextUnit, lineHeight: TextUnit, weight: Int = 400): TextStyle =
    brandedTextStyle(rememberGoogleSansFamily(weight = weight, opticalSize = size.value), size, lineHeight)

/**
 * Text whose line height is tighter than its font's own height, like a display face set solid.
 * The platform still gives the first line's top and the last line's bottom the font's full
 * height; this takes that extra off evenly so the text box is lines × line height, as on the web.
 */
@Composable
internal fun BrandedTightText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0))
            val line = style.lineHeight.toPx()
            val excess = (placeable.height - (placeable.height / line).roundToInt() * line).roundToInt().coerceAtLeast(0)
            layout(placeable.width, placeable.height - excess) { placeable.place(0, -excess / 2) }
        },
    )
}

/** Leans everything this element draws by [degrees] about its centre, like CSS skewX. */
internal fun Modifier.skewX(degrees: Float): Modifier = drawWithContent {
    val skew = Matrix().apply { values[Matrix.SkewX] = tan(Math.toRadians(degrees.toDouble())).toFloat() }
    withTransform({
        translate(center.x, center.y)
        transform(skew)
        translate(-center.x, -center.y)
    }) { this@drawWithContent.drawContent() }
}

/**
 * A rounded rectangle in [fill] with a soft shadow sized like a CSS box-shadow: [blur] is the CSS
 * blur radius and [offsetY] its vertical offset.
 */
internal fun Modifier.boxShadow(fill: Color, color: Color, offsetY: Dp, blur: Dp, cornerRadius: Dp): Modifier = drawBehind {
    // CSS blurs by a sigma of half the radius; the platform shadow layer takes radius = (sigma - 0.5) / 0.57735.
    val sigma = blur.toPx() / 2f
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = fill.toArgb()
        setShadowLayer(radius, 0f, offsetY.toPx(), color.toArgb())
    }
    val corner = cornerRadius.toPx()
    drawContext.canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner, corner, paint)
}
