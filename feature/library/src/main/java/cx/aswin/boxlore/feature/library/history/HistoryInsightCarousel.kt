package cx.aswin.boxlore.feature.library.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningTimeBucket
import cx.aswin.boxlore.feature.library.R

private sealed interface InsightCard {
    data class Metric(
        val label: String,
        val value: String,
        val detail: String? = null,
    ) : InsightCard {
        val detailHasNumber: Boolean
            get() = detail?.any { it.isDigit() } == true
    }

    data class TopShow(
        val name: String,
        val detail: String,
        val imageUrl: String?,
    ) : InsightCard
}

private val InsightCardWidth = 280.dp
private val InsightCardHeight = 156.dp

@Composable
fun HistoryInsightCarousel(
    insights: ListeningInsightSummary,
    modifier: Modifier = Modifier,
) {
    val cards = buildInsightCards(insights)

    if (cards.isEmpty()) return

    val pagerState = rememberPagerState { cards.size }
    val horizontalOverscrollLock =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = Offset(x = available.x, y = 0f)
            }
        }

    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fixed(InsightCardWidth),
        contentPadding = PaddingValues(end = 48.dp),
        pageSpacing = 12.dp,
        modifier =
            modifier
                .fillMaxWidth()
                .height(InsightCardHeight)
                .nestedScroll(horizontalOverscrollLock),
    ) { index ->
        InsightSwipeCard(
            card = cards[index],
            modifier = Modifier.fillMaxSize(),
        )
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
        ),
    )
    if (insights.streakDays > 0) {
        add(
            InsightCard.Metric(
                label = stringResource(R.string.history_stat_streak),
                value = insights.streakDays.toString(),
                detail = null,
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
            ),
        )
    }
}

@Composable
private fun InsightSwipeCard(
    card: InsightCard,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        when (card) {
            is InsightCard.TopShow -> TopShowCardBody(card)
            is InsightCard.Metric -> MetricCardBody(card)
        }
    }
}

@Composable
private fun MetricCardBody(card: InsightCard.Metric) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        val heightPx = maxHeight.value
        val lengthFactor =
            when {
                card.value.length <= 2 -> 0.62f
                card.value.length <= 4 -> 0.48f
                card.value.length <= 8 -> 0.34f
                else -> 0.24f
            }
        val valueSp =
            (heightPx * lengthFactor).coerceIn(36f, 72f).sp

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!card.detailHasNumber) {
                    card.detail?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = card.value,
                    style =
                        MaterialTheme.typography.displayMedium.copy(
                            fontSize = valueSp,
                            lineHeight = valueSp * 1.05f,
                        ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                if (card.detailHasNumber) {
                    Text(
                        text = card.detail.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopShowCardBody(card: InsightCard.TopShow) {
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!card.imageUrl.isNullOrBlank()) {
            OptimizedImage(
                url = card.imageUrl,
                proxyWidth = 200,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.history_card_top_show),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = card.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
