package cx.aswin.boxlore.core.designsystem.analytics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import cx.aswin.boxlore.core.analytics.ScreenViews
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [TrackScreenView]: one view per visit, not again after the activity is recreated (rotation, fold,
 * resize), a new visit for a new key, and the screen trail following sheets that open and close.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackScreenViewTest {
    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<String>()

    @Before
    fun setUp() {
        ScreenViews.resetForTest()
    }

    @After
    fun tearDown() {
        ScreenViews.resetForTest()
    }

    @Test
    fun aVisitSendsOnceAndRecreatingTheScreenDoesNotSendAgain() {
        ScreenViews.show(ScreenViews.Registration(), "library_hub")
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TrackScreenView("history_insights") { previous -> sent += "history_insights after $previous" } }
        compose.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(listOf("history_insights after library_hub"), sent)
        assertEquals(ScreenViews.Trail(current = "history_insights", previous = "library_hub"), ScreenViews.trail)
    }

    @Test
    fun anotherShowOnTheSamePageIsANewVisit() {
        var show by mutableStateOf("p1")
        compose.setContent { TrackScreenView("podcast_detail", show) { sent += show } }
        compose.waitForIdle()

        show = "p2"
        compose.waitForIdle()

        assertEquals(listOf("p1", "p2"), sent)
    }

    @Test
    fun aSheetIsItsOwnVisitAndClosingItGivesThePageBack() {
        var sheetOpen by mutableStateOf(true)
        compose.setContent {
            TrackScreenView("player_full") { previous -> sent += "player_full after $previous" }
            if (sheetOpen) TrackScreenView("player_speed_sheet") { previous -> sent += "player_speed_sheet after $previous" }
        }
        compose.waitForIdle()

        sheetOpen = false
        compose.waitForIdle()
        assertEquals(ScreenViews.Trail(current = "player_full", previous = "player_speed_sheet"), ScreenViews.trail)

        sheetOpen = true
        compose.waitForIdle()

        assertEquals(
            listOf("player_full after null", "player_speed_sheet after player_full", "player_speed_sheet after player_full"),
            sent,
        )
    }

    @Test
    fun theNextStepReplacesTheLastOneAndNamesItAsTheScreenBefore() {
        ScreenViews.show(ScreenViews.Registration(), "subscriptions")
        var step by mutableStateOf("new_folder_contents")
        compose.setContent { TrackScreenView(step) { previous -> sent += "$step after $previous" } }
        compose.waitForIdle()

        step = "new_folder_name"
        compose.waitForIdle()

        assertEquals(listOf("new_folder_contents after subscriptions", "new_folder_name after new_folder_contents"), sent)
        assertEquals(ScreenViews.Trail(current = "new_folder_name", previous = "new_folder_contents"), ScreenViews.trail)
    }

    @Test
    fun closingAStoryWithItsCardGivesThePageBack() {
        var storyOpen by mutableStateOf(true)
        compose.setContent {
            TrackScreenView("history_insights", send = {})
            if (storyOpen) {
                TrackScreenView("listening_story", send = {})
                TrackScreenView("listening_story_card", send = {})
            }
        }
        compose.waitForIdle()

        storyOpen = false
        compose.waitForIdle()

        assertEquals("history_insights", ScreenViews.trail.current)
    }
}
