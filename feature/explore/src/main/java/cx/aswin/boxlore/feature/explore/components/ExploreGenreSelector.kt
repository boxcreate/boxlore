package cx.aswin.boxlore.feature.explore.components

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
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme

// Synced with GenreSelector.kt / onboarding search icons.
internal data class ExploreGenreItem(val label: String, val value: String, val icon: ImageVector)

internal val EXPLORE_GENRES = listOf(
    ExploreGenreItem("News", "News", Icons.Rounded.Newspaper),
    ExploreGenreItem("Tech", "Technology", Icons.Rounded.Computer),
    ExploreGenreItem("Business", "Business", Icons.Rounded.Work),
    ExploreGenreItem("Comedy", "Comedy", Icons.Rounded.SentimentVerySatisfied),
    ExploreGenreItem("True Crime", "True Crime", Icons.Rounded.Fingerprint),
    ExploreGenreItem("Sports", "Sports", Icons.Rounded.SportsBaseball),
    ExploreGenreItem("Health", "Health", Icons.Rounded.FavoriteBorder),
    ExploreGenreItem("History", "History", Icons.Rounded.AccountBalance),
    ExploreGenreItem("Arts", "Arts", Icons.Rounded.Palette),
    ExploreGenreItem("Society", "Society & Culture", Icons.Rounded.Person),
    ExploreGenreItem("Education", "Education", Icons.Rounded.School),
    ExploreGenreItem("Science", "Science", Icons.Rounded.Science),
    ExploreGenreItem("TV & Film", "TV & Film", Icons.Rounded.Movie),
    ExploreGenreItem("Fiction", "Fiction", Icons.Rounded.AutoStories),
    ExploreGenreItem("Music", "Music", Icons.Rounded.MusicNote),
    ExploreGenreItem("Religion", "Religion & Spirituality", Icons.Rounded.Star),
    ExploreGenreItem("Family", "Kids & Family", Icons.Rounded.Face),
    ExploreGenreItem("Leisure", "Leisure", Icons.Rounded.Weekend),
    ExploreGenreItem("Govt", "Government", Icons.Rounded.Gavel),
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

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ExploreGenreFilterChip(
                label = "All",
                selected = selectedCategory == "All",
                onClick = { onCategorySelected("All") },
                icon = Icons.Rounded.Apps,
            )
        }

        items(displayGenres, key = { it.value }) { genre ->
            ExploreGenreFilterChip(
                label = genre.label,
                selected = selectedCategory == genre.value,
                onClick = { onCategorySelected(genre.value) },
                icon = genre.icon,
            )
        }

        item {
            AssistChip(
                label = { Text("More") },
                onClick = { showSheet = true },
                trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                modifier = Modifier.heightIn(min = 48.dp),
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
            ExploreGenreSheetContent(selectedCategory) { category ->
                onCategorySelected(category)
                showSheet = false
            }
        }
    }
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
private fun ExploreGenreSheetContent(selectedCategory: String, onCategorySelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
    ) {
        Text(
            text = "Browse Genres",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ExploreGenreFilterChip(
                label = "All",
                selected = selectedCategory == "All",
                onClick = {
                    onCategorySelected("All")
                },
                icon = Icons.Rounded.Apps,
            )

            EXPLORE_GENRES.forEach { genre ->
                ExploreGenreFilterChip(
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
