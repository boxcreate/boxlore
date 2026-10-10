package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.explore.R

@Composable
internal fun ExploreEmptyState(searchMode: Boolean = true) {
    ExploreSearchLandingHeader(
        title = stringResource(if (searchMode) R.string.explore_show_search_empty_title else R.string.explore_browse_empty_title),
        subtitle = stringResource(if (searchMode) R.string.explore_show_search_empty_hint else R.string.explore_browse_empty_hint),
        modifier = Modifier.padding(vertical = 24.dp),
    )
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
