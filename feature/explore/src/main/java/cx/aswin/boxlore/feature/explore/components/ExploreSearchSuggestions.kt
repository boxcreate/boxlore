package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.explore.R
import cx.aswin.boxlore.feature.explore.SearchTab
import cx.aswin.boxlore.feature.explore.logic.ConceptSearchIdleLogic

/** Shared, scrollable orientation for search suggestions and recovery. */
@Composable
internal fun ExploreSearchLandingHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().semantics { heading() },
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
            )
        }
    }
}

/** A compact status fits the feed rather than reserving a large empty loader panel. */
@Composable
internal fun ExploreSearchLoadingState(searchTab: SearchTab, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("explore_search_loading")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        BoxLoreLoader.Expressive(size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(if (searchTab == SearchTab.SHOWS) R.string.explore_show_search_loading else R.string.explore_question_search_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Questions retain their exact query strings and ordering; only presentation belongs here. */
@Composable
internal fun ConceptQuestionList(onExampleClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val icons = listOf(Icons.Rounded.AccountBalance, Icons.Rounded.AutoAwesome, Icons.Rounded.Psychology, Icons.Rounded.AccountBalanceWallet)
    val examples = ConceptSearchIdleLogic.examples
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        examples.forEachIndexed { index, example ->
            val top = if (index == 0) 24.dp else 6.dp
            val bottom = if (index == examples.lastIndex) 24.dp else 6.dp
            Surface(
                onClick = { onExampleClick(example.query) },
                modifier = Modifier.fillMaxWidth().testTag("concept_suggestion_$index"),
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(icons.getOrElse(index) { Icons.Rounded.AutoAwesome }, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Text(
                        example.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = GoogleSansWeight.semiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
