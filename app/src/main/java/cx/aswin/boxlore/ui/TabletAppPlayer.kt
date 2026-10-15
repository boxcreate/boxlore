package cx.aswin.boxlore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreDockedPlayerBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.playback.PlaybackArtworkResolver
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.feature.player.v2.FullPlayerActions
import cx.aswin.boxlore.feature.player.v2.FullPlayerDependencies
import cx.aswin.boxlore.feature.player.v2.FullPlayerDisplay
import cx.aswin.boxlore.feature.player.v2.FullPlayerV2
import cx.aswin.boxlore.feature.player.v2.LocalPlayerFullscreenRequest
import cx.aswin.boxlore.feature.player.v2.PlayerSheetActions
import cx.aswin.boxlore.feature.player.v2.PlayerSheetLayout
import cx.aswin.boxlore.feature.player.v2.PlayerSheetScaffold
import cx.aswin.boxlore.feature.player.v2.playerPresentationController
import cx.aswin.boxlore.feature.player.v2.rememberPlayerColors
import cx.aswin.boxlore.navigation.TabletDetailNavigation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class TabletPlayerOptions(val navigationStyle: NavigationStyle, val seekButtons: Boolean, val expandTrigger: Long, val hasNavigationRail: Boolean)

@Composable
internal fun TabletAppPlayer(
    dependencies: FullPlayerDependencies,
    navigation: TabletDetailNavigation,
    contextPane: Boolean,
    actions: PlayerSheetActions,
    options: TabletPlayerOptions,
) {
    val stableState = remember(dependencies.playbackRepository) {
        dependencies.playbackRepository.playerState.map { it.copy(position = 0, bufferedPosition = 0) }.distinctUntilChanged()
    }
    val state by stableState.collectAsStateWithLifecycle(initialValue = PlayerState())
    val episode = state.currentEpisode ?: return
    val art = PlaybackArtworkResolver.resolveEpisodeImageUrl(episode.imageUrl, episode.podcastImageUrl, state.currentPodcast?.imageUrl)
    val colors = rememberPlayerColors(art)
    var fullscreenVideo by rememberSaveable(episode.id) { mutableStateOf(false) }
    val presentation = playerPresentationController()
    val fullscreenRequest = rememberTabletPlayerFullscreenRequest(episode.id, navigation, presentation) { fullscreenVideo = it }
    val closeImmersive = {
        presentation?.expanded = false
        navigation.immersive = false
    }
    val openImmersive = {
        presentation?.expanded = true
        navigation.immersive = true
    }
    BackHandler(enabled = navigation.immersive, onBack = closeImmersive)
    LaunchedEffect(options.expandTrigger) {
        if (options.expandTrigger > 0 && contextPane) openImmersive()
    }
    CompositionLocalProvider(LocalPlayerFullscreenRequest provides fullscreenRequest) {
    androidx.compose.runtime.key("retained_tablet_player") {
        if (contextPane || navigation.immersive) {
            FullPlayerV2(
                dependencies,
                FullPlayerDisplay(colors.scheme, fullscreenVideo, remember { object : NestedScrollConnection {} }, true, isContextPane = contextPane),
                FullPlayerActions(
                    onFullscreenVideoChange = { fullscreenVideo = it },
                    onCollapse = closeImmersive,
                    onEpisodeInfoClick = actions.onEpisodeInfoClick,
                    onPodcastInfoClick = actions.onPodcastInfoClick,
                    onOpenImmersivePlayer = openImmersive,
                    onDockPlayer = {
                        presentation?.expanded = false
                        navigation.playerDocked = true
                    },
                ),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val metrics = navigationChromeMetrics(options.navigationStyle)
                val navigationClearance = if (options.hasNavigationRail) 0.dp else metrics.bottomNavigationClearance
                val canvas = LocalBoxlorePaneBounds.current
                val dock = LocalBoxloreDockedPlayerBounds.current
                val leftInset = ((dock?.left ?: 0f) - (canvas?.left ?: 0f)).coerceAtLeast(0f).dp
                val rightInset = ((canvas?.right ?: maxWidth.value) - (dock?.right ?: maxWidth.value)).coerceAtLeast(0f).dp
                PlayerSheetScaffold(
                    dependencies.playbackRepository,
                    dependencies.downloadRepository,
                    dependencies.userPrefs,
                    PlayerSheetLayout(
                        collapsedTargetY = with(density) { (maxHeight - bottom - navigationClearance - metrics.miniPlayerHeight - 8.dp).coerceAtLeast(0.dp).toPx() },
                        containerHeight = maxHeight + bottom + 50.dp,
                        collapsedHorizontalPadding = if (options.hasNavigationRail) {
                            maxOf(16.dp, (maxWidth - leftInset - rightInset - 720.dp) / 2)
                        } else {
                            if (options.navigationStyle == NavigationStyle.Floating) 16.dp else 12.dp
                        },
                        navigationStyle = options.navigationStyle,
                        expandTrigger = options.expandTrigger,
                        miniPlayerSeekButtonsEnabled = options.seekButtons,
                        collapsedLeftInset = leftInset,
                        collapsedRightInset = rightInset,
                    ),
                    actions,
                )
            }
        }
    }
    }
}
