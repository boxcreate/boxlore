package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls

/**
 * The trending posters. While they load, the grid fills with posters' shapes, so nothing jumps
 * when the shows arrive. [header], on short windows, puts the chips and the heading first in the
 * grid so they scroll away.
 */
@Composable
@Suppress("LongParameterList")
internal fun PopularShowsGrid(
    shows: List<Podcast>,
    loading: Boolean,
    cells: GridCells,
    asRows: Boolean,
    subscribedIds: Set<String>,
    onSubscribe: (Podcast) -> Unit,
    bottomPadding: Dp,
    header: SearchGridHeader?,
    modifier: Modifier,
) {
    val distinct = remember(shows) { LazyListKeyPolicy.deduplicateById(shows) { it.id } }
    if (!loading && distinct.isEmpty() && header == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.onboarding_search_no_popular), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val grid = rememberLazyGridState()
    KeepTopShow(grid, distinct, headerItems = if (header != null) SearchGridHeaderItems else 0, arrangement = Triple(header != null, asRows, cells))
    ShimmerScope(active = loading) {
        LazyVerticalGrid(
            columns = cells,
            state = grid,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(if (asRows) 0.dp else 16.dp),
            modifier = modifier.testTag(SEARCH_GRID_TAG),
        ) {
            if (header != null) {
                item(key = "search_categories", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleedSideways(16.dp)) { SearchCategoryRow(header.selectedGenre, header.onGenreSelect, header.chips) } }
                item(key = "search_heading", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleedSideways(16.dp)) { SearchSectionTitle(header.selectedGenre) } }
            }
            when {
                loading -> items(SkeletonPosterCount, key = { "skeleton-$it" }) { if (asRows) SearchResultRowSkeleton() else PopularPodcastSkeleton() }
                distinct.isEmpty() -> item(key = "search_no_popular", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.onboarding_search_no_popular),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> items(distinct, key = { LazyListKeyPolicy.safeKey(it.id, prefix = PopularShowKeyPrefix) }) { podcast ->
                    if (asRows) {
                        SearchResultRow(podcast = podcast, isSubscribed = podcast.id in subscribedIds, onSubscribe = { onSubscribe(podcast) })
                    } else {
                        PopularPodcastGridItem(podcast = podcast, isSubscribed = podcast.id in subscribedIds, onClick = { onSubscribe(podcast) })
                    }
                }
            }
        }
    }
}

private const val SkeletonPosterCount = 6
private const val PopularShowKeyPrefix = "popular_show"
internal const val SearchGridHeaderItems = 2

/** On short windows the topic chips and the heading ride at the top of the grid. */
internal class SearchGridHeader(val selectedGenre: String?, val onGenreSelect: (String?) -> Unit, val chips: LazyListState)

/** A trending poster's shape while the shows load: the square art and its two lines of text. */
@Composable
private fun PopularPodcastSkeleton() {
    Column(Modifier.fillMaxWidth().boxloreCardWidth(BoxloreCardFamily.Poster).testTag(SEARCH_SKELETON_TAG)) {
        SkeletonBlock(Modifier.aspectRatio(1f).fillMaxWidth(), shape = MaterialTheme.shapes.large)
        Spacer(modifier = Modifier.height(6.dp))
        SkeletonTextLines(MaterialTheme.typography.bodyMedium, 1, Modifier.padding(horizontal = 4.dp), widths = listOf(0.8f))
        SkeletonTextLines(MaterialTheme.typography.bodySmall, 1, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), widths = listOf(0.5f))
    }
}

/** A trending row's shape while the shows load: the art, two lines of text and the badge. */
@Composable
private fun SearchResultRowSkeleton() {
    Row(Modifier.fillMaxWidth().padding(12.dp).testTag(SEARCH_SKELETON_TAG), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(56.dp), shape = MaterialTheme.shapes.medium)
        Spacer(modifier = Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            SkeletonTextLines(MaterialTheme.typography.bodyLarge, 1, widths = listOf(0.8f))
            SkeletonTextLines(MaterialTheme.typography.bodySmall, 1, widths = listOf(0.5f))
        }
    }
}

/**
 * A trending poster. The whole tile is one checkbox; its mark is Echo's solid badge, which blooms
 * into a shape when picked.
 */
@Composable
private fun PopularPodcastGridItem(podcast: Podcast, isSubscribed: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (isSubscribed) R.string.onboarding_search_selected else R.string.onboarding_search_select)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .boxloreCardWidth(BoxloreCardFamily.Poster)
            .clip(MaterialTheme.shapes.large)
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                toggleableState = ToggleableState(isSubscribed)
                stateDescription = state
            }
            .expressiveClickable(onClick = onClick),
    ) {
        Box(Modifier.aspectRatio(1f).fillMaxWidth().clip(MaterialTheme.shapes.large)) {
            OptimizedImage(
                url = podcast.imageUrl,
                fallbackUrls = podcast.artworkUrls(),
                proxyWidth = 400,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                EchoSelectionBadge(selected = isSubscribed, icon = if (isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add, size = 32.dp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = GoogleSansWeight.bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = podcast.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

/** Lets a full-width header inside the padded grid reach the grid's edges, [by] on each side. */
private fun Modifier.bleedSideways(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Keeps the show at the top of [grid] in place when its [arrangement] changes (the header in or
 * out, posters or rows, the columns); at the very top the header shows too.
 */
@Composable
private fun KeepTopShow(grid: LazyGridState, shows: List<Podcast>, headerItems: Int, arrangement: Any) {
    // Read while composing, before the new layout moves anything: the show on top, even one partly
    // scrolled past, and the share of it scrolled past; none at the very top (the header shows there)
    // or while a header row is on top.
    val anchor = remember(arrangement) {
        Snapshot.withoutReadObservation {
            val atTop = grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0
            // The grid also lists items kept just above the top; the first on screen is the first that ends below it.
            grid.layoutInfo.visibleItemsInfo.firstOrNull { it.offset.y + it.size.height > 0 }
                ?.takeIf { !atTop && (it.key as? String)?.startsWith(PopularShowKeyPrefix) == true }
                ?.let { it.key to (-it.offset.y).coerceAtLeast(0).toFloat() / it.size.height.coerceAtLeast(1) }
        }
    }
    LaunchedEffect(arrangement) {
        val index = anchor?.let { (key, _) -> shows.indexOfFirst { LazyListKeyPolicy.safeKey(it.id, prefix = PopularShowKeyPrefix) == key } } ?: -1
        when {
            // The first show too: once the header has scrolled away it stays away.
            anchor != null && index >= 0 -> {
                val target = index + headerItems
                grid.scrollToItem(target)
                // The same share of the show stays scrolled past at its new size.
                val height = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }?.size?.height ?: 0
                if (anchor.second > 0f && height > 0) grid.scrollToItem(target, (anchor.second * height).toInt())
            }
            grid.firstVisibleItemIndex in 1..headerItems -> grid.scrollToItem(0)
        }
    }
}
