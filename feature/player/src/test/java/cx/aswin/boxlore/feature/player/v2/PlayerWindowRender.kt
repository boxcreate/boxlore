package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Renders the Standard full player in a box exactly [window]'s size (inside a root at least as big),
 * with [queueSize] queued episodes titled "Queued 1", "Queued 2", …, optional fake [insets], and the
 * given [store]. Returns the playback mock so callers can verify calls.
 */
internal fun PhoneGoldenRule.renderPlayerInWindow(
    window: State<BoxloreWindowLayout>,
    queueSize: Int = 1,
    insets: SystemInsets? = null,
    store: PlayerPresentationStore? = PlayerPresentationStore(tabletUsed = true),
): PlaybackRepository {
    val episode = TestFixtures.episode(title = "A curious journey", imageUrl = PhoneGoldenRule.ARTWORK_URL, description = "Notes about the places we discover.")
    val playback = mock(PlaybackRepository::class.java)
    `when`(playback.playerState).thenReturn(
        MutableStateFlow(
            PlayerState(
                currentEpisode = episode,
                currentPodcast = TestFixtures.podcast(imageUrl = PhoneGoldenRule.ARTWORK_URL),
                duration = 3_600_000L,
                queue = listOf(episode) + List(queueSize) { episode.copy(id = "queued-$it", title = "Queued ${it + 1}") },
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
    prepare()
    setContent {
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
    settlePlayer()
    // Sent once the content reads its insets, as the system does after the first layout.
    insets?.let { fake -> compose.runOnUiThread { applyFakeSystemInsets(compose.activity, fake) } }
    settlePlayer()
    return playback
}

internal fun PhoneGoldenRule.settlePlayer() {
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()
    compose.mainClock.advanceTimeBy(1_600)
    compose.waitForIdle()
}
