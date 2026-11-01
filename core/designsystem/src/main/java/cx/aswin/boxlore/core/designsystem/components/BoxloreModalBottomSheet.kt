package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions

/** Whether [BoxloreModalBottomSheet] shows as a centered dialog here (expanded or folded tablet windows) rather than a sheet. */
@Composable
fun boxloreSheetIsCentered(): Boolean {
    val window = LocalBoxloreWindowLayout.current
    return window.usesTabletLayout && (window.widthClass >= BoxloreWindowWidth.Expanded || window.foldSafeRegions().size > 1)
}

/** Native phone sheet; a bounded, inset-aware dialog on expanded tablet windows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // Source-compatible adapter for Material's sheet parameters.
fun BoxloreModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    centeredDismissAllowed: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!boxloreSheetIsCentered()) {
        ModalBottomSheet(
            onDismissRequest, modifier, sheetState, sheetMaxWidth, sheetGesturesEnabled, shape,
            containerColor, contentColor, tonalElevation, scrimColor, dragHandle, contentWindowInsets, properties, content,
        )
    } else {
        ExpandedSheetDialog(onDismissRequest, modifier, sheetMaxWidth, containerColor, contentColor, tonalElevation, scrimColor, contentWindowInsets, properties, centeredDismissAllowed, content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // Retain owner colors, insets and dismissal policy.
private fun ExpandedSheetDialog(
    dismiss: () -> Unit,
    modifier: Modifier,
    maxWidth: Dp,
    containerColor: Color,
    contentColor: Color,
    elevation: Dp,
    scrimColor: Color,
    insets: @Composable () -> WindowInsets,
    properties: ModalBottomSheetProperties,
    dismissAllowed: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxloreDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissAllowed && properties.shouldDismissOnBackPress,
            dismissOnClickOutside = dismissAllowed && properties.shouldDismissOnClickOutside,
            securePolicy = properties.securePolicy,
        ),
        maxWidth = minOf(maxWidth, 640.dp),
        scrimColor = scrimColor,
        panelTag = "expanded_sheet_panel",
    ) {
        Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = containerColor, contentColor = contentColor, tonalElevation = elevation) {
            Column(Modifier.windowInsetsPadding(insets()), content = content)
        }
    }
}
