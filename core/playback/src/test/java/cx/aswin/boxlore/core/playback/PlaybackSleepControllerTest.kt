package cx.aswin.boxlore.core.playback

import android.content.SharedPreferences
import cx.aswin.boxlore.core.model.SleepTimerConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSleepControllerTest {
    private val scope = TestScope()
    private val state = MutableStateFlow(PlayerState())
    private val controller = PlaybackSleepController(
        scope = scope,
        playerStateFlow = state,
        prefs = mock(SharedPreferences::class.java),
        mediaHandle = PlaybackMediaControllerHandle(),
        stopProgressTicker = {},
        lastSleepPromptWindowIdKey = "window",
        debugSkipSleepWindowKey = "debug",
    )

    @After fun tearDown() = controller.cancelTimer()

    @Test
    fun `a fixed timer remembers the length the listener picked until it is turned off or changed`() {
        controller.setSleepTimer(30)
        scope.runCurrent()
        assertEquals(30, state.value.sleepTimerPresetMinutes)

        controller.setSleepTimer(SleepTimerConstants.END_OF_EPISODE_MINUTES)
        scope.runCurrent()
        assertNull("End of episode is not a fixed length", state.value.sleepTimerPresetMinutes)

        controller.setSleepTimer(45)
        controller.setSleepTimer(0)
        scope.runCurrent()
        assertNull(state.value.sleepTimerPresetMinutes)
    }

    @Test
    fun `the remembered length clears when the timer runs out`() {
        controller.setSleepTimer(15)
        assertEquals(15, state.value.sleepTimerPresetMinutes)
        SleepTimerHolder.activeSleepTimerEndMs = System.currentTimeMillis() - 1
        scope.runCurrent()
        assertNull(state.value.sleepTimerPresetMinutes)
    }
}
