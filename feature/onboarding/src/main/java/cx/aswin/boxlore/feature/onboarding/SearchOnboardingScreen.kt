package cx.aswin.boxlore.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.BoxloreSearchField
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.layout.boxlorePosterColumns
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.list.ProgressiveSearchScrollLogic
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls

/** A category chip on the onboarding search: [categoryValue] is the genre key sent to search, null for Trending. */
data class SearchCategoryItem(
    @StringRes val labelRes: Int,
    val categoryValue: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

val SEARCH_CATEGORIES =
    listOf(
        SearchCategoryItem(R.string.onboarding_search_trending, null, Icons.Rounded.Whatshot, Color(0xFFE0F7FA), Color(0xFF006064)),
        SearchCategoryItem(R.string.onboarding_genre_comedy, "Comedy", Icons.Rounded.SentimentVerySatisfied, Color(0xFFF3E5F5), Color(0xFF4A148C)),
        SearchCategoryItem(R.string.onboarding_genre_true_crime, "True Crime", Icons.Rounded.Fingerprint, Color(0xFFFFEBEE), Color(0xFF880E4F)),
        SearchCategoryItem(R.string.onboarding_genre_tech, "Technology", Icons.Rounded.Computer, Color(0xFFE3F2FD), Color(0xFF0D47A1)),
        SearchCategoryItem(R.string.onboarding_genre_news, "News", Icons.Rounded.Newspaper, Color(0xFFE8F5E9), Color(0xFF1B5E20)),
        SearchCategoryItem(R.string.onboarding_genre_business, "Business", Icons.Rounded.Work, Color(0xFFFFF3E0), Color(0xFFE65100)),
    )

/**
 * Onboarding search: trending shows by topic, or typed results, with the picks and Continue in one
 * slim bar at the bottom. Where a pinned search box, topic chips and heading would leave the grid
 * under one and a half rows (sideways phones, the Fold 8 cover with a pick, split-screen), the
 * chips and heading scroll away with the shows. The results always scroll clear of the keyboard.
 */
@Composable
@Suppress("LongParameterList")
internal fun OnboardingSearchScreen(
    query: String,
    results: List<Podcast>,
    alsoFoundResults: List<Podcast> = emptyList(),
    isSearching: Boolean,
    searchFailed: Boolean = false,
    onRetrySearch: () -> Unit = {},
    subscribedIds: Set<String>,
    selectedPodcasts: Map<String, Podcast>,
    onQueryChange: (String) -> Unit,
    onSubscribe: (Podcast) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    popularPodcasts: List<Podcast>,
    isPopularLoading: Boolean,
    selectedSearchGenre: String?,
    onGenreSelect: (String?) -> Unit,
    isAiSideTrip: Boolean = false,
) {
    val window = LocalBoxloreWindowLayout.current
    val tablet = window.usesTabletLayout
    // On phones the keyboard covers the bar; the results scroll clear of it instead.
    val keyboard = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    val typing = !tablet && keyboard > 0.dp
    val listBottom = OnboardingSearchListPadding + if (tablet) 0.dp else keyboard
    var picksOpen by rememberSaveable { mutableStateOf(false) }
    val picks = remember(subscribedIds, selectedPodcasts) {
        LazyListKeyPolicy.deduplicateById(subscribedIds.mapNotNull { selectedPodcasts[it] }) { it.id }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = boxloreContentWidthDp().coerceAtMost(OnboardingSearchMaxWidth)
        val columns = boxlorePosterColumns(window, width)
        val compactHeader = boxloreIsShortPane() || onboardingSearchHeaderCompact(width, maxHeight.value, columns, hasPicks = subscribedIds.isNotEmpty())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .boxloreCardWidth(BoxloreCardFamily.Utility)
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .then(if (tablet) Modifier.imePadding() else Modifier),
        ) {
            SearchBoxRow(query, onQueryChange, onBack)
            if (query.isEmpty() && !compactHeader) SearchCategoryRow(selectedSearchGenre, onGenreSelect)
            SearchBody(
                SearchBodyState(query, results, alsoFoundResults, isSearching, searchFailed, popularPodcasts, isPopularLoading, selectedSearchGenre, subscribedIds),
                SearchLayout(columns, compactHeader, listBottom),
                SearchBodyActions(onRetrySearch, onSubscribe, onGenreSelect),
                Modifier.fillMaxWidth().weight(1f),
            )
            if (subscribedIds.isNotEmpty() && !typing) {
                SearchPicksBar(picks, subscribedIds.size, isAiSideTrip, onOpenPicks = { picksOpen = true }, onDone = onDone)
            }
        }
    }
    LaunchedEffect(picks.isEmpty()) { if (picks.isEmpty()) picksOpen = false }
    if (picksOpen && picks.isNotEmpty()) SearchPicksSheet(picks, onRemove = onSubscribe, onDismiss = { picksOpen = false })
}

/** Back (or clear the query) and the search box. */
@Composable
private fun SearchBoxRow(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit) {
    val focus = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onBack() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
        }
        BoxloreSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { focus.clearFocus() },
            placeholder = stringResource(R.string.onboarding_search_placeholder),
            modifier = Modifier.weight(1f),
            maxWidth = null,
        )
    }
}

/** What a search shows under the box: typed results, the trending grid, a loader, or why there is nothing. */
private class SearchBodyState(
    val query: String,
    val results: List<Podcast>,
    val alsoFoundResults: List<Podcast>,
    val isSearching: Boolean,
    val searchFailed: Boolean,
    val popularPodcasts: List<Podcast>,
    val isPopularLoading: Boolean,
    val selectedSearchGenre: String?,
    val subscribedIds: Set<String>,
)

/** The grid's columns, whether the chips ride in the grid, and the room left under the lists. */
private class SearchLayout(val columns: Int, val compactHeader: Boolean, val listBottom: Dp)

private class SearchBodyActions(val onRetrySearch: () -> Unit, val onSubscribe: (Podcast) -> Unit, val onGenreSelect: (String?) -> Unit)

@Composable
private fun SearchBody(state: SearchBodyState, layout: SearchLayout, actions: SearchBodyActions, modifier: Modifier) {
    val nothing = state.results.isEmpty() && state.alsoFoundResults.isEmpty()
    when {
        state.isSearching -> Box(modifier, contentAlignment = Alignment.Center) { BoxLoreLoader.Expressive(size = 100.dp) }
        state.query.isEmpty() -> Column(modifier) {
            if (!layout.compactHeader) SearchSectionTitle(state.selectedSearchGenre)
            PopularShowsGrid(
                shows = state.popularPodcasts,
                loading = state.isPopularLoading,
                columns = layout.columns,
                subscribedIds = state.subscribedIds,
                onSubscribe = actions.onSubscribe,
                bottomPadding = layout.listBottom,
                header = if (layout.compactHeader) SearchGridHeader(state.selectedSearchGenre, actions.onGenreSelect) else null,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        nothing && state.searchFailed -> OnboardingSearchFailed(actions.onRetrySearch, modifier.padding(horizontal = 24.dp))
        nothing -> OnboardingSearchEmpty(modifier.padding(horizontal = 24.dp))
        else -> SearchResultsList(state.query, state.results, state.alsoFoundResults, state.subscribedIds, actions.onSubscribe, layout.listBottom, modifier)
    }
}

/**
 * Whether the search box, topic chips and heading would leave the poster grid under one and a half
 * rows in a [heightDp]-tall window [widthDp] wide (the picks bar too, with [hasPicks]); then the
 * chips and heading scroll with the shows instead of staying pinned.
 */
internal fun onboardingSearchHeaderCompact(widthDp: Float, heightDp: Float, columns: Int, hasPicks: Boolean): Boolean {
    val cols = columns.coerceAtLeast(1)
    val tile = (widthDp - PosterGridSides - PosterGridGap * (cols - 1)) / cols + PosterTextHeight
    val viewport = heightDp - PinnedHeaderHeight - if (hasPicks) PicksBarHeight else NavigationAllowance
    return viewport < tile * MinVisibleRows
}

private const val PosterGridSides = 32f
private const val PosterGridGap = 16f
private const val PosterTextHeight = 46f
private const val PinnedHeaderHeight = 196f
private const val PicksBarHeight = 104f
private const val NavigationAllowance = 24f
private const val MinVisibleRows = 1.5f
private const val OnboardingSearchMaxWidth = 720f
private val OnboardingSearchListPadding = 8.dp

@Composable
private fun SearchCategoryRow(selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxWidth().testTag(SEARCH_CATEGORIES_TAG),
    ) {
        items(SEARCH_CATEGORIES) { category ->
            PillFilterChip(
                label = stringResource(category.labelRes),
                selected = selected == category.categoryValue,
                onClick = { onSelect(category.categoryValue) },
                icon = category.icon,
            )
        }
    }
}

@Composable
private fun SearchSectionTitle(selectedSearchGenre: String?) {
    val category = SEARCH_CATEGORIES.find { it.categoryValue == selectedSearchGenre }
    Text(
        text = if (selectedSearchGenre == null || category == null) {
            stringResource(R.string.onboarding_search_trending_shows)
        } else {
            stringResource(R.string.onboarding_search_popular_in, stringResource(category.labelRes))
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = GoogleSansWeight.bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp).semantics { heading() },
    )
}

/**
 * The trending posters. While they load, the grid fills with posters' shapes, so nothing jumps
 * when the shows arrive. [header], on short windows, puts the chips and the heading first in the
 * grid so they scroll away.
 */
@Composable
@Suppress("LongParameterList")
private fun PopularShowsGrid(
    shows: List<Podcast>,
    loading: Boolean,
    columns: Int,
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
    // The grid keeps its first show in place when the header moves in; at the very top, show the header too.
    LaunchedEffect(header != null) {
        if (header != null && grid.firstVisibleItemIndex == SearchGridHeaderItems && grid.firstVisibleItemScrollOffset == 0) grid.scrollToItem(0)
    }
    ShimmerScope(active = loading) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.testTag(SEARCH_GRID_TAG),
        ) {
            if (header != null) {
                item(key = "search_categories", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleedSideways(16.dp)) { SearchCategoryRow(header.selectedGenre, header.onGenreSelect) } }
                item(key = "search_heading", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleedSideways(16.dp)) { SearchSectionTitle(header.selectedGenre) } }
            }
            when {
                loading -> items(SkeletonPosterCount, key = { "skeleton-$it" }) { PopularPodcastSkeleton() }
                distinct.isEmpty() -> item(key = "search_no_popular", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.onboarding_search_no_popular),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> items(distinct, key = { LazyListKeyPolicy.safeKey(it.id, prefix = "popular_show") }) { podcast ->
                    PopularPodcastGridItem(podcast = podcast, isSubscribed = podcast.id in subscribedIds, onClick = { onSubscribe(podcast) })
                }
            }
        }
    }
}

private const val SkeletonPosterCount = 6
private const val SearchGridHeaderItems = 2

/** On short windows the topic chips and the heading ride at the top of the grid. */
private class SearchGridHeader(val selectedGenre: String?, val onGenreSelect: (String?) -> Unit)

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

@Composable
@Suppress("LongParameterList")
private fun SearchResultsList(
    query: String,
    results: List<Podcast>,
    alsoFoundResults: List<Podcast>,
    subscribedIds: Set<String>,
    onSubscribe: (Podcast) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier,
) {
    val distinctResults = remember(results) { LazyListKeyPolicy.deduplicateById(results) { it.id } }
    val distinctAlsoFound = remember(alsoFoundResults, distinctResults) {
        val catalogIds = distinctResults.mapNotNull { it.id.trim().takeIf(String::isNotEmpty) }.toSet()
        val filtered = alsoFoundResults.filter { it.id.trim().takeIf(String::isNotEmpty) !in catalogIds }
        LazyListKeyPolicy.deduplicateById(filtered) { it.id }
    }
    val listState = rememberLazyListState()
    val pinSnapshot = ProgressiveSearchScrollLogic.Snapshot(
        query = query,
        topResultId = distinctResults.firstOrNull()?.id,
        hasAlsoFoundSection = distinctAlsoFound.isNotEmpty(),
    )
    // Saved with the list's place, so a fold or display-size change doesn't count as new results.
    var previousPin by rememberSaveable(stateSaver = PinSnapshotSaver) { mutableStateOf<ProgressiveSearchScrollLogic.Snapshot?>(null) }
    LaunchedEffect(pinSnapshot) {
        if (ProgressiveSearchScrollLogic.shouldPinToTop(previousPin, pinSnapshot)) listState.scrollToItem(0)
        previousPin = pinSnapshot
    }
    LazyColumn(
        state = listState,
        modifier = modifier.testTag(SEARCH_RESULTS_TAG),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (distinctResults.isNotEmpty() && distinctAlsoFound.isNotEmpty()) {
            item(key = "matches_header") { SearchResultsHeading(R.string.onboarding_search_matches) }
        }
        items(distinctResults, key = { LazyListKeyPolicy.safeKey(it.id, prefix = "search_show") }) { podcast ->
            SearchResultRow(podcast = podcast, isSubscribed = podcast.id in subscribedIds, onSubscribe = { onSubscribe(podcast) })
        }
        if (distinctAlsoFound.isNotEmpty()) {
            item(key = "also_found_header") { SearchResultsHeading(R.string.onboarding_search_also_found) }
            items(distinctAlsoFound, key = { LazyListKeyPolicy.safeKey(it.id, prefix = "also_show") }) { podcast ->
                SearchResultRow(podcast = podcast, isSubscribed = podcast.id in subscribedIds, onSubscribe = { onSubscribe(podcast) })
            }
        }
    }
}

private val PinSnapshotSaver = listSaver<ProgressiveSearchScrollLogic.Snapshot?, Any?>(
    save = { snapshot -> snapshot?.let { listOf(it.query, it.topResultId, it.hasAlsoFoundSection) }.orEmpty() },
    restore = { saved -> if (saved.isEmpty()) null else ProgressiveSearchScrollLogic.Snapshot(saved[0] as String, saved[1] as String?, saved[2] as Boolean) },
)

@Composable
private fun SearchResultsHeading(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = GoogleSansWeight.bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp).semantics { heading() },
    )
}

@Composable
private fun OnboardingSearchEmpty(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_search_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_search_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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

internal const val SEARCH_GRID_TAG = "onboarding_search_grid"
internal const val SEARCH_RESULTS_TAG = "onboarding_search_results"
internal const val SEARCH_CATEGORIES_TAG = "onboarding_search_categories"
internal const val SEARCH_SKELETON_TAG = "onboarding_search_skeleton"
internal const val SEARCH_PICKS_BAR_TAG = "onboarding_search_picks_bar"
internal const val SEARCH_PICKS_BUTTON_TAG = "onboarding_search_picks"
internal const val SEARCH_PICKS_SHEET_TAG = "onboarding_search_picks_sheet"

/** Lets a full-width header inside the padded grid reach the grid's edges, [by] on each side. */
private fun Modifier.bleedSideways(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** A show search that couldn't be read says so and offers Try again, never "No Podcasts Found". */
@Composable
private fun OnboardingSearchFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_search_failed_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_search_failed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.onboarding_search_try_again))
            }
        }
    }
}
