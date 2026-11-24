package cx.aswin.boxlore.feature.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Gavel
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.AdaptiveOverflowRow
import cx.aswin.boxlore.core.designsystem.components.BoxloreFilterChip
import cx.aswin.boxlore.core.designsystem.components.BoxloreFilterChipDefaults
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.BoxloreMoreChip
import cx.aswin.boxlore.core.designsystem.components.rememberAdaptiveOverflowState
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.home.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreSelector(
    selectedCategory: String?, // Null = Top
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saved, so the More sheet stays open when the window changes size or the activity is recreated.
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val overflow = rememberAdaptiveOverflowState()
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
            onOverflow = { showSheet = true },
            modifier = modifier,
            state = overflow,
            item = { index ->
                if (index == 0) {
                    HomeGenreFilterChip(stringResource(R.string.home_genres_top), selectedCategory == null, { onCategorySelected(null) }, Icons.Rounded.Whatshot)
                } else {
                    val genre = GENRES[index - 1]
                    HomeGenreFilterChip(stringResource(genre.label), selectedCategory == genre.value, { onCategorySelected(genre.value) }, genre.icon)
                }
            },
            more = { onClick -> HomeMoreGenresChip(onClick) },
        )
    } else {
        Row(
            modifier =
            modifier
                .fillMaxWidth()
                // Runs to the screen edge, with the gutter inside the scroll, so chips slide off the edge.
                .bleed(HomeFeedSpacing.GridSide)
                .horizontalScroll(scrollState)
                .padding(horizontal = HomeFeedSpacing.GridSide),
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

            HomeMoreGenresChip { showSheet = true }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            // Behind More on a wide row: the genres the row couldn't fit, following the row as the
            // window changes. On phones, or once everything fits, every genre with Top.
            val remaining = overflow.remaining
            val sheetGenres = if (tablet && remaining.isNotEmpty()) remaining.map { GENRES[it - 1] } else GENRES
            HomeGenreSheetContent(selectedCategory, sheetGenres, includeTop = !tablet || remaining.isEmpty()) { category ->
                onCategorySelected(category)
                showSheet = false
            }
        }
    }
}

@Composable
private fun HomeMoreGenresChip(onClick: () -> Unit) {
    BoxloreMoreChip(onClick = onClick, label = stringResource(R.string.home_genres_more), modifier = Modifier.testTag("home_genres_more"))
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = GoogleSansWeight.bold,
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
                    onClick = { onCategorySelected(null) },
                    icon = Icons.Rounded.Whatshot,
                    container = BoxloreFilterChipDefaults.SheetContainer,
                )
            }

            genres.forEach { genre ->
                HomeGenreFilterChip(
                    label = stringResource(genre.label),
                    selected = selectedCategory == genre.value,
                    onClick = { onCategorySelected(genre.value) },
                    icon = genre.icon,
                    container = BoxloreFilterChipDefaults.SheetContainer,
                )
            }
        }
    }
}

/** A genre chip: on Home's row in the page's container, in the More sheet in the sheet's. */
@Composable
private fun HomeGenreFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    container: Color = BoxloreFilterChipDefaults.PageContainer,
) {
    BoxloreFilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = icon,
        container = container,
        modifier = Modifier.testTag("home_genre_chip"),
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
