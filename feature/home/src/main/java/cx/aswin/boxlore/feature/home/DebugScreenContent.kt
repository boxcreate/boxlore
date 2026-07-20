package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.ranking.LearnerInspectorSnapshot
import cx.aswin.boxlore.core.ranking.LearningEvent
import cx.aswin.boxlore.core.ranking.RankingShadowSnapshot
import kotlinx.coroutines.launch

private enum class DebugTab(
    val label: String,
    val icon: ImageVector,
) {
    Learner("Learner", Icons.Rounded.AutoAwesome),
    Sleep("Sleep", Icons.Rounded.Bedtime),
    Playback("Playback", Icons.Rounded.PlayCircle),
    Database("Database", Icons.Rounded.Storage),
    Flags("Flags", Icons.Rounded.CleaningServices),
}

internal data class DebugScreenContentState(
    val skipSleepWindow: Boolean,
    val isInNightWindow: Boolean,
    val playerState: PlayerState,
    val learnerSnapshot: LearnerInspectorSnapshot?,
    val learnerLoading: Boolean,
    val learningEvents: List<LearningEvent>,
    val logEnabled: Boolean,
    val shadowDiagnostics: List<RankingShadowSnapshot>,
    val history: List<DebugHistoryItem>,
    val podcasts: List<PodcastEntity>,
    val scrollBottomPadding: Dp,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugScreenContent(
    viewModel: DebugViewModel,
    state: DebugScreenContentState,
    modifier: Modifier = Modifier,
) {
    val tabs = DebugTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(tab.label) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            DebugScreenPagerPage(
                tab = tabs[page],
                viewModel = viewModel,
                state = state,
            )
        }
    }
}

@Composable
private fun DebugScreenPagerPage(
    tab: DebugTab,
    viewModel: DebugViewModel,
    state: DebugScreenContentState,
) {
    when (tab) {
        DebugTab.Learner ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                AdaptiveLearnerDebugSection(
                    state =
                        AdaptiveLearnerDebugState(
                            snapshot = state.learnerSnapshot,
                            events = state.learningEvents,
                            logEnabled = state.logEnabled,
                            shadowDiagnostics = state.shadowDiagnostics,
                            loading = state.learnerLoading,
                        ),
                    onSetLogEnabled = viewModel::setLogEnabled,
                    onRefresh = viewModel::refreshLearnerSnapshot,
                    bottomContentPadding = state.scrollBottomPadding,
                )
            }

        DebugTab.Sleep ->
            DebugTabScrollPane(bottomContentPadding = state.scrollBottomPadding) {
                SleepTestingSection(
                    state =
                        SleepTestingState(
                            skipSleepWindow = state.skipSleepWindow,
                            isInNightWindow = state.isInNightWindow,
                            showLateNightNudge = state.playerState.showLateNightNudge,
                            sleepTimerEnd = state.playerState.sleepTimerEnd,
                            sleepAtEndOfEpisode = state.playerState.sleepAtEndOfEpisode,
                        ),
                    actions =
                        SleepTestingActions(
                            onToggleSkipSleepWindow = viewModel::setSkipSleepWindow,
                            onForcePromptNow = viewModel::forceSleepPromptNow,
                            onClearSleepTimer = viewModel::clearSleepTimer,
                            onResetSleepWindowGuard = viewModel::resetSleepWindowGuard,
                        ),
                )
            }

        DebugTab.Playback ->
            DebugTabScrollPane(bottomContentPadding = state.scrollBottomPadding) {
                PlaybackStateSection(
                    episodeTitle = state.playerState.currentEpisode?.title,
                    podcastTitle = state.playerState.currentPodcast?.title,
                    isPlaying = state.playerState.isPlaying,
                    isLoading = state.playerState.isLoading,
                    position = state.playerState.position,
                    duration = state.playerState.duration,
                )
            }

        DebugTab.Database ->
            DebugTabScrollPane(bottomContentPadding = state.scrollBottomPadding) {
                DbInspectorSection(
                    history = state.history,
                    podcasts = state.podcasts,
                    onDeleteHistoryItem = viewModel::deleteHistoryItem,
                )
            }

        DebugTab.Flags ->
            DebugTabScrollPane(bottomContentPadding = state.scrollBottomPadding) {
                FlagsCacheSection(
                    onResetFeatureFlag = viewModel::resetFeatureFlag,
                    onClearDismissedCuriosities = viewModel::clearDismissedCuriosities,
                )
            }
    }
}
