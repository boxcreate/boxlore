package cx.aswin.boxlore.feature.library.history

import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.feature.library.R
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Composable
fun HistoryPeriodSelector(
    selected: ListeningPeriod,
    onSelect: (ListeningPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            ListeningPeriod.DAYS_7 to stringResource(R.string.history_period_7d),
            ListeningPeriod.DAYS_30 to stringResource(R.string.history_period_30d),
            ListeningPeriod.DAYS_180 to stringResource(R.string.history_period_180d),
            ListeningPeriod.ALL to stringResource(R.string.history_period_all),
        )
    ConnectedOptionSelector(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        labelStyle = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun ListeningTimeCard(
    insights: ListeningInsightSummary,
    modifier: Modifier = Modifier,
) {
    val precise = insights.hasEnoughData
    val displayMs = listeningTimeDisplayMs(insights)
    val durationText = formatDuration(displayMs)
    val deltaLabel = listeningTimeDeltaLabel(insights)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 30.dp, y = (-38).dp)
                        .size(132.dp)
                        .rotate(12f)
                        .clip(ExpressiveShapes.Puffy)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_time_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = GoogleSansWeight.bold,
                    maxLines = 1,
                )
                ListeningTimeMetaChips(
                    precise = precise,
                    streakDays = insights.streakDays,
                    deltaLabel = deltaLabel,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListeningTimeMetaChips(
    precise: Boolean,
    streakDays: Int,
    deltaLabel: String?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!precise) {
            TimeMetaChip(text = stringResource(R.string.history_time_estimated_short))
        }
        if (streakDays > 0) {
            TimeMetaChip(
                text = stringResource(R.string.history_time_streak, streakDays),
            )
        }
        if (deltaLabel != null) {
            TimeMetaChip(
                text = "$deltaLabel ${stringResource(R.string.history_time_vs_previous_short)}",
            )
        }
    }
}

private fun listeningTimeDisplayMs(insights: ListeningInsightSummary): Long =
    if (insights.hasEnoughData) insights.totalConsumedMs else insights.estimatedLibraryMs

private fun listeningTimeDeltaLabel(insights: ListeningInsightSummary): String? {
    if (!insights.hasEnoughData || insights.period == ListeningPeriod.ALL) return null
    val delta = insights.totalConsumedMs - insights.previousPeriodConsumedMs
    val sign = if (delta >= 0) "+" else "-"
    return "$sign${formatDuration(abs(delta))}"
}

@Composable
private fun TimeMetaChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0m"
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes.coerceAtLeast(1)}m"
    }
}
