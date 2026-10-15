package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreFoldSafeViewport
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion

private class DialogPanelCoordinates(var value: LayoutCoordinates? = null)

/** Native phone hosting; tablet portals retain owner content inside an inset-aware 640dp frame. */
@Composable
@Suppress("LongParameterList") // Dialog owner policy and test tag are independent of content.
fun BoxloreDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    maxWidth: Dp = 640.dp,
    scrimColor: Color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
    panelTag: String = "boxlore_dialog_panel",
    content: @Composable () -> Unit,
) {
    if (!LocalBoxloreWindowLayout.current.usesTabletLayout) {
        Dialog(onDismissRequest, properties, content)
        return
    }
    Dialog(
        onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        val panel = remember { DialogPanelCoordinates() }
        val appearance = remember { Animatable(0.96f) }
        LaunchedEffect(Unit) { appearance.animateTo(1f, ExpressiveMotion.QuickSpring) }
        Box(
            Modifier.fillMaxSize().background(scrimColor).pointerInput(properties.dismissOnClickOutside, onDismissRequest) {
                detectTapGestures { position ->
                    val bounds = panel.value?.takeIf { it.isAttached }?.boundsInRoot()
                    val outsidePanel = bounds != null && !bounds.contains(position)
                    if (properties.dismissOnClickOutside && outsidePanel) onDismissRequest()
                }
            },
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().boxloreFoldSafeViewport().safeDrawingPadding().imePadding().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.widthIn(max = minOf(maxWidth, 640.dp)).fillMaxWidth().heightIn(max = maxHeight * 0.9f)
                        .graphicsLayer {
                            scaleX = appearance.value
                            scaleY = appearance.value
                            alpha = ((appearance.value - 0.96f) / 0.04f).coerceIn(0f, 1f)
                        }.clip(MaterialTheme.shapes.extraLarge).onGloballyPositioned { panel.value = it }.testTag(panelTag),
                    propagateMinConstraints = true,
                    content = { content() },
                )
            }
        }
    }
}
