package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.explore.R

/** No shows: no matches for a search or none to browse, or Try again when [failed] says the read failed. */
@Composable
internal fun ExploreEmptyState(searchMode: Boolean = true, failed: Boolean = false, onRetry: () -> Unit = {}) {
    if (failed) {
        ExploreLoadFailedState(
            title = stringResource(if (searchMode) R.string.explore_show_search_failed_title else R.string.explore_browse_failed_title),
            onRetry = onRetry,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    ExploreSearchLandingHeader(
        title = stringResource(if (searchMode) R.string.explore_show_search_empty_title else R.string.explore_browse_empty_title),
        subtitle = stringResource(if (searchMode) R.string.explore_show_search_empty_hint else R.string.explore_browse_empty_hint),
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

/** A read that failed says so and offers Try again, so it never reads as "nothing there". */
@Composable
internal fun ExploreLoadFailedState(title: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ExploreSearchLandingHeader(title, stringResource(R.string.explore_load_failed_hint))
        FilledTonalButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.explore_try_again))
        }
    }
}

/** The end of the Top chart grid: a loader while the next page loads, or Try again when it failed. */
internal fun LazyStaggeredGridScope.exploreNextPageFooter(isLoadingMore: Boolean, failed: Boolean, onRetry: () -> Unit) {
    if (!isLoadingMore && !failed) return
    item(key = "explore_next_page_footer", span = StaggeredGridItemSpan.FullLine) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLoadingMore) {
                BoxLoreLoader.Expressive(size = 40.dp)
            } else {
                Text(
                    stringResource(R.string.explore_next_page_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.explore_try_again))
                }
            }
        }
    }
}

@Composable
internal fun ExploreEpisodesSearchEmptyState(onExampleClick: (String) -> Unit, modifier: Modifier = Modifier) {
    ConceptSearchLanding(
        title = stringResource(R.string.explore_question_search_empty_title),
        subtitle = stringResource(R.string.explore_question_search_empty_hint),
        onExampleClick = onExampleClick,
        modifier = modifier,
    )
}

@Composable
internal fun ExploreEpisodesSearchIdleState(onExampleClick: (String) -> Unit, modifier: Modifier = Modifier) {
    ConceptSearchLanding(
        title = stringResource(R.string.explore_question_search_idle_title),
        subtitle = stringResource(R.string.explore_question_search_idle_hint),
        onExampleClick = onExampleClick,
        modifier = modifier,
    )
}

@Composable
private fun ConceptSearchLanding(title: String, subtitle: String, onExampleClick: (String) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExploreSearchLandingHeader(title, subtitle)
        ConceptQuestionList(onExampleClick)
    }
}

@Composable
fun ExploreRecommendationsEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your Taste Profile is Growing",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "As you listen to more episodes and subscribe to shows, we'll curate personalized recommendations here tailored to your taste.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
