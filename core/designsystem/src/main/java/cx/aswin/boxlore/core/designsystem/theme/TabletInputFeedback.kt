package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout

/** Adds tablet focus and mouse feedback without altering a target's measured size. */
fun Modifier.tabletInputFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
): Modifier = composed {
    if (!LocalBoxloreWindowLayout.current.usesTabletLayout) return@composed this
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val hover = animateFloatAsState(if (enabled && hovered) 0.08f else 0f, ExpressiveMotion.QuickSpring, label = "tabletHover")
    val color = MaterialTheme.colorScheme.primary
    hoverable(interactionSource, enabled).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, color, alpha = hover.value)
            if (enabled && focused) drawOutline(outline, color, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Secondary click invokes precisely the existing long-press action, including its guards. */
private fun Modifier.tabletSecondaryClick(enabled: Boolean, onLongClick: (() -> Unit)?): Modifier = composed {
    if (!LocalBoxloreWindowLayout.current.usesTabletLayout || onLongClick == null) return@composed this
    val action by rememberUpdatedState(onLongClick)
    pointerInput(enabled) {
        if (enabled) detectTabletSecondaryClicks { action() }
    }
}

private suspend fun PointerInputScope.detectTabletSecondaryClicks(action: () -> Unit) = awaitPointerEventScope {
    val gesture = TabletSecondaryClickGesture()
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val handling = gesture.update(
            isPress = event.type == PointerEventType.Press,
            secondaryPressed = event.buttons.isSecondaryPressed,
            consumed = event.changes.any { it.isConsumed },
        )
        if (handling == SecondaryClickHandling.InvokeAndConsume) action()
        if (handling != SecondaryClickHandling.Ignore) event.changes.forEach { it.consume() }
    }
}

internal enum class SecondaryClickHandling { Ignore, Consume, InvokeAndConsume }

/** One pointer-input coroutine owns one gesture; cancellation drops its ownership. */
internal class TabletSecondaryClickGesture {
    private var active = false

    fun update(isPress: Boolean, secondaryPressed: Boolean, consumed: Boolean): SecondaryClickHandling {
        if (!active) {
            if (!isPress || !secondaryPressed || consumed) return SecondaryClickHandling.Ignore
            active = true
            return SecondaryClickHandling.InvokeAndConsume
        }
        active = secondaryPressed
        return SecondaryClickHandling.Consume
    }
}

/** Optional native click behavior shared by implicit and explicit interaction sources. */
@Immutable
data class BoxloreClickOptions(
    val role: Role? = null,
    val onDoubleClick: (() -> Unit)? = null,
    val hapticFeedbackEnabled: Boolean = true,
)

/** A supplied instance preserves explicit null source/indication values. */
@Stable
data class BoxloreClickInteraction(
    val interactionSource: MutableInteractionSource?,
    val indication: Indication?,
)

fun Modifier.boxloreCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    options: BoxloreClickOptions = BoxloreClickOptions(),
    interaction: BoxloreClickInteraction? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val nativeInteraction = interaction ?: if (LocalBoxloreWindowLayout.current.usesTabletLayout) {
        BoxloreClickInteraction(remember { MutableInteractionSource() }, LocalIndication.current)
    } else {
        null
    }
    if (nativeInteraction == null) {
        combinedClickable(enabled, onClickLabel, options.role, onLongClickLabel, onLongClick, options.onDoubleClick, options.hapticFeedbackEnabled, onClick)
    } else {
        combinedClickable(
            nativeInteraction.interactionSource, nativeInteraction.indication, enabled, onClickLabel, options.role,
            onLongClickLabel, onLongClick, options.onDoubleClick, options.hapticFeedbackEnabled, onClick,
        ).tabletSecondaryClick(enabled, onLongClick)
    }
}
