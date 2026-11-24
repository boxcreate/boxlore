package cx.aswin.boxlore.feature.info

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.info.branded.BrandedShowHeader
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
import cx.aswin.boxlore.feature.info.branded.brandedEpisodePicture
import cx.aswin.boxlore.feature.info.branded.brandedShowPicture
import cx.aswin.boxlore.feature.info.branded.brandedSpotlightKey
import cx.aswin.boxlore.feature.info.components.EpisodeFeedItemRow
import cx.aswin.boxlore.feature.info.components.EpisodeFeedRowUi
import cx.aswin.boxlore.feature.info.components.EpisodeListIndicators
import cx.aswin.boxlore.feature.info.components.EpisodeListSkeleton
import cx.aswin.boxlore.feature.info.components.EpisodePlayStateWrapper
import cx.aswin.boxlore.feature.info.components.EpisodeSelectionUi
import cx.aswin.boxlore.feature.info.components.EpisodeToolbar
import cx.aswin.boxlore.feature.info.components.PodcastEpisodeActionsCallbacks
import cx.aswin.boxlore.feature.info.components.PodcastEpisodeActionsState
import cx.aswin.boxlore.feature.info.components.PodcastInfoEpisodeSearch
import cx.aswin.boxlore.feature.info.components.ShowCaughtUpCard
import cx.aswin.boxlore.feature.info.components.ShowFeedStatusLine
import cx.aswin.boxlore.feature.info.components.ShowListStatusLine
import cx.aswin.boxlore.feature.info.components.ShowPageLoadFailedRow
import cx.aswin.boxlore.feature.info.components.ToolbarWarningBanner
import cx.aswin.boxlore.feature.info.components.podcastEpisodeCardProgress
import cx.aswin.boxlore.feature.info.components.showFeedStatusText
import cx.aswin.boxlore.feature.info.logic.FeedItem
import cx.aswin.boxlore.feature.info.logic.HideCompletedFill
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import cx.aswin.boxlore.feature.info.logic.podcastSearchCoverage
import cx.aswin.boxlore.feature.info.logic.podcastSearchHasNoResults
import cx.aswin.boxlore.feature.info.logic.podcastSearchShowsFailure
import cx.aswin.boxlore.feature.info.sections.PodcastInfoHeroSection

internal data class EpisodeListContentState(
    val listState: LazyListState,
    val modifier: Modifier,
    val contentPadding: PaddingValues,
    val state: PodcastInfoUiState.Success,
    val sortedPersons: List<Person>,
    val isDescExpanded: Boolean,
    val accentColor: Color,
    val isSystemNotificationsBlocked: Boolean,
    val toolbarWarning: ToolbarWarning,
    val feedItems: List<FeedItem>,
    val episodeListIndicators: EpisodeListIndicators,
    val autoScrolledEpisodeId: String?,
    val selectedEpisodeIdSet: Set<String>,
    val selectionActive: Boolean,
    val isSearchActive: Boolean,
    val searchFocusRequester: FocusRequester,
    val hideCompletedFill: HideCompletedFill.State = HideCompletedFill.State.NONE,
    /** A branded page's latest episode, shown in its spotlight card instead of the list. */
    val spotlightEpisode: Episode? = null,
    /** The show is hidden everywhere: a note under its actions says so and offers Unhide. */
    val isHidden: Boolean = false,
    /** Episode titles only: short title rows and matching loading rows. */
    val titlesOnly: Boolean = false,
)

internal data class EpisodeListContentCallbacks(
    val onDescExpandedChange: (Boolean) -> Unit,
    val onPlayEpisode: (Episode) -> Unit,
    val onPodcastClick: (String) -> Unit,
    val onEditGenre: () -> Unit,
    val onSearchChange: (String) -> Unit,
    val onSortToggle: () -> Unit,
    val onSubscribeClick: () -> Unit,
    val onNotificationsToggle: () -> Unit,
    val onAutoDownloadToggle: () -> Unit,
    val onSearchFocused: () -> Unit,
    val onSearchClose: () -> Unit,
    val onDismissWarning: () -> Unit,
    val onWarningAction: () -> Unit,
    val onEpisodeClick: (Episode, String, Int?) -> Unit,
    val onToggleSelection: (Episode) -> Unit,
    val onLongPressSelection: (Episode) -> Unit,
    val onLoadMore: () -> Unit,
    val onShowCompleted: () -> Unit = {},
    /** Try again after a page of episodes couldn't load ([PodcastInfoUiState.Success.pageLoadFailed]). */
    val onRetryPage: () -> Unit = {},
    val onUnhide: () -> Unit = {},
)

@Composable
internal fun PodcastInfoEpisodeList(
    contentState: EpisodeListContentState,
    callbacks: EpisodeListContentCallbacks,
    viewModel: PodcastInfoViewModel,
) {
    LazyColumn(
        state = contentState.listState,
        modifier = contentState.modifier,
        contentPadding = contentState.contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        heroItem(contentState, callbacks)

        item(key = "toolbar") {
            AnimatedContent(
                targetState = contentState.isSearchActive,
                transitionSpec = { fadeIn(tween(180, delayMillis = 60)).togetherWith(fadeOut(tween(120))) },
                label = "podcast_episode_search",
            ) { searching ->
                if (searching) {
                    Box(Modifier.fillMaxWidth()) {
                        PodcastInfoEpisodeSearch(
                            podcastTitle = contentState.state.podcast.title,
                            query = contentState.state.searchQuery,
                            onQueryChange = callbacks.onSearchChange,
                            onClose = callbacks.onSearchClose,
                            focusRequester = contentState.searchFocusRequester,
                        )
                        if (contentState.state.isSearching) {
                            LinearProgressIndicator(
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .padding(horizontal = 40.dp, vertical = 2.dp).height(2.dp),
                            )
                        }
                    }
                } else {
            EpisodeToolbar(
                searchQuery = contentState.state.searchQuery,
                onSearchChange = callbacks.onSearchChange,
                isSearching = contentState.state.isSearching,
                currentSort = contentState.state.currentSort,
                onSortToggle = callbacks.onSortToggle,
                isSubscribed = contentState.state.isSubscribed,
                onSubscribeClick = callbacks.onSubscribeClick,
                accentColor = contentState.accentColor,
                supportsReleaseAutomation = true,
                notificationsEnabled = contentState.state.podcast.notificationsEnabled,
                isSystemNotificationsBlocked = contentState.isSystemNotificationsBlocked,
                onNotificationsToggle = callbacks.onNotificationsToggle,
                autoDownloadEnabled = contentState.state.podcast.autoDownloadEnabled,
                onAutoDownloadToggle = callbacks.onAutoDownloadToggle,
                genre = contentState.state.podcast.genre,
                onSearchFocused = callbacks.onSearchFocused,
            )
                }
            }
        }

        toolbarWarningItem(
            warning = contentState.toolbarWarning,
            onDismiss = callbacks.onDismissWarning,
            onAction = callbacks.onWarningAction,
        )

        if (contentState.isHidden && !contentState.isSearchActive) {
            item(key = "hidden_note") { PodcastInfoHiddenNote(onUnhide = callbacks.onUnhide, modifier = Modifier.animateItem()) }
        }

        brandedSpotlightItem(contentState, callbacks, viewModel)

        feedStatusItem(contentState.state.feedStatus, contentState.hideCompletedFill, contentState.isSearchActive)

        if (!contentState.isSearchActive && contentState.hideCompletedFill == HideCompletedFill.State.CAUGHT_UP) {
            item(key = "caught_up") {
                ShowCaughtUpCard(onShowCompleted = callbacks.onShowCompleted, modifier = Modifier.animateItem())
            }
        }

        searchCoverageItem(contentState)

        episodesSection(
            contentState = contentState,
            callbacks = callbacks,
            viewModel = viewModel,
        )

        if (showsPageFailed(contentState)) {
            item(key = "page_failed") {
                ShowPageLoadFailedRow(
                    listEmpty = contentState.state.episodes.isEmpty(),
                    onRetry = callbacks.onRetryPage,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (showsLoadingCards(contentState)) {
            // Loading more, or every episode for oldest first: cards in the list's own shape.
            items(count = LOADING_CARDS, key = { "loading_card_$it" }) {
                EpisodeListSkeleton(contentState.titlesOnly, Modifier.padding(horizontal = 16.dp).animateItem())
            }
        }

        searchFailedItem(contentState, callbacks)
        searchNoResultsItem(contentState)
    }
}

/** A finished search with no matches. */
private fun LazyListScope.searchNoResultsItem(contentState: EpisodeListContentState) {
    val state = contentState.state
    if (!podcastSearchHasNoResults(contentState.isSearchActive, state.searchQuery, state.isSearching, state.searchResults, state.searchFailed)) return
    item {
        Box(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.podcast_info_no_search_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val LOADING_CARDS = 2

/** A search of a show longer than the server lists: says how many of the newest episodes it covered, above its results. */
private fun LazyListScope.searchCoverageItem(contentState: EpisodeListContentState) {
    val state = contentState.state
    val searched =
        podcastSearchCoverage(contentState.isSearchActive, state.searchQuery, state.isSearching, state.searchFailed, state.searchedNewest) ?: return
    item(key = "search_coverage") {
        Text(
            text = pluralStringResource(R.plurals.podcast_info_search_newest_only, searched, LocaleFormats.number(searched.toLong(), currentLocale())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

/** A search that couldn't read the show's episodes: says so and runs the same search again on Try again. */
private fun LazyListScope.searchFailedItem(contentState: EpisodeListContentState, callbacks: EpisodeListContentCallbacks) {
    val state = contentState.state
    if (!podcastSearchShowsFailure(contentState.isSearchActive, state.searchQuery, state.isSearching, state.searchFailed)) return
    item(key = "search_failed") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.podcast_info_search_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { callbacks.onSearchChange(state.searchQuery) }) {
                Text(stringResource(R.string.podcast_info_retry))
            }
        }
    }
}

private fun showsPageFailed(contentState: EpisodeListContentState): Boolean = contentState.state.pageLoadFailed &&
    !contentState.isSearchActive &&
    contentState.state.searchResults == null &&
    !contentState.state.isLoadingMore

private fun showsLoadingCards(contentState: EpisodeListContentState): Boolean = !contentState.isSearchActive &&
    !contentState.state.isRssRefreshing &&
    (contentState.state.isLoadingMore || contentState.hideCompletedFill == HideCompletedFill.State.FINDING)

/**
 * Feed-first show pages (P4 PR 16): reading the feed, the server's list instead, or offline. With
 * completed episodes hidden and more pages loading to find unfinished ones, the line says that
 * unless a feed read is already saying something busier.
 */
private fun LazyListScope.feedStatusItem(status: ShowFeedStatus, fill: HideCompletedFill.State, searching: Boolean) {
    if (showFeedStatusText(status) != null) {
        item(key = "feed_status") {
            ShowFeedStatusLine(status = status, modifier = Modifier.animateItem())
        }
    } else if (fill == HideCompletedFill.State.FINDING && !searching) {
        item(key = "feed_status") {
            ShowListStatusLine(
                text = stringResource(R.string.podcast_info_hide_finding),
                busy = true,
                icon = Icons.Rounded.Info,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** The show's header: a branded page's own, or the regular hero. */
private fun LazyListScope.heroItem(contentState: EpisodeListContentState, callbacks: EpisodeListContentCallbacks) {
    item {
        val brandedPage = LocalBrandedPage.current?.page
        if (brandedPage != null) {
            BrandedShowHeader(
                page = brandedPage,
                state = contentState.state,
                sortedPersons = contentState.sortedPersons,
                isDescExpanded = contentState.isDescExpanded,
                onDescExpandedChange = callbacks.onDescExpandedChange,
                onPlayEpisode = callbacks.onPlayEpisode,
                onPodcastClick = callbacks.onPodcastClick,
                onEditGenre = callbacks.onEditGenre,
            )
        } else {
            PodcastInfoHeroSection(
                state = contentState.state,
                sortedPersons = contentState.sortedPersons,
                isDescExpanded = contentState.isDescExpanded,
                onDescExpandedChange = callbacks.onDescExpandedChange,
                onPlayEpisode = callbacks.onPlayEpisode,
                onPodcastClick = callbacks.onPodcastClick,
                onEditGenre = callbacks.onEditGenre,
            )
        }
    }
}

/** A branded page's latest episode, with the same states and actions as its card in the list. */
private fun LazyListScope.brandedSpotlightItem(
    contentState: EpisodeListContentState,
    callbacks: EpisodeListContentCallbacks,
    viewModel: PodcastInfoViewModel,
) {
    val episode = contentState.spotlightEpisode ?: return
    item(key = brandedSpotlightKey(episode)) {
        val brandedPage = LocalBrandedPage.current?.page ?: return@item
        val indicators = contentState.episodeListIndicators
        EpisodePlayStateWrapper(episodeId = episode.id, playbackStateFlow = viewModel.episodePlaybackState) { playState ->
            val isResume = playState?.isResume == true
            val isCompleted = episode.id in indicators.completedEpisodeIds
            val podcast = contentState.state.podcast
            val spotlight = BrandedSpotlight(
                episode = episode,
                episodePicture = brandedEpisodePicture(episode, podcast),
                showPicture = brandedShowPicture(podcast),
                actions = PodcastEpisodeActionsState(
                    isPlaying = playState?.isPlaying == true,
                    isResume = isResume,
                    isLiked = episode.id in indicators.likedEpisodeIds,
                    isDownloaded = episode.id in indicators.downloadedEpisodeIds,
                    isDownloading = episode.id in indicators.downloadingEpisodeIds,
                    isQueued = episode.id in indicators.queuedEpisodeIds,
                ),
                callbacks = PodcastEpisodeActionsCallbacks(
                    onPlay = { viewModel.onPlayClick(episode) },
                    onLike = { viewModel.onToggleLike(episode) },
                    onDownload = { viewModel.toggleDownload(episode) },
                    onQueue = { viewModel.toggleQueue(episode) },
                ),
                isCompleted = isCompleted,
                progress = podcastEpisodeCardProgress(isResume, isCompleted, playState?.progress ?: 0f),
                timeLeft = playState?.timeLeft?.asString(),
                isSelected = episode.id in contentState.selectedEpisodeIdSet,
                onOpen = {
                    if (contentState.selectionActive) {
                        callbacks.onToggleSelection(episode)
                    } else {
                        viewModel.recordEpisodeClick(episode.id)
                        callbacks.onEpisodeClick(episode, "podcast_info_episodes_list", 0)
                    }
                },
                onLongPress = { callbacks.onLongPressSelection(episode) },
            )
            // Every spotlight stops at a comfortable width on wide pages: a 480dp card and its 16dp sides,
            // centred as a card, or on the page's start edge when it is drawn open on the page.
            val alignment = if (brandedPage.spotlightIsCard) Alignment.CenterHorizontally else Alignment.Start
            Box(Modifier.animateItem().fillMaxWidth().wrapContentWidth(alignment).widthIn(max = BrandedSpotlightMaxWidth)) {
                brandedPage.Spotlight(spotlight, Modifier.testTag(PODCAST_INFO_BRANDED_SPOTLIGHT_TAG))
            }
        }
    }
}

private fun LazyListScope.toolbarWarningItem(
    warning: ToolbarWarning,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    if (warning != ToolbarWarning.NONE) {
        item(key = "toolbar_warning") {
            ToolbarWarningBanner(
                warning = warning,
                onDismiss = onDismiss,
                onAction = onAction,
            )
        }
    }
}

private fun LazyListScope.episodesSection(
    contentState: EpisodeListContentState,
    callbacks: EpisodeListContentCallbacks,
    viewModel: PodcastInfoViewModel,
) {
    itemsIndexed(
        items = contentState.feedItems,
        key = { _, item -> item.id },
    ) { itemIndex, feedItem ->
        EpisodeFeedItemRow(
            feedItem = feedItem,
            viewModel = viewModel,
            ui = EpisodeFeedRowUi(
                accentColor = contentState.accentColor,
                indicators = contentState.episodeListIndicators,
                autoScrolledEpisodeId = contentState.autoScrolledEpisodeId.takeUnless { contentState.isSearchActive },
                podcastImageUrl = contentState.state.podcast.imageUrl.takeIf { it.isNotEmpty() }
                    ?: contentState.state.podcast.fallbackImageUrl,
                isSearchActive = contentState.isSearchActive,
                titlesOnly = contentState.titlesOnly,
            ),
            onEpisodeClick = callbacks.onEpisodeClick,
            selection = EpisodeSelectionUi(
                selectedEpisodeIds = contentState.selectedEpisodeIdSet,
                isActive = contentState.selectionActive,
                onToggle = callbacks.onToggleSelection,
                onLongPress = callbacks.onLongPressSelection,
            ),
            modifier = Modifier.animateItem().animateContentSize(),
        )

        val isLastItem = itemIndex == contentState.feedItems.lastIndex
        val canLoadMore = !contentState.isSearchActive &&
            contentState.state.searchResults == null &&
            isLastItem &&
            contentState.state.hasMoreEpisodes &&
            !contentState.state.isLoadingMore &&
            !contentState.state.pageLoadFailed

        if (canLoadMore) {
            LaunchedEffect(contentState.feedItems.size) {
                callbacks.onLoadMore()
            }
        }
    }
}

private val BrandedSpotlightMaxWidth = 512.dp

internal const val PODCAST_INFO_BRANDED_SPOTLIGHT_TAG = "podcast_info_branded_spotlight"
