package cx.aswin.boxlore.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxlorePosterColumns
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.list.ProgressiveSearchScrollLogic
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
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

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
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
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .then(if (LocalBoxloreWindowLayout.current.usesTabletLayout) Modifier.imePadding() else Modifier),
    ) {
        // Prominent search bar in top row next to back button
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (query.isNotEmpty()) {
                        onQueryChange("")
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_back),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.onboarding_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(percent = 50),
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.onboarding_search_clear), modifier = Modifier.size(20.dp))
                        }
                    }
                },
            )
        }

        // Compact category row directly below search bar
        if (query.isEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(SEARCH_CATEGORIES) { category ->
                    PillFilterChip(
                        label = stringResource(category.labelRes),
                        selected = selectedSearchGenre == category.categoryValue,
                        onClick = { onGenreSelect(category.categoryValue) },
                        icon = category.icon,
                    )
                }
            }
        }

        when {
            isSearching -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxLoreLoader.Expressive(size = 100.dp)
                }
            }
            query.isEmpty() -> {
                // Discovery view: Trending shows
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val titleLabel = stringResource(SEARCH_CATEGORIES.find { it.categoryValue == selectedSearchGenre }?.labelRes ?: R.string.onboarding_search_trending)
                    val sectionTitle =
                        if (selectedSearchGenre == null) {
                            stringResource(R.string.onboarding_search_trending_shows)
                        } else {
                            stringResource(R.string.onboarding_search_popular_in, titleLabel)
                        }
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = GoogleSansWeight.bold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                    )

                    if (isPopularLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            BoxLoreLoader.Expressive(size = 80.dp)
                        }
                    } else if (popularPodcasts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.onboarding_search_no_popular), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val distinctPopular = remember(popularPodcasts) {
                            LazyListKeyPolicy.deduplicateById(popularPodcasts) { it.id }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(boxlorePosterColumns(LocalBoxloreWindowLayout.current, boxloreContentWidthDp().coerceAtMost(720f))),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(
                                distinctPopular,
                                key = { LazyListKeyPolicy.safeKey(it.id, prefix = "popular_show") }
                            ) { podcast ->
                                val isSubscribed = podcast.id in subscribedIds
                                PopularPodcastGridItem(
                                    podcast = podcast,
                                    isSubscribed = isSubscribed,
                                    onClick = { onSubscribe(podcast) },
                                )
                            }
                        }
                    }
                }
            }
            results.isEmpty() && alsoFoundResults.isEmpty() && searchFailed -> {
                OnboardingSearchFailed(onRetrySearch, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp))
            }
            results.isEmpty() && alsoFoundResults.isEmpty() && !isSearching -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            else -> {
                val distinctResults = remember(results) {
                    LazyListKeyPolicy.deduplicateById(results) { it.id }
                }
                val distinctAlsoFound =
                    remember(alsoFoundResults, distinctResults) {
                        val catalogIds = distinctResults.mapNotNull { it.id.trim().takeIf(String::isNotEmpty) }.toSet()
                        val filtered = alsoFoundResults.filter { it.id.trim().takeIf(String::isNotEmpty) !in catalogIds }
                        LazyListKeyPolicy.deduplicateById(filtered) { it.id }
                    }
                val listState = rememberLazyListState()
                val pinSnapshot =
                    ProgressiveSearchScrollLogic.Snapshot(
                        query = query,
                        topResultId = distinctResults.firstOrNull()?.id,
                        hasAlsoFoundSection = distinctAlsoFound.isNotEmpty(),
                    )
                var previousPin by remember {
                    mutableStateOf<ProgressiveSearchScrollLogic.Snapshot?>(null)
                }
                LaunchedEffect(pinSnapshot) {
                    if (ProgressiveSearchScrollLogic.shouldPinToTop(previousPin, pinSnapshot)) {
                        listState.scrollToItem(0)
                    }
                    previousPin = pinSnapshot
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (distinctResults.isNotEmpty() && distinctAlsoFound.isNotEmpty()) {
                        item(key = "matches_header") {
                            Text(
                                text = stringResource(R.string.onboarding_search_matches),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = GoogleSansWeight.bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(
                        distinctResults,
                        key = { LazyListKeyPolicy.safeKey(it.id, prefix = "search_show") }
                    ) { podcast ->
                        SearchResultRow(
                            podcast = podcast,
                            isSubscribed = podcast.id in subscribedIds,
                            onSubscribe = { onSubscribe(podcast) },
                        )
                    }
                    if (distinctAlsoFound.isNotEmpty()) {
                        item(key = "also_found_header") {
                            Text(
                                text = stringResource(R.string.onboarding_search_also_found),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = GoogleSansWeight.bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            )
                        }
                        items(
                            distinctAlsoFound,
                            key = { LazyListKeyPolicy.safeKey(it.id, prefix = "also_show") }
                        ) { podcast ->
                            SearchResultRow(
                                podcast = podcast,
                                isSubscribed = podcast.id in subscribedIds,
                                onSubscribe = { onSubscribe(podcast) },
                            )
                        }
                    }
                }
            }
        }

        // Done button panel at bottom with selected shows bar
        if (subscribedIds.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.onboarding_search_selected_shows, subscribedIds.size, subscribedIds.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val selectedPodcastsList = remember(subscribedIds, selectedPodcasts) {
                        val list = subscribedIds.mapNotNull { selectedPodcasts[it] }
                        LazyListKeyPolicy.deduplicateById(list) { it.id }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(
                            selectedPodcastsList,
                            key = { LazyListKeyPolicy.safeKey(it.id, prefix = "selected_show") }
                        ) { podcast ->
                            Box(
                                modifier = Modifier.size(56.dp),
                            ) {
                                OptimizedImage(
                                    url = podcast.imageUrl,
                                    fallbackUrls = podcast.artworkUrls(),
                                    proxyWidth = 150,
                                    contentDescription = podcast.title,
                                    modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                )
                                // Tiny custom circular close badge on top-right (avoids IconButton target size constraint)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(16.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.error,
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                        ).clickable { onSubscribe(podcast) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.onboarding_search_remove),
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(10.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isAiSideTrip) {
                        Text(
                            text = stringResource(R.string.onboarding_search_side_trip_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onDone,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            pluralStringResource(
                                if (isAiSideTrip) R.plurals.onboarding_search_subscribe_continue_chat else R.plurals.onboarding_search_continue_with,
                                subscribedIds.size,
                                subscribedIds.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularPodcastGridItem(
    podcast: Podcast,
    isSubscribed: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .boxloreCardWidth(BoxloreCardFamily.Poster)
            .clip(MaterialTheme.shapes.large)
            .expressiveClickable(onClick = onClick),
    ) {
        Box(
            modifier =
            Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
        ) {
            OptimizedImage(
                url = podcast.imageUrl,
                fallbackUrls = podcast.artworkUrls(),
                proxyWidth = 400,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )

            // Subscribed indicator overlay
            val containerColor =
                if (isSubscribed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                }
            val contentColor =
                if (isSubscribed) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = 4.dp,
                modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = if (isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = stringResource(if (isSubscribed) R.string.onboarding_search_selected else R.string.onboarding_search_select),
                        modifier = Modifier.size(16.dp),
                    )
                }
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

@Composable
private fun SearchResultRow(
    podcast: Podcast,
    isSubscribed: Boolean,
    onSubscribe: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .expressiveClickable(onClick = onSubscribe)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 400,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
            Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                podcast.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = GoogleSansWeight.medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                podcast.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isSubscribed) {
            FilledIconButton(
                onClick = onSubscribe,
                colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.onboarding_search_selected),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            OutlinedIconButton(
                onClick = onSubscribe,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.onboarding_search_select),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
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
