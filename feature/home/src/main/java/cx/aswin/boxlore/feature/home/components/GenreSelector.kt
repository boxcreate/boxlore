package cx.aswin.boxlore.feature.home.components

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
import androidx.compose.material3.ModalBottomSheet
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
import cx.aswin.boxlore.feature.home.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreSelector(
    selectedCategory: String?, // Null = Top
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }

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
                label = genre.label,
                selected = selectedCategory == genre.value,
                onClick = { onCategorySelected(genre.value) },
                icon = genre.icon,
            )
        }

        AssistChip(
            onClick = { showSheet = true },
            label = { Text(stringResource(R.string.home_genres_more)) },
            trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp),
            shape = CircleShape,
            border = null,
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            HomeGenreSheetContent(selectedCategory) { category ->
                onCategorySelected(category)
                showSheet = false
            }
        }
    }
}

@Composable
private fun HomeGenreSheetContent(selectedCategory: String?, onCategorySelected: (String?) -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
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
            HomeGenreFilterChip(
                label = stringResource(R.string.home_genres_top),
                selected = selectedCategory == null,
                onClick = {
                    onCategorySelected(null)
                },
                icon = Icons.Rounded.Whatshot,
            )

            GENRES.forEach { genre ->
                HomeGenreFilterChip(
                    label = genre.label,
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
    val label: String,
    val value: String,
    val icon: ImageVector,
)

private val GENRES =
    listOf(
        GenreItem("News", "News", Icons.Rounded.Newspaper),
        GenreItem("Tech", "Technology", Icons.Rounded.Computer),
        GenreItem("Business", "Business", Icons.Rounded.Work),
        GenreItem("Comedy", "Comedy", Icons.Rounded.SentimentVerySatisfied),
        GenreItem("True Crime", "True Crime", Icons.Rounded.Fingerprint),
        GenreItem("Sports", "Sports", Icons.Rounded.SportsBaseball),
        GenreItem("Health", "Health", Icons.Rounded.FavoriteBorder),
        GenreItem("History", "History", Icons.Rounded.AccountBalance),
        GenreItem("Arts", "Arts", Icons.Rounded.Palette),
        GenreItem("Society", "Society & Culture", Icons.Rounded.Person),
        GenreItem("Education", "Education", Icons.Rounded.School),
        GenreItem("Science", "Science", Icons.Rounded.Science),
        GenreItem("TV & Film", "TV & Film", Icons.Rounded.Movie),
        GenreItem("Fiction", "Fiction", Icons.Rounded.AutoStories),
        GenreItem("Music", "Music", Icons.Rounded.MusicNote),
        GenreItem("Religion", "Religion & Spirituality", Icons.Rounded.Star),
        GenreItem("Family", "Kids & Family", Icons.Rounded.Face),
        GenreItem("Leisure", "Leisure", Icons.Rounded.Weekend),
        GenreItem("Govt", "Government", Icons.Rounded.Gavel),
    )
