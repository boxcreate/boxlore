package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSheetNestedScrollLogicTest {
    @Test
    fun childConsumptionLocksTheGestureToContent() {
        assertFalse(
            PlayerSheetNestedScrollLogic.contentOwnsGestureAfterScroll(
                alreadyOwned = false,
                childConsumedY = 0f,
            ),
        )
        assertTrue(
            PlayerSheetNestedScrollLogic.contentOwnsGestureAfterScroll(
                alreadyOwned = false,
                childConsumedY = 12f,
            ),
        )
        assertTrue(
            PlayerSheetNestedScrollLogic.contentOwnsGestureAfterScroll(
                alreadyOwned = true,
                childConsumedY = 0f,
            ),
        )
    }

    @Test
    fun leftoverDownwardOverscrollDoesNotCollapseWhileContentOwnsGesture() {
        assertFalse(
            PlayerSheetNestedScrollLogic.shouldMoveSheetOnPostScroll(
                contentOwnsGesture = true,
                sheetOffset = 0f,
                availableY = 80f,
                sourceIsUserInput = true,
            ),
        )
    }

    @Test
    fun downwardDragFromRestAtTopStillMovesTheSheet() {
        assertTrue(
            PlayerSheetNestedScrollLogic.shouldMoveSheetOnPostScroll(
                contentOwnsGesture = false,
                sheetOffset = 0f,
                availableY = 80f,
                sourceIsUserInput = true,
            ),
        )
    }

    @Test
    fun leftoverFlingDoesNotSettleWhileContentOwnsExpandedSheet() {
        assertFalse(
            PlayerSheetNestedScrollLogic.shouldSettleSheetOnPostFling(
                contentOwnsGesture = true,
                sheetOffset = 0f,
                animationRunning = false,
            ),
        )
    }

    @Test
    fun flingFromRestAtTopStillSettlesTheSheet() {
        assertTrue(
            PlayerSheetNestedScrollLogic.shouldSettleSheetOnPostFling(
                contentOwnsGesture = false,
                sheetOffset = 0f,
                animationRunning = false,
            ),
        )
    }

    @Test
    fun alreadyDraggedSheetStillSettlesEvenIfContentOwnedTheGesture() {
        assertTrue(
            PlayerSheetNestedScrollLogic.shouldSettleSheetOnPostFling(
                contentOwnsGesture = true,
                sheetOffset = 200f,
                animationRunning = false,
            ),
        )
    }

    @Test
    fun runningAnimationSkipsASecondSettle() {
        assertFalse(
            PlayerSheetNestedScrollLogic.shouldSettleSheetOnPostFling(
                contentOwnsGesture = false,
                sheetOffset = 80f,
                animationRunning = true,
            ),
        )
    }

    @Test
    fun nonUserInputNeverMovesTheSheet() {
        assertFalse(
            PlayerSheetNestedScrollLogic.shouldMoveSheetOnPostScroll(
                contentOwnsGesture = false,
                sheetOffset = 0f,
                availableY = 80f,
                sourceIsUserInput = false,
            ),
        )
    }
}
