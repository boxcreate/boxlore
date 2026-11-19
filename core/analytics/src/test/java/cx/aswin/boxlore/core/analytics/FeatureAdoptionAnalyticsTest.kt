package cx.aswin.boxlore.core.analytics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Feature-adoption person properties go out once per distinct settled value, remembered across launches. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureAdoptionAnalyticsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    /** One SharedPreferences file that outlives each simulated launch. */
    private val disk =
        object : FeatureAdoptionAnalytics.Store {
            var saved: String? = null

            override fun lastSent(): String? = saved

            override fun saveSent(encoded: String) {
                saved = encoded
            }
        }

    private val base = FeatureAdoption(playlistsCount = 2, hiddenShowsCount = 1, episodeTitlesOnly = false, trimSilence = true, showsWithOwnSpeed = 3)

    private fun sets() = recorder.filter { it.first == AnalyticsGlossary.PERSON_SET_EVENT }.map { it.second }

    @BeforeEach
    fun setUp() {
        FeatureAdoptionAnalytics.resetForTest()
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
        FeatureAdoptionAnalytics.resetForTest()
    }

    /** One app launch: follows [values] until the test's virtual time settles, then stops. */
    private fun TestScope.launchApp(values: MutableStateFlow<FeatureAdoption>) =
        backgroundScope.launch { FeatureAdoptionAnalytics.sync(values, disk) }

    @Test
    fun `values are sent once and not again after a restart with the same values`() = runTest {
        val values = MutableStateFlow(base)
        val first = launchApp(values)
        advanceTimeBy(FeatureAdoptionAnalytics.DEBOUNCE_MS + 1)
        runCurrent()
        first.cancel()

        assertEquals(
            listOf(
                mapOf(
                    "playlists_count" to 2,
                    "hidden_shows_count" to 1,
                    "episode_titles_only" to false,
                    "trim_silence" to true,
                    "shows_with_own_speed" to 3,
                ),
            ),
            sets(),
        )

        FeatureAdoptionAnalytics.resetForTest()
        launchApp(MutableStateFlow(base))
        advanceTimeBy(FeatureAdoptionAnalytics.DEBOUNCE_MS * 3)
        runCurrent()
        assertEquals(1, sets().size)
    }

    @Test
    fun `a change is sent again, and rapid changes go out as one`() = runTest {
        val values = MutableStateFlow(base)
        launchApp(values)
        advanceTimeBy(FeatureAdoptionAnalytics.DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(1, sets().size)

        // Three quick edits, one second apart, settle as one send of the last value.
        repeat(3) { index ->
            values.value = base.copy(playlistsCount = 3 + index)
            advanceTimeBy(1_000L)
        }
        runCurrent()
        assertEquals(1, sets().size)
        advanceTimeBy(FeatureAdoptionAnalytics.DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(2, sets().size)
        assertEquals(5, sets().last()["playlists_count"])

        // Back and forth within the window ends where it started: nothing new to send.
        values.value = base.copy(playlistsCount = 9)
        advanceTimeBy(500L)
        values.value = base.copy(playlistsCount = 5)
        advanceTimeBy(FeatureAdoptionAnalytics.DEBOUNCE_MS * 2)
        runCurrent()
        assertEquals(2, sets().size)
    }

    @Test
    fun `an identity reset resends the current values`() = runTest {
        launchApp(MutableStateFlow(base))
        advanceTimeBy(FeatureAdoptionAnalytics.DEBOUNCE_MS + 1)
        runCurrent()

        FeatureAdoptionAnalytics.resendAfterIdentityReset()

        assertEquals(2, sets().size)
        assertEquals(sets().first(), sets().last())
    }
}
