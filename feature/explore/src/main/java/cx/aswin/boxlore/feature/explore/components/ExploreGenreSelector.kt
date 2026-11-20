package cx.aswin.boxlore.feature.explore.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.AdaptiveOverflowRow
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.feature.explore.R

// Synced with GenreSelector.kt / onboarding search icons.
/** [value] is the category key sent to the API and analytics; [labelRes] the chip text; [nameRes] the full name for headings. */
internal data class ExploreGenreItem(
    @StringRes val labelRes: Int,
    val value: String,
    val icon: ImageVector,
    @StringRes val nameRes: Int = labelRes,
)

internal val EXPLORE_GENRES = listOf(
    ExploreGenreItem(R.string.explore_genre_news, "News", Icons.Rounded.Newspaper),
    ExploreGenreItem(R.string.explore_genre_tech, "Technology", Icons.Rounded.Computer, R.string.explore_genre_name_technology),
    ExploreGenreItem(R.string.explore_genre_business, "Business", Icons.Rounded.Work),
    ExploreGenreItem(R.string.explore_genre_comedy, "Comedy", Icons.Rounded.SentimentVerySatisfied),
    ExploreGenreItem(R.string.explore_genre_true_crime, "True Crime", Icons.Rounded.Fingerprint),
    ExploreGenreItem(R.string.explore_genre_sports, "Sports", Icons.Rounded.SportsBaseball),
    ExploreGenreItem(R.string.explore_genre_health, "Health", Icons.Rounded.FavoriteBorder),
    ExploreGenreItem(R.string.explore_genre_history, "History", Icons.Rounded.AccountBalance),
    ExploreGenreItem(R.string.explore_genre_arts, "Arts", Icons.Rounded.Palette),
    ExploreGenreItem(R.string.explore_genre_society, "Society & Culture", Icons.Rounded.Person, R.string.explore_genre_name_society),
    ExploreGenreItem(R.string.explore_genre_education, "Education", Icons.Rounded.School),
    ExploreGenreItem(R.string.explore_genre_science, "Science", Icons.Rounded.Science),
    ExploreGenreItem(R.string.explore_genre_tv_film, "TV & Film", Icons.Rounded.Movie),
    ExploreGenreItem(R.string.explore_genre_fiction, "Fiction", Icons.Rounded.AutoStories),
    ExploreGenreItem(R.string.explore_genre_music, "Music", Icons.Rounded.MusicNote),
    ExploreGenreItem(R.string.explore_genre_religion, "Religion & Spirituality", Icons.Rounded.Star, R.string.explore_genre_name_religion),
    ExploreGenreItem(R.string.explore_genre_family, "Kids & Family", Icons.Rounded.Face, R.string.explore_genre_name_family),
    ExploreGenreItem(R.string.explore_genre_leisure, "Leisure", Icons.Rounded.Weekend),
    ExploreGenreItem(R.string.explore_genre_govt, "Government", Icons.Rounded.Gavel, R.string.explore_genre_name_government),
)

/**
 * Home's native Material 3 genre-chip treatment with Explore's existing filtering.
 */
@Composable
internal fun ExploreGenreSelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
) {
    DiscoveryExpressiveTheme { ExploreGenreSelectorContent(selectedCategory, onCategorySelected) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreGenreSelectorContent(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    var overflowGenres by remember { mutableStateOf<List<ExploreGenreItem>>(emptyList()) }
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout

    val displayGenres = remember(selectedCategory) {
        val topGenres = EXPLORE_GENRES.take(5)
        if (selectedCategory != "All") {
            val selectedGenre = EXPLORE_GENRES.find { it.value == selectedCategory }
            if (selectedGenre != null) {
                listOf(selectedGenre) + (topGenres - selectedGenre)
            } else {
                topGenres
            }
        } else {
            topGenres
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategory) {
        listState.animateScrollToItem(0)
    }

    if (tablet) {
        AdaptiveOverflowRow(
            itemCount = EXPLORE_GENRES.size + 1,
            selectedIndex = EXPLORE_GENRES.indexOfFirst { it.value == selectedCategory }.takeIf { it >= 0 }?.plus(1),
            onOverflow = { indices ->
                overflowGenres = indices.map { EXPLORE_GENRES[it - 1] }
                showSheet = true
            },
            modifier = Modifier.padding(horizontal = 6.dp),
            item = { index ->
                if (index == 0) {
                    ExploreGenreFilterChip(stringResource(R.string.explore_genre_all), selectedCategory == "All", { onCategorySelected("All") }, Icons.Rounded.Apps)
                } else {
                    val genre = EXPLORE_GENRES[index - 1]
                    ExploreGenreFilterChip(stringResource(genre.labelRes), selectedCategory == genre.value, { onCategorySelected(genre.value) }, genre.icon)
                }
            },
            onWidthChanged = { showSheet = false },
            more = { onClick -> ExploreMoreGenresChip(onClick) },
        )
    } else {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ExploreGenreFilterChip(
                    label = stringResource(R.string.explore_genre_all),
                    selected = selectedCategory == "All",
                    onClick = { onCategorySelected("All") },
                    icon = Icons.Rounded.Apps,
                )
            }

            items(displayGenres, key = { it.value }) { genre ->
                ExploreGenreFilterChip(
                    label = stringResource(genre.labelRes),
                    selected = selectedCategory == genre.value,
                    onClick = { onCategorySelected(genre.value) },
                    icon = genre.icon,
                )
            }

            item {
                AssistChip(
                    label = { Text(stringResource(R.string.explore_genre_more)) },
                    onClick = { showSheet = true },
                    trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                    border = null,
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ExploreGenreSheetContent(selectedCategory, if (tablet) overflowGenres else EXPLORE_GENRES, !tablet) { category ->
                onCategorySelected(category)
                showSheet = false
            }
        }
    }
}

@Composable
private fun ExploreMoreGenresChip(onClick: () -> Unit) {
    AssistChip(
        label = { Text(stringResource(R.string.explore_genre_more)) },
        onClick = onClick,
        trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
        modifier = Modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        border = null,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

/** Mirrors HomeGenreFilterChip tokens; no feature-to-feature dependency. */
@Composable
private fun ExploreGenreFilterChip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreGenreSheetContent(selectedCategory: String, genres: List<ExploreGenreItem>, includeAll: Boolean, onCategorySelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth().tabletSheetScroll()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
    ) {
        Text(
            text = stringResource(R.string.explore_genre_browse_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (includeAll) {
                ExploreGenreFilterChip(
                label = stringResource(R.string.explore_genre_all),
                selected = selectedCategory == "All",
                onClick = {
                    onCategorySelected("All")
                },
                icon = Icons.Rounded.Apps,
                )
            }

            genres.forEach { genre ->
                ExploreGenreFilterChip(
                    label = stringResource(genre.labelRes),
                    selected = selectedCategory == genre.value,
                    onClick = {
                        onCategorySelected(genre.value)
                    },
                    icon = genre.icon,
                )
            }
        }
    }
}

/** The Top tab heading: "Top charts", or "Top in" a genre's full name (an unknown category key shows as given). */
@Composable
internal fun exploreTopHeaderTitle(category: String): String {
    if (category == "All") return stringResource(R.string.explore_top_charts)
    val genre = EXPLORE_GENRES.find { it.value == category }
    return stringResource(R.string.explore_top_in_genre, genre?.let { stringResource(it.nameRes) } ?: category)
}
