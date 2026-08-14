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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.ExpressiveSolarSystemEmptyState
import cx.aswin.boxlore.feature.library.LocalLastSeenEpisodes
import cx.aswin.boxlore.feature.library.PlayAllFab
import cx.aswin.boxlore.feature.library.logic.SubscriptionManualOrderLogic
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ShowsGenreHeaderKey = "shows_genre_header"

@Composable
internal fun ShowsTabContent(
    podcasts: List<Podcast>,
    onExploreClick: () -> Unit,
    onPodcastClick: (String) -> Unit,
    isGridView: Boolean,
    canReorder: Boolean = false,
    pinnedPodcastIds: Set<String> = emptySet(),
    onReorder: (orderedIds: List<String>) -> Unit = {},
) {
    if (podcasts.isEmpty()) {
        ExpressiveSolarSystemEmptyState(
            title = "No Subscriptions Yet",
            description = "Follow your favorite podcasts to see them here.",
            actionText = "Find Podcasts",
            onExploreClick = onExploreClick
        )
    } else {
        val distinctGenres = remember(podcasts) { extractDistinctGenres(podcasts) }
        var selectedGenre by rememberSaveable { mutableStateOf("All") }
        val filteredPodcasts = remember(podcasts, selectedGenre) { filterPodcastsByGenre(podcasts, selectedGenre) }
        val distinctPodcasts = remember(filteredPodcasts) { filteredPodcasts.distinctBy { it.id } }
        val reorderEnabled = canReorder && selectedGenre == "All"
        var orderedPodcasts by remember { mutableStateOf(distinctPodcasts) }
        LaunchedEffect(distinctPodcasts.map { it.id }) {
            orderedPodcasts = distinctPodcasts
        }

        val genreChips: @Composable () -> Unit = {
            SubscriptionGenreChips(
                selectedGenre = selectedGenre,
                onGenreChange = {
                    selectedGenre = it
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(it, "shows")
                },
                distinctGenres = distinctGenres,
                contentPadding = PaddingValues(horizontal = if (isGridView) 0.dp else 16.dp)
            )
        }

        if (isGridView) {
            val gridState = rememberLazyGridState()
            val reorderableGridState =
                rememberReorderableLazyGridState(gridState) { from, to ->
                    if (!reorderEnabled) return@rememberReorderableLazyGridState
                    val fromId = from.key as? String ?: return@rememberReorderableLazyGridState
                    val toId = to.key as? String ?: return@rememberReorderableLazyGridState
                    if (fromId == ShowsGenreHeaderKey || toId == ShowsGenreHeaderKey) {
                        return@rememberReorderableLazyGridState
                    }
                    val moved =
                        moveVisiblePodcasts(orderedPodcasts, fromId, toId)
                            ?: return@rememberReorderableLazyGridState
                    orderedPodcasts = moved
                    onReorder(moved.map { it.id })
                }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = ShowsGenreHeaderKey, span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        genreChips()
                    }
                }

                items(items = orderedPodcasts, key = { it.id }) { podcast ->
                    val lastSeenEpisodes = LocalLastSeenEpisodes.current
                    ReorderableItem(
                        reorderableGridState,
                        key = podcast.id,
                        enabled = reorderEnabled,
                    ) { isDragging ->
                        SubscriptionGridCard(
                            podcast = podcast,
                            lastSeenId = lastSeenEpisodes[podcast.id],
                            onClick = { onPodcastClick(podcast.id) },
                            isPinned = podcast.id in pinnedPodcastIds,
                            isDragging = isDragging,
                            dragModifier =
                                if (reorderEnabled) {
                                    Modifier.longPressDraggableHandle()
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()
            val reorderableListState =
                rememberReorderableLazyListState(listState) { from, to ->
                    if (!reorderEnabled) return@rememberReorderableLazyListState
                    val fromId = from.key as? String ?: return@rememberReorderableLazyListState
                    val toId = to.key as? String ?: return@rememberReorderableLazyListState
                    if (fromId == ShowsGenreHeaderKey || toId == ShowsGenreHeaderKey) {
                        return@rememberReorderableLazyListState
                    }
                    val moved =
                        moveVisiblePodcasts(orderedPodcasts, fromId, toId)
                            ?: return@rememberReorderableLazyListState
                    orderedPodcasts = moved
                    onReorder(moved.map { it.id })
                }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = ShowsGenreHeaderKey) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        genreChips()
                    }
                }

                items(items = orderedPodcasts, key = { it.id }) { podcast ->
                    ReorderableItem(
                        reorderableListState,
                        key = podcast.id,
                        enabled = reorderEnabled,
                    ) { isDragging ->
                        SubscriptionListRow(
                            podcast = podcast,
                            onClick = { onPodcastClick(podcast.id) },
                            isPinned = podcast.id in pinnedPodcastIds,
                            isDragging = isDragging,
                            dragModifier =
                                if (reorderEnabled) {
                                    Modifier.longPressDraggableHandle()
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
            }
        }
    }
}

private fun moveVisiblePodcasts(
    current: List<Podcast>,
    fromId: String,
    toId: String,
): List<Podcast>? {
    val currentIds = current.map { it.id }
    val nextIds = SubscriptionManualOrderLogic.move(currentIds, fromId, toId)
    if (nextIds == currentIds) return null
    val byId = current.associateBy { it.id }
    return nextIds.mapNotNull { byId[it] }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LatestTabContent(
    podcasts: List<Podcast>,
    allHistory: List<ListeningHistoryEntity>,
    useSmartRank: Boolean,
    hideCompleted: Boolean,
    scoreEpisodes: suspend (
        List<Podcast>,
        List<ListeningHistoryEntity>,
    ) -> Map<String, Double>,
    actions: LatestTabActions,
    isPlayerActive: Boolean = false,
) {
    val allWithLatest = remember(podcasts) { podcasts.filter { it.latestEpisode != null } }
    val episodePodcasts = remember(allWithLatest, hideCompleted) {
        if (hideCompleted) {
            allWithLatest.filter { it.episodeStatus != EpisodeStatus.COMPLETED }
        } else {
            allWithLatest
        }
    }

    when {
        allWithLatest.isEmpty() ->
            ExpressiveSolarSystemEmptyState(
                title = "No New Episodes",
                description = "You're all caught up! Explore for more content.",
                actionText = "Discover Shows",
                onExploreClick = actions.onExploreClick,
            )
        episodePodcasts.isEmpty() ->
            ExpressiveSolarSystemEmptyState(
                title = "You're all caught up",
                description = "Hidden played episodes are out of the way. Turn off Hide played in Sort to see them again.",
                actionText = "Discover Shows",
                onExploreClick = actions.onExploreClick,
            )
        else ->
            LatestEpisodesList(
                episodePodcasts = episodePodcasts,
                allHistory = allHistory,
                useSmartRank = useSmartRank,
                scoreEpisodes = scoreEpisodes,
                actions = actions,
                isPlayerActive = isPlayerActive,
            )
    }
}

/** Callbacks for [LatestTabContent], grouped to keep the composable parameter list small. */
internal data class LatestTabActions(
    val onExploreClick: () -> Unit,
    val onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    val onPlayEpisode: ((Episode, Podcast) -> Unit)?,
    val onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LatestEpisodesList(
    episodePodcasts: List<Podcast>,
    allHistory: List<ListeningHistoryEntity>,
    useSmartRank: Boolean,
    scoreEpisodes: suspend (
        List<Podcast>,
        List<ListeningHistoryEntity>,
    ) -> Map<String, Double>,
    actions: LatestTabActions,
    isPlayerActive: Boolean,
) {
    val distinctGenres = remember(episodePodcasts) { extractDistinctGenres(episodePodcasts) }
    var selectedGenre by rememberSaveable { mutableStateOf("All") }
    val filteredEpisodePodcasts = remember(episodePodcasts, selectedGenre) {
        filterPodcastsByGenre(episodePodcasts, selectedGenre)
    }
    val episodeScores by produceState(
        initialValue = emptyMap<String, Double>(),
        filteredEpisodePodcasts,
        allHistory,
        useSmartRank,
    ) {
        value = scoreLatestIfNeeded(useSmartRank, filteredEpisodePodcasts, allHistory, scoreEpisodes)
    }
    val displayPodcasts = remember(filteredEpisodePodcasts, useSmartRank, episodeScores) {
        sortLatestDisplayPodcasts(filteredEpisodePodcasts, useSmartRank, episodeScores)
    }
    val groupedEpisodes = remember(displayPodcasts, useSmartRank) {
        groupLatestByDateHeader(displayPodcasts, useSmartRank)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 240.dp, top = 4.dp),
            modifier = Modifier.fillMaxSize(),
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                )
            }
            latestEpisodeItems(
                useSmartRank = useSmartRank,
                displayPodcasts = displayPodcasts,
                groupedEpisodes = groupedEpisodes,
                actions = actions,
            )
        }
        LatestPlayAllFab(
            displayPodcasts = displayPodcasts,
            isPlayerActive = isPlayerActive,
            onPlayEpisodes = actions.onPlayEpisodes,
        )
    }
}

internal suspend fun scoreLatestIfNeeded(
    useSmartRank: Boolean,
    podcasts: List<Podcast>,
    history: List<ListeningHistoryEntity>,
    scoreEpisodes: suspend (List<Podcast>, List<ListeningHistoryEntity>) -> Map<String, Double>,
): Map<String, Double> =
    if (useSmartRank) scoreEpisodes(podcasts, history) else emptyMap()

internal fun sortLatestDisplayPodcasts(
    podcasts: List<Podcast>,
    useSmartRank: Boolean,
    episodeScores: Map<String, Double>,
): List<Podcast> =
    if (useSmartRank) {
        podcasts.sortedByDescending { episodeScores[it.latestEpisode?.id] ?: 0.0 }
    } else {
        podcasts.sortedByDescending { it.latestEpisode!!.publishedDate }
    }

internal fun groupLatestByDateHeader(
    podcasts: List<Podcast>,
    useSmartRank: Boolean,
): Map<String, List<Podcast>> =
    if (useSmartRank) {
        emptyMap()
    } else {
        podcasts.groupBy { getChronologicalHeader(it.latestEpisode!!.publishedDate) }
    }

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.latestEpisodeItems(
    useSmartRank: Boolean,
    displayPodcasts: List<Podcast>,
    groupedEpisodes: Map<String, List<Podcast>>,
    actions: LatestTabActions,
) {
    if (useSmartRank) {
        items(items = displayPodcasts, key = { "${it.id}_latest_smart" }) { podcast ->
            LatestEpisodeListRow(podcast = podcast, actions = actions)
        }
        return
    }
    groupedEpisodes.forEach { (header, podcastsInGroup) ->
        stickyHeader { DateHeader(text = header) }
        items(items = podcastsInGroup, key = { "${it.id}_latest_chrono" }) { podcast ->
            LatestEpisodeListRow(podcast = podcast, actions = actions)
        }
    }
}

@Composable
private fun BoxScope.LatestPlayAllFab(
    displayPodcasts: List<Podcast>,
    isPlayerActive: Boolean,
    onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)?,
) {
    if (displayPodcasts.isEmpty() || onPlayEpisodes == null) return
    val firstPodcast = displayPodcasts.firstOrNull() ?: return
    PlayAllFab(
        isPlayerActive = isPlayerActive,
        onClick = {
            onPlayEpisodes(displayPodcasts.map { it.latestEpisode!! }, firstPodcast)
        },
    )
}

@Composable
private fun LatestEpisodeListRow(
    podcast: Podcast,
    actions: LatestTabActions,
) {
    val episode = podcast.latestEpisode!!
    LatestEpisodeRow(
        episode = episode,
        podcast = podcast,
        onClick = { actions.onEpisodeClick?.invoke(episode, podcast, "library_latest_episodes") },
        onPlay =
            if (actions.onPlayEpisode != null) {
                { actions.onPlayEpisode.invoke(episode, podcast) }
            } else {
                null
            },
    )
}
