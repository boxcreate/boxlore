package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Shrinks a label in [step]s from [max] towards [min] until it fits its lines with every word
 * whole: Material's step-based size accepts a long word split across two lines, which reads as a
 * broken word. At [min] the label keeps that size and its overflow (an ellipsis) is the last resort.
 */
internal data class WordSafeAutoSize(val min: TextUnit, val max: TextUnit, val step: TextUnit = 0.5.sp) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit =
        fontSizeFor(text.text) { size -> performLayout(constraints, text, size) }

    /** The size this picks for [text], laying it out with [layout]: for measuring a label ahead of drawing it. */
    fun fontSizeFor(text: String, layout: (TextUnit) -> TextLayoutResult): TextUnit {
        var size = max.value
        while (size > min.value) {
            if (layout(size.sp).fitsWholeWords(text)) return size.sp
            size -= step.value
        }
        return min
    }
}

/** True when nothing is cut or shortened and every line but the last ends between words. */
internal fun TextLayoutResult.fitsWholeWords(text: String): Boolean {
    if (hasVisualOverflow || (lineCount > 0 && isLineEllipsized(lineCount - 1))) return false
    return (0 until lineCount - 1).all { line -> endsBetweenWords(text, getLineEnd(line)) }
}

private fun endsBetweenWords(text: String, end: Int): Boolean {
    if (end <= 0 || end >= text.length) return true
    val before = text[end - 1]
    return before.isWhitespace() || before == '-' || before == '/' || text[end].isWhitespace()
}
