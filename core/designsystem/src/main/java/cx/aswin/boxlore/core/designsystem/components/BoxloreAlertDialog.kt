package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions

/**
 * Material [AlertDialog] with the same slots and defaults. Phones and unfolded tablets get the
 * native Material dialog; a folded tablet hosts the dialog in one fold-safe region instead of
 * across the hinge.
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
    if (!window.usesTabletLayout || window.foldSafeRegions().size == 1) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
        return
    }
    val dialogTitle = stringResource(androidx.compose.material3.R.string.m3c_dialog)
    BoxloreDialog(onDismissRequest, properties, maxWidth = DialogMaxWidth) {
        Box(
            modifier.sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth).semantics { paneTitle = dialogTitle },
            propagateMinConstraints = true,
        ) {
            AlertDialogPanel(
                confirmButton = confirmButton,
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = text,
                style = AlertDialogPanelStyle(shape, containerColor, tonalElevation, iconContentColor, titleContentColor, textContentColor),
            )
        }
    }
}

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
private fun AlertDialogPanel(
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    style: AlertDialogPanelStyle,
) {
    val typography = MaterialTheme.typography
    Surface(shape = style.shape, color = style.containerColor, tonalElevation = style.tonalElevation) {
        Column(Modifier.padding(24.dp)) {
            icon?.let {
                CompositionLocalProvider(LocalContentColor provides style.iconColor) {
                    Box(Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)) { it() }
                }
            }
            title?.let {
                ProvideColorAndStyle(style.titleColor, typography.headlineSmall) {
                    Box(Modifier.padding(bottom = 16.dp).align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)) { it() }
                }
            }
            text?.let {
                ProvideColorAndStyle(style.textColor, typography.bodyMedium) {
                    Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp).align(Alignment.Start)) { it() }
                }
            }
            Box(Modifier.align(Alignment.End)) {
                ProvideColorAndStyle(MaterialTheme.colorScheme.primary, typography.labelLarge) {
                    DialogButtons(confirmButton, dismissButton)
                }
            }
        }
    }
}

/** Confirm sits after dismiss in a row and above it when the buttons wrap, as in Material. */
@Composable
private fun DialogButtons(confirmButton: @Composable () -> Unit, dismissButton: @Composable (() -> Unit)?) {
    val direction = LocalLayoutDirection.current
    val flipped = if (direction == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr
    // Touch-target padding already separates stacked buttons, so only the remainder is added.
    val touchPadding = LocalMinimumInteractiveComponentSize.current.takeOrElse { 0.dp } - ButtonDefaults.MinHeight
    val stackedSpacing = (8.dp - touchPadding).coerceIn(0.dp, 8.dp)
    CompositionLocalProvider(LocalLayoutDirection provides flipped) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(stackedSpacing)) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                confirmButton()
                dismissButton?.invoke()
            }
        }
    }
}

@Composable
private fun ProvideColorAndStyle(color: Color, style: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides color) { ProvideTextStyle(style, content) }
}

private val DialogMinWidth = 280.dp
private val DialogMaxWidth = 560.dp
