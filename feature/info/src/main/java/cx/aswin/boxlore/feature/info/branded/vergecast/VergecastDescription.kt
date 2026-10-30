package cx.aswin.boxlore.feature.info.branded.vergecast

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedDescriptionExtras
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import kotlin.math.tan

/**
 * The show description as a Verge-style dek in a subtle, sharp-cornered panel: brighter text beside
 * a sharp bar leaning like the tag.
 */
@Composable
internal fun VergecastDescription(description: BrandedDescription, modifier: Modifier) {
    val palette = vergecastPalette()
    Column(
        modifier
            .padding(top = 16.dp)
            .clip(PanelShape)
            .background(palette.card)
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .expressiveClickable(shape = PanelShape) { description.onToggle() }
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        if (description.text.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().drawBehind { drawLeaningBar(palette.accentOnPage) }) {
                Text(
                    text = description.text,
                    style = brandedSansStyle(15.sp, 21.sp),
                    color = palette.chipText,
                    maxLines = if (description.expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 15.dp),
                )
            }
        }
        BrandedDescriptionExtras(description)
    }
}

private val PanelShape = RoundedCornerShape(4.dp)

/**
 * A 4dp bar beside the first two lines, 2dp in from their top and bottom, leaning 8° about its
 * centre like the tag. Opened text runs on below it, so the bar keeps its length and lean.
 */
private fun DrawScope.drawLeaningBar(color: Color) {
    val inset = 2.dp.toPx()
    val halfWidth = 2.dp.toPx()
    val centreX = 5.dp.toPx()
    val top = inset
    val bottom = minOf(size.height, 42.dp.toPx()) - inset
    val lean = (bottom - top) / 2f * tan(Math.toRadians(8.0)).toFloat()
    val bar = Path().apply {
        moveTo(centreX - halfWidth + lean, top)
        lineTo(centreX + halfWidth + lean, top)
        lineTo(centreX + halfWidth - lean, bottom)
        lineTo(centreX - halfWidth - lean, bottom)
        close()
    }
    drawPath(bar, color)
}
