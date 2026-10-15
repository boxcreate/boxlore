package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import cx.aswin.boxlore.feature.explore.components.ExploreSearchEpisodeCard

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

internal fun LazyStaggeredGridScope.tabletTrendingItems(hero: Podcast, posters: List<Podcast>, columns: Int, category: String, onPodcastClick: (String, String, String?, Int?) -> Unit) {
    tabletExploreItems(columns, hero, posters, LazyListKeyPolicy.safeKey(hero.id, prefix = "trending_hero"), { index, podcast -> LazyListKeyPolicy.safeKey(podcast.id, index, prefix = "grid") }) { podcast, featured, position ->
        ExploreBrowsePodcastCard(podcast, featured, category == "All") {
            onPodcastClick(podcast.id, if (featured) "explore_hero" else "explore_grid", category, position)
        }
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
private fun exploreEpisodeParent(episode: Episode) = Podcast(
    id = episode.podcastId ?: "",
    title = episode.podcastTitle ?: "Podcast",
    artist = "",
    imageUrl = episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: episode.imageUrl?.takeIf { it.isNotBlank() } ?: "",
    description = "",
    genre = episode.podcastGenre ?: "Podcast",
)
