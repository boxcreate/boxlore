package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions
import kotlinx.coroutines.flow.first

/** Whether [BoxloreModalBottomSheet] shows as a centered dialog here (expanded or folded tablet windows) rather than a sheet. */
@Composable
fun boxloreSheetIsCentered(): Boolean {
    val window = LocalBoxloreWindowLayout.current
    return window.usesTabletLayout && (window.widthClass >= BoxloreWindowWidth.Expanded || window.foldSafeRegions().size > 1)
}

/**
 * Native phone sheet; a bounded, inset-aware dialog on expanded tablet windows.
 *
 * When the window crosses into the other presentation (840dp, a tablet turning, a fold half-opening)
 * the content moves into the new host instead of starting again, so what the listener did in the
 * sheet stays: switches, ticks, drafts and scroll, held in `remember` or `rememberSaveable`. The new
 * host opens first, the content moves across in one frame, then the old host closes. Neither
 * [onDismissRequest] nor a second page view happens during the switch.
 */
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
    val handoff = rememberSheetHandoff(boxloreSheetIsCentered())
    val latestContent = rememberUpdatedState(content)
    // One content owner for both hosts. Moving it keeps every remembered value, saveable or not.
    val retained = remember { movableContentWithReceiverOf<ColumnScope> { latestContent.value(this) } }
    if (SheetHost.Sheet in handoff.hosts) {
        ModalBottomSheet(
            onDismissRequest, modifier, sheetState, sheetMaxWidth, sheetGesturesEnabled, shape,
            containerColor, contentColor, tonalElevation, scrimColor, dragHandle, contentWindowInsets, properties,
        ) { handoff.Slot(SheetHost.Sheet, retained) }
    }
    if (SheetHost.Centered in handoff.hosts) {
        ExpandedSheetDialog(
            onDismissRequest, modifier, sheetMaxWidth, containerColor, contentColor, tonalElevation, scrimColor,
            contentWindowInsets, properties, centeredDismissAllowed,
        ) { handoff.Slot(SheetHost.Centered, retained) }
    }
}

internal enum class SheetHost { Sheet, Centered }

/**
 * Which hosts are open and which one draws the content. Each host is its own window, so the content
 * can move only between two windows that are both open: the new host opens holding a blank of the
 * content's height (so a phone sheet settles where it would with the content in it), reports that
 * it is ready, takes the content in the next frame, and then the old host closes.
 */
@Stable
private class SheetHandoff(initial: SheetHost) {
    var owner by mutableStateOf(initial)
    var hosts by mutableStateOf(setOf(initial))
    val ready = mutableStateMapOf<SheetHost, Boolean>()
    var contentHeightPx by mutableIntStateOf(0)

    @Composable
    fun Slot(host: SheetHost, content: @Composable ColumnScope.() -> Unit) {
        DisposableEffect(host) {
            ready[host] = true
            onDispose { ready.remove(host) }
        }
        val owns = owner == host
        Column(Modifier.fillMaxWidth().onSizeChanged { if (owns) contentHeightPx = it.height }) {
            if (owns) {
                content()
            } else {
                Spacer(Modifier.height(with(LocalDensity.current) { contentHeightPx.toDp() }))
            }
        }
    }
}

@Composable
private fun rememberSheetHandoff(centered: Boolean): SheetHandoff {
    val target = if (centered) SheetHost.Centered else SheetHost.Sheet
    val handoff = remember { SheetHandoff(target) }
    LaunchedEffect(target) {
        if (handoff.owner != target) {
            handoff.hosts = handoff.hosts + target
            snapshotFlow { handoff.ready[target] == true }.first { it }
            handoff.owner = target
            withFrameNanos { }
        }
        handoff.hosts = setOf(target)
    }
    return handoff
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
