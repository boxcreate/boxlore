package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.DpRect
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
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sideways phones in a window exactly their size, with and without a status bar, side navigation
 * buttons and a camera cutout: the play button and both rows of quick actions end inside the window,
 * and the header buttons and play controls stay clear of the side system bars.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneLandscapeInsetsTest(private val width: Int, private val height: Int, private val insets: SystemInsets?) {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(width, height, false))

    @Test
    fun `play and both rows of quick actions fit and stay clear of the system bars`() {
        render()
        val safe = DpRect(
            left = androidx.compose.ui.unit.Dp((insets?.let { maxOf(it.navStart, it.cutoutStart) } ?: 0).toFloat()),
            top = androidx.compose.ui.unit.Dp((insets?.statusTop ?: 0).toFloat()),
            right = androidx.compose.ui.unit.Dp((width - (insets?.navEnd ?: 0)).toFloat()),
            bottom = androidx.compose.ui.unit.Dp((height - (insets?.navBottom ?: 0)).toFloat()),
        )
        listOf("Play", "Sleep", "Complete", "Share", "Cast").forEach { label ->
            val bounds = fixture.compose.onNodeWithContentDescription(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("$label at $bounds must sit inside $safe on a ${width}x$height phone (insets $insets)", bounds.left >= safe.left && bounds.right <= safe.right && bounds.top >= safe.top && bounds.bottom <= safe.bottom)
        }
    }

    private fun render() {
        val episode = TestFixtures.episode(title = "A curious journey", imageUrl = PhoneGoldenRule.ARTWORK_URL, audioUrl = "https://audio.example.invalid/episode.mp3")
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
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(width.toFloat(), height.toFloat())) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                    FullPlayerV2(
                        FullPlayerDependencies(playback, downloads, prefs),
                        FullPlayerDisplay(MaterialTheme.colorScheme, false, object : NestedScrollConnection {}, true),
                        FullPlayerActions({}, {}, {}, {}),
                    )
                }
            }
        }
        settle()
        // Sent once the content reads its insets, as the system does after the first layout.
        insets?.let { fake -> fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, fake) } }
        settle()
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}x{1} {2}")
        fun configurations(): List<Array<Any?>> = listOf(
            arrayOf(915, 412, null),
            arrayOf(915, 412, SystemInsets.SIDEWAYS_3_BUTTON),
            arrayOf(800, 360, null),
            arrayOf(800, 360, SystemInsets.SIDEWAYS_3_BUTTON),
            arrayOf(780, 360, SystemInsets.SIDEWAYS_3_BUTTON),
        )
    }
}
