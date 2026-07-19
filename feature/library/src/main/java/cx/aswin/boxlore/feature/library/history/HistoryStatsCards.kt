package cx.aswin.boxlore.feature.library.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.feature.library.R
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val roundedPressShape = RoundedCornerShape(12.dp)
    val checkedShape = ButtonGroupDefaults.connectedButtonCheckedShape

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (period, label) ->
            ToggleButton(
                checked = selected == period,
                onCheckedChange = { checked -> if (checked) onSelect(period) },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 ->
                            ButtonGroupDefaults.connectedLeadingButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                        options.lastIndex ->
                            ButtonGroupDefaults.connectedTrailingButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                        else ->
                            ButtonGroupDefaults.connectedMiddleButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                    },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListeningTimeCard(
    insights: ListeningInsightSummary,
    modifier: Modifier = Modifier,
) {
    val precise = insights.hasEnoughData
    val displayMs = if (precise) insights.totalConsumedMs else insights.estimatedLibraryMs
    val durationText = formatDuration(displayMs)
    val deltaLabel =
        if (precise && insights.period != ListeningPeriod.ALL) {
            val delta = insights.totalConsumedMs - insights.previousPeriodConsumedMs
            val sign = if (delta >= 0) "+" else "-"
            "$sign${formatDuration(abs(delta))}"
        } else {
            null
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            val lengthFactor =
                when {
                    durationText.length <= 2 -> 0.28f
                    durationText.length <= 5 -> 0.22f
                    else -> 0.16f
                }
            val valueSp =
                min(maxWidth.value * lengthFactor, 68f).coerceAtLeast(40f).sp

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_time_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = durationText,
                        style =
                            MaterialTheme.typography.displayMedium.copy(
                                fontSize = valueSp,
                                lineHeight = valueSp * 1.05f,
                            ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!precise) {
                            TimeMetaChip(text = stringResource(R.string.history_time_estimated_short))
                        }
                        if (insights.streakDays > 0) {
                            TimeMetaChip(
                                text = stringResource(R.string.history_time_streak, insights.streakDays),
                            )
                        }
                        if (deltaLabel != null) {
                            TimeMetaChip(
                                text = "$deltaLabel ${stringResource(R.string.history_time_vs_previous_short)}",
                            )
                        }
                    }
                }
            }
        }
    }
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
            fontWeight = FontWeight.Medium,
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
