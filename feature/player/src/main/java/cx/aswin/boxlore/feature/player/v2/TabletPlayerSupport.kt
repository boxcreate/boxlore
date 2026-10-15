package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.playback.addSameShowContinuationEpisodes
import cx.aswin.boxlore.core.playback.dismissSameShowContinuation
import cx.aswin.boxlore.feature.player.ChaptersSheetContent
import cx.aswin.boxlore.feature.player.QueueSheetContent
import cx.aswin.boxlore.feature.player.R
import cx.aswin.boxlore.feature.player.TranscriptView
import kotlinx.coroutines.launch

internal enum class PlayerSupportingTab(val label: Int) {
    UpNext(R.string.minimal_up_next),
    Chapters(R.string.player_chapters_tab),
    Transcript(R.string.player_transcript_tab),
    Notes(R.string.player_notes_tab),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabletPlayerSupport(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: TabletPlayerResources,
) {
    val colors = display.colorScheme
    Surface(
        modifier = Modifier.nestedScroll(display.sheetNestedScrollConnection).boxloreCardWidth(BoxloreCardFamily.Reading)
            .fillMaxSize().testTag("tablet_player_support"),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.fillMaxSize()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = ui.supportingTab.ordinal,
                containerColor = colors.surfaceContainerLow,
                contentColor = colors.primary,
                edgePadding = 0.dp,
            ) {
                PlayerSupportingTab.entries.forEach { tab ->
                    Tab(
                        selected = ui.supportingTab == tab,
                        onClick = {
                            if (ui.supportingTab != tab) {
                                val event = when (tab) {
                                    PlayerSupportingTab.UpNext -> "queue"
                                    PlayerSupportingTab.Chapters -> "chapters_sheet"
                                    PlayerSupportingTab.Transcript -> "transcript_inline"
                                    PlayerSupportingTab.Notes -> null
                                }
                                event?.let { cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction(it) }
                                ui.supportingTab = tab
                            }
                        },
                        text = { Text(stringResource(tab.label), maxLines = 1) },
                    )
                }
            }
            Crossfade(ui.supportingTab, animationSpec = ExpressiveMotion.QuickSpring, label = "player supporting content") { tab ->
                when (tab) {
                    PlayerSupportingTab.UpNext -> TabletQueueContent(model, dependencies, colors, actions, ui, resources)
                    PlayerSupportingTab.Chapters -> ChaptersSheetContent(
                        chapters = model.state.currentChapters,
                        positionFlow = flows.position,
                        colorScheme = colors,
                        onSeek = {
                            AnalyticsHelper.setSeekSource("chapters_list")
                            dependencies.playbackRepository.seekTo(it)
                        },
                        onClose = {},
                        chaptersUrl = model.episode.chaptersUrl,
                        isChaptersLoading = model.state.isChaptersLoading,
                        showHeader = false,
                    )
                    PlayerSupportingTab.Transcript -> TabletTranscriptContent(model, dependencies, display, flows, ui)
                    PlayerSupportingTab.Notes -> {
                        val notesEmpty = remember(model.episode.description) {
                            HtmlCompat.fromHtml(model.episode.description, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().isBlank()
                        }
                        if (notesEmpty) {
                            TabletPlayerEmptyContent(stringResource(R.string.player_no_notes))
                        } else {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                NotesPreviewCard(model.episode.description, colors, onOpenEpisodeInfo = {
                                    cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("episode_info")
                                    actions.onCollapse()
                                    actions.onEpisodeInfoClick(model.episode)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletQueueContent(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    colors: androidx.compose.material3.ColorScheme,
    actions: FullPlayerActions,
    ui: FullPlayerUiState,
    resources: TabletPlayerResources,
) {
    val sameShowOnly by dependencies.userPrefs.sameShowQueueOnlyStream.collectAsStateWithLifecycle(initialValue = false)
    QueueSheetContent(
        queue = model.state.queue.drop(1),
        currentPodcast = model.podcast,
        colorScheme = colors,
        smartQueueEnabled = !sameShowOnly,
        sameShowContinuation = model.state.sameShowContinuation,
        showHeader = false,
        actions = queueSheetActions(
            dependencies.playbackRepository,
            model.podcast,
            resources.queue,
            ui,
            actions,
            PlayerQueueSheetCallbacks(
                onEnableSmartQueue = { resources.body.scope.launch { dependencies.userPrefs.setSameShowQueueOnly(false) } },
                onAddSameShowEpisodes = { resources.body.scope.launch { dependencies.playbackRepository.addSameShowContinuationEpisodes() } },
                onDismissSameShowBanner = { dependencies.playbackRepository.dismissSameShowContinuation() },
            ),
        ),
    )
}

@Composable
private fun TabletTranscriptContent(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
) {
    if (model.state.currentTranscript.isNotEmpty()) {
        TranscriptView(
            transcript = model.state.currentTranscript,
            positionFlow = flows.position,
            colorScheme = display.colorScheme,
            onSeek = {
                AnalyticsHelper.setSeekSource("transcript_tap")
                dependencies.playbackRepository.seekTo(it)
            },
            isSyncEnabled = ui.isSyncEnabled,
            onSyncEnabledChange = { ui.isSyncEnabled = it },
        )
    } else {
        val loading = model.state.autoTranscriptState in setOf(AutoTranscriptState.CHECKING, AutoTranscriptState.GENERATING)
        TabletPlayerEmptyContent(stringResource(if (loading) R.string.player_preparing_transcript else R.string.player_no_transcript), loading)
    }
}

@Composable
private fun TabletPlayerEmptyContent(message: String, loading: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) BoxLoreLoader.Expressive(size = 40.dp)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
