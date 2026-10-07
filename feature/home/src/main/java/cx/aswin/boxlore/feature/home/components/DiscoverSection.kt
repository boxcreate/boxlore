package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme
import cx.aswin.boxlore.feature.home.R

/** Genre-filtered discovery; selection and navigation remain owned by the feed. */
@Composable
fun DiscoverSection(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DiscoveryExpressiveTheme {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HomeFeedSpacing.HeaderContentGap)) {
            HomeTopLevelSectionHeader(
                title = stringResource(R.string.home_explore_shows),
                seeAllContentDescription = stringResource(R.string.home_shows_see_all),
                onSeeAllClick = onHeaderClick,
                chapter = HomeDiscoveryChapter.EXPLORE,
            )
            GenreSelector(selectedCategory = selectedCategory, onCategorySelected = onCategorySelected)
        }
    }
}
