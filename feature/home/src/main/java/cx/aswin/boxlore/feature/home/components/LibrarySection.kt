package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.StableCompletedDownloadList
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import cx.aswin.boxlore.feature.home.logic.HomeMixModeLogic

val LocalLastSeenEpisodes = androidx.compose.runtime.compositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
fun YourShowsSection(
    subscribedPodcasts: StablePodcastList,
    latestEpisodes: StablePodcastList, // Enriched with latest episodes
    selectedPodcastId: String?,
    selectedPodcastEpisodes: StableEpisodeList,
    isSelectedPodcastLoading: Boolean,
    isSelectedRssRefreshing: Boolean,
    episodePlaybackState: StablePlaybackStateMap,
    softExpireProgressEpisodeIds: Set<String> = emptySet(),
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    onPodcastSelected: (String?) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayMix: (HomeMixMode) -> Unit,
    onMixModeChanged: (HomeMixMode) -> Unit,
    selectedMixMode: HomeMixMode,
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
    onViewLibrary: () -> Unit,
    onViewDownloads: () -> Unit,
    downloadedEpisodeIds: Set<String> = emptySet(),
    completedDownloads: StableCompletedDownloadList = StableCompletedDownloadList(emptyList()),
    modifier: Modifier = Modifier,
    pinnedPodcastIds: Set<String> = emptySet(),
    onTogglePin: (String) -> Unit = {},
) {
    if (subscribedPodcasts.list.isEmpty()) return
    val contentTarget = selectedPodcastId ?: subscribedPodcasts.list.singleOrNull()?.id
    val showingMix = selectedPodcastId == null && subscribedPodcasts.list.size > 1
    val mixMode = HomeMixModeLogic.resolveMode(
        selectedMixMode,
        HomeMixModeLogic.canOfferOffline(subscribedPodcasts.list.size, completedDownloads.list.size),
    )
    val hasMixContent = when (mixMode) {
        HomeMixMode.DAILY -> latestEpisodes.list.any { it.latestEpisode != null }
        HomeMixMode.OFFLINE -> HomeMixModeLogic.visibleOfflineItems(completedDownloads.list).isNotEmpty()
    }

    Column(modifier = modifier) {
        // --- Header ---
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier.expressiveClickable {
                    if (selectedPodcastId != null) {
                        onPodcastSelected(null) // clear selection
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bookmarks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_your_shows_heading),
                    style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = rememberSectionHeaderFontFamily(),
                        fontWeight = GoogleSansWeight.semiBold,
                    ),
                    letterSpacing = (-0.5).sp,
                )
            }

            HomeChapterBrowseButton(
                chapter = HomeDiscoveryChapter.LIBRARY,
                description = stringResource(R.string.home_view_library),
                onClick = onViewLibrary,
                icon = Icons.Rounded.ChevronRight,
            )
        }

        val showPureSurfaceBorder = LocalSurfaceStyle.current in setOf(
            SurfaceStyles.AMOLED,
            SurfaceStyles.PURE_WHITE,
            SurfaceStyles.DYNAMIC_OLED_WHITE,
        )
        // One surface persists across Mix, show filtering, loading and caught-up states.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .homeMixBackdrop(enabled = showingMix && hasMixContent)
                .then(
                    if (showPureSurfaceBorder) {
                        Modifier.border(
                        1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(30.dp),
                    )
                    } else {
                        Modifier
                    },
                )
                .testTag("home_your_shows_surface"),
        ) {
            YourShowsSelectors(
                podcasts = subscribedPodcasts.list,
                selectedPodcastId = selectedPodcastId,
                lastSeenEpisodes = LocalLastSeenEpisodes.current,
                pinnedPodcastIds = pinnedPodcastIds,
                onPodcastSelected = onPodcastSelected,
                onTogglePin = onTogglePin,
            )
            AnimatedContent(
                targetState = contentTarget,
                transitionSpec = { yourShowsContentTransform(forward = targetState != null) },
                label = "shows_mode_transition",
                modifier = Modifier.fillMaxWidth(),
            ) { activeShowId ->
                if (activeShowId == null) {
                    HomeMixModule(
                        dailyPodcasts = latestEpisodes.list,
                        subscribedPodcastCount = subscribedPodcasts.list.size,
                        completedDownloads = completedDownloads,
                        selectedMode = selectedMixMode,
                        episodePlaybackState = episodePlaybackState,
                        softExpireProgressEpisodeIds = softExpireProgressEpisodeIds,
                        currentPlayingEpisodeId = currentPlayingEpisodeId,
                        isPlaying = isPlaying,
                        downloadedEpisodeIds = downloadedEpisodeIds,
                        onModeChanged = onMixModeChanged,
                        onPlayMix = onPlayMix,
                        onEpisodeClick = onEpisodeClick,
                        onPlayEpisode = onPlayEpisode,
                        onViewDownloads = onViewDownloads,
                    )
                } else {
                    // Scenario B: Filtered State (A Specific Sub is Selected) / Scenario C: Only 1 Sub Edge Case
                    val activeId = activeShowId
                    val filteredScrollState = rememberScrollState()
                    val filteredScrollConnection = remember(filteredScrollState) {
                        HomeEpisodeListScrollConnection(
                            canScrollForward = { filteredScrollState.canScrollForward },
                            canScrollBackward = { filteredScrollState.canScrollBackward },
                        )
                    }
                    val frame = retainExitingShowsFrame(
                        active = activeId == contentTarget,
                        current = SelectedShowPresentation(selectedPodcastEpisodes, isSelectedPodcastLoading, isSelectedRssRefreshing),
                    )
                    val selectedPodcast = subscribedPodcasts.list.find { it.id == activeId }

                    if (selectedPodcast == null) {
                        Text(
                            text = stringResource(R.string.home_shows_no_episodes),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .animateContentSize(YourShowsMotion.spatial())
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        ) {
                            SelectedShowHeader(
                                podcast = selectedPodcast,
                                canClear = subscribedPodcasts.list.size > 1,
                                onOpenShow = { onPodcastClick(selectedPodcast) },
                                onClear = { onPodcastSelected(null) },
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if ((frame.loading || frame.refreshing) &&
                                frame.episodes.list.isEmpty()
                            ) {
                                Box(
                                    modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BoxLoreLoader.Expressive(size = 48.dp)
                                }
                            } else if (frame.episodes.list.isEmpty()) {
                                Box(
                                    modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_shows_no_episodes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Column(
                                    modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 356.dp)
                                        .testTag("home_filtered_episodes")
                                        .observeEpisodeListGestures(filteredScrollConnection)
                                        .nestedScroll(filteredScrollConnection)
                                        .verticalScroll(filteredScrollState),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (selectedPodcast.isRss && frame.refreshing) {
                                        Text(
                                            text = stringResource(R.string.home_shows_refreshing_rss),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    val latestTwoIds =
                                        remember(frame.episodes) {
                                            frame.episodes.list
                                                .sortedByDescending { it.publishedDate }
                                                .take(2)
                                                .map { it.id }
                                                .toSet()
                                        }
                                    frame.episodes.list.take(8).forEach { episode ->
                                        val state = episodePlaybackState.map[episode.id]
                                        DenseEpisodeRow(
                                            episode = episode,
                                            podcast = selectedPodcast,
                                            actions =
                                            DenseEpisodeRowActions(
                                                onClick = { onEpisodeClick(episode, selectedPodcast, "home_filtered_latest_episodes") },
                                                onPlay = {
                                                    onPlayEpisode(
                                                        episode,
                                                        selectedPodcast,
                                                        cx.aswin.boxlore.core.model.PlaybackEntryPoint.GENERIC,
                                                    )
                                                },
                                            ),
                                            showPodcastTitle = false,
                                            playback =
                                            DenseEpisodeRowPlayback(
                                                overrideStatus = state?.first,
                                                overrideProgress = state?.second,
                                                currentPlayingEpisodeId = currentPlayingEpisodeId,
                                                isPlaying = isPlaying,
                                            ),
                                            isEligibleForNewTag = episode.id in latestTwoIds,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        shape = RoundedCornerShape(20.dp),
                                        modifier =
                                        Modifier
                                            .testTag("home_filtered_see_all")
                                            .heightIn(min = 48.dp)
                                            .fillMaxWidth()
                                            .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = RoundedCornerShape(20.dp)) { onPodcastClick(selectedPodcast) },
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.home_shows_see_all_episodes),
                                                style =
                                                MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = GoogleSansWeight.bold,
                                                    letterSpacing = 0.1.sp,
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
