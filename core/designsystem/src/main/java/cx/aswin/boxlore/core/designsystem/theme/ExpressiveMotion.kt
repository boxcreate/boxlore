package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Material 3 Expressive Motion Physics.
 *
 * Spatial motion tokens. Press feedback is scoped separately by [ExpressivePressFeedback].
 */

object ExpressiveMotion {
    // Very bouncy spring for release
    val BouncySpring = spring<Float>(
        dampingRatio = 0.45f, // Very bouncy!
        stiffness = 300f // Slow enough to see the bounce
    )

    // Quick spring for press
    val QuickSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // Formal spring for professional reveals (M3 Expressive)
    val FormalSpring = spring<Float>(
        dampingRatio = 0.8f, // Grounded, minimal bounce
        stiffness = 400f // Slightly faster for a punchy reveal
    )

    /**
     * Large spatial morphs (player sheet expand/collapse).
     *
     * Matches M3 Expressive *slow spatial* stiffness (200), but critically damped:
     * a full-screen sheet must ease into the anchor in one continuous motion —
     * underdamped bounce reads as a second animation fighting travel direction
     * (especially collapse: past mini, then jumps back up).
     */
    val SpatialLargeSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 200f
    )

    // Sleek Fade Spec (App Store style)
    val SleekFadeSpec = tween<Float>(
        durationMillis = 500,
        easing = LinearOutSlowInEasing
    )
}

/** Surfaces stay still; only custom controls opt into a small, non-bouncy response. */
enum class ExpressivePressFeedback { Surface, Control }

@Composable
private fun Modifier.expressivePressLayer(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    feedback: ExpressivePressFeedback,
    pressScaleEnabled: Boolean,
    shape: Shape?,
): Modifier {
    val clipped = if (shape == null) this else clip(shape)
    if (feedback != ExpressivePressFeedback.Control || !pressScaleEnabled) return clipped
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (enabled && pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "controlPressScale",
    )
    // Read the animation during drawing, without recomposing the control each frame.
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.then(if (shape == null) Modifier else Modifier.clip(shape))
}

/**
 * Material feedback for custom surfaces. Cards, rows and text keep their layout steady.
 * Small custom controls opt into [ExpressivePressFeedback.Control]; native Material buttons
 * already own their feedback and must not also receive this modifier.
 *
 * Pass the target's actual [shape] to bound the ripple. Explicit null [indication] retains
 * ripple-free controls. Set [pressScaleEnabled] false while a drag overlay owns scale.
 */
fun Modifier.expressiveClickable(
    enabled: Boolean = true,
    shape: Shape? = null,
    indication: Indication? = ripple(),
    pressScaleEnabled: Boolean = true,
    feedback: ExpressivePressFeedback = ExpressivePressFeedback.Surface,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .expressivePressLayer(
            interactionSource = interactionSource,
            enabled = enabled,
            feedback = feedback,
            pressScaleEnabled = pressScaleEnabled,
            shape = shape,
        )
        .clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onClick = onClick
        )
}

/** Uses the caller's interaction source for coordinated custom controls. */
fun Modifier.expressiveClickable(
    interactionSource: MutableInteractionSource?,
    enabled: Boolean = true,
    shape: Shape? = null,
    indication: Indication? = ripple(),
    pressScaleEnabled: Boolean = true,
    feedback: ExpressivePressFeedback = ExpressivePressFeedback.Surface,
    onClick: () -> Unit
): Modifier = composed {
    val localInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    this
        .expressivePressLayer(
            interactionSource = localInteractionSource,
            enabled = enabled,
            feedback = feedback,
            pressScaleEnabled = pressScaleEnabled,
            shape = shape,
        )
        .clickable(
            interactionSource = localInteractionSource,
            indication = indication,
            enabled = enabled,
            onClick = onClick
        )
}

/** Same surface/control feedback, preserving long press for selection and contextual actions. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.expressiveClickable(
    enabled: Boolean = true,
    shape: Shape? = null,
    indication: Indication? = ripple(),
    feedback: ExpressivePressFeedback = ExpressivePressFeedback.Surface,
    onLongClickLabel: String? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .expressivePressLayer(
            interactionSource = interactionSource,
            enabled = enabled,
            feedback = feedback,
            pressScaleEnabled = true,
            shape = shape,
        )
        .combinedClickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}
