package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast

/**
 * One Top chart place on tablets: the rank, the cover, the show's name and who makes it (plus its
 * genre under All). Screen readers hear "Number 3" before the title.
 */
@Composable
internal fun ExploreChartRow(rank: Int, podcast: Podcast, showGenre: Boolean, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = exploreRankDescription(rank)
    ExploreShowListRow(
        podcast = podcast,
        showGenre = showGenre,
        shape = shape,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        uniformHeight = true,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // The row already says "Number 3"; the numeral itself stays silent.
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
