package cx.aswin.boxlore.feature.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.feature.widgets.actions.WidgetActionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetActionHandlerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val playback = FakePlayback()
    private val store = NowPlayingWidgetSnapshotStore(context)

    private class FakePlayback : WidgetPlaybackSource {
        override val state = MutableStateFlow(WidgetPlaybackState())
        val calls = mutableListOf<String>()

        override suspend fun restoreBeforeCollect() = Unit

        override suspend fun restoreBeforeAction() {
            calls += "restore"
        }

        override suspend fun togglePlayPause() {
            calls += "toggle"
        }

        override suspend fun previous() {
            calls += "previous"
        }

        override suspend fun next() {
            calls += "next"
        }

        override suspend fun skipForward() {
            calls += "forward"
        }

        override suspend fun skipBackward() {
            calls += "back"
        }

        override suspend fun refreshPosition() {
            calls += "refresh"
        }
    }

    @Before
    fun install() {
        val fake = playback
        NowPlayingWidgetDependenciesHolder.instance =
            object : NowPlayingWidgetDependencies {
                override val context: Context = this@WidgetActionHandlerTest.context
                override val scope: CoroutineScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
                override val playback: WidgetPlaybackSource = fake
            }
        // What the widget last painted: ten seconds in.
        store.write(NowPlayingWidgetSnapshot(episodeId = "ep-1", positionMs = 10_000L, durationMs = 3_600_000L, seekForwardMs = 30_000L, seekBackwardMs = 10_000L))
    }

    @After
    fun uninstall() {
        NowPlayingWidgetDependenciesHolder.instance = null
    }

    @Test
    fun aSeekIsPreviewedFromThePlayersLivePositionOnTheSameEpisode() = runTest {
        playback.state.value = WidgetPlaybackState(episodeId = "ep-1", positionMs = 100_000L, durationMs = 3_600_000L)

        WidgetActionHandler.handle(WidgetControl.SKIP_FORWARD)

        assertEquals(130_000L, store.read()?.positionMs)
    }

    @Test
    fun aSeekKeepsThePaintedPositionWhenThePlayerIsOnAnotherEpisode() = runTest {
        playback.state.value = WidgetPlaybackState(episodeId = "ep-2", positionMs = 100_000L, durationMs = 900_000L)

        WidgetActionHandler.handle(WidgetControl.SKIP_FORWARD)

        assertEquals(40_000L, store.read()?.positionMs)
        assertEquals(3_600_000L, store.read()?.durationMs)
    }

    @Test
    fun everyTransportControlAsksForTheRealPositionOnceAfterItsAction() = runTest {
        val actions =
            mapOf(
                WidgetControl.TOGGLE to "toggle",
                WidgetControl.PREVIOUS to "previous",
                WidgetControl.NEXT to "next",
                WidgetControl.SKIP_BACK to "back",
                WidgetControl.SKIP_FORWARD to "forward",
            )
        for ((control, action) in actions) {
            playback.calls.clear()
            WidgetActionHandler.handle(control)
            assertEquals("$control", listOf("restore", action, "refresh"), playback.calls)
        }
    }

    @Test
    fun openingTheAppTouchesNeitherThePlayerNorThePosition() = runTest {
        WidgetActionHandler.handle(WidgetControl.OPEN_APP)

        assertEquals(emptyList<String>(), playback.calls)
        assertEquals(10_000L, store.read()?.positionMs)
    }
}
