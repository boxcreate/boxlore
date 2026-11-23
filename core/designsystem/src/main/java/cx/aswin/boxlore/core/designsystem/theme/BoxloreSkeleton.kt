package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Skeleton colours that read on any surface in light, dark and Pure. */
object BoxloreSkeletonDefaults {
    val baseColor: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
}

/**
 * One loading block. It follows the surrounding [ShimmerScope]'s clock, read only while drawing, so
 * a screen of blocks adds no recomposition per frame. Size it with [modifier].
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    baseColor: Color = BoxloreSkeletonDefaults.baseColor,
    highlightColor: Color = BoxloreSkeletonDefaults.highlightColor,
) {
    Box(modifier.m3Shimmer(baseColor, highlightColor, shape = shape))
}

/**
 * Placeholder lines for text in [style]. The whole block is as tall as [lines] lines of real text in
 * that style at the current density and font size, so a loading screen keeps its shape when the text
 * arrives. [widths] are fractions of the available width, one per line; the last repeats when there
 * are more lines than widths.
 */
@Composable
fun SkeletonTextLines(
    style: TextStyle,
    lines: Int,
    modifier: Modifier = Modifier,
    widths: List<Float> = listOf(0.9f, 0.6f),
    baseColor: Color = BoxloreSkeletonDefaults.baseColor,
    highlightColor: Color = BoxloreSkeletonDefaults.highlightColor,
) {
    val count = lines.coerceAtLeast(1)
    val barHeight = with(LocalDensity.current) { skeletonBarHeight(style) }
    Box(modifier.fillMaxWidth()) {
        // Blank lines of the real style give the block the loaded text's exact height.
        Text(
            text = "\n".repeat(count - 1), // i18n-ignore
            style = style,
            color = Color.Transparent,
            maxLines = count,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(Modifier.matchParentSize()) {
            repeat(count) { line ->
                val fraction = (widths.getOrNull(line) ?: widths.lastOrNull() ?: 1f).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                    SkeletonBlock(Modifier.fillMaxWidth(fraction).height(barHeight), baseColor = baseColor, highlightColor = highlightColor)
                }
            }
        }
    }
}

/**
 * One bar's height for [style]: three quarters of its font size, never taller than its line. An
 * unset font size is `Text`'s default, and an em size or line height is relative to the font size.
 */
internal fun Density.skeletonBarHeight(style: TextStyle): Dp {
    val fontSize = when {
        style.fontSize.isSp -> style.fontSize.toDp()
        style.fontSize.isEm -> DefaultTextSize.toDp() * style.fontSize.value
        else -> DefaultTextSize.toDp()
    }
    val line = when {
        style.lineHeight.isSp -> style.lineHeight.toDp()
        style.lineHeight.isEm -> fontSize * style.lineHeight.value
        else -> fontSize
    }
    return (fontSize * SkeletonBarToFontSize).coerceAtMost(line)
}

private const val SkeletonBarToFontSize = 0.75f
private val DefaultTextSize = 14.sp
