package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions

/**
 * Material's alert dialog with the same slots and defaults, drawn as Material draws it. Phones and
 * unfolded tablets get native dialog hosting; a folded tablet hosts it in one fold-safe region
 * instead of across the hinge. Both use one window, so state inside the slots survives folding.
 */
@Suppress("LongParameterList") // Source-compatible adapter for Material's alert dialog parameters.
@Composable
fun BoxloreAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val window = LocalBoxloreWindowLayout.current
    val folded = window.usesTabletLayout && window.foldSafeRegions().size != 1
    val dialogTitle = stringResource(androidx.compose.material3.R.string.m3c_dialog)
    val slots = AlertDialogSlots(confirmButton, dismissButton, icon, title, text)
    val style = AlertDialogPanelStyle(shape, containerColor, tonalElevation, iconContentColor, titleContentColor, textContentColor)
    // One window for both presentations, so the slots keep their state when the device folds.
    BoxloreDialogHost(
        onDismissRequest = onDismissRequest,
        properties = properties,
        tablet = folded,
        maxWidth = DialogMaxWidth,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        panelTag = "boxlore_dialog_panel",
    ) {
        Box(
            modifier.sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth).semantics { paneTitle = dialogTitle },
            propagateMinConstraints = true,
        ) {
            AlertDialogPanel(slots, style)
        }
    }
}

private class AlertDialogSlots(
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable (() -> Unit)?,
    val icon: @Composable (() -> Unit)?,
    val title: @Composable (() -> Unit)?,
    val text: @Composable (() -> Unit)?,
)

private class AlertDialogPanelStyle(
    val shape: Shape,
    val containerColor: Color,
    val tonalElevation: Dp,
    val iconColor: Color,
    val titleColor: Color,
    val textColor: Color,
)

/** Mirrors Material's alert dialog layout: icon, title, scrollable-weight text, then end-aligned buttons. */
@Composable
private fun AlertDialogPanel(slots: AlertDialogSlots, style: AlertDialogPanelStyle) {
    val typography = MaterialTheme.typography
    Surface(shape = style.shape, color = style.containerColor, tonalElevation = style.tonalElevation) {
        Column(Modifier.padding(24.dp)) {
            slots.icon?.let {
                CompositionLocalProvider(LocalContentColor provides style.iconColor) {
                    Box(Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)) { it() }
                }
            }
            slots.title?.let {
                ProvideColorAndStyle(style.titleColor, typography.headlineSmall) {
                    Box(Modifier.padding(bottom = 16.dp).align(if (slots.icon == null) Alignment.Start else Alignment.CenterHorizontally)) { it() }
                }
            }
            slots.text?.let {
                ProvideColorAndStyle(style.textColor, typography.bodyMedium) {
                    Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp).align(Alignment.Start)) { it() }
                }
            }
            Box(Modifier.align(Alignment.End)) {
                ProvideColorAndStyle(MaterialTheme.colorScheme.primary, typography.labelLarge) {
                    DialogButtons(slots.confirmButton, slots.dismissButton)
                }
            }
        }
    }
}

/**
 * Confirm sits after dismiss in a row, and above it at the end edge when they don't fit side by
 * side, as in Material. Stacked buttons keep [DialogButtonGap] between their visible containers: a
 * default Material button's touch target already holds part of it, a full-height boxlore button none.
 */
@Composable
private fun DialogButtons(confirmButton: @Composable () -> Unit, dismissButton: @Composable (() -> Unit)?) {
    // The room above and below a default button's container inside its touch target.
    val touchInset = ((LocalMinimumInteractiveComponentSize.current.takeOrElse { 0.dp } - ButtonDefaults.MinHeight) / 2).coerceAtLeast(0.dp)
    Layout(content = {
        confirmButton()
        dismissButton?.invoke()
    }) { measurables, constraints ->
        val gap = DialogButtonGap.roundToPx()
        val buttons = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rowWidth = buttons.sumOf { it.width } + gap * (buttons.size - 1).coerceAtLeast(0)
        if (rowWidth <= constraints.maxWidth) {
            val height = buttons.maxOfOrNull { it.height } ?: 0
            layout(rowWidth, height) {
                var end = rowWidth
                buttons.forEach { button ->
                    end -= button.width
                    button.placeRelative(end, (height - button.height) / 2)
                    end -= gap
                }
            }
        } else {
            val insets = measurables.map { if (it.layoutId == FullHeightDialogButton) 0 else touchInset.roundToPx() }
            val spacings = insets.zipWithNext { above, below -> (gap - above - below).coerceAtLeast(0) }
            val width = buttons.maxOfOrNull { it.width } ?: 0
            layout(width, buttons.sumOf { it.height } + spacings.sum()) {
                var top = 0
                buttons.forEachIndexed { index, button ->
                    button.placeRelative(width - button.width, top)
                    top += button.height + spacings.getOrElse(index) { 0 }
                }
            }
        }
    }
}

/** Marks a dialog button whose container fills its whole height, with no touch-target room around it. */
private object FullHeightDialogButton

@Composable
private fun ProvideColorAndStyle(color: Color, style: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides color) { ProvideTextStyle(style, content) }
}

/**
 * A dialog's main action: one filled pill, at least 48dp tall. A [destructive] action (one that
 * deletes or removes something for good) is filled in the error colour. Pair it with
 * [BoxloreDialogDismissButton], so the action and Cancel never look alike.
 */
@Composable
fun BoxloreDialogConfirmButton(text: String, onClick: () -> Unit, destructive: Boolean = false, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = DialogButtonMinHeight).layoutId(FullHeightDialogButton),
        colors = if (destructive) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}

/** A dialog's quiet way out (Cancel, Not now): a text button with no container. */
@Composable
fun BoxloreDialogDismissButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = DialogButtonMinHeight).layoutId(FullHeightDialogButton)) {
        Text(text, textAlign = TextAlign.Center)
    }
}

/** Which colour role a [BoxloreDialogIcon] takes: neutral, a caution, or something that destroys. */
enum class BoxloreDialogIconTone { Neutral, Caution, Destructive }

/** A dialog's icon in a 48dp circle of the [tone]'s container colour, its icon in the matching on-colour. */
@Composable
fun BoxloreDialogIcon(icon: ImageVector, tone: BoxloreDialogIconTone = BoxloreDialogIconTone.Neutral) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BoxloreDialogIconTone.Neutral -> colors.secondaryContainer to colors.onSecondaryContainer
        BoxloreDialogIconTone.Caution -> colors.tertiaryContainer to colors.onTertiaryContainer
        BoxloreDialogIconTone.Destructive -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(24.dp))
    }
}

private val DialogButtonMinHeight = 48.dp
private val DialogButtonGap = 8.dp
private val DialogMinWidth = 280.dp
private val DialogMaxWidth = 560.dp
