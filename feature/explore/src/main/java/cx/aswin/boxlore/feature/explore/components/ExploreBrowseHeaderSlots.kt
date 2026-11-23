package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.feature.explore.R
import cx.aswin.boxlore.feature.explore.SearchTab

/**
 * Explore's search box. [active] shows Back (leave search: clear the query and topic); Clear empties
 * the query; the keyboard's search key calls [onSearch].
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    DockedSearchBar(
        expanded = false,
        onExpandedChange = onActiveChange,
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier.focusRequester(focusRequester).then(if (LocalBoxloreWindowLayout.current.usesTabletLayout) Modifier.fillMaxWidth() else Modifier),
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onSearch() },
                expanded = false,
                onExpandedChange = onActiveChange,
                placeholder = {
                    Text(stringResource(if (searchTab == SearchTab.EPISODES) R.string.explore_search_episodes else R.string.explore_search_podcasts))
                },
                leadingIcon = {
                    if (active || query.isNotEmpty()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.explore_back))
                        }
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.explore_clear_search))
                        }
                    }
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) { }
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
            SearchTabSelector(selectedTab = searchTab, onTabSelected = onSearchTabSelected, modifier = Modifier.fillMaxWidth())
        }
        ExploreHeaderRow.Genres -> {
            Spacer(modifier = Modifier.height(8.dp))
            ExploreGenreSelector(selectedCategory = category, onCategorySelected = onCategorySelected)
        }
        ExploreHeaderRow.Moods -> {
            Spacer(modifier = Modifier.height(8.dp))
            ExploreVibeChipRow(vibes = moods, onVibeSelected = onMoodSelected)
        }
        ExploreHeaderRow.None -> Unit
    }
    Spacer(Modifier.height(8.dp))
}
