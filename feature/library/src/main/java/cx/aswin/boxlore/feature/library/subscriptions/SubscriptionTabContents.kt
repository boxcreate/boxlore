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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
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

@Composable
internal fun ShowsTabContent(
    podcasts: List<Podcast>,
    onExploreClick: () -> Unit,
    onPodcastClick: (String) -> Unit,
    isGridView: Boolean
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        genreChips()
                    }
                }

                items(items = distinctPodcasts, key = { it.id }) { podcast ->
                    val lastSeenEpisodes = LocalLastSeenEpisodes.current
                    SubscriptionGridCard(
                        podcast = podcast,
                        lastSeenId = lastSeenEpisodes[podcast.id],
                        onClick = { onPodcastClick(podcast.id) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        genreChips()
                    }
                }

                items(items = distinctPodcasts, key = { it.id }) { podcast ->
                    SubscriptionListRow(
                        podcast = podcast,
                        onClick = { onPodcastClick(podcast.id) }
                    )
                }
            }
        }
    }
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

private suspend fun scoreLatestIfNeeded(
    useSmartRank: Boolean,
    podcasts: List<Podcast>,
    history: List<ListeningHistoryEntity>,
    scoreEpisodes: suspend (List<Podcast>, List<ListeningHistoryEntity>) -> Map<String, Double>,
): Map<String, Double> =
    if (useSmartRank) scoreEpisodes(podcasts, history) else emptyMap()

private fun sortLatestDisplayPodcasts(
    podcasts: List<Podcast>,
    useSmartRank: Boolean,
    episodeScores: Map<String, Double>,
): List<Podcast> =
    if (useSmartRank) {
        podcasts.sortedByDescending { episodeScores[it.latestEpisode?.id] ?: 0.0 }
    } else {
        podcasts.sortedByDescending { it.latestEpisode!!.publishedDate }
    }

private fun groupLatestByDateHeader(
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
