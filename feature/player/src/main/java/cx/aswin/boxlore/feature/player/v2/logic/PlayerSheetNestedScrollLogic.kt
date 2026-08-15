package cx.aswin.boxlore.feature.player.v2.logic

/**
 * Nested-scroll handoff between full-player content and the mini/full sheet.
 *
 * A swipe that is still scrolling queue/notes must stop at the content top.
 * Collapsing the sheet requires a new downward swipe from that rest position.
 */
internal object PlayerSheetNestedScrollLogic {
    const val EXPANDED_OFFSET_EPS = 0.5f

    fun isFullyExpanded(sheetOffset: Float): Boolean = sheetOffset <= EXPANDED_OFFSET_EPS

    fun contentOwnsGestureAfterScroll(
        alreadyOwned: Boolean,
        childConsumedY: Float,
    ): Boolean = alreadyOwned || childConsumedY != 0f

    fun shouldMoveSheetOnPostScroll(
        contentOwnsGesture: Boolean,
        sheetOffset: Float,
        availableY: Float,
        sourceIsUserInput: Boolean,
    ): Boolean {
        if (!sourceIsUserInput || availableY == 0f) return false
        // Leftover downward overscroll from a content scroll must not start collapse.
        if (contentOwnsGesture && isFullyExpanded(sheetOffset) && availableY > 0f) return false
        return true
    }

    fun shouldSettleSheetOnPostFling(
        contentOwnsGesture: Boolean,
        sheetOffset: Float,
        animationRunning: Boolean,
    ): Boolean {
        if (animationRunning) return false
        if (contentOwnsGesture && isFullyExpanded(sheetOffset)) return false
        return true
    }
}
