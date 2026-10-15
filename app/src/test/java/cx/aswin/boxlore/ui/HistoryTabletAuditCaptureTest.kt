package cx.aswin.boxlore.ui

import android.app.Application
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.history.HistoryScreen
import cx.aswin.boxlore.feature.history.HistorySuccessState
import cx.aswin.boxlore.feature.history.HistoryUiState
import cx.aswin.boxlore.feature.history.HistoryViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryTabletAuditCaptureTest(private val empty: Boolean, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun capture() {
        assumeTrue(System.getProperty("boxlore.tabletCaptures") == "after")
        val vm = mock(HistoryViewModel::class.java)
        val day = LocalDate.of(2026, 10, 15)
        val items = List(12) { index -> ListeningHistoryItem("$index", "show-1", "Finding our way through the universe $index", PhoneGoldenRule.ARTWORK_URL, PhoneGoldenRule.ARTWORK_URL, "https://audio.example.invalid/episode.mp3", "Curious world", 120_000L, 3_600_000L, false, true, 1_760_000_000_000L) }
        val state = if (empty) HistoryUiState.Empty else HistoryUiState.Success(HistorySuccessState(captureInsights(), mapOf(day to items), setOf(day), activeDays = setOf(day)))
        `when`(vm.uiState).thenReturn(MutableStateFlow<HistoryUiState>(state))
        `when`(vm.events).thenReturn(emptyFlow())
        fixture.prepare()
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) { HistoryScreen(vm, {}, {}, {}) }
        }
        fixture.capture(if (empty) "history-empty" else "history-loaded")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "empty={0}-{1}")
        fun configurations(): List<Array<Any>> = listOf(false, true).flatMap { empty ->
            listOf(1280 to 800, 840 to 1280).flatMap { (width, height) -> listOf(false, true).map { dark -> arrayOf<Any>(empty, PhoneGoldenConfiguration(width, height, dark)) } }
        }
    }
}

private fun captureInsights() = ListeningInsightSummary(
    period = ListeningPeriod.DAYS_30,
    trackingSinceEpochMs = 1_760_000_000_000L,
    totalConsumedMs = 7_200_000L,
    previousPeriodConsumedMs = 3_600_000L,
    completedCount = 2,
    inProgressCount = 3,
    likedCount = 2,
    sessionCount = 4,
    averageSessionMs = 1_800_000L,
    longestSessionMs = 3_600_000L,
    streakDays = 2,
    activeDaysInPeriod = 3,
    peakBucket = null,
    morningMs = 0L,
    afternoonMs = 7_200_000L,
    eveningMs = 0L,
    nightMs = 0L,
    topShow = null,
    hasEnoughData = true,
)
