package cx.aswin.boxlore.feature.info.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal enum class SubscriptionButtonPhase {
    SUBSCRIBE,
    CONFIRMATION,
    SUBSCRIBED,
}

internal fun subscriptionButtonPhase(wasSubscribed: Boolean, isSubscribed: Boolean, animationsEnabled: Boolean): SubscriptionButtonPhase = when {
    !isSubscribed -> SubscriptionButtonPhase.SUBSCRIBE
    !wasSubscribed && animationsEnabled -> SubscriptionButtonPhase.CONFIRMATION
    else -> SubscriptionButtonPhase.SUBSCRIBED
}

@Composable
internal fun rememberSubscriptionButtonPhase(isSubscribed: Boolean): SubscriptionButtonPhase {
    var phase by remember { mutableStateOf(subscriptionButtonPhase(isSubscribed, isSubscribed, animationsEnabled = false)) }
    var previous by remember { mutableStateOf(isSubscribed) }
    LaunchedEffect(isSubscribed) {
        phase = subscriptionButtonPhase(previous, isSubscribed, ValueAnimator.areAnimatorsEnabled())
        // Record the new state before suspending, so a reversal cannot replay a stale confirmation.
        previous = isSubscribed
        if (phase == SubscriptionButtonPhase.CONFIRMATION) {
            delay(800L)
            phase = SubscriptionButtonPhase.SUBSCRIBED
        }
    }
    return phase
}

internal data class SubscriptionAutomationReveal(
    val extent: Float,
    val bellAlpha: Float,
    val downloadAlpha: Float,
) {
    val fullyVisible: Boolean get() = extent == 1f
}

internal fun subscriptionAutomationReveal(fraction: Float): SubscriptionAutomationReveal {
    val extent = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return SubscriptionAutomationReveal(
        extent = extent,
        bellAlpha = ((extent - 0.08f) / 0.52f).coerceIn(0f, 1f),
        downloadAlpha = ((extent - 0.4f) / 0.55f).coerceIn(0f, 1f),
    )
}

/** A clipped, measured slot reserves exactly the space the subscription button gives up. */
@Composable
internal fun SubscriptionAutomationSlot(reveal: SubscriptionAutomationReveal, enabled: Boolean, content: @Composable () -> Unit) {
    val accessibility = if (enabled && reveal.fullyVisible) Modifier else Modifier.clearAndSetSemantics {}
    Layout(
        modifier = accessibility.clipToBounds(),
        content = {
            Row(Modifier.padding(start = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                content()
            }
        },
    ) { measurables, constraints ->
        val fullWidth = 102.dp.roundToPx()
        val height = 48.dp.roundToPx()
        val controls = measurables.single().measure(Constraints.fixed(fullWidth, height))
        val visibleWidth = constraints.constrainWidth((fullWidth * reveal.extent).roundToInt())
        layout(visibleWidth, constraints.constrainHeight(height)) {
            controls.placeRelative(0, 0)
        }
    }
}

/** Reuse the show genre mapping while retaining the original heart fallback. */
internal fun subscriptionGenreIcon(genre: String): ImageVector =
    genreIconFor(genre).takeUnless { it == Icons.Rounded.Category } ?: Icons.Rounded.Favorite

@Composable
internal fun SubscriptionGenreConfirmation(genre: String) {
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        arrival.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 450f))
    }
    Icon(
        imageVector = subscriptionGenreIcon(genre),
        contentDescription = null,
        modifier = Modifier.size(24.dp).graphicsLayer {
            // One spring keeps the small tilt and scale arrival coordinated, without a second bounce.
            scaleX = 0.78f + 0.22f * arrival.value
            scaleY = scaleX
            rotationZ = -14f * (1f - arrival.value)
        },
    )
}
