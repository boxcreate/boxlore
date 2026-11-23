package cx.aswin.boxlore.feature.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
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
import androidx.compose.material.icons.rounded.Whatshot
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.AdaptiveOverflowRow
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.feature.home.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreSelector(
    selectedCategory: String?, // Null = Top
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    var overflowGenres by remember { mutableStateOf<List<GenreItem>>(emptyList()) }
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout

    val displayGenres =
        remember(selectedCategory) {
            val topGenres = GENRES.take(5)
            if (selectedCategory != null) {
                val selectedGenre = GENRES.find { it.value == selectedCategory }
                if (selectedGenre != null) {
                    listOf(selectedGenre) + (topGenres - selectedGenre)
                } else {
                    topGenres
                }
            } else {
                topGenres
            }
        }

    val scrollState = rememberScrollState()

    LaunchedEffect(selectedCategory) {
        scrollState.animateScrollTo(0)
    }

    if (tablet) {
        AdaptiveOverflowRow(
            itemCount = GENRES.size + 1,
            selectedIndex = GENRES.indexOfFirst { it.value == selectedCategory }.takeIf { it >= 0 }?.plus(1),
            onOverflow = { indices ->
                overflowGenres = indices.map { GENRES[it - 1] }
                showSheet = true
            },
            modifier = modifier,
            item = { index ->
                if (index == 0) {
                    HomeGenreFilterChip(stringResource(R.string.home_genres_top), selectedCategory == null, { onCategorySelected(null) }, Icons.Rounded.Whatshot)
                } else {
                    val genre = GENRES[index - 1]
                    HomeGenreFilterChip(stringResource(genre.label), selectedCategory == genre.value, { onCategorySelected(genre.value) }, genre.icon)
                }
            },
            onWidthChanged = { showSheet = false },
            more = { onClick -> HomeMoreGenresChip(onClick) },
        )
    } else {
        Row(
            modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeGenreFilterChip(
                label = stringResource(R.string.home_genres_top),
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                icon = Icons.Rounded.Whatshot,
            )

            displayGenres.forEach { genre ->
                HomeGenreFilterChip(
                    label = stringResource(genre.label),
                    selected = selectedCategory == genre.value,
                    onClick = { onCategorySelected(genre.value) },
                    icon = genre.icon,
                )
            }

            AssistChip(
                onClick = { showSheet = true },
                label = { Text(stringResource(R.string.home_genres_more)) },
                trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("home_genres_more"),
                shape = CircleShape,
                border = null,
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            HomeGenreSheetContent(selectedCategory, if (tablet) overflowGenres else GENRES, !tablet) { category ->
                onCategorySelected(category)
                showSheet = false
            }
        }
    }
}

@Composable
private fun HomeMoreGenresChip(onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.home_genres_more)) },
        trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
        modifier = Modifier.heightIn(min = 48.dp).testTag("home_genres_more"),
        shape = CircleShape,
        border = null,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun HomeGenreSheetContent(selectedCategory: String?, genres: List<GenreItem>, includeTop: Boolean, onCategorySelected: (String?) -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth().tabletSheetScroll()
            .testTag("home_genres_sheet")
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
    ) {
        Text(
            text = stringResource(R.string.home_browse_genres),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (includeTop) {
                HomeGenreFilterChip(
                label = stringResource(R.string.home_genres_top),
                selected = selectedCategory == null,
                onClick = {
                    onCategorySelected(null)
                },
                icon = Icons.Rounded.Whatshot,
                )
            }

            genres.forEach { genre ->
                HomeGenreFilterChip(
                    label = stringResource(genre.label),
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

@Composable
private fun HomeGenreFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
) {
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

// Synced with ExploreGenreSelector / onboarding search icons. TODO: Move to core:model
private data class GenreItem(
    @StringRes val label: Int,
    val value: String,
    val icon: ImageVector,
)

private val GENRES =
    listOf(
        GenreItem(R.string.home_genre_news, "News", Icons.Rounded.Newspaper),
        GenreItem(R.string.home_genre_tech, "Technology", Icons.Rounded.Computer),
        GenreItem(R.string.home_genre_business, "Business", Icons.Rounded.Work),
        GenreItem(R.string.home_genre_comedy, "Comedy", Icons.Rounded.SentimentVerySatisfied),
        GenreItem(R.string.home_genre_true_crime, "True Crime", Icons.Rounded.Fingerprint),
        GenreItem(R.string.home_genre_sports, "Sports", Icons.Rounded.SportsBaseball),
        GenreItem(R.string.home_genre_health, "Health", Icons.Rounded.FavoriteBorder),
        GenreItem(R.string.home_genre_history, "History", Icons.Rounded.AccountBalance),
        GenreItem(R.string.home_genre_arts, "Arts", Icons.Rounded.Palette),
        GenreItem(R.string.home_genre_society, "Society & Culture", Icons.Rounded.Person),
        GenreItem(R.string.home_genre_education, "Education", Icons.Rounded.School),
        GenreItem(R.string.home_genre_science, "Science", Icons.Rounded.Science),
        GenreItem(R.string.home_genre_tv_film, "TV & Film", Icons.Rounded.Movie),
        GenreItem(R.string.home_genre_fiction, "Fiction", Icons.Rounded.AutoStories),
        GenreItem(R.string.home_genre_music, "Music", Icons.Rounded.MusicNote),
        GenreItem(R.string.home_genre_religion, "Religion & Spirituality", Icons.Rounded.Star),
        GenreItem(R.string.home_genre_family, "Kids & Family", Icons.Rounded.Face),
        GenreItem(R.string.home_genre_leisure, "Leisure", Icons.Rounded.Weekend),
        GenreItem(R.string.home_genre_govt, "Government", Icons.Rounded.Gavel),
    )
