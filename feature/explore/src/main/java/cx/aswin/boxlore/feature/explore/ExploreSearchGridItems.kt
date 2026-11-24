package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.explore.components.ExploreEmptyState
import cx.aswin.boxlore.feature.explore.components.ExploreEpisodesSearchEmptyState
import cx.aswin.boxlore.feature.explore.components.ExploreEpisodesSearchIdleState
import cx.aswin.boxlore.feature.explore.components.ExploreRelatedShowsRail
import cx.aswin.boxlore.feature.explore.components.ExploreSearchEpisodeCard
import cx.aswin.boxlore.feature.explore.components.ExploreSearchLoadingState
import cx.aswin.boxlore.feature.explore.components.ExploreSearchSectionHeader
import cx.aswin.boxlore.feature.explore.components.ExploreShowResultRow
import cx.aswin.boxlore.feature.explore.components.ExploreSuggestedMoodsHeader
import cx.aswin.boxlore.feature.explore.components.ExploreVibeCard
import cx.aswin.boxlore.feature.explore.components.exploreConnectedRowShape
import cx.aswin.boxlore.feature.explore.logic.ExploreListRowLogic

/** What Ask anything shows: its results with the order, keys and analytics positions they always had. */
internal class ExploreEpisodeSearchContent(
    val query: String,
    val episodes: List<Episode>,
    val podcasts: List<Podcast>,
    val loading: Boolean,
    val searched: Boolean,
    val category: String,
)

/** Ask anything: the example questions, a loader, no matches, or Related shows then episodes. */
internal fun LazyStaggeredGridScope.exploreEpisodeSearchItems(
    content: ExploreEpisodeSearchContent,
    columns: Int,
    onExampleClick: (String) -> Unit,
    onPodcastClick: (String, String, String?, Int?) -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit,
) {
    if (content.query.isEmpty()) {
        item(span = StaggeredGridItemSpan.FullLine) { ExploreEpisodesSearchIdleState(onExampleClick = onExampleClick) }
        return
    }
    val eps = content.episodes
    val pods = content.podcasts
    val showContent = eps.isNotEmpty() || pods.isNotEmpty()
    when {
        content.loading && !showContent -> item(span = StaggeredGridItemSpan.FullLine) { ExploreSearchLoadingState(SearchTab.EPISODES) }
        !content.loading && content.searched && !showContent -> item(span = StaggeredGridItemSpan.FullLine) {
            ExploreEpisodesSearchEmptyState(onExampleClick = onExampleClick)
        }
        showContent -> {
            if (pods.isNotEmpty()) {
                item(key = "semantic_related_shows_rail", span = StaggeredGridItemSpan.FullLine) {
                    ExploreRelatedShowsRail(
                        podcasts = pods,
                        onPodcastClick = { podcast, index ->
                            AnalyticsHelper.trackSearchResultTapped(
                                surface = "explore",
                                resultType = "podcast",
                                podcastId = podcast.id,
                                episodeId = null,
                                positionIndex = index,
                                searchQuery = content.query,
                                searchMode = "concept_semantic",
                            )
                            onPodcastClick(podcast.id, "explore_search_concept", content.category, index)
                        },
                    )
                }
            }
            if (eps.isNotEmpty()) {
                item(key = "semantic_episodes_header", span = StaggeredGridItemSpan.FullLine) {
                    ExploreSearchSectionHeader(
                        title = stringResource(R.string.explore_search_episode_results),
                        icon = Icons.Rounded.Headphones,
                    )
                }
                if (columns >= 3) {
                    tabletSemanticEpisodeItems(eps, columns, content.query, onEpisodeClick)
                } else {
                    compactSemanticEpisodeItems(eps, content.query, onEpisodeClick)
                }
            }
            if (content.loading) item(span = StaggeredGridItemSpan.FullLine) { ExploreSearchMoreLoader() }
        }
    }
}

private fun LazyStaggeredGridScope.compactSemanticEpisodeItems(eps: List<Episode>, query: String, onEpisodeClick: (Episode, Podcast) -> Unit) {
    item(key = LazyListKeyPolicy.safeKey(eps.firstOrNull()?.id, prefix = "semantic_episodes_hero"), span = StaggeredGridItemSpan.FullLine) {
        SemanticEpisodeCard(eps[0], featured = true, position = 0, query = query, onEpisodeClick = onEpisodeClick)
    }
    itemsIndexed(eps.drop(1), key = { index, episode -> LazyListKeyPolicy.safeKey(episode.id, index, prefix = "search_semantic") }) { index, episode ->
        SemanticEpisodeCard(episode, featured = false, position = index + 1, query = query, onEpisodeClick = onEpisodeClick)
    }
}

@Composable
private fun SemanticEpisodeCard(episode: Episode, featured: Boolean, position: Int, query: String, onEpisodeClick: (Episode, Podcast) -> Unit) {
    val parentPodcast = exploreEpisodeParent(episode)
    ExploreSearchEpisodeCard(
        episode = episode,
        featured = featured,
        onClick = {
            AnalyticsHelper.trackSearchResultTapped(
                surface = "explore",
                resultType = "episode",
                podcastId = parentPodcast.id,
                episodeId = episode.id,
                positionIndex = position,
                searchQuery = query,
                searchMode = "concept_semantic",
            )
            onEpisodeClick(episode, parentPodcast)
        },
    )
}

/** What Find a show shows: the typed query, the catalog matches and the hybrid extras. */
internal class ExploreShowSearchContent(
    val query: String,
    val moods: List<Pair<String, String>>,
    val matches: List<Podcast>,
    val alsoFound: List<Podcast>,
    val loading: Boolean,
    val failed: Boolean,
    val category: String,
)

/** Find a show: Explore collections before typing, then Matches and Also found, a loader or no matches. */
internal fun LazyStaggeredGridScope.exploreShowSearchItems(
    content: ExploreShowSearchContent,
    hasCatalog: Boolean,
    rowColumns: Int,
    onMoodClick: (Pair<String, String>) -> Unit,
    onPodcastClick: (String, String, String?, Int?) -> Unit,
    onRetry: () -> Unit,
) {
    if (content.query.isEmpty()) {
        item(key = "suggested_moods_header", span = StaggeredGridItemSpan.FullLine) {
            ExploreSuggestedMoodsHeader(hasSuggestions = content.moods.isNotEmpty())
        }
        items(content.moods, key = { LazyListKeyPolicy.safeKey(it.first, prefix = "vibe") }) { vibe ->
            ExploreVibeCard(vibe = vibe, onClick = { onMoodClick(vibe) })
        }
        return
    }
    val hasAlsoFound = content.alsoFound.isNotEmpty()
    val showContent = hasCatalog || hasAlsoFound
    when {
        content.loading && !showContent -> item(span = StaggeredGridItemSpan.FullLine) { ExploreSearchLoadingState(SearchTab.SHOWS) }
        !content.loading && !showContent -> item(span = StaggeredGridItemSpan.FullLine) { ExploreEmptyState(failed = content.failed, onRetry = onRetry) }
        else -> {
            val showGenreChip = content.category == "All"
            if (hasCatalog && hasAlsoFound) {
                item(key = "matches_header", span = StaggeredGridItemSpan.FullLine) {
                    ExploreSearchSectionHeader(title = stringResource(R.string.explore_search_matches), icon = Icons.Rounded.Search)
                }
            }
            orderedExploreItems(content.matches, { index, podcast -> LazyListKeyPolicy.safeKey(podcast.id, index, prefix = "grid") }) { podcast, index ->
                ShowResultRow(podcast, index, content.matches.size, rowColumns, showGenreChip) {
                    onPodcastClick(podcast.id, "explore_search", content.category, index)
                }
            }
            if (hasAlsoFound) {
                item(key = "also_found_header", span = StaggeredGridItemSpan.FullLine) {
                    ExploreSearchSectionHeader(
                        title = stringResource(R.string.explore_search_also_found),
                        icon = Icons.Rounded.AutoAwesome,
                    )
                }
                orderedExploreItems(content.alsoFound, { index, podcast -> LazyListKeyPolicy.safeKey(podcast.id, index, prefix = "also") }) { podcast, index ->
                    ShowResultRow(podcast, index, content.alsoFound.size, rowColumns, showGenreChip) {
                        onPodcastClick(podcast.id, "explore_search_also_found", content.category, content.matches.size + index)
                    }
                }
            }
            if (content.loading) item(span = StaggeredGridItemSpan.FullLine) { ExploreSearchMoreLoader() }
        }
    }
}

/** More results are on their way below the ones already shown. */
@Composable
private fun ExploreSearchMoreLoader() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        BoxLoreLoader.Expressive(size = 48.dp)
    }
}

/**
 * One Find a show result. In one column, each section is a connected list; with two or three
 * columns each row is its own rounded surface, no wider than 480dp.
 */
@Composable
private fun ShowResultRow(podcast: Podcast, index: Int, count: Int, columns: Int, showGenre: Boolean, onClick: () -> Unit) {
    val (starts, ends) = if (columns == 1) ExploreListRowLogic.connectedEnds(index, count, 1) else true to true
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        ExploreShowResultRow(
            podcast = podcast,
            showGenre = showGenre,
            shape = exploreConnectedRowShape(starts, ends),
            onClick = onClick,
            modifier = Modifier.widthIn(max = 480.dp),
            uniformHeight = columns > 1,
        )
    }
}
