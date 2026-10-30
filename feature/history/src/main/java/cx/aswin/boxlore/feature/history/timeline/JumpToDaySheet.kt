package cx.aswin.boxlore.feature.history.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.history.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle

/**
 * The Jump to a day calendar: a month of days, Monday first, each shaded by how long you
 * listened ([level]); days without listening are plain and can't be picked.
 */
object HistoryCalendarLogic {
    /** Under this is the lightest shade; from [LONG_DAY_MS] the darkest. */
    const val MEDIUM_DAY_MS = 35L * 60_000L
    const val LONG_DAY_MS = 60L * 60_000L

    /** 0: no listening; 1, 2, 3: lighter to darker. A day played before timing began is 1. */
    fun level(listenedMs: Long, played: Boolean): Int = when {
        listenedMs >= LONG_DAY_MS -> 3
        listenedMs >= MEDIUM_DAY_MS -> 2
        listenedMs > 0L || played -> 1
        else -> 0
    }

    /** The month's cells: nulls before the 1st so it falls under its weekday (Monday first), then every day. */
    fun cells(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        return List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    }

    /** The earliest month the sheet can show: the month of the first listen, or [current] without one. */
    fun earliestMonth(firstListen: LocalDate?, current: YearMonth): YearMonth = firstListen?.let { YearMonth.from(it) }?.coerceAtMost(current) ?: current
}

/** Test tag of the sheet's content (captures render it on its own; the sheet has its own window). */
const val JUMP_TO_DAY_CONTENT_TAG = "history_jump_to_day"

/** What the sheet shows: listening per day, days played, the first listen and today. */
data class JumpToDayState(
    val listenedByDay: Map<LocalDate, Long>,
    val playedDays: Set<LocalDate>,
    val firstListenDate: LocalDate?,
    val today: LocalDate,
)

/**
 * Jump to a day (board A2): a surfaceContainerHigh sheet with the month calendar, a Less–More
 * legend and quick jumps to a week ago, a month ago and the first listen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JumpToDaySheet(state: JumpToDayState, onJump: (LocalDate) -> Unit, onDismiss: () -> Unit,) {
    val colors = MaterialTheme.colorScheme
    BoxloreModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { JumpToDayHandle() },
    ) {
        JumpToDayContent(state, onJump, onDismiss)
    }
}

/** The sheet's 32×4 outlineVariant handle, 14dp from the top. */
@Composable
fun JumpToDayHandle() {
    Box(
        Modifier
            .padding(top = 14.dp)
            .size(width = 32.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** The sheet's content: title, month calendar, legend and quick jumps (also rendered on its own by captures). */
@Composable
fun JumpToDayContent(state: JumpToDayState, onJump: (LocalDate) -> Unit, onDismiss: () -> Unit,) {
    val colors = MaterialTheme.colorScheme
    val current = YearMonth.from(state.today)
    val earliest = HistoryCalendarLogic.earliestMonth(state.firstListenDate, current)
    var shownMonth by rememberSaveable { mutableStateOf(current.toString()) }
    val month = YearMonth.parse(shownMonth)
    Column(
        modifier = Modifier.fillMaxWidth().testTag(JUMP_TO_DAY_CONTENT_TAG).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.history_jump_title), style = TimelineType.month, color = colors.onSurface)
            Text(
                stringResource(R.string.history_jump_body),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
                color = colors.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { shownMonth = month.minusMonths(1).toString() }, enabled = month > earliest) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.history_jump_previous_month))
            }
            Text(TimelineTime.monthYearTitle(month, currentLocale()), style = TimelineType.header, color = colors.onSurface)
            IconButton(onClick = { shownMonth = month.plusMonths(1).toString() }, enabled = month < current) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.history_jump_next_month))
            }
        }
        CalendarGrid(month, state) { date ->
            onJump(date)
            onDismiss()
        }
        CalendarLegend()
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickJumpChip(stringResource(R.string.history_jump_week_ago)) {
                onJump(state.today.minusWeeks(1))
                onDismiss()
            }
            QuickJumpChip(stringResource(R.string.history_jump_month_ago)) {
                onJump(state.today.minusMonths(1))
                onDismiss()
            }
            state.firstListenDate?.let { first ->
                QuickJumpChip(stringResource(R.string.history_jump_first_listen)) {
                    onJump(first)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(month: YearMonth, state: JumpToDayState, onPick: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = currentLocale()
    val weekdays = DayOfWeek.entries.map { it.getDisplayName(JavaTextStyle.NARROW_STANDALONE, locale) }
    val cells = HistoryCalendarLogic.cells(month)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weekdays.forEach { day ->
                Text(
                    day,
                    style = TimelineType.meta.copy(letterSpacing = 0.sp),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).height(24.dp).padding(top = 4.dp).clearAndSetSemantics { },
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (index in 0 until 7) {
                    val date = week.getOrNull(index)
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (date != null) CalendarDay(date, state, onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, state: JumpToDayState, onPick: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val listened = state.listenedByDay[date] ?: 0L
    val level = HistoryCalendarLogic.level(listened, date in state.playedDays)
    val isToday = date == state.today
    val text = date.dayOfMonth.toString()
    val style = TimelineType.label
    if (level == 0) {
        Box(
            modifier = Modifier.size(44.dp).then(if (isToday) Modifier.border(2.dp, colors.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = style, color = colors.onSurfaceVariant.copy(alpha = 0.45f))
        }
        return
    }
    val (background, foreground) =
        when (level) {
            1 -> colors.primary.copy(alpha = 0.2f) to colors.onSurface
            2 -> colors.primaryContainer to colors.onPrimaryContainer
            else -> colors.primary to colors.onPrimary
        }
    val listenedText = if (listened > 0L) durationText(listened) else stringResource(R.string.history_jump_played)
    val label =
        if (isToday) {
            stringResource(R.string.history_jump_today, listenedText)
        } else {
            stringResource(R.string.history_jump_day, TimelineTime.shortDate(date, currentLocale()), listenedText)
        }
    Box(
        modifier =
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (isToday) Modifier.border(2.dp, colors.onSurface, CircleShape) else Modifier)
            .expressiveClickable(shape = CircleShape, feedback = ExpressivePressFeedback.Control) { onPick(date) }
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = foreground)
    }
}

@Composable
private fun CalendarLegend() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.history_jump_less), style = TimelineType.meta, color = colors.onSurfaceVariant)
        listOf(colors.primary.copy(alpha = 0.2f), colors.primaryContainer, colors.primary).forEach { color ->
            Box(Modifier.size(16.dp).clip(CircleShape).background(color))
        }
        Text(stringResource(R.string.history_jump_more), style = TimelineType.meta, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun QuickJumpChip(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier =
        Modifier
            .height(48.dp)
            .clip(shape)
            .background(colors.surfaceContainerHighest)
            .expressiveClickable(shape = shape, feedback = ExpressivePressFeedback.Control, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TimelineType.label.copy(fontWeight = GoogleSansWeight.semiBold), color = colors.onSurface)
    }
}
