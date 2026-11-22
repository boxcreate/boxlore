package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Single-select connected [ToggleButton] row (Appearance, region, history filters, etc.).
 * Equal-width options fill the row using connected leading/middle/trailing shapes. Long or
 * translated names shrink a little (down to 11sp) and wrap to two lines with words kept whole; when
 * a name still does not fit its share of the row, the options stack as a connected list instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongParameterList") // Options, state and the look callers already pass, plus colours.
fun <T> ConnectedOptionSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: (option: T, selected: T) -> Boolean = { a, b -> a == b },
    labelStyle: TextStyle? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
) {
    require(options.isNotEmpty()) { "ConnectedOptionSelector requires at least one option" }
    val textStyle = labelStyle ?: LocalTextStyle.current
    val maxSize = if (textStyle.fontSize.isSpecified) textStyle.fontSize else DefaultLabelSize
    val autoSize = WordSafeAutoSize(min = if (maxSize.value < SelectorLabelMinSize.value) maxSize else SelectorLabelMinSize, max = maxSize)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val labels = options.map { it.second }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = remember(labels, textStyle, constraints.maxWidth, density, direction, contentPadding) {
            val spacing = with(density) { ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx() }
            val padding = with(density) { (contentPadding.calculateLeftPadding(direction) + contentPadding.calculateRightPadding(direction)).roundToPx() }
            val share = (constraints.maxWidth - spacing * (labels.size - 1)) / labels.size - padding
            constraints.hasBoundedWidth &&
                labels.any { label ->
                share <= 0 || !measurer.measure(label, textStyle.copy(fontSize = autoSize.min), maxLines = 2, constraints = Constraints(maxWidth = share)).fitsWholeWords(label)
            }
        }
        val button: @Composable (Int, Modifier, ToggleButtonShapes) -> Unit = { index, buttonModifier, shapes ->
            val (key, label) = options[index]
            ToggleButton(
                checked = isSelected(key, selected),
                onCheckedChange = { checked -> if (checked) onSelect(key) },
                modifier = buttonModifier.heightIn(min = SelectorMinHeight).semantics { role = Role.RadioButton },
                shapes = shapes,
                colors = colors,
                contentPadding = contentPadding,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    style = textStyle,
                    autoSize = autoSize,
                )
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                options.indices.forEach { index -> button(index, Modifier.fillMaxWidth(), stackedShapes(index, options.size)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                options.indices.forEach { index -> button(index, Modifier.weight(1f), rowShapes(index, options.size)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rowShapes(index: Int, count: Int): ToggleButtonShapes {
    val pressed = RoundedCornerShape(12.dp)
    val checked = ButtonGroupDefaults.connectedButtonCheckedShape
    return when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(pressedShape = pressed, checkedShape = checked)
        count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes(pressedShape = pressed, checkedShape = checked)
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes(pressedShape = pressed, checkedShape = checked)
    }
}

/** The row's connected shapes turned on their side: round at the top of the first, the bottom of the last. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun stackedShapes(index: Int, count: Int): ToggleButtonShapes {
    val outer = StackedOuterCorner
    val inner = StackedInnerCorner
    val shape = when {
        count == 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomEnd = inner, bottomStart = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomEnd = outer, bottomStart = outer)
        else -> RoundedCornerShape(inner)
    }
    return ToggleButtonShapes(shape = shape, pressedShape = RoundedCornerShape(12.dp), checkedShape = ButtonGroupDefaults.connectedButtonCheckedShape)
}

/** Every option is a full 48dp touch target, however short its label. */
private val SelectorMinHeight = 48.dp
private val SelectorLabelMinSize = 11.sp
private val DefaultLabelSize = 14.sp
private val StackedOuterCorner = 20.dp
private val StackedInnerCorner = 4.dp
