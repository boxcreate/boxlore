package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appMiniPlayerTopOffset
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The phone sheet keeps the listener's open or closed choice while the window rotates or the shell re-hosts it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerSheetScaffoldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val episode = TestFixtures.episode(title = "A curious journey")
    private val store = PlayerPresentationStore()
    private val windowHeight = mutableStateOf(PORTRAIT)
    private val shown = mutableStateOf(true)

    // Mirrors the app shell: a notification's request reaches the sheet until the sheet reports it handled.
    private val openRequest = mutableLongStateOf(0L)
    private val handledRequest = mutableLongStateOf(0L)
    private val handled = mutableListOf<Long>()

    @Test
    fun `rotating either way keeps the sheet where the listener left it`() {
        render()
        rotate(expanded = false)
        compose.onNodeWithText(episode.title).performClick()
        compose.waitForIdle()
        assertTrue(store.expanded)
        rotate(expanded = true)
    }

    @Test
    fun `an open request from outside expands the sheet once and is not replayed`() {
        render()
        compose.runOnIdle { openRequest.longValue = 1_000L }
        compose.waitForIdle()
        assertTrue("The notification tap opens the player", store.expanded)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(store.expanded)

        // Phone to tablet and back removes the phone sheet and composes it again.
        compose.runOnIdle { shown.value = false }
        compose.waitForIdle()
        compose.runOnIdle { shown.value = true }
        compose.waitForIdle()
        assertFalse("A handled request never reopens the player", store.expanded)
        rotate(expanded = false)
        assertEquals(listOf(1_000L), handled)

        compose.runOnIdle { openRequest.longValue = 2_000L }
        compose.waitForIdle()
        assertTrue("A later tap opens the player again", store.expanded)
        assertEquals(listOf(1_000L, 2_000L), handled)
    }

    private fun rotate(expanded: Boolean) {
        listOf(LANDSCAPE, PORTRAIT, LANDSCAPE, PORTRAIT).forEach { height ->
            compose.runOnIdle { windowHeight.value = height }
            compose.waitForIdle()
            assertEquals("Window height $height", expanded, store.expanded)
        }
    }

    private fun render() {
        val playback = mock(PlaybackRepository::class.java)
        `when`(playback.playerState).thenReturn(
            MutableStateFlow(PlayerState(currentEpisode = episode, currentPodcast = TestFixtures.podcast(), duration = 3_600_000L)),
        )
        val downloads = mock(DownloadRepository::class.java)
        `when`(downloads.isDownloaded(episode.id)).thenReturn(flowOf(false))
        `when`(downloads.isDownloading(episode.id)).thenReturn(flowOf(false))
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.fullPlayerStyleStream).thenReturn(flowOf(FullPlayerStyle.STANDARD))
        `when`(prefs.cachedFullPlayerStyle).thenReturn(FullPlayerStyle.STANDARD)
        `when`(prefs.sameShowQueueOnlyStream).thenReturn(flowOf(false))
        `when`(prefs.hasSeenSwipeDismissTip).thenReturn(flowOf(true))
        `when`(prefs.hasSeenSwipeMinimizeTip).thenReturn(flowOf(true))
        compose.setContent {
            CompositionLocalProvider(LocalPlayerPresentationStore provides store) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize()) {
                        if (shown.value) {
                            val height = windowHeight.value
                            PlayerSheetScaffold(
                                playback,
                                downloads,
                                prefs,
                                PlayerSheetLayout(
                                    collapsedTargetY = with(LocalDensity.current) { appMiniPlayerTopOffset(NavigationStyle.Floating, height, 24.dp).toPx() },
                                    containerHeight = height + 74.dp,
                                    expandTrigger = openRequest.longValue.takeIf { it != handledRequest.longValue } ?: 0L,
                                ),
                                PlayerSheetActions(
                                    onExpandRequestHandled = { request ->
                                        handled += request
                                        handledRequest.longValue = request
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        val PORTRAIT = 914.dp
        val LANDSCAPE = 411.dp
    }
}
