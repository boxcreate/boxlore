package cx.aswin.boxcast.feature.player.v2.logic

import kotlin.math.abs

internal enum class ConfirmationVisibility {
    SHOW,
    HIDE,
    UNCHANGED
}

internal fun confirmationVisibility(
    offset: Float,
    threshold: Float,
    currentlyVisible: Boolean
): ConfirmationVisibility = when {
    !currentlyVisible && abs(offset) > threshold * 0.5f -> ConfirmationVisibility.SHOW
    currentlyVisible && abs(offset) < threshold * 0.3f -> ConfirmationVisibility.HIDE
    else -> ConfirmationVisibility.UNCHANGED
}

internal fun shouldConfirmDismiss(offset: Float, threshold: Float): Boolean =
    abs(offset) > threshold

internal fun dismissDirection(offset: Float): Int = if (offset < 0f) -1 else 1

internal fun confirmationTarget(offset: Float, threshold: Float): Float =
    dismissDirection(offset) * threshold * 1.5f
