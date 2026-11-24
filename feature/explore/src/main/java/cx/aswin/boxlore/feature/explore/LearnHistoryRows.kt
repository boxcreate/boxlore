package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreFilterChip
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.text.LocaleFormats
import java.util.Calendar
import java.util.Locale

/** Which cards Lore history shows. Only the screen remembers it; history itself is unchanged. */
internal enum class LearnHistoryFilter { All, Queued, Skipped }

internal fun LearnHistoryFilter.keeps(entry: LearnHistoryEntry): Boolean = when (this) {
    LearnHistoryFilter.All -> true
    LearnHistoryFilter.Queued -> entry.action == LearnHistoryAction.QUEUE
    LearnHistoryFilter.Skipped -> entry.action == LearnHistoryAction.DISMISS
}

@Composable
internal fun LearnHistoryFilterRow(selected: LearnHistoryFilter, onSelect: (LearnHistoryFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LearnHistoryFilter.entries.forEach { filter ->
            BoxloreFilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = stringResource(
                    when (filter) {
                        LearnHistoryFilter.All -> R.string.lore_history_filter_all
                        LearnHistoryFilter.Queued -> R.string.lore_history_queued
                        LearnHistoryFilter.Skipped -> R.string.lore_history_filter_skipped
                    },
                ),
            )
        }
    }
}

@Composable
internal fun LearnHistoryGroupHeader(period: LearnHistoryPeriod, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val label = when (period) {
        LearnHistoryPeriod.Today -> stringResource(R.string.lore_history_today)
        LearnHistoryPeriod.Yesterday -> stringResource(R.string.lore_history_yesterday)
        LearnHistoryPeriod.ThisWeek -> stringResource(R.string.lore_history_this_week)
        LearnHistoryPeriod.Earlier -> stringResource(R.string.lore_history_earlier)
        is LearnHistoryPeriod.Month -> remember(period, locale) {
            learnHistoryMonthLabel(period, Calendar.getInstance().get(Calendar.YEAR), locale)
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 6.dp).semantics { heading() },
    )
}

/**
 * A month group's heading: the month, with the year when it isn't this year. It starts with a
 * capital like the other headings, also where the language writes months in lower case
 * ("novembre" becomes "Novembre").
 */
internal fun learnHistoryMonthLabel(period: LearnHistoryPeriod.Month, thisYear: Int, locale: Locale): String {
    val style = if (period.year == thisYear) LocaleFormats.DateStyle.MONTH else LocaleFormats.DateStyle.MONTH_YEAR
    return LocaleFormats.date(period.anyMs, style, locale).replaceFirstChar { it.titlecase(locale) }
}

/** Where a row sits in its group, for the connected list's corners. */
internal fun learnHistoryRowShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/**
 * One card in Lore history: its cover, the question (at most three lines) and a quiet "Queued ·
 * show" or "Skipped · show" line, with Restore at the end. The row opens the episode.
 */
@Composable
internal fun LearnHistoryRow(
    entry: LearnHistoryEntry,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = loreArtworkSources(entry.imageUrl, entry.feedImage)
    val queued = entry.action == LearnHistoryAction.QUEUE
    val actionLabel = stringResource(if (queued) R.string.lore_history_queued else R.string.lore_history_dismissed)
    val meta = listOfNotNull(actionLabel, entry.podcastTitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
    Surface(
        modifier = modifier.fillMaxWidth().expressiveClickable(shape = shape, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OptimizedImage(
                url = sources.firstOrNull().orEmpty(),
                fallbackUrls = sources,
                proxyWidth = 160,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.question.ifBlank { entry.episodeTitle },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (queued) Icons.AutoMirrored.Rounded.PlaylistAdd else Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onRestore, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.lore_history_restore))
            }
        }
    }
}

/** When a filter matches nothing: says so, with a way back to every card. */
@Composable
internal fun LearnHistoryFilterEmpty(filter: LearnHistoryFilter, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (filter == LearnHistoryFilter.Queued) R.string.lore_history_none_queued else R.string.lore_history_none_skipped),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onShowAll) { Text(stringResource(R.string.lore_history_show_all)) }
    }
}
