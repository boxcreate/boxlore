package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.EqualHeightPosterGrid
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.TabletFeaturedRow
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.explore.components.ExploreBrowseCardSkeleton
import cx.aswin.boxlore.feature.explore.components.ExploreBrowsePodcastCard
import cx.aswin.boxlore.feature.explore.components.ExploreChartRow
import cx.aswin.boxlore.feature.explore.components.ExploreSearchEpisodeCard
import cx.aswin.boxlore.feature.explore.components.exploreConnectedRowShape
import cx.aswin.boxlore.feature.explore.logic.ExploreListRowLogic

/** Rows retain absolute ranks and stable keys; equal-height groups preserve visual reading order. */
private fun <T> LazyStaggeredGridScope.tabletExploreItems(
    columns: Int,
    hero: T,
    posters: List<T>,
    heroKey: String,
    posterKey: (Int, T) -> String,
    card: @Composable (T, Boolean, Int) -> Unit,
) {
    val alongside = (columns - 2).coerceAtLeast(1).coerceAtMost(posters.size)
    item(key = heroKey, span = StaggeredGridItemSpan.FullLine) {
        TabletFeaturedRow(
            columns = columns,
            horizontalSpacing = 12.dp,
            featured = { card(hero, true, 0) },
            posters = {
                EqualHeightPosterGrid(columns = columns - 2, horizontalSpacing = 12.dp, verticalSpacing = 12.dp) {
                    posters.take(alongside).forEachIndexed { index, value ->
                        key(posterKey(index, value)) { card(value, false, index + 1) }
                    }
                }
            },
        )
    }
    orderedExploreItems(posters.drop(alongside), { index, value -> posterKey(index + alongside, value) }) { value, index ->
        card(value, false, index + alongside + 1)
    }
}

/** Each result keeps its lazy identity and index when the window changes column count. */
internal fun <T> LazyStaggeredGridScope.orderedExploreItems(values: List<T>, itemKey: (Int, T) -> String, card: @Composable (T, Int) -> Unit) {
    itemsIndexed(values, key = itemKey) { index, value -> card(value, index) }
}

internal fun LazyStaggeredGridScope.tabletRecommendationItems(episodes: List<Episode>, columns: Int, onEpisodeClick: (Episode, Podcast) -> Unit) {
    tabletExploreItems(columns, episodes.first(), episodes.drop(1), LazyListKeyPolicy.safeKey(episodes.first().id, prefix = "rec_hero"), { index, episode -> LazyListKeyPolicy.safeKey(episode.id, index, prefix = "rec") }) { episode, featured, position ->
        val podcast = exploreEpisodeParent(episode)
        CuratedEpisodeCard(
            podcast = podcast,
            episode = episode,
            showSubtitle = false,
            presentation = if (featured) FeedMediaCardPresentation.ExpressiveFeatured else FeedMediaCardPresentation.ExpressivePoster,
            onClick = {
                AnalyticsHelper.trackExploreRecommendationCardTapped(episode.id, episode.title, podcast.id, podcast.title, position)
                onEpisodeClick(episode, podcast)
            },
        )
    }
}

internal fun LazyStaggeredGridScope.tabletSemanticEpisodeItems(episodes: List<Episode>, columns: Int, query: String, onEpisodeClick: (Episode, Podcast) -> Unit) {
    tabletExploreItems(columns, episodes.first(), episodes.drop(1), LazyListKeyPolicy.safeKey(episodes.first().id, prefix = "semantic_episodes_hero"), { index, episode -> LazyListKeyPolicy.safeKey(episode.id, index, prefix = "search_semantic") }) { episode, featured, position ->
        val podcast = exploreEpisodeParent(episode)
        ExploreSearchEpisodeCard(episode, featured, onClick = {
            AnalyticsHelper.trackSearchResultTapped(surface = "explore", resultType = "episode", podcastId = podcast.id, episodeId = episode.id, positionIndex = position, searchQuery = query, searchMode = "concept_semantic")
            onEpisodeClick(episode, podcast)
        })
    }
}

internal fun LazyStaggeredGridScope.tabletExploreSkeletonItems(columns: Int) {
    tabletExploreItems(columns, 0, (1..maxOf(4, columns)).toList(), "tablet_explore_hero_skeleton", { index, _ -> "tablet_explore_skeleton_$index" }) { _, featured, _ -> ExploreBrowseCardSkeleton(featured) }
}

/** Matches the existing browse/search projection, including its fallback order. */
internal fun exploreEpisodeParent(episode: Episode) = Podcast(
    id = episode.podcastId ?: "",
    title = episode.podcastTitle ?: "Podcast",
    artist = "",
    imageUrl = episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: episode.imageUrl?.takeIf { it.isNotBlank() } ?: "",
    description = "",
    genre = episode.podcastGenre ?: "Podcast",
)

/**
 * Top on tablets as a chart: the featured number 1 beside the next places as ranked rows, then the
 * rest as rows in [columns] columns, read row by row. Keys and tap positions match the phone chart.
 */
internal fun LazyStaggeredGridScope.tabletChartItems(
    hero: Podcast,
    rest: List<Podcast>,
    columns: Int,
    category: String,
    onPodcastClick: (String, String, String?, Int?) -> Unit,
) {
    val alongsideColumns = (columns - 1).coerceAtLeast(1)
    val alongside = (alongsideColumns * CHART_ROWS_BESIDE_FEATURED).coerceAtMost(rest.size)
    val showGenre = category == "All"
    val below = rest.drop(alongside)
    item(key = LazyListKeyPolicy.safeKey(hero.id, prefix = "trending_hero"), span = StaggeredGridItemSpan.FullLine) {
        ExploreChartFeaturedRow(
            columns = columns,
            featured = {
                ExploreBrowsePodcastCard(hero, featured = true, showGenre = showGenre, rank = 1) {
                    onPodcastClick(hero.id, "explore_hero", category, 0)
                }
            },
            alongside = {
                EqualHeightPosterGrid(columns = alongsideColumns, horizontalSpacing = 12.dp, verticalSpacing = 2.dp) {
                    rest.take(alongside).forEachIndexed { index, podcast ->
                        key(LazyListKeyPolicy.safeKey(podcast.id, index, prefix = "grid")) {
                            val lastRow = index >= alongside - alongsideColumns
                            ExploreChartRow(
                                rank = index + 2,
                                podcast = podcast,
                                showGenre = showGenre,
                                shape = exploreConnectedRowShape(starts = index < alongsideColumns, ends = lastRow && below.size <= index % alongsideColumns + 1),
                                onClick = { onPodcastClick(podcast.id, "explore_grid", category, index + 1) },
                            )
                        }
                    }
                }
            },
        )
    }
    orderedExploreItems(below, { index, podcast -> LazyListKeyPolicy.safeKey(podcast.id, index + alongside, prefix = "grid") }) { podcast, index ->
        val absolute = index + alongside
        // Rows under the featured card start a column; the others continue the rows beside it.
        val (firstRow, ends) = ExploreListRowLogic.connectedEnds(index, below.size, columns)
        ExploreChartRow(
            rank = absolute + 2,
            podcast = podcast,
            showGenre = showGenre,
            shape = exploreConnectedRowShape(starts = firstRow && (index % columns == 0 || alongside == 0), ends = ends),
            onClick = { onPodcastClick(podcast.id, "explore_grid", category, absolute + 1) },
        )
    }
}

/** The featured card one chart column wide, as tall as the ranked rows beside it. */
@Composable
private fun ExploreChartFeaturedRow(columns: Int, featured: @Composable () -> Unit, alongside: @Composable () -> Unit) {
    Layout(
        content = {
            Box(Modifier.testTag("tablet_featured_card"), propagateMinConstraints = true) { featured() }
            Box(Modifier.testTag("tablet_featured_neighbors")) { alongside() }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val count = columns.coerceAtLeast(2)
        val column = ((constraints.maxWidth - gap * (count - 1)) / count).coerceAtLeast(1)
        val neighbours = measurables[1].measure(Constraints.fixedWidth((constraints.maxWidth - column - gap).coerceAtLeast(1)))
        val card = measurables[0].measure(if (neighbours.height > 0) Constraints.fixed(column, neighbours.height) else Constraints.fixedWidth(column))
        layout(constraints.maxWidth, maxOf(card.height, neighbours.height)) {
            card.placeRelative(0, 0)
            neighbours.placeRelative(column + gap, 0)
        }
    }
}

/** Ranked rows beside the featured card: two rows in each column next to it. */
private const val CHART_ROWS_BESIDE_FEATURED = 2
