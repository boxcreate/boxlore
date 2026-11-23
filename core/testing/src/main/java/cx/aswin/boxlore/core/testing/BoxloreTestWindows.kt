package cx.aswin.boxlore.core.testing

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout

/**
 * Named windows for adaptive tests: phones, the Galaxy Z Fold 8 cover, a phone split-screen half,
 * the pairs either side of the 600dp, 480dp and 840dp gates, book foldables and tablets. Sizes are
 * in dp. Hinges are synthetic; they say where a real device reports one, not how it behaves.
 */
object BoxloreTestWindows {
    val PHONE = BoxloreWindowLayout(412f, 915f)
    val PHONE_SMALL = BoxloreWindowLayout(360f, 800f)
    val PHONE_LAND = BoxloreWindowLayout(915f, 412f)

    val COVER_443 = BoxloreWindowLayout(443f, 649f)
    val COVER_475 = BoxloreWindowLayout(475f, 695f)
    val COVER_LAND = BoxloreWindowLayout(695f, 475f)

    /** One half of a phone in split-screen. */
    val SPLIT_HALF = BoxloreWindowLayout(412f, 450f)

    val W599_H900 = BoxloreWindowLayout(599f, 900f)
    val W600_H900 = BoxloreWindowLayout(600f, 900f)
    val W900_H479 = BoxloreWindowLayout(900f, 479f)
    val W900_H480 = BoxloreWindowLayout(900f, 480f)
    val W839 = BoxloreWindowLayout(839f, 900f)
    val W841 = BoxloreWindowLayout(841f, 900f)
    val GATE_599x479 = BoxloreWindowLayout(599f, 479f)
    val GATE_600x480 = BoxloreWindowLayout(600f, 480f)

    /** An upright book foldable half open: a zero-width vertical fold down the middle. */
    val UPRIGHT_BOOK = BoxloreWindowLayout(750f, 832f, listOf(verticalFold(375f, 375f, 832f, occluding = false)))
    val FOLD_FLAT = BoxloreWindowLayout(932f, 650f)

    /** Half open like a laptop: a 21dp horizontal hinge at 315-336dp. */
    val FOLD_TABLETOP = FOLD_FLAT.copy(folds = listOf(horizontalFold(315f, 336f, 932f)))

    /** Half open like a book: a 21dp vertical hinge at 456-477dp. */
    val FOLD_BOOK = FOLD_FLAT.copy(folds = listOf(verticalFold(456f, 477f, 650f, occluding = true)))
    val PIXEL_FOLD = BoxloreWindowLayout(841f, 701f)

    val TABLET_PORTRAIT = BoxloreWindowLayout(800f, 1_280f)
    val TABLET = BoxloreWindowLayout(1_280f, 800f)
    val DESKTOP = BoxloreWindowLayout(1_600f, 1_000f)

    /**
     * The named window a capture of [width]x[height] with [posture] stands for, or null. Captures use
     * it so a named foldable keeps the same hinge in pictures and in tests.
     */
    fun forCapture(width: Int, height: Int, posture: String): BoxloreWindowLayout? {
        val fold = posture.removeSuffix("-insets").removeSuffix("insets")
        val orientation = when (fold) {
            "tabletop" -> BoxloreFoldOrientation.Horizontal
            "book" -> BoxloreFoldOrientation.Vertical
            else -> return null
        }
        return listOf(UPRIGHT_BOOK, FOLD_TABLETOP, FOLD_BOOK).firstOrNull { window ->
            val size = window.widthDp.toInt() to window.heightDp.toInt()
            size == (width to height) && window.folds.single().orientation == orientation
        }
    }

    private fun horizontalFold(top: Float, bottom: Float, width: Float) = BoxloreFold(
        BoxloreFoldState.HalfOpened,
        BoxloreFoldOrientation.Horizontal,
        BoxloreLayoutBounds(0f, top, width, bottom),
        isSeparating = true,
        isOccluding = true,
    )

    private fun verticalFold(left: Float, right: Float, height: Float, occluding: Boolean) = BoxloreFold(
        BoxloreFoldState.HalfOpened,
        BoxloreFoldOrientation.Vertical,
        BoxloreLayoutBounds(left, 0f, right, height),
        isSeparating = true,
        isOccluding = occluding,
    )
}

/**
 * Moves [state] through [steps] in order. After each step it lets composition settle (one frame
 * when the clock is paused) and runs [check] with that window, so transition tests read the same
 * in every module.
 */
fun ComposeContentTestRule.walkWindows(
    state: MutableState<BoxloreWindowLayout>,
    vararg steps: BoxloreWindowLayout,
    check: (BoxloreWindowLayout) -> Unit,
) {
    steps.forEach { window ->
        runOnIdle { state.value = window }
        Snapshot.sendApplyNotifications()
        if (!mainClock.autoAdvance) mainClock.advanceTimeByFrame()
        waitForIdle()
        check(window)
    }
}
