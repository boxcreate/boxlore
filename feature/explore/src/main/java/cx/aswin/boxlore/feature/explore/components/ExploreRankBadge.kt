package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.explore.R

/**
 * A show's place in Top charts: a 32dp circle that grows into a pill for two or three digits. It
 * takes no touches, so the card under it stays tappable. Screen readers hear "Number 3" with the
 * card's title; [announce] false leaves the number to the card (the featured card names it itself).
 */
@Composable
internal fun ExploreRankBadge(rank: Int, modifier: Modifier = Modifier, announce: Boolean = true) {
    val description = exploreRankDescription(rank)
    Box(
        modifier = modifier
            .testTag(EXPLORE_RANK_BADGE_TAG)
            .clearAndSetSemantics { if (announce) contentDescription = description }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

/** "Number 3", for a chart position read by a screen reader. */
@Composable
internal fun exploreRankDescription(rank: Int): String = androidx.compose.ui.res.stringResource(R.string.explore_chart_rank_description, rank)

internal const val EXPLORE_RANK_BADGE_TAG = "explore_rank_badge"
