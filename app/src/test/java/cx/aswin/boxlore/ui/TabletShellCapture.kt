package cx.aswin.boxlore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.feature.player.v2.FullPlayerDependencies
import cx.aswin.boxlore.feature.player.v2.PlayerSheetActions
import cx.aswin.boxlore.feature.player.v2.RetainedPlayerPresentation
import cx.aswin.boxlore.navigation.TabletContextRoot
import cx.aswin.boxlore.navigation.rememberTabletDetailNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Real shell/player presentation with fixed local data, never an Application-backed service. */
@Composable
internal fun TabletShellCapture(route: String, episode: Episode, podcast: Podcast, playing: Boolean, content: @Composable () -> Unit) {
    val navigation = rememberTabletDetailNavigation(rememberNavController())
    val dependencies = remember { tabletCapturePlayer(episode, podcast) }
    RetainedPlayerPresentation {
        TabletAppShell(
            navigation,
            true,
            true,
            playing,
            route,
            {},
            TabletShellContent(
            primary = content,
            details = { NavHost(navigation.detail, TabletContextRoot) { composable(TabletContextRoot) {} } },
            player = { contextPane ->
                TabletAppPlayer(dependencies, navigation, contextPane, PlayerSheetActions(), TabletPlayerOptions(NavigationStyle.Floating, false, 0, true))
            },
            ),
        )
    }
}

private fun tabletCapturePlayer(episode: Episode, podcast: Podcast): FullPlayerDependencies {
    val playback = mock(PlaybackRepository::class.java)
    `when`(playback.playerState).thenReturn(
        MutableStateFlow(
            PlayerState(
        currentEpisode = episode,
        currentPodcast = podcast,
        duration = 3_600_000L,
        position = 120_000L,
        queue = listOf(episode, episode.copy(id = "capture-next", title = "Following our curiosity")),
        currentChapters = listOf(Chapter(0.0, "Finding a starting point"), Chapter(1_200.0, "The next question")),
            ),
        ),
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
    return FullPlayerDependencies(playback, downloads, prefs)
}
