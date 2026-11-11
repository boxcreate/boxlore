package cx.aswin.boxlore.widgets

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenWidgetsInstallerTest {
    @Test
    fun theFirstPickerPreviewPublishWaitsForStartupAndLaterLooksPublishAtOnce() = runTest {
        val look = MutableStateFlow(0)
        val startupReady = CompletableDeferred<Unit>()
        var published = 0
        val job =
            launch {
                HomeScreenWidgetsInstaller.publishWhenStartupReady(look.map { }, awaitStartupReady = { startupReady.await() }) { published++ }
            }

        // The look is known from the start, and changes once more, while the first screen is still coming up.
        advanceUntilIdle()
        look.value = 1
        advanceUntilIdle()
        assertEquals("nothing is published before the app is up", 0, published)

        startupReady.complete(Unit)
        advanceUntilIdle()
        assertEquals("the current look is published once", 1, published)

        look.value = 2
        advanceUntilIdle()
        assertEquals("a later change is published straight away", 2, published)
        job.cancel()
    }
}
