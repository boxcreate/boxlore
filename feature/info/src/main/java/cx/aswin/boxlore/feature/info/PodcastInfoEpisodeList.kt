package cx.aswin.boxlore.feature.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.feature.info.components.EpisodeFeedItemRow
import cx.aswin.boxlore.feature.info.components.EpisodeFeedRowUi
import cx.aswin.boxlore.feature.info.components.EpisodeListIndicators
import cx.aswin.boxlore.feature.info.components.EpisodeSelectionUi
import cx.aswin.boxlore.feature.info.components.EpisodeToolbar
import cx.aswin.boxlore.feature.info.components.ToolbarWarningBanner
import cx.aswin.boxlore.feature.info.logic.FeedItem
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
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
    val onDismissWarning: () -> Unit,
    val onWarningAction: () -> Unit,
    val onEpisodeClick: (Episode, String, Int?) -> Unit,
    val onToggleSelection: (Episode) -> Unit,
    val onLongPressSelection: (Episode) -> Unit,
    val onLoadMore: () -> Unit,
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
        item {
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

        item(key = "toolbar") {
            EpisodeToolbar(
                searchQuery = contentState.state.searchQuery,
                onSearchChange = callbacks.onSearchChange,
                isSearching = contentState.state.isSearching,
                currentSort = contentState.state.currentSort,
                onSortToggle = callbacks.onSortToggle,
                isSubscribed = contentState.state.isSubscribed,
                onSubscribeClick = callbacks.onSubscribeClick,
                accentColor = contentState.accentColor,
                supportsReleaseAutomation = !contentState.state.podcast.isRss,
                notificationsEnabled = contentState.state.podcast.notificationsEnabled,
                isSystemNotificationsBlocked = contentState.isSystemNotificationsBlocked,
                onNotificationsToggle = callbacks.onNotificationsToggle,
                autoDownloadEnabled = contentState.state.podcast.autoDownloadEnabled,
                onAutoDownloadToggle = callbacks.onAutoDownloadToggle,
                genre = contentState.state.podcast.genre,
                onSearchFocused = callbacks.onSearchFocused,
            )
        }

        toolbarWarningItem(
            warning = contentState.toolbarWarning,
            onDismiss = callbacks.onDismissWarning,
            onAction = callbacks.onWarningAction,
        )

        episodesSection(
            contentState = contentState,
            callbacks = callbacks,
            viewModel = viewModel,
        )

        if (contentState.state.isLoadingMore && !contentState.state.isRssRefreshing) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxLoreLoader.CircularWavy(size = 32.dp)
                }
            }
        }

        if (contentState.state.searchResults?.isEmpty() == true) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No episodes found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                autoScrolledEpisodeId = contentState.autoScrolledEpisodeId,
                podcastImageUrl = contentState.state.podcast.imageUrl.takeIf { it.isNotEmpty() }
                    ?: contentState.state.podcast.fallbackImageUrl,
            ),
            onEpisodeClick = callbacks.onEpisodeClick,
            selection = EpisodeSelectionUi(
                selectedEpisodeIds = contentState.selectedEpisodeIdSet,
                isActive = contentState.selectionActive,
                onToggle = callbacks.onToggleSelection,
                onLongPress = callbacks.onLongPressSelection,
            ),
        )

        val isLastItem = itemIndex == contentState.feedItems.lastIndex
        val canLoadMore = contentState.state.searchResults == null &&
            isLastItem &&
            contentState.state.hasMoreEpisodes &&
            !contentState.state.isLoadingMore

        if (canLoadMore) {
            LaunchedEffect(contentState.feedItems.size) {
                callbacks.onLoadMore()
            }
        }
    }
}
