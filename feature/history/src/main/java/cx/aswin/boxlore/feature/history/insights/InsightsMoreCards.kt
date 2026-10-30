package cx.aswin.boxlore.feature.history.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.TimelineTime
import cx.aswin.boxlore.feature.history.timeline.TimelineType
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * "Your October" (A5): the month as a mosaic, each day the cover of the show played most, empty
 * days plain, today outlined; tapping a day opens it in the Timeline.
 */
@Composable
internal fun MonthMosaicCard(mosaic: Mosaic, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale()
    InsightsCard {
        InsightsCardHeader(
            stringResource(R.string.history_ins_your_month, mosaic.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)),
            stringResource(R.string.history_ins_days_of, mosaic.listenedDays, mosaic.daysSoFar),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                        style = TimelineType.meta.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    )
                }
            }
            mosaic.cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (index in 0 until 7) {
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            week.getOrNull(index)?.let { MosaicCell(it, it.date == today, onOpenDay) }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.history_ins_mosaic_hint), style = TimelineType.meta, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun MosaicCell(day: MosaicDay, isToday: Boolean, onOpenDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val number = day.date.dayOfMonth.toString()
    if (!day.listened) {
        Box(
            modifier = Modifier.fillMaxSize().clip(shape).background(colors.surfaceContainerLow).then(if (isToday) Modifier.todayOutline(colors.onSurface) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = TimelineType.meta, color = colors.onSurfaceVariant.copy(alpha = 0.55f))
        }
        return
    }
    val label = TimelineTime.shortDate(day.date, currentLocale())
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .then(if (isToday) Modifier.todayOutline(colors.onSurface) else Modifier)
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .expressiveClickable(shape = shape, feedback = ExpressivePressFeedback.Control) { onOpenDay(day.date) }
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        OptimizedImage(url = day.imageUrl, proxyWidth = 96, contentDescription = null, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(2.dp)
                .heightIn(min = 16.dp)
                .widthIn(min = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isToday) colors.primary else colors.surface.copy(alpha = 0.82f))
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = InsightsType.badge10, color = if (isToday) colors.onPrimary else colors.onSurface)
        }
    }
}

/** Today's 2dp onSurface outline, 2dp outside the cell (drawn past its bounds, so the cover keeps its size). */
private fun Modifier.todayOutline(color: Color): Modifier = drawBehind {
    val gap = 3.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(-gap, -gap),
        size = Size(size.width + gap * 2, size.height + gap * 2),
        cornerRadius = CornerRadius(13.dp.toPx()),
        style = Stroke(width = 2.dp.toPx()),
    )
}

/** Records (A5): biggest day, longest run, longest sitting and most in one day, two by two. */
@Composable
internal fun RecordsSection(records: Records) {
    val locale = currentLocale()
    val cards =
        buildList {
            records.biggestDay?.let { (date, ms) ->
                add(RecordCard(Icons.Rounded.EmojiEvents, stringResource(R.string.history_rec_biggest_day), durationText(ms), TimelineTime.dayTitleText(date, false, locale)))
            }
            records.longestRun?.let { (days, start, end) ->
                add(
                    RecordCard(
                        Icons.Rounded.LocalFireDepartment,
                        stringResource(R.string.history_rec_longest_run),
                        pluralStringResource(R.plurals.history_rec_days, days, days),
                        stringResource(R.string.history_rec_range, TimelineTime.shortDate(start, locale), TimelineTime.shortDate(end, locale)),
                    ),
                )
            }
            records.longestSitting?.let { (ms, title) ->
                add(RecordCard(Icons.Rounded.Headphones, stringResource(R.string.history_rec_longest_sitting), durationText(ms), title))
            }
            records.mostInOneDay?.let { most ->
                add(
                    RecordCard(
                        Icons.Rounded.Layers,
                        stringResource(R.string.history_rec_most_in_day),
                        pluralStringResource(cx.aswin.boxlore.feature.history.R.plurals.history_episode_count, most.episodes, most.episodes),
                        stringResource(R.string.history_rec_of_show, most.showName, TimelineTime.shortDate(most.date, locale)),
                    ),
                )
            }
        }
    if (cards.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.history_ins_records),
            style = TimelineType.header,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        cards.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { RecordTile(it, Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

private data class RecordCard(val icon: ImageVector, val label: String, val value: String, val detail: String,)

@Composable
private fun RecordTile(card: RecordCard, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(16.dp).semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.padding(bottom = 4.dp).size(36.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(card.icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Text(card.label, style = TimelineType.meta, color = colors.onSurfaceVariant)
        Text(card.value, style = InsightsType.value24, color = colors.onSurface)
        Text(card.detail, style = TimelineType.meta.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** What you listen to (A5): genres by listening time as one 16dp bar, then a two-column legend. */
@Composable
internal fun GenresCard(genres: List<GenreShare>) {
    if (genres.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary, colors.primaryContainer, colors.outlineVariant)
    val other = stringResource(R.string.history_ins_genre_other)
    val names = genres.map { it.genre ?: other }
    val description = genres.indices.joinToString { "${names[it]} ${InsightsWording.percent(genres[it].share)}%" }
    InsightsCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.history_ins_genres), style = TimelineType.header, color = colors.onSurface)
            Text(stringResource(R.string.history_ins_genres_hint), style = TimelineType.meta, color = colors.onSurfaceVariant)
        }
        Row(modifier = Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            genres.forEachIndexed { index, genre ->
                val start = if (index == 0) 8.dp else 3.dp
                val end = if (index == genres.lastIndex) 8.dp else 3.dp
                Box(
                    Modifier
                        .weight(genre.share.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
                        .background(palette[index % palette.size]),
                )
            }
        }
        genres.indices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { index ->
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape).background(palette[index % palette.size])
                                .then(if (index == 3) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier),
                        )
                        Text("${names[index]} ${InsightsWording.percent(genres[index].share)}%", style = InsightsType.body13, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** "178 finished" (A5): finished all time and a bar to the next milestone. */
@Composable
internal fun MilestoneCard(milestone: FinishedMilestone) {
    val colors = MaterialTheme.colorScheme
    InsightsCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.history_ins_finished_count, milestone.finished), style = InsightsType.value24, color = colors.onSurface)
            Text(stringResource(R.string.history_ins_all_time), style = TimelineType.meta, color = colors.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.surfaceVariant)) {
            Box(
                Modifier
                    .fillMaxWidth((milestone.finished.toFloat() / milestone.next).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(colors.primary),
            )
        }
        Text(
            stringResource(R.string.history_ins_to_milestone, milestone.next - milestone.finished, milestone.next),
            style = InsightsType.body14,
            color = colors.onSurfaceVariant,
        )
    }
}

/** Episodes in progress (A5): how many, how many went stale, and a way to review them. */
@Composable
internal fun InProgressCard(inProgress: InProgress, onReview: () -> Unit) {
    if (inProgress.count == 0) return
    val colors = MaterialTheme.colorScheme
    InsightsCard(color = colors.surfaceContainerHigh) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                pluralStringResource(R.plurals.history_ins_in_progress_title, inProgress.count, inProgress.count),
                style = TimelineType.header,
                color = colors.onSurface,
            )
            Text(
                if (inProgress.stale > 0) {
                    pluralStringResource(R.plurals.history_ins_in_progress_stale, inProgress.stale, inProgress.stale)
                } else {
                    stringResource(R.string.history_ins_in_progress_fresh)
                },
                style = InsightsType.body14,
                color = colors.onSurfaceVariant,
            )
        }
        Button(
            onClick = onReview,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
            modifier = Modifier.height(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp),
        ) {
            Text(stringResource(R.string.history_ins_review_in_progress), style = InsightsType.label)
        }
    }
}
