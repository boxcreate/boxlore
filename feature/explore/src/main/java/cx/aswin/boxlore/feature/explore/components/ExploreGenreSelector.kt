package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Data Handling (Synced with GenreSelector.kt and OnboardingScreen.kt)
internal data class ExploreGenreItem(val label: String, val value: String, val icon: ImageVector)

internal val EXPLORE_GENRES = listOf(
    ExploreGenreItem("News", "News", Icons.Rounded.Newspaper),
    ExploreGenreItem("Tech", "Technology", Icons.Rounded.Computer),
    ExploreGenreItem("Business", "Business", Icons.Rounded.Work),
    ExploreGenreItem("Comedy", "Comedy", Icons.Rounded.EmojiEvents),
    ExploreGenreItem("True Crime", "True Crime", Icons.Rounded.Search),
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
    ExploreGenreItem("Govt", "Government", Icons.Rounded.Gavel)
)

/**
 * Expandable Genre Cloud for Explore
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ExploreGenreSelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    // Dynamic list construction
    val displayGenres = remember(selectedCategory) {
        val topGenres = EXPLORE_GENRES.take(5)
        if (selectedCategory != "All") {
            val selectedGenre = EXPLORE_GENRES.find { it.value == selectedCategory }
            if (selectedGenre != null) {
                // Selected + (Top 5 - Selected)
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

    // Top horizontal list (Subset)
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. "All" (Always first)
        item {
            FilterChip(
                selected = selectedCategory == "All",
                onClick = { onCategorySelected("All") },
                label = { Text("All", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedCategory == "All")
            )
        }

        // 2. Dynamic Subset
        items(displayGenres, key = { it.value }) { genre ->
            FilterChip(
                selected = selectedCategory == genre.value,
                onClick = { onCategorySelected(genre.value) },
                label = { Text(genre.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedCategory == genre.value)
            )
        }

        // 3. Expand Button
        item {
            FilterChip(
                selected = showSheet,
                onClick = { showSheet = true },
                label = { Text("More Genres") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Expand",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = Color.Transparent
                )
            )
        }    }

    // Full Genre Sheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp) // Nav bar padding
            ) {
                Text(
                    text = "Browse Genres",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "All" in sheet
                    FilterChip(
                        selected = selectedCategory == "All",
                        onClick = { 
                            onCategorySelected("All")
                            showSheet = false 
                        },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                             enabled = true,
                             selected = selectedCategory == "All",
                             borderColor = Color.Transparent
                        )
                    )

                    EXPLORE_GENRES.forEach { genre ->
                        val isSelected = selectedCategory == genre.value
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                onCategorySelected(genre.value)
                                showSheet = false 
                            },
                            label = { Text(genre.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                 enabled = true,
                                 selected = isSelected,
                                 borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}
