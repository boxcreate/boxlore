package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

/** Enlarge only copy that fits comfortably beside the existing artwork and controls. */
internal fun measureHeroTitle(
    text: String,
    style: TextStyle,
    measurer: TextMeasurer,
    constraints: Constraints,
    availableHeight: Int,
): TextLayoutResult {
    for (size in listOf(32, 30, 28, 26)) {
        val result = measurer.measure(
            text = text,
            style = style.copy(fontSize = size.sp, lineHeight = (size + 4).sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            constraints = constraints,
        )
        if (result.lineCount <= 2 && !result.hasVisualOverflow && result.size.height <= availableHeight) return result
    }
    return measurer.measure(
        text = text,
        style = style.copy(fontSize = 24.sp, lineHeight = 28.sp),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        constraints = constraints,
    )
}
