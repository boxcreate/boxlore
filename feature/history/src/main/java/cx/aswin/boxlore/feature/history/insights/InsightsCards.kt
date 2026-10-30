package cx.aswin.boxlore.feature.history.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.TimelineType
import cx.aswin.boxlore.feature.history.timeline.durationText
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlin.math.sqrt

/** The organic blob of the boards' cards: unequal corner rounding, as their CSS border-radius. */
internal val InsightsBlobShape = RoundedCornerShape(topStartPercent = 46, topEndPercent = 54, bottomEndPercent = 42, bottomStartPercent = 58)

/** A 28dp-cornered, 20dp-padded insights card on [color] (surfaceContainer by default). */
@Composable
internal fun InsightsCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit,) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** A card's 16/22 title with an optional fact on the right. */
@Composable
internal fun InsightsCardHeader(title: String, trailing: String? = null) {
    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = TimelineType.header, color = MaterialTheme.colorScheme.onSurface)
        if (trailing != null) Text(trailing, style = TimelineType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 7d | 30d | 180d | All: 44dp, 4dp apart; the chosen one primary and fully round, the others 10dp corners with round outer ends. */
@Composable
internal fun InsightsPeriodSelector(selected: ListeningPeriod, onSelect: (ListeningPeriod) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.history_period_group)
    val names =
        mapOf(
            ListeningPeriod.DAYS_7 to stringResource(R.string.history_period_7d),
            ListeningPeriod.DAYS_30 to stringResource(R.string.history_period_30d),
            ListeningPeriod.DAYS_180 to stringResource(R.string.history_period_180d),
            ListeningPeriod.ALL to stringResource(R.string.history_period_all),
        )
    Row(modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ListeningPeriod.entries.forEachIndexed { index, period ->
            val isSelected = period == selected
            val shape =
                when {
                    isSelected -> RoundedCornerShape(22.dp)
                    index == 0 -> RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 10.dp, bottomEnd = 10.dp)
                    index == ListeningPeriod.entries.lastIndex -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 22.dp, bottomEnd = 22.dp)
                    else -> RoundedCornerShape(10.dp)
                }
            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(shape)
                    .background(if (isSelected) colors.primary else colors.surfaceContainerHigh)
                    .expressiveClickable(shape = shape, feedback = ExpressivePressFeedback.Control) { onSelect(period) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(names.getValue(period), style = TimelineType.label, color = if (isSelected) colors.onPrimary else colors.onSurface)
            }
        }
    }
}

/** Time listened (A4): primaryContainer, the total at 45sp, a day's average, and pills for the change and the best run. */
@Composable
internal fun TimeListenedCard(time: TimeListened, period: ListeningPeriod) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.primaryContainer)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 36.dp, y = (-44).dp)
                .size(150.dp)
                .rotate(12f)
                .clip(InsightsBlobShape)
                .background(colors.primary.copy(alpha = 0.14f)),
        )
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.history_ins_time_title), style = InsightsType.label, color = colors.onPrimaryContainer)
            Text(durationText(time.totalMs), style = InsightsType.hero, color = colors.onPrimaryContainer)
            Text(perDayText(time.perDayMs), style = InsightsType.body16, color = colors.onPrimaryContainer)
            val comparison = comparisonText(time, period)
            if (comparison != null || time.bestRunDays > 1) {
                FlowRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (comparison != null) {
                        val up = time.totalMs >= (time.previousMs ?: 0L)
                        InsightsPill(if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, comparison)
                    }
                    if (time.bestRunDays > 1) InsightsPill(Icons.Rounded.LocalFireDepartment, pluralStringResource(R.plurals.history_ins_best_run, time.bestRunDays, time.bestRunDays))
                }
            }
        }
    }
}

/** A 32dp pill on onPrimaryContainer at 12%: a 16dp icon and 13sp text. */
@Composable
internal fun InsightsPill(icon: ImageVector?, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(colors.onPrimaryContainer.copy(alpha = 0.12f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(16.dp))
        Text(text, style = InsightsType.pill, color = colors.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Your listening type (A4): tertiaryContainer, the type at 28sp, then where the listening falls and how much you finish. */
@Composable
internal fun ListeningTypeCard(type: ListeningType) {
    val colors = MaterialTheme.colorScheme
    InsightsCard(color = colors.tertiaryContainer) {
        Text(stringResource(R.string.history_ins_type_title), style = InsightsType.label, color = colors.onTertiaryContainer)
        Text(typeName(type), style = InsightsType.typeName, color = colors.onTertiaryContainer)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            type.topPart?.let { part -> InsightsFact(partIcon(part), partFact(part, type.topPartShare), colors.onTertiaryContainer) }
            type.finishRate?.let { rate -> InsightsFact(Icons.Rounded.CheckCircleOutline, finishFact(rate), colors.onTertiaryContainer) }
        }
    }
}

internal fun partIcon(part: DayPart): ImageVector = when (part) {
    DayPart.MORNING -> Icons.Rounded.WbSunny
    DayPart.AFTERNOON -> Icons.Rounded.LightMode
    DayPart.EVENING -> Icons.Rounded.DarkMode
    DayPart.NIGHT -> Icons.Rounded.Bedtime
}

@Composable
private fun InsightsFact(icon: ImageVector, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = InsightsType.body14, color = color)
    }
}

/** Your top shows (A4): the first large with its rank badge, then the next four with bars against the first. */
@Composable
internal fun TopShowsCard(shows: List<TopShow>) {
    val first = shows.firstOrNull() ?: return
    val colors = MaterialTheme.colorScheme
    InsightsCard {
        InsightsCardHeader(stringResource(R.string.history_ins_top_shows), stringResource(R.string.history_ins_by_time))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OptimizedImage(url = first.imageUrl, proxyWidth = 256, contentDescription = null, modifier = Modifier.size(112.dp).clip(RoundedCornerShape(24.dp)))
                Box(
                    Modifier.offset(x = (-6).dp, y = (-6).dp).size(32.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) { Text("1", style = InsightsType.rankBadge, color = colors.onPrimary) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(first.name, style = InsightsType.title20, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.history_ins_top_share, durationText(first.listenedMs), InsightsWording.percent(first.share)),
                    style = InsightsType.body14,
                    color = colors.onSurfaceVariant,
                )
                if (first.finishedCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.history_ins_episodes_finished, first.finishedCount, first.finishedCount),
                        style = InsightsType.body14,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (shows.size > 1) {
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shows.drop(1).forEachIndexed { index, show -> RankedShow(index + 2, show, show.listenedMs.toFloat() / first.listenedMs) }
            }
        }
    }
}

@Composable
private fun RankedShow(rank: Int, show: TopShow, fraction: Float) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank.toString(), style = TimelineType.label, color = colors.onSurfaceVariant, modifier = Modifier.width(16.dp))
        OptimizedImage(url = show.imageUrl, proxyWidth = 128, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(show.name, style = TimelineType.label, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(durationText(show.listenedMs), style = TimelineType.meta, color = colors.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(colors.primary))
            }
        }
    }
}

/** When you listen (A4): a 24-hour clock with a wedge per part of the day, sized by its share, and the legend. */
@Composable
internal fun WhenYouListenCard(parts: List<PartShare>) {
    if (parts.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val rankColor = { index: Int ->
        when (index) {
            0 -> colors.primary
            1 -> colors.primaryContainer
            else -> colors.outlineVariant
        }
    }
    InsightsCard {
        Text(stringResource(R.string.history_ins_when), style = TimelineType.header, color = colors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ListeningClock(parts, rankColor)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(mostlyText(parts), style = InsightsType.title18, color = colors.onSurface)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    parts.forEachIndexed { index, share ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape).background(rankColor(index))
                                    .then(if (index == 1) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier),
                            )
                            Text(
                                stringResource(R.string.history_ins_part_share, partName(share.part), InsightsWording.percent(share.share)),
                                style = InsightsType.body13,
                                color = if (index < 2) colors.onSurface else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The board's 180dp clock: a surfaceContainerHigh disc with a dashed outlineVariant ring; each part
 * of the day is a quarter wedge (night top-right, morning bottom-right, afternoon bottom-left,
 * evening top-left) of radius 70dp × √(share / largest share), and a surface dot at the centre.
 */
@Composable
private fun ListeningClock(parts: List<PartShare>, rankColor: (Int) -> Color) {
    val colors = MaterialTheme.colorScheme
    val description =
        parts.map { stringResource(R.string.history_ins_part_share, partName(it.part), InsightsWording.percent(it.share)) }.joinToString()
    val labels = listOf(R.string.history_clock_12am, R.string.history_clock_6am, R.string.history_clock_12pm, R.string.history_clock_6pm).map { stringResource(it) }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp)
    Box(modifier = Modifier.size(180.dp).clearAndSetSemantics { contentDescription = description }) {
        val largest = parts.maxOf { it.share }.coerceAtLeast(0.0001f)
        Canvas(modifier = Modifier.size(180.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val unit = size.width / 180f
            drawCircle(colors.surfaceContainerHigh, radius = 74 * unit, center = center)
            drawCircle(colors.outlineVariant, radius = 74 * unit, center = center, style = Stroke(width = 1 * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * unit, 4 * unit))))
            parts.forEachIndexed { index, share ->
                val radius = 70 * unit * sqrt(share.share / largest)
                val start =
                    when (share.part) {
                        DayPart.NIGHT -> -90f
                        DayPart.MORNING -> 0f
                        DayPart.AFTERNOON -> 90f
                        DayPart.EVENING -> 180f
                    }
                drawArc(rankColor(index), start, 90f, useCenter = true, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2))
            }
            drawCircle(colors.surface, radius = 5 * unit, center = center)
        }
        Text(labels[0], style = labelStyle, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.TopCenter))
        Text(labels[1], style = labelStyle, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterEnd))
        Text(labels[2], style = labelStyle, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.BottomCenter))
        Text(labels[3], style = labelStyle, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterStart))
    }
}

/** "Wednesdays are your big day" (A4): seven 120dp bars, the biggest in primary, the rest surfaceContainerHighest. */
@Composable
internal fun BigDayCard(weekdays: Weekdays, period: ListeningPeriod) {
    val colors = MaterialTheme.colorScheme
    val biggest = weekdays.biggestMs.coerceAtLeast(1L)
    val description = stringResource(R.string.history_ins_weekday_chart, weekdays.biggest.getDisplayName(TextStyle.FULL_STANDALONE, cx.aswin.boxlore.feature.history.timeline.currentLocale()))
    InsightsCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.history_ins_big_day, weekdayPlural(weekdays.biggest)), style = TimelineType.header, color = colors.onSurface)
            Text(bigDayMeta(weekdays, period), style = TimelineType.meta, color = colors.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp).clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            DayOfWeek.entries.forEachIndexed { index, day ->
                val isBiggest = day == weekdays.biggest
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val fraction = weekdays.listenedMs[index].toFloat() / biggest
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((BAR_MAX_DP * fraction).coerceAtLeast(BAR_MIN_DP).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isBiggest) colors.primary else colors.surfaceContainerHighest),
                    )
                    Text(
                        day.getDisplayName(TextStyle.NARROW_STANDALONE, cx.aswin.boxlore.feature.history.timeline.currentLocale()),
                        style = TimelineType.meta.copy(fontWeight = if (isBiggest) GoogleSansWeight.semiBold else GoogleSansWeight.regular),
                        color = if (isBiggest) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The tallest bar leaves room for its 16dp label and 6dp gap in the 120dp chart. */
private const val BAR_MAX_DP = 96f
private const val BAR_MIN_DP = 4f
