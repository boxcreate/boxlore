package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Turning a phone sideways and back keeps the same live player: its page position and seekbar label. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneRotationStateTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(915, 915, false))
    private val window = mutableStateOf(BoxloreWindowLayout(412f, 915f))
    private val episode = TestFixtures.episode(title = "A curious journey", imageUrl = PhoneGoldenRule.ARTWORK_URL, description = "Notes about the places we discover.")

    @Test
    fun `turning the phone sideways and back keeps the page position and the seekbar's total-length label`() {
        val store = PlayerPresentationStore()
        render(store)
        fixture.compose.onNodeWithText("-", substring = true).performClick()
        advance()
        scrollTo("Next journey")
        val upright = fixture.compose.onNodeWithText("Next journey").getUnclippedBoundsInRoot().top
        assertTrue("The page scrolled to Up next", upright < 915.dp)

        listOf(BoxloreWindowLayout(915f, 412f), BoxloreWindowLayout(412f, 915f), BoxloreWindowLayout(915f, 412f), BoxloreWindowLayout(412f, 915f)).forEach { layout ->
            fixture.compose.runOnIdle { window.value = layout }
            advance()
            assertTrue("Total length kept at ${layout.widthDp} x ${layout.heightDp}", store.ui.showTotalDuration)
            assertEquals("The time-left label stays replaced by the total", 0, fixture.compose.onAllNodesWithText("-", substring = true).fetchSemanticsNodes().size)
        }
        assertEquals(upright, fixture.compose.onNodeWithText("Next journey").getUnclippedBoundsInRoot().top)
    }

    private fun render(store: PlayerPresentationStore) {
        val playback = mock(PlaybackRepository::class.java)
        `when`(playback.playerState).thenReturn(
            MutableStateFlow(
                PlayerState(
                    currentEpisode = episode,
                    currentPodcast = TestFixtures.podcast(imageUrl = PhoneGoldenRule.ARTWORK_URL),
                    duration = 3_600_000L,
                    queue = listOf(episode, episode.copy(id = "next", title = "Next journey")),
                ),
            ),
        )
        val downloads = mock(DownloadRepository::class.java)
        `when`(downloads.isDownloaded(anyString())).thenReturn(flowOf(false))
        `when`(downloads.isDownloading(anyString())).thenReturn(flowOf(false))
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.fullPlayerStyleStream).thenReturn(flowOf(FullPlayerStyle.STANDARD))
        `when`(prefs.cachedFullPlayerStyle).thenReturn(FullPlayerStyle.STANDARD)
        `when`(prefs.sameShowQueueOnlyStream).thenReturn(flowOf(false))
        fixture.prepare()
        fixture.setContent {
            val layout = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides layout, LocalPlayerPresentationStore provides store) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                    Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                        FullPlayerV2(
                            FullPlayerDependencies(playback, downloads, prefs),
                            FullPlayerDisplay(MaterialTheme.colorScheme, false, object : NestedScrollConnection {}, true),
                            FullPlayerActions({}, {}, {}, {}),
                        )
                    }
                }
            }
        }
        advance()
    }

    private fun scrollTo(text: String) {
        fixture.compose.mainClock.autoAdvance = true
        try {
            fixture.compose.onNodeWithText(text).performScrollTo()
            fixture.compose.waitForIdle()
        } finally {
            fixture.compose.mainClock.autoAdvance = false
        }
        advance()
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
