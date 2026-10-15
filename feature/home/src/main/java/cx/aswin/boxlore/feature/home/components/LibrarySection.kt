package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
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
    wideWithoutBrief: Boolean = false,
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

    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    val sidebar = tablet && wideWithoutBrief && boxloreContentWidthDp() >= 672f
    val moduleModifier = if (sidebar) modifier.wrapContentWidth().widthIn(max = 1_200.dp) else modifier.boxloreCardWidth(BoxloreCardFamily.Utility)
    Column(modifier = moduleModifier) {
        // --- Header ---
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 12.dp),
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
                    // Explicit, so an artwork-coloured shell cannot leak its foreground into Home.
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // The 48dp button is taller than the title, so it is centred on the title line without
            // setting the row height; otherwise it adds empty space between the hero and this title.
            Box(Modifier.centredWithoutHeight()) {
                HomeChapterBrowseButton(
                    chapter = HomeDiscoveryChapter.LIBRARY,
                    description = stringResource(R.string.home_view_library),
                    onClick = onViewLibrary,
                    icon = Icons.Rounded.ChevronRight,
                )
            }
        }

        // One surface persists across Mix, show filtering, loading and caught-up states.
        YourShowsModuleFrame(
            sidebar = sidebar,
            selectorCount = subscribedPodcasts.list.size + if (subscribedPodcasts.list.size > 1) 1 else 0,
            background = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(homeMixModuleColor()).homeMixBackdrop(enabled = showingMix && hasMixContent),
            selectors = {
                if (sidebar && subscribedPodcasts.list.size == 1) {
                    val onlyShow = subscribedPodcasts.list.single()
                    TabletSingleShowSpotlight(onlyShow, LocalLastSeenEpisodes.current[onlyShow.id], CoverPin(onlyShow.id in pinnedPodcastIds) { onTogglePin(onlyShow.id) }, onPodcastClick)
                } else {
            YourShowsSelectors(
                podcasts = subscribedPodcasts.list,
                selectedPodcastId = selectedPodcastId,
                lastSeenEpisodes = LocalLastSeenEpisodes.current,
                pinnedPodcastIds = pinnedPodcastIds,
                onPodcastSelected = onPodcastSelected,
                onTogglePin = onTogglePin,
                vertical = sidebar,
            )
                }
            },
            content = {
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
                                .padding(
                                    start = HomeMixLayout.ContentInset,
                                    end = HomeMixLayout.ContentInset,
                                    top = HomeMixLayout.TopPadding,
                                    bottom = HomeMixLayout.BottomPadding,
                                ),
                        ) {
                            SelectedShowHeader(
                                podcast = selectedPodcast,
                                canClear = subscribedPodcasts.list.size > 1,
                                onOpenShow = { onPodcastClick(selectedPodcast) },
                                onClear = { onPodcastSelected(null) },
                            )
                            Spacer(modifier = Modifier.height(HomeMixLayout.HeaderGap))

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
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    if (selectedPodcast.isRss && frame.refreshing) {
                                        Text(
                                            text = stringResource(R.string.home_shows_refreshing_rss),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                                    }
                                    val latestTwoIds =
                                        remember(frame.episodes) {
                                            frame.episodes.list
                                                .sortedByDescending { it.publishedDate }
                                                .take(2)
                                                .map { it.id }
                                                .toSet()
                                        }
                                    val visible = frame.episodes.list.take(8)
                                    // The rows and the See all footer form one connected list.
                                    val segments = visible.size + 1
                                    YourShowsEpisodeRows(visible.size, allowGrid = sidebar) { index, columns ->
                                        val episode = visible[index]
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
                                            shape = if (columns > 1) RoundedCornerShape(16.dp) else yourShowsListShape(index, segments),
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
                                    if (!tablet) SeeAllEpisodesRow(shape = yourShowsListShape(segments - 1, segments)) { onPodcastClick(selectedPodcast) }
                                }
                                if (tablet) {
                                    Spacer(Modifier.height(2.dp))
                                    SeeAllEpisodesRow(shape = yourShowsListShape(1, 2)) { onPodcastClick(selectedPodcast) }
                                }
                            }
                        }
                    }
                }
            }
                    },
        )
    }
}

/** The last segment of a selected show's list; opens the show page. */
@Composable
private fun SeeAllEpisodesRow(shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = shape,
        modifier =
        Modifier
            .testTag("home_filtered_see_all")
            .heightIn(min = 52.dp)
            .fillMaxWidth()
            .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = shape, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.home_shows_see_all_episodes),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Reports no height, so the row keeps its text height; the content is centred on the row and overflows evenly. */
private fun Modifier.centredWithoutHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.place(0, -placeable.height / 2) }
}
