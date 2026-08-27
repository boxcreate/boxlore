package cx.aswin.boxlore.feature.library.history

import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningTimeBucket
import cx.aswin.boxlore.feature.library.R

private sealed interface InsightCard {
    data class Metric(
        val label: String,
        val value: String,
        val detail: String? = null,
        val icon: ImageVector,
    ) : InsightCard

    data class TopShow(
        val name: String,
        val detail: String,
        val imageUrl: String?,
    ) : InsightCard
}

@Composable
fun HistoryInsightCarousel(
    insights: ListeningInsightSummary,
    modifier: Modifier = Modifier,
) {
    val cards = buildInsightCards(insights)

    if (cards.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cards.filterIsInstance<InsightCard.TopShow>().forEach { card ->
            TopShowCardBody(card)
        }
        val metrics = cards.filterIsInstance<InsightCard.Metric>()
        metrics.chunked(2).forEach { rowCards ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowCards.forEach { card ->
                    MetricCardBody(
                        card = card,
                        wide = rowCards.size == 1,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun buildInsightCards(insights: ListeningInsightSummary): List<InsightCard> {
    val peakLabel = peakBucketLabel(insights)
    val peakConsumedMs = peakBucketConsumedMs(insights)
    val peakShare = peakBucketShare(insights, peakConsumedMs)
    return buildList {
        addTopShowCard(insights)
        addMetricCards(insights, peakLabel, peakConsumedMs, peakShare)
    }
}

@Composable
private fun peakBucketLabel(insights: ListeningInsightSummary): String =
    insights.peakBucket?.let { timeBucketLabel(it) }
        ?: stringResource(R.string.history_waiting_for_plays)

private fun peakBucketConsumedMs(insights: ListeningInsightSummary): Long =
    when (insights.peakBucket) {
        ListeningTimeBucket.MORNING -> insights.morningMs
        ListeningTimeBucket.AFTERNOON -> insights.afternoonMs
        ListeningTimeBucket.EVENING -> insights.eveningMs
        ListeningTimeBucket.NIGHT -> insights.nightMs
        null -> 0L
    }

private fun peakBucketShare(
    insights: ListeningInsightSummary,
    peakConsumedMs: Long,
): Int? =
    if (insights.peakBucket != null && insights.totalConsumedMs > 0L) {
        ((peakConsumedMs * 100L) / insights.totalConsumedMs).toInt().coerceIn(0, 100)
    } else {
        null
    }

@Composable
private fun MutableList<InsightCard>.addTopShowCard(insights: ListeningInsightSummary) {
    val topShow = insights.topShow ?: return
    add(
        InsightCard.TopShow(
            name = topShow.podcastName,
            detail =
                if (topShow.consumedMs > 0) {
                    stringResource(
                        R.string.history_top_show_listened,
                        formatDuration(topShow.consumedMs),
                    )
                } else {
                    stringResource(
                        R.string.history_top_show_play_count,
                        topShow.sessionCount,
                    )
                },
            imageUrl = topShow.podcastImageUrl,
        ),
    )
}

@Composable
private fun MutableList<InsightCard>.addMetricCards(
    insights: ListeningInsightSummary,
    peakLabel: String,
    peakConsumedMs: Long,
    peakShare: Int?,
) {
    add(
        InsightCard.Metric(
            label = stringResource(R.string.history_card_active_days),
            value = insights.activeDaysInPeriod.toString(),
            detail = stringResource(R.string.history_active_days_detail),
            icon = Icons.Rounded.CalendarMonth,
        ),
    )
    add(
        InsightCard.Metric(
            label = stringResource(R.string.history_card_completion),
            value = insights.completedCount.toString(),
            detail =
                stringResource(
                    R.string.history_completion_in_progress,
                    insights.inProgressCount,
                ),
            icon = Icons.Rounded.CheckCircle,
        ),
    )
    add(
        InsightCard.Metric(
            label = stringResource(R.string.history_card_window),
            value = peakLabel,
            detail =
                when {
                    peakShare != null && peakConsumedMs > 0L ->
                        stringResource(
                            R.string.history_window_share,
                            formatDuration(peakConsumedMs),
                            peakShare,
                        )
                    else -> null
                },
            icon = Icons.Rounded.Schedule,
        ),
    )
    if (insights.streakDays > 0) {
        add(
            InsightCard.Metric(
                label = stringResource(R.string.history_stat_streak),
                value = insights.streakDays.toString(),
                detail = null,
                icon = Icons.Rounded.LocalFireDepartment,
            ),
        )
    }
    if (insights.hasEnoughData) {
        add(
            InsightCard.Metric(
                label = stringResource(R.string.history_card_sessions),
                value = formatDuration(insights.averageSessionMs),
                detail =
                    stringResource(
                        R.string.history_sessions_longest,
                        formatDuration(insights.longestSessionMs),
                    ),
                icon = Icons.Rounded.Headphones,
            ),
        )
    }
}

@Composable
private fun MetricCardBody(
    card: InsightCard.Metric,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (wide) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricCardText(
                    card = card,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp).size(28.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = card.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = GoogleSansWeight.semiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                MetricValueAndDetail(card)
            }
        }
    }
}

@Composable
private fun MetricCardText(
    card: InsightCard.Metric,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = card.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        MetricValueAndDetail(card)
    }
}

@Composable
private fun MetricValueAndDetail(card: InsightCard.Metric) {
    Text(
        text = card.value,
        style =
            if (card.value.length > 10) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
        fontWeight = GoogleSansWeight.bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
    )
    card.detail?.let { detail ->
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun TopShowCardBody(card: InsightCard.TopShow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!card.imageUrl.isNullOrBlank()) {
                OptimizedImage(
                    url = card.imageUrl,
                    proxyWidth = 160,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_card_top_show),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                )
                Text(
                    text = card.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
