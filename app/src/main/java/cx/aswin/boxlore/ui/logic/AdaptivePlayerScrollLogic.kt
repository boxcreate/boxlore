package cx.aswin.boxlore.ui.logic

import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import kotlin.math.abs

internal fun canUseAdaptivePlayer(
    style: NavigationStyle,
    navigationVisible: Boolean,
    hasEpisode: Boolean,
    suppressed: Boolean,
    widthDp: Float,
): Boolean = style == NavigationStyle.Floating &&
    navigationVisible &&
    hasEpisode &&
    !suppressed &&
    widthDp >= 320f

/** Include descendant header pre-consumption while excluding unconsumed edge overscroll. */
internal fun browsingConsumedDelta(preAvailable: Float?, childConsumed: Float, postAvailable: Float): Float =
    if (preAvailable != null) preAvailable - postAvailable else childConsumed

/** Accumulates deliberate browsing travel; fling, overscroll and player gestures do not change chrome. */
internal class AdaptivePlayerScrollLogic(private val threshold: Float) {
    var isCompact: Boolean = false
        private set
    private var travel = 0f

    init {
        require(threshold.isFinite() && threshold > 0f)
    }

    fun onScroll(
        consumedX: Float,
        consumedY: Float,
        userInput: Boolean,
        enabled: Boolean,
        playerBusy: Boolean,
    ): Boolean {
        if (!enabled) {
            reset()
            return isCompact
        }
        if (playerBusy) {
            endGesture()
            return isCompact
        }
        if (!userInput || !consumedX.isFinite() || !consumedY.isFinite()) {
            return isCompact
        }
        if (consumedY == 0f || abs(consumedY) <= abs(consumedX)) {
            return isCompact
        }
        if (travel != 0f && (consumedY < 0f) != (travel < 0f)) {
            travel = 0f
        }
        travel += consumedY
        if (abs(travel) >= threshold) {
            isCompact = travel < 0f
            travel = 0f
        }
        return isCompact
    }

    fun endGesture() {
        travel = 0f
    }

    fun reset() {
        isCompact = false
        endGesture()
    }
}
