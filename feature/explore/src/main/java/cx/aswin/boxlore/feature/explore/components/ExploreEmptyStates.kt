package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.BoxloreSearchFieldMaxWidth
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
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

/**
 * A read the API refused until this phone passes the quick check: what needs it and why ([title],
 * [body]), and a button that starts it, which waits while [checking]. [missed] says the last check
 * didn't pass. Never "nothing found" or "check your connection", which would both be untrue.
 */
@Composable
internal fun ExploreQuickCheckNotice(
    title: String,
    body: String,
    checking: Boolean,
    missed: Boolean,
    onStartCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().testTag(EXPLORE_QUICK_CHECK_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExploreSearchLandingHeader(title, body)
        FilledTonalButton(onClick = onStartCheck, enabled = !checking, modifier = Modifier.heightIn(min = 48.dp)) {
            if (checking) {
                BoxLoreLoader.CircularWavy(size = 20.dp, color = LocalContentColor.current)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.explore_check_running))
            } else {
                Text(stringResource(R.string.explore_check_start))
            }
        }
        if (missed && !checking) {
            Text(
                stringResource(R.string.explore_check_missed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
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
    // In the search box's column (start-aligned, at most 720dp), so the examples line up under the box they fill in.
    Column(
        modifier.fillMaxWidth().wrapContentWidth(Alignment.Start).widthIn(max = BoxloreSearchFieldMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        // boxlore's rounded "cookie" badge in the theme colour, as Lore history's empty state has.
        Surface(
            shape = ExpressiveShapes.Cookie6,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(64.dp).testTag(EXPLORE_TASTE_BADGE_TAG),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.explore_taste_growing_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.explore_taste_growing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

internal const val EXPLORE_TASTE_BADGE_TAG = "explore_taste_badge"
internal const val EXPLORE_QUICK_CHECK_TAG = "explore_quick_check"
