package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.DESKTOP
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE_LAND
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W839
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W841
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lore history: filters, grouped rows in a readable column, its round buttons and the Clear dialog. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LearnHistoryScreenTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_600, 1_280, false))

    private val window = mutableStateOf(PHONE)
    private val viewModel = mock(LearnHistoryViewModel::class.java)
    private var backs = 0
    private lateinit var restoration: StateRestorationTester

    private fun entry(index: Int, action: LearnHistoryAction, atMs: Long) = LearnHistoryEntry(
        "card-$index", "Episode $index", "Curious world", null, null, "1", null, 600, null,
        "Question $index?", null, 8, action, atMs,
    )

    private fun render(
        entries: List<LearnHistoryEntry> = List(6) { index ->
            entry(index, if (index % 2 == 0) LearnHistoryAction.QUEUE else LearnHistoryAction.DISMISS, System.currentTimeMillis() - index * 60_000L)
        },
        now: () -> Long = System::currentTimeMillis,
    ) {
        `when`(viewModel.uiState).thenReturn(MutableStateFlow(LearnHistoryUiState.Success(entries)))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(size.widthDp.dp, size.heightDp.dp).testTag(ROOT)) {
                        Surface {
                            CompositionLocalProvider(LocalLearnHistoryClock provides now) {
                                LearnHistoryScreen(viewModel, appBottomChromeContentPadding(isMiniPlayerVisible = false), onBack = { backs++ }, onEpisodeClick = {})
                            }
                        }
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    private fun moveTo(next: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = next }
        fixture.compose.waitForIdle()
    }

    private fun string(id: Int) = fixture.compose.activity.getString(id)

    private fun shownQuestions() = (0 until 6).map { "Question $it?" }.filter { fixture.compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `the Queued filter shows only queued cards and survives resizing and recreation`() {
        render()
        fixture.compose.onNodeWithText(string(R.string.lore_history_queued)).performClick()
        fixture.compose.waitForIdle()
        val queued = listOf("Question 0?", "Question 2?", "Question 4?")
        assertEquals(queued, shownQuestions())
        for (next in listOf(TABLET, PHONE)) {
            moveTo(next)
            assertEquals("$next", queued, shownQuestions())
        }
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(queued, shownQuestions())
        fixture.compose.onNodeWithText(string(R.string.lore_history_filter_skipped)).performClick()
        fixture.compose.waitForIdle()
        assertEquals(listOf("Question 1?", "Question 3?", "Question 5?"), shownQuestions())
    }

    @Test
    fun `rows stay in a readable column on wide windows and fill a phone`() {
        render()
        for ((next, maxWidth) in listOf(PHONE_LAND to 720f, DESKTOP to 720f, PHONE to PHONE.widthDp - 32f)) {
            moveTo(next)
            val row = fixture.compose.onAllNodesWithTag(LEARN_HISTORY_ROW_TAG)[0].fetchSemanticsNode().boundsInRoot
            val width = with(fixture.compose.density) { row.width.toDp().value }
            if (next == PHONE) assertEquals("phone row", maxWidth, width, 1f) else assertTrue("$next row $width", width <= maxWidth + 0.5f)
        }
    }

    @Test
    fun `restore and back work once each, and a long-press on Back doesn't leave`() {
        render()
        val restores = { mockingDetails(viewModel).invocations.count { it.method.name == "restoreEntry" } }
        fixture.compose.onAllNodes(hasContentDescription(string(R.string.lore_history_restore)))[0].performClick()
        assertEquals(1, restores())

        val back = fixture.compose.onNodeWithContentDescription(string(R.string.explore_back))
        val bounds = back.fetchSemanticsNode().boundsInRoot
        assertEquals(48f, with(fixture.compose.density) { bounds.width.toDp().value }, 0.5f)
        val clear = fixture.compose.onNodeWithContentDescription(string(R.string.lore_history_clear)).fetchSemanticsNode().boundsInRoot
        assertTrue("Back and Clear history sit at opposite ends", clear.left >= bounds.right)
        back.performClick()
        fixture.compose.waitForIdle()
        assertEquals(1, backs)
        // The long-press tooltip itself isn't readable under Robolectric here; a long-press must still not go back.
        back.performTouchInput { longClick() }
        fixture.compose.waitForIdle()
        assertEquals("a long-press is not a tap", 1, backs)
    }

    @Test
    fun `Clear history asks first, clears once, and the dialog survives resizing`() {
        render()
        val clears = { mockingDetails(viewModel).invocations.count { it.method.name == "clearAll" } }
        fixture.compose.onNodeWithContentDescription(string(R.string.lore_history_clear)).performClick()
        for (next in listOf(TABLET, PHONE, W839, W841, W839)) {
            moveTo(next)
            fixture.compose.onNodeWithText(string(R.string.lore_history_clear_title)).assertExists()
        }
        fixture.compose.onNodeWithText(string(R.string.lore_history_cancel)).performClick()
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText(string(R.string.lore_history_clear_title)).assertDoesNotExist()
        assertEquals(0, clears())

        fixture.compose.onNodeWithContentDescription(string(R.string.lore_history_clear)).performClick()
        fixture.compose.onNodeWithText(string(R.string.lore_history_clear_confirm)).performClick()
        fixture.compose.waitForIdle()
        assertEquals(1, clears())
        fixture.compose.onNodeWithText(string(R.string.lore_history_clear_title)).assertDoesNotExist()
    }

    @Test
    fun `a card seen today moves under Yesterday when the day turns while the page is open`() {
        val zone = java.time.ZoneId.systemDefault()
        var clock = java.time.LocalDate.of(2026, 3, 9).atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        render(listOf(entry(0, LearnHistoryAction.QUEUE, clock - 60_000L)), now = { clock })
        fixture.compose.onNodeWithText(string(R.string.lore_history_today)).assertExists()
        clock += 20 * 60_000L
        fixture.compose.mainClock.advanceTimeBy(20 * 60_000L)
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText(string(R.string.lore_history_yesterday)).assertExists()
        fixture.compose.onNodeWithText(string(R.string.lore_history_today)).assertDoesNotExist()
    }

    @Test
    fun `month headings use the language's month names`() {
        val october = java.time.LocalDate.of(2025, 10, 2).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("Oktober", LocaleFormats.date(october, LocaleFormats.DateStyle.MONTH, Locale.GERMANY))
        assertEquals("octobre", LocaleFormats.date(october, LocaleFormats.DateStyle.MONTH, Locale.FRANCE).lowercase(Locale.FRANCE))
    }

    @Test
    fun `month headings start with a capital like the other headings`() {
        val november = java.time.LocalDate.of(2025, 11, 2).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val period = LearnHistoryPeriod.Month(2025, 11, november)
        assertEquals("Novembre", learnHistoryMonthLabel(period, thisYear = 2025, Locale.FRANCE))
        assertEquals("Noviembre de 2025", learnHistoryMonthLabel(period, thisYear = 2026, Locale.forLanguageTag("es")))
        assertEquals("Novembro de 2025", learnHistoryMonthLabel(period, thisYear = 2026, Locale.forLanguageTag("pt-BR")))
        assertEquals("November", learnHistoryMonthLabel(period, thisYear = 2025, Locale.GERMANY))
    }

    @Test
    fun `a filter that matches nothing says so and offers every card again`() {
        render(List(3) { entry(it, LearnHistoryAction.DISMISS, System.currentTimeMillis() - it * 60_000L) })
        fixture.compose.onNodeWithText(string(R.string.lore_history_queued)).performClick()
        fixture.compose.waitForIdle()
        assertEquals(emptyList<String>(), shownQuestions())
        fixture.compose.onNodeWithText(string(R.string.lore_history_none_queued)).assertExists()
        fixture.compose.onNodeWithText(string(R.string.lore_history_show_all)).performClick()
        fixture.compose.waitForIdle()
        assertEquals(listOf("Question 0?", "Question 1?", "Question 2?"), shownQuestions())
        fixture.compose.onNodeWithText(string(R.string.lore_history_none_queued)).assertDoesNotExist()
    }

    private companion object {
        const val ROOT = "learn_history_test_root"
    }
}
