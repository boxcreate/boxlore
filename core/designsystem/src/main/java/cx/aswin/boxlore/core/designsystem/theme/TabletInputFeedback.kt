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
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            var secondaryGesture = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val secondaryPress = event.type == PointerEventType.Press && event.buttons.isSecondaryPressed && !secondaryGesture
                if (secondaryPress && event.changes.none { it.isConsumed }) {
                    secondaryGesture = true
                    action()
                }
                if (secondaryGesture) {
                    event.changes.forEach { it.consume() }
                    if (!event.buttons.isSecondaryPressed) secondaryGesture = false
                }
            }
        }
    }
}

@Suppress("LongParameterList") // Mirrors Foundation's public click contract.
fun Modifier.boxloreCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    hapticFeedbackEnabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    if (!LocalBoxloreWindowLayout.current.usesTabletLayout) {
        combinedClickable(enabled, onClickLabel, role, onLongClickLabel, onLongClick, onDoubleClick, hapticFeedbackEnabled, onClick)
    } else {
        boxloreCombinedClickable(remember { MutableInteractionSource() }, LocalIndication.current, enabled, onClickLabel, role, onLongClickLabel, onLongClick, onDoubleClick, hapticFeedbackEnabled, onClick)
    }
}

@Suppress("LongParameterList") // Mirrors Foundation's explicit interaction-source overload.
fun Modifier.boxloreCombinedClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    hapticFeedbackEnabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = combinedClickable(
    interactionSource, indication, enabled, onClickLabel, role, onLongClickLabel, onLongClick, onDoubleClick, hapticFeedbackEnabled, onClick,
).tabletSecondaryClick(enabled, onLongClick)
