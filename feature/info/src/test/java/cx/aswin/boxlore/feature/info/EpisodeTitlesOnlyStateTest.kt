package cx.aswin.boxlore.feature.info

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A show page opens with the stored Episode titles only choice, not full rows that then shrink. */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeTitlesOnlyStateTest {
    @Test
    fun `a new page reads the app's running copy at once, before prefs answer`() = runTest {
        // Reviewed: each page started off until DataStore answered, so a cached show drew full cards first.
        val appCopy = MutableStateFlow(true)
        val prefsNotYetRead = MutableSharedFlow<Boolean>()

        val page = episodeTitlesOnlyState(appCopy, prefsNotYetRead, backgroundScope)

        assertSame(appCopy, page)
        assertTrue(page.value, "on from the first frame")
        appCopy.value = false
        assertFalse(page.value, "follows the choice when it changes")
    }

    @Test
    fun `without an app copy the page reads prefs itself`() = runTest {
        val stored = MutableStateFlow(true)

        val page = episodeTitlesOnlyState(null, stored, backgroundScope)
        assertFalse(page.value, "off until prefs answer")
        backgroundScope.launch { page.collect {} }
        runCurrent()

        assertTrue(page.value)
    }
}
