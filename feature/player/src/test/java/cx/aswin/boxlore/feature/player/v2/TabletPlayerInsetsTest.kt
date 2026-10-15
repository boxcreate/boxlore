package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletPlayerInsetsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))
    private lateinit var view: View
    private var bottomInset = 0

    @Test
    fun `immersive long titles leave complete bottom controls above system gesture navigation`() {
        val playback = mock(PlaybackRepository::class.java)
        val downloads = mock(DownloadRepository::class.java)
        val prefs = mock(UserPreferencesRepository::class.java)
        val episode = TestFixtures.episode(title = "The surprising places that changed how we understand our world", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        val podcast = TestFixtures.podcast(title = "Everything Everywhere Daily: History, Science, Geography and More", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        `when`(playback.playerState).thenReturn(MutableStateFlow(PlayerState(currentEpisode = episode, currentPodcast = podcast, duration = 3_600_000L, queue = listOf(episode))))
        `when`(downloads.isDownloaded(episode.id)).thenReturn(flowOf(false))
        `when`(downloads.isDownloading(episode.id)).thenReturn(flowOf(false))
        `when`(prefs.fullPlayerStyleStream).thenReturn(flowOf(FullPlayerStyle.STANDARD))
        `when`(prefs.cachedFullPlayerStyle).thenReturn(FullPlayerStyle.STANDARD)
        `when`(prefs.sameShowQueueOnlyStream).thenReturn(flowOf(false))
        fixture.prepare()
        fixture.setContent {
            view = LocalView.current
            val inset = WindowInsets.safeDrawing.getBottom(LocalDensity.current)
            SideEffect { bottomInset = inset }
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1_280f, 800f)) {
                BoxLoreTheme(dynamicColor = false) {
                    FullPlayerV2(FullPlayerDependencies(playback, downloads, prefs), FullPlayerDisplay(MaterialTheme.colorScheme, false, object : NestedScrollConnection {}, true), FullPlayerActions({}, {}, {}, {}))
                }
            }
        }
        advance()
        fixture.compose.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                    .build(),
            )
        }
        advance()
        assertEquals(24, bottomInset)
        assertEquals(1_280, view.width)
        assertEquals(800, view.height)
        val lastControl = fixture.compose.onNodeWithContentDescription("Complete").assertHasClickAction().getUnclippedBoundsInRoot()
        assertTrue("Complete tile: $lastControl", (lastControl.bottom - lastControl.top).value >= 56f)
        assertTrue("Complete tile must clear system navigation: $lastControl", lastControl.bottom.value <= 776f)
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
