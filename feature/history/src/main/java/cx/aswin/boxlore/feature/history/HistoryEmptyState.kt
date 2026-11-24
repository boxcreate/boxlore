package cx.aswin.boxlore.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreScrollableCenteredColumn
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.history.insights.InsightsBlobShape

/** The empty page's test tag. */
const val HISTORY_EMPTY_TAG = "history_empty"

/**
 * Both tabs with no history yet: the History icon in a coloured shape, a centred title and line, and
 * Explore. Centred when it fits; in short windows it scrolls, so Explore always clears [contentPadding]
 * (the switcher, the bar and the mini player).
 */
@Composable
internal fun HistoryEmptyState(
    onExploreClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val direction = LocalLayoutDirection.current
    // In a short window (a sideways phone, a split half) the icon sits beside the title and the gaps
    // tighten, so the title, line and Explore fit above the switcher without scrolling.
    val compact = boxloreIsShortPane()
    BoxloreScrollableCenteredColumn(
        modifier = modifier.fillMaxSize().testTag(HISTORY_EMPTY_TAG),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(direction) + 24.dp,
            end = contentPadding.calculateEndPadding(direction) + 24.dp,
            top = contentPadding.calculateTopPadding() + if (compact) 4.dp else 16.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        val title: @Composable () -> Unit = {
            Text(
                text = stringResource(R.string.history_empty_title),
                style = historyTextStyle(22f, 26f, GoogleSansWeight.semiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                title()
            }
        } else {
            Box(Modifier.size(72.dp).clip(InsightsBlobShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(20.dp))
            title()
        }
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        Text(
            text = stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        FilledTonalButton(onClick = onExploreClick, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.history_empty_explore), maxLines = 1)
        }
    }
}
