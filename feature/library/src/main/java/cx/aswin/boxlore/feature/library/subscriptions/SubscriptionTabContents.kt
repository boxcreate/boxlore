package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.layout.boxlorePosterColumns
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.library.LibraryEmptyContent
import cx.aswin.boxlore.feature.library.LibraryEmptyState
import cx.aswin.boxlore.feature.library.LibraryPlaybackLayout
import cx.aswin.boxlore.feature.library.LocalLastSeenEpisodes
import cx.aswin.boxlore.feature.library.PlayAllFab
import cx.aswin.boxlore.feature.library.R
import cx.aswin.boxlore.feature.library.SubscriptionSort
import cx.aswin.boxlore.feature.library.libraryGridWidthDp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Callbacks for [ShowsTabContent], grouped to keep the composable parameter list small. */
internal data class ShowsTabActions(
    val onExploreClick: () -> Unit,
    val onPodcastClick: (String) -> Unit,
    val onPodcastLongClick: (Podcast) -> Unit = {},
    val onReorder: (orderedIds: List<String>) -> Unit = {},
    val onReorderFolders: (orderedFolderIds: List<String>) -> Unit = {},
    val onNewFolderClick: (() -> Unit)? = null,
    val onFolderClick: (String) -> Unit = {},
    val onFolderLongClick: (SubscriptionFolder) -> Unit = {},
) {
    fun toFolderCardActions(): FolderCardActions = FolderCardActions(
        onPodcastClick = onPodcastClick,
        onFolderClick = onFolderClick,
        onFolderLongClick = onFolderLongClick,
    )
}

/** Configuration for [ShowsTabContent], grouped to keep the composable parameter list small. */
internal data class ShowsTabConfig(
    val isGridView: Boolean,
    val canReorder: Boolean = false,
    val reorderMode: ReorderMode = ReorderMode.Inactive,
    val pinnedPodcastIds: Set<String> = emptySet(),
    val sort: SubscriptionSort = SubscriptionSort.SmartRank,
    val manualOrder: List<String> = emptyList(),
    val folderSort: FolderInterSort = FolderInterSort.Inherit,
    val folderManualOrder: List<String> = emptyList(),
    val intraFolderSort: FolderIntraSort = FolderIntraSort.Inherit,
    val smartOrderIds: List<String> = emptyList(),
    /** On tablets, room under the last row for the floating tab selector (0 when the tabs are docked). */
    val floatingTabsClearance: Dp = 0.dp,
)

internal data class ShowsGridConfig(
    val reorderMode: ReorderMode,
    val pinnedPodcastIds: Set<String>,
    val floatingTabsClearance: Dp = 0.dp,
) {
    val isFoldersReordering: Boolean get() = reorderMode is ReorderMode.Folders
    val isRootReordering: Boolean get() = reorderMode is ReorderMode.RootShows
}

@Composable
private fun rememberShowsPartition(
    podcasts: List<Podcast>,
    folders: List<SubscriptionFolder>,
    selectedGenre: String,
    config: ShowsTabConfig,
): PartitionedSubscriptionItems {
    val filteredPodcasts = remember(podcasts, selectedGenre) { filterPodcastsByGenre(podcasts, selectedGenre) }
    val distinctPodcasts = remember(filteredPodcasts) { filteredPodcasts.distinctBy { it.id } }
    val filteredFolders = remember(folders, selectedGenre, podcasts) {
        filterFoldersByGenre(folders, selectedGenre, podcasts)
    }
    return remember(
        distinctPodcasts,
        filteredFolders,
        config.sort,
        config.folderSort,
        config.folderManualOrder,
        config.intraFolderSort,
        config.smartOrderIds,
    ) {
        partitionSubscribedShows(
            podcasts = distinctPodcasts,
            folders = filteredFolders,
            sort = config.sort,
            folderSort = config.folderSort,
            folderManualOrder = config.folderManualOrder,
            intraFolderSort = config.intraFolderSort,
            smartOrderIds = config.smartOrderIds,
        )
    }
}

@Composable
private fun rememberOrderedFolderKeys(
    folders: List<SubscriptionFolder>,
    folderManualOrder: List<String>,
): MutableState<List<String>> = remember(folders, folderManualOrder) {
    mutableStateOf(
        if (folderManualOrder.isNotEmpty()) {
            val existing = folders.map { it.id }.toSet()
            val ordered = folderManualOrder.filter { it in existing }
            val remainder = folders.map { it.id }.filter { it !in ordered }
            ordered + remainder
        } else {
            folders.map { it.id }
        },
    )
}

@Composable
internal fun ShowsTabContent(
    podcasts: List<Podcast>,
    folders: List<SubscriptionFolder>,
    config: ShowsTabConfig,
    actions: ShowsTabActions,
) {
    if (podcasts.isEmpty()) {
        LibraryEmptyState(
            content = LibraryEmptyContent(
                title = stringResource(R.string.library_subscriptions_empty_title),
                body = stringResource(R.string.library_subscriptions_empty_body),
                icon = Icons.Rounded.Podcasts,
                shape = ExpressiveShapes.Circle,
                actionText = stringResource(R.string.library_find_podcasts),
            ),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            onAction = actions.onExploreClick,
            modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
        )
        return
    }
    val distinctGenres = remember(podcasts) { extractDistinctGenres(podcasts) }
    var selectedGenre by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(distinctGenres) {
        val stillAvailable = selectedGenre.equals("All", ignoreCase = true) ||
            distinctGenres.any { it.equals(selectedGenre, ignoreCase = true) }
        if (!stillAvailable) selectedGenre = "All"
    }
    val partition = rememberShowsPartition(podcasts, folders, selectedGenre, config)
    val unfiledPodcasts = partition.unfiledPodcasts
    val isFoldersReordering = config.reorderMode is ReorderMode.Folders
    val isRootReordering = config.reorderMode is ReorderMode.RootShows
    val isManualSort = config.sort == SubscriptionSort.Manual

    var orderedKeys by rememberShowsOrderedKeys(
        unfiledPodcasts = unfiledPodcasts,
        manualOrder = config.manualOrder,
        isManualSort = isManualSort,
        isRootReordering = isRootReordering,
    )
    val orderedPodcasts = remember(orderedKeys, unfiledPodcasts, isManualSort, isRootReordering) {
        resolveOrderedPodcasts(
            unfiledPodcasts = unfiledPodcasts,
            orderedKeys = orderedKeys,
            isManualSort = isManualSort,
            isRootReordering = isRootReordering,
        )
    }

    var orderedFolderKeys by rememberOrderedFolderKeys(folders, config.folderManualOrder)

    val onMove = rememberShowsMoveHandler(
        isFoldersReordering = isFoldersReordering,
        isRootReordering = isRootReordering,
        orderedFolderKeys = orderedFolderKeys,
        onFolderKeysChange = { orderedFolderKeys = it },
        orderedKeys = orderedKeys,
        onKeysChange = { orderedKeys = it },
        actions = actions,
    )

    val genreChips: @Composable () -> Unit = {
        SubscriptionGenreChips(
            selectedGenre = selectedGenre,
            onGenreChange = {
                selectedGenre = it
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(it, "shows")
            },
            distinctGenres = distinctGenres,
            podcasts = podcasts,
            contentPadding = PaddingValues(horizontal = if (config.isGridView) 0.dp else 16.dp),
            onNewFolderClick = actions.onNewFolderClick,
        )
    }

    val folderItems = rememberShowsFolderItems(
        partition = partition,
        orderedFolderKeys = orderedFolderKeys,
        isFoldersReordering = isFoldersReordering,
        folderSort = config.folderSort,
    )

    val gridConfig = ShowsGridConfig(
        reorderMode = config.reorderMode,
        pinnedPodcastIds = config.pinnedPodcastIds,
        floatingTabsClearance = config.floatingTabsClearance,
    )

    if (config.isGridView) {
        ShowsReorderableGrid(
            folderItems = folderItems,
            orderedPodcasts = orderedPodcasts,
            gridConfig = gridConfig,
            actions = actions,
            onMove = onMove,
            genreChips = genreChips,
        )
    } else {
        ShowsReorderableList(
            orderedPodcasts = orderedPodcasts,
            folderItems = folderItems,
            gridConfig = gridConfig,
            actions = actions,
            onMove = onMove,
            genreChips = genreChips,
        )
    }
}

@Composable
private fun ShowsReorderableGrid(
    folderItems: ShowsFolderItems,
    orderedPodcasts: List<Podcast>,
    gridConfig: ShowsGridConfig,
    actions: ShowsTabActions,
    onMove: (fromId: String, toId: String) -> Unit,
    genreChips: @Composable () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val reorderableGridState =
        rememberReorderableLazyGridState(gridState) { from, to ->
            val fromId = from.key as? String ?: return@rememberReorderableLazyGridState
            val toId = to.key as? String ?: return@rememberReorderableLazyGridState
            onMove(fromId, toId)
        }
    val folderActions = remember(actions) { actions.toFolderCardActions() }
    val hasGridItemsAfterPinned = orderedPodcasts.isNotEmpty() || folderItems.compactFolders.isNotEmpty()
    val sectionLabels = remember(folderItems.folders, gridConfig.isFoldersReordering, hasGridItemsAfterPinned) {
        showsSectionLabels(folderItems.folders, gridConfig.isFoldersReordering)?.takeIf { hasGridItemsAfterPinned }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(boxlorePosterColumns(LocalBoxloreWindowLayout.current, libraryGridWidthDp(), compactColumns = 3, gapDp = 12f)),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = showsBottomPadding(gridConfig.floatingTabsClearance), top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Grid).fillMaxSize(),
    ) {
        item(key = ShowsGenreHeaderKey, span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                genreChips()
            }
        }

        folderGridItems(
            folderItems = folderItems,
            gridConfig = gridConfig,
            reorderableGridState = reorderableGridState,
            folderActions = folderActions,
            labels = sectionLabels,
        )

        // Shows outside folders (unfiled podcasts)
        items(
            items = orderedPodcasts,
            key = { it.id },
            span = { GridItemSpan(1) },
        ) { podcast ->
            val lastSeenEpisodes = LocalLastSeenEpisodes.current
            ReorderableItem(
                reorderableGridState,
                key = podcast.id,
                enabled = gridConfig.isRootReordering,
            ) { isDragging ->
                val onCardClick: (() -> Unit)? = if (gridConfig.isRootReordering) {
                    null
                } else {
                    { actions.onPodcastClick(podcast.id) }
                }
                val onCardLongClick: (() -> Unit)? = if (gridConfig.isRootReordering) {
                    null
                } else {
                    { actions.onPodcastLongClick(podcast) }
                }
                SubscriptionGridCard(
                    podcast = podcast,
                    lastSeenId = lastSeenEpisodes[podcast.id],
                    onClick = onCardClick,
                    onLongClick = onCardLongClick,
                    isPinned = podcast.id in gridConfig.pinnedPodcastIds,
                    isDragging = isDragging,
                    modifier =
                    if (gridConfig.isRootReordering) {
                        Modifier.longPressDraggableHandle()
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun ShowsReorderableList(
    orderedPodcasts: List<Podcast>,
    folderItems: ShowsFolderItems,
    gridConfig: ShowsGridConfig,
    actions: ShowsTabActions,
    onMove: (fromId: String, toId: String) -> Unit,
    genreChips: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderableListState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromId = from.key as? String ?: return@rememberReorderableLazyListState
            val toId = to.key as? String ?: return@rememberReorderableLazyListState
            onMove(fromId, toId)
        }
    val folderActions = remember(actions) { actions.toFolderCardActions() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = showsBottomPadding(gridConfig.floatingTabsClearance), top = 8.dp),
        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Episode).fillMaxSize(),
    ) {
        item(key = ShowsGenreHeaderKey) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                genreChips()
            }
        }
        items(
            items = folderItems.folders,
            key = { "list_folder_${it.id}" },
        ) { folder ->
            ShowsListFolderItem(
                folder = folder,
                folderShows = folderItems.podcastsByFolderId[folder.id].orEmpty(),
                reorderableListState = reorderableListState,
                isFoldersReordering = gridConfig.isFoldersReordering,
                folderActions = folderActions,
            )
        }
        items(items = orderedPodcasts, key = { it.id }) { podcast ->
            ShowsListPodcastItem(
                podcast = podcast,
                reorderableListState = reorderableListState,
                isRootReordering = gridConfig.isRootReordering,
                isPinned = podcast.id in gridConfig.pinnedPodcastIds,
                actions = actions,
            )
        }
    }
}

@Composable
private fun LazyItemScope.ShowsListFolderItem(
    folder: SubscriptionFolder,
    folderShows: List<Podcast>,
    reorderableListState: ReorderableLazyListState,
    isFoldersReordering: Boolean,
    folderActions: FolderCardActions,
) {
    ReorderableItem(
        reorderableListState,
        key = "list_folder_${folder.id}",
        enabled = isFoldersReordering,
    ) { isDragging ->
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .then(if (isFoldersReordering) Modifier.longPressDraggableHandle() else Modifier),
        ) {
            PinnedEnlargedFolderCard(
                folder = folder,
                podcasts = folderShows,
                actions = folderActions,
                isDragging = isDragging,
                isReordering = isFoldersReordering,
            )
        }
    }
}

@Composable
private fun LazyItemScope.ShowsListPodcastItem(
    podcast: Podcast,
    reorderableListState: ReorderableLazyListState,
    isRootReordering: Boolean,
    isPinned: Boolean,
    actions: ShowsTabActions,
) {
    ReorderableItem(
        reorderableListState,
        key = podcast.id,
        enabled = isRootReordering,
    ) { isDragging ->
        val onRowClick: (() -> Unit)? = if (isRootReordering) {
            null
        } else {
            { actions.onPodcastClick(podcast.id) }
        }
        val onRowLongClick: (() -> Unit)? = if (isRootReordering) {
            null
        } else {
            { actions.onPodcastLongClick(podcast) }
        }
        SubscriptionListRow(
            podcast = podcast,
            onClick = onRowClick,
            onLongClick = onRowLongClick,
            isPinned = isPinned,
            isDragging = isDragging,
            dragModifier = if (isRootReordering) {
                Modifier.longPressDraggableHandle()
            } else {
                Modifier
            },
        )
    }
}

/** Configuration for [LatestTabContent], grouped to keep the composable parameter list small. */
internal data class LatestTabConfig(
    val useSmartRank: Boolean,
    val hideCompleted: Boolean,
    val isPlayerActive: Boolean = false,
    val playAllBottomPadding: androidx.compose.ui.unit.Dp? = null,
    val isRefreshing: Boolean = false,
    /** Rows swiped to played whose Undo still shows: drawn as played, hidden with Hide played. */
    val pendingPlayedEpisodeIds: Set<String> = emptySet(),
    /** Archived episodes, never listed. */
    val dismissedEpisodeIds: Set<String> = emptySet(),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LatestTabContent(
    podcasts: List<Podcast>,
    allHistory: List<ListeningHistoryEntity>,
    config: LatestTabConfig,
    scoreEpisodes: suspend (
        List<Podcast>,
        List<ListeningHistoryEntity>,
    ) -> Map<String, Double>,
    actions: LatestTabActions,
) {
    val allWithLatest = remember(podcasts, config.dismissedEpisodeIds) { latestWithoutArchived(podcasts, config.dismissedEpisodeIds) }
    val episodePodcasts = remember(allWithLatest, config.hideCompleted, config.pendingPlayedEpisodeIds) {
        visibleLatestPodcasts(allWithLatest, config.hideCompleted, config.pendingPlayedEpisodeIds)
    }

    LatestPullToRefresh(isRefreshing = config.isRefreshing, onRefresh = actions.onRefresh) {
        when {
            allWithLatest.isEmpty() ->
                PullableEmptyState {
                    LatestEmptyState(R.string.library_latest_empty_title, R.string.library_latest_empty_body, actions.onExploreClick)
                }
            episodePodcasts.isEmpty() ->
                PullableEmptyState {
                    LatestEmptyState(R.string.library_latest_all_hidden_title, R.string.library_latest_all_hidden_body, actions.onExploreClick)
                }
            else ->
                LatestEpisodesList(
                    episodePodcasts = episodePodcasts,
                    allHistory = allHistory,
                    config = config,
                    scoreEpisodes = scoreEpisodes,
                    actions = actions,
                )
        }
    }
}

/** Callbacks for [LatestTabContent], grouped to keep the composable parameter list small. */
internal data class LatestTabActions(
    val onExploreClick: () -> Unit,
    val onPlayEpisode: ((Episode, Podcast) -> Unit)?,
    val onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    val onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
    /** Swipe or TalkBack action on a row; rows don't swipe when null. */
    val onSwipeAction: ((LatestSwipeAction, Episode, Podcast) -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LatestEpisodesList(
    episodePodcasts: List<Podcast>,
    allHistory: List<ListeningHistoryEntity>,
    config: LatestTabConfig,
    scoreEpisodes: suspend (
        List<Podcast>,
        List<ListeningHistoryEntity>,
    ) -> Map<String, Double>,
    actions: LatestTabActions,
) {
    val distinctGenres = remember(episodePodcasts) { extractDistinctGenres(episodePodcasts) }
    var selectedGenre by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(distinctGenres) {
        val stillAvailable = selectedGenre.equals("All", ignoreCase = true) ||
            distinctGenres.any { it.equals(selectedGenre, ignoreCase = true) }
        if (!stillAvailable) selectedGenre = "All"
    }
    val filteredEpisodePodcasts = remember(episodePodcasts, selectedGenre) {
        filterPodcastsByGenre(episodePodcasts, selectedGenre)
    }
    val episodeScores by produceState(
        initialValue = emptyMap<String, Double>(),
        filteredEpisodePodcasts,
        allHistory,
        config.useSmartRank,
    ) {
        value = scoreLatestIfNeeded(config.useSmartRank, filteredEpisodePodcasts, allHistory, scoreEpisodes)
    }
    val displayPodcasts = remember(filteredEpisodePodcasts, config.useSmartRank, episodeScores) {
        sortLatestDisplayPodcasts(filteredEpisodePodcasts, config.useSmartRank, episodeScores)
    }
    val locale = currentLocale()
    val groupedEpisodes = remember(displayPodcasts, config.useSmartRank, locale) {
        groupLatestByDateHeader(displayPodcasts, config.useSmartRank, locale)
    }

    LibraryPlaybackLayout(modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Episode).fillMaxSize(), content = {
        LazyColumn(
            contentPadding = PaddingValues(bottom = if (LocalBoxloreWindowLayout.current.usesTabletLayout) 24.dp else 240.dp, top = 4.dp),
            modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Episode).fillMaxSize(),
        ) {
            item {
                SubscriptionGenreChips(
                    selectedGenre = selectedGenre,
                    onGenreChange = {
                        selectedGenre = it
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper
                            .trackLibrarySubscriptionsGenreFiltered(it, "latest")
                    },
                    distinctGenres = distinctGenres,
                    podcasts = episodePodcasts,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            latestEpisodeItems(
                useSmartRank = config.useSmartRank,
                displayPodcasts = displayPodcasts,
                groupedEpisodes = groupedEpisodes,
                pendingPlayedEpisodeIds = config.pendingPlayedEpisodeIds,
                actions = actions,
            )
        }
        }, playbackAction = {
        LatestPlayAllFab(
            displayPodcasts = displayPodcasts,
            isPlayerActive = config.isPlayerActive,
            onPlayEpisodes = actions.onPlayEpisodes,
            bottomPaddingOverride = config.playAllBottomPadding,
        )
    })
}

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.latestEpisodeItems(
    useSmartRank: Boolean,
    displayPodcasts: List<Podcast>,
    groupedEpisodes: Map<UiText, List<Podcast>>,
    pendingPlayedEpisodeIds: Set<String>,
    actions: LatestTabActions,
) {
    if (useSmartRank) {
        items(items = displayPodcasts, key = { "${it.id}_latest_smart" }) { podcast ->
            LatestEpisodeListRow(podcast, displayPodcasts, pendingPlayedEpisodeIds, actions)
        }
        return
    }
    groupedEpisodes.forEach { (header, podcastsInGroup) ->
        stickyHeader { DateHeader(text = header.asString()) }
        items(items = podcastsInGroup, key = { "${it.id}_latest_chrono" }) { podcast ->
            LatestEpisodeListRow(podcast, displayPodcasts, pendingPlayedEpisodeIds, actions)
        }
    }
}

@Composable
private fun BoxScope.LatestPlayAllFab(
    displayPodcasts: List<Podcast>,
    isPlayerActive: Boolean,
    onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)?,
    bottomPaddingOverride: androidx.compose.ui.unit.Dp? = null,
) {
    if (displayPodcasts.isEmpty() || onPlayEpisodes == null) return
    val episodes = remember(displayPodcasts) { latestPlaybackEpisodes(displayPodcasts) }
    val firstPodcast = remember(displayPodcasts, episodes) {
        displayPodcasts.firstOrNull { it.id == episodes.firstOrNull()?.podcastId }
    } ?: return
    PlayAllFab(
        isPlayerActive = isPlayerActive,
        bottomPaddingOverride = bottomPaddingOverride,
        onClick = {
            onPlayEpisodes(episodes, firstPodcast)
        },
    )
}

@Composable
private fun LatestEpisodeListRow(
    podcast: Podcast,
    displayPodcasts: List<Podcast>,
    pendingPlayedEpisodeIds: Set<String>,
    actions: LatestTabActions,
) {
    val episode = podcast.latestEpisode!!
    val row: @Composable () -> Unit = {
        LatestEpisodeRow(
            episode = episode,
            podcast = podcast,
            onClick = { actions.onEpisodeClick?.invoke(episode, podcast, "library_latest_episodes") },
            onPlay =
            if (actions.onPlayEpisodes != null) {
                { actions.onPlayEpisodes.invoke(latestPlaybackEpisodes(displayPodcasts, episode.id), podcast) }
            } else if (actions.onPlayEpisode != null) {
                { actions.onPlayEpisode.invoke(episode, podcast) }
            } else {
                null
            },
            showAsPlayed = episode.id in pendingPlayedEpisodeIds,
        )
    }
    val onSwipeAction = actions.onSwipeAction
    if (onSwipeAction == null) {
        row()
    } else {
        LatestEpisodeSwipeRow(onAction = { action -> onSwipeAction(action, episode, podcast) }, content = row)
    }
}

/**
 * Room under the Shows grid or list: 180dp on phones for the floating bar and mini player; on
 * tablets just the mini player (where it covers this pane) and the floating tab selector.
 */
@Composable
private fun showsBottomPadding(floatingTabsClearance: Dp): Dp =
    if (LocalBoxloreWindowLayout.current.usesTabletLayout) boxloreContentBottomPadding(PHONE_SHOWS_BOTTOM) + floatingTabsClearance else PHONE_SHOWS_BOTTOM

private val PHONE_SHOWS_BOTTOM = 180.dp

/** New Episodes with nothing to list: the section's sparkle in a puffy medallion. */
@Composable
private fun LatestEmptyState(@androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes body: Int, onExplore: () -> Unit) {
    LibraryEmptyState(
        content = LibraryEmptyContent(
            title = stringResource(title),
            body = stringResource(body),
            icon = Icons.Rounded.AutoAwesome,
            shape = ExpressiveShapes.Puffy,
            actionText = stringResource(R.string.library_discover_shows),
        ),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        onAction = onExplore,
        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
    )
}
