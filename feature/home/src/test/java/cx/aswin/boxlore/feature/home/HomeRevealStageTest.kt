package cx.aswin.boxlore.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cold-start regression: the skeleton hands over one section per frame, never all in one frame. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeRevealStageTest {
    @get:Rule val composeRule = createComposeRule()

    private val ready = mutableStateOf(false)
    private val stages = mutableListOf<Int>()

    private fun render(initiallyReady: Boolean) {
        ready.value = initiallyReady
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { stages += rememberHomeRevealStage(ready.value) }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun current() = composeRule.runOnIdle { stages.last() }

    /** Under a manual clock the write has to be handed to Compose before the next frame sees it. */
    private fun setReady(value: Boolean) {
        ready.value = value
        Snapshot.sendApplyNotifications()
    }

    @Test fun `ready content advances hero then Your shows then discovery on separate frames`() {
        render(initiallyReady = false)
        assertEquals(HomeRevealStage.SKELETON, current())
        setReady(true)
        val seen = mutableListOf<Int>()
        repeat(6) {
            composeRule.mainClock.advanceTimeByFrame()
            seen += current()
        }
        val distinct = seen.distinct()
        assertEquals(listOf(HomeRevealStage.HERO, HomeRevealStage.YOUR_SHOWS, HomeRevealStage.DISCOVERY), distinct.filter { it > HomeRevealStage.SKELETON })
        seen.zipWithNext().forEach { (before, after) -> assertEquals("At most one stage per frame", true, after - before in 0..1) }
        assertEquals(HomeRevealStage.COMPLETE, seen.last())
    }

    @Test fun `content already present on first composition shows at once`() {
        render(initiallyReady = true)
        assertEquals(HomeRevealStage.COMPLETE, stages.first())
    }

    @Test fun `loading again returns to the skeleton and stages the next reveal`() {
        render(initiallyReady = true)
        setReady(false)
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals(HomeRevealStage.SKELETON, current())
        setReady(true)
        repeat(6) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals(HomeRevealStage.COMPLETE, current())
    }
}
