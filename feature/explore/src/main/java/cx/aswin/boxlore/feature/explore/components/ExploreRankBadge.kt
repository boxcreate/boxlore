package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.explore.R

/**
 * A show's place in Top charts on its cover: a 32dp circle that grows into a pill for two or three
 * digits. Screen readers hear "Number 3, <title>".
 */
@Composable
internal fun ExploreRankBadge(rank: Int, title: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.explore_chart_rank_description, rank, title)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.testTag(EXPLORE_RANK_BADGE_TAG).clearAndSetSemantics { contentDescription = description },
    ) {
        Box(Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(text = "$rank", style = MaterialTheme.typography.labelLarge, fontWeight = GoogleSansWeight.bold, maxLines = 1)
        }
    }
}

internal const val EXPLORE_RANK_BADGE_TAG = "explore_rank_badge"
