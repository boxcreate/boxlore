package cx.aswin.boxlore.feature.explore.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreSearchField
import cx.aswin.boxlore.core.designsystem.components.BoxloreSearchFieldMaxWidth
import cx.aswin.boxlore.feature.explore.R
import cx.aswin.boxlore.feature.explore.SearchTab

/**
 * Explore's search box: the shared search field, at most 720dp wide and lined up with the page's
 * headings. [active] shows Back (leave search: clear the query and topic); Clear empties the query;
 * the keyboard's search key calls [onSearch]. The hint says what to type in each search mode.
 */
@Composable
@Suppress("LongParameterList") // The query, its mode and the box's callbacks, as the screen owns them.
internal fun ExploreHeaderSearchField(
    query: String,
    searchTab: SearchTab,
    active: Boolean,
    focusRequester: FocusRequester,
    onActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    val searching = active || query.isNotEmpty()
    BoxloreSearchField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        placeholder = stringResource(exploreSearchPlaceholder(searching, searchTab)),
        modifier = Modifier
            .focusRequester(focusRequester)
            // Focusing the box opens search, as tapping it always has.
            .onFocusChanged { if (it.hasFocus) onActiveChange(true) },
        active = searching,
        onBack = onBack,
        maxWidth = BoxloreSearchFieldMaxWidth,
    )
}

/** The search box's hint: what to type in the current mode, or "Search podcasts" while browsing. */
@StringRes
internal fun exploreSearchPlaceholder(searching: Boolean, searchTab: SearchTab): Int = when {
    !searching -> R.string.explore_search_podcasts
    searchTab == SearchTab.EPISODES -> R.string.explore_search_placeholder_questions
    else -> R.string.explore_search_placeholder_shows
}

/** Which row sits under the search box: the search modes, the genre filters (Top) or the moods (For You). */
internal enum class ExploreHeaderRow { SearchModes, Genres, Moods, None }

/** The rows under Explore's search box, with the spacing each one always had. */
@Composable
@Suppress("LongParameterList") // Each row's state and callback.
internal fun ExploreHeaderSelectors(
    row: ExploreHeaderRow,
    searchTab: SearchTab,
    onSearchTabSelected: (SearchTab) -> Unit,
    category: String,
    onCategorySelected: (String) -> Unit,
    moods: List<Pair<String, String>>,
    onMoodSelected: (String, String) -> Unit,
) {
    when (row) {
        ExploreHeaderRow.SearchModes -> {
            Spacer(modifier = Modifier.height(12.dp))
            // In the search box's column, so the modes sit under it on wide windows.
            SearchTabSelector(selectedTab = searchTab, onTabSelected = onSearchTabSelected, modifier = Modifier.widthIn(max = BoxloreSearchFieldMaxWidth).fillMaxWidth())
        }
        ExploreHeaderRow.Genres, ExploreHeaderRow.Moods -> {
            Spacer(modifier = Modifier.height(8.dp))
            ExploreBrowseRowSlot(
                showMoods = row == ExploreHeaderRow.Moods,
                shareHeight = moods.isNotEmpty(),
                genres = { ExploreGenreSelector(selectedCategory = category, onCategorySelected = onCategorySelected) },
                moods = { ExploreVibeChipRow(vibes = moods, onVibeSelected = onMoodSelected) },
            )
        }
        ExploreHeaderRow.None -> Unit
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * The genre row (Top) and the mood row (For You) in one slot as tall as the taller of the two, so
 * the header and the feed under it never move when swiping between the tabs, at any text size.
 * Only the current row is placed, drawn, tappable and read out.
 */
@Composable
private fun ExploreBrowseRowSlot(showMoods: Boolean, shareHeight: Boolean, genres: @Composable () -> Unit, moods: @Composable () -> Unit) {
    Layout(
        content = {
            Box(if (showMoods) Modifier.clearAndSetSemantics { } else Modifier) { genres() }
            Box(if (showMoods) Modifier else Modifier.clearAndSetSemantics { }) { moods() }
        },
    ) { measurables, constraints ->
        val rows = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val shown = rows[if (showMoods) 1 else 0]
        val height = if (shareHeight) rows.maxOf { it.height } else shown.height
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else shown.width
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) { shown.place(0, 0) }
    }
}
