package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Single-select connected [ToggleButton] row (Appearance, region, history filters, etc.).
 * Equal-width options fill the row using connected leading/middle/trailing shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedOptionSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: (option: T, selected: T) -> Boolean = { a, b -> a == b },
    labelStyle: TextStyle? = null,
) {
    require(options.isNotEmpty()) { "ConnectedOptionSelector requires at least one option" }
    val roundedPressShape = RoundedCornerShape(12.dp)
    val checkedShape = ButtonGroupDefaults.connectedButtonCheckedShape
    val textStyle = labelStyle ?: LocalTextStyle.current

    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (key, label) ->
            ToggleButton(
                checked = isSelected(key, selected),
                onCheckedChange = { checked -> if (checked) onSelect(key) },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 ->
                            ButtonGroupDefaults.connectedLeadingButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                        options.lastIndex ->
                            ButtonGroupDefaults.connectedTrailingButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                        else ->
                            ButtonGroupDefaults.connectedMiddleButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                    },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = textStyle,
                )
            }
        }
    }
}
