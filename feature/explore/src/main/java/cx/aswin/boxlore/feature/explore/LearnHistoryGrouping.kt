package cx.aswin.boxlore.feature.explore

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** When a group of Lore history cards was seen, newest first. */
internal sealed interface LearnHistoryPeriod {
    data object Today : LearnHistoryPeriod
    data object Yesterday : LearnHistoryPeriod

    /** Earlier this week, by the language's first day of the week. */
    data object ThisWeek : LearnHistoryPeriod

    /** A calendar month before this week; [anyMs] is one of its cards' times, for naming the month. */
    data class Month(val year: Int, val month: Int, val anyMs: Long) : LearnHistoryPeriod

    /** Cards saved before history kept a time. */
    data object Earlier : LearnHistoryPeriod
}

internal data class LearnHistoryGroup(val period: LearnHistoryPeriod, val entries: List<LearnHistoryEntry>)

/**
 * Groups history (already newest first) under Today, Yesterday, This week, then each month, with
 * cards that have no time last under Earlier. Order inside a group is kept. The week starts on the
 * [locale]'s first day, so Monday in Germany and Sunday in the US.
 */
internal fun groupLearnHistory(entries: List<LearnHistoryEntry>, nowMs: Long, zone: ZoneId, locale: Locale): List<LearnHistoryGroup> {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
    val grouped = LinkedHashMap<Any, MutableList<LearnHistoryEntry>>()
    val periods = LinkedHashMap<Any, LearnHistoryPeriod>()
    entries.forEach { entry ->
        val period = periodOf(entry.dismissedAtMs, today, weekStart, zone)
        val key: Any = if (period is LearnHistoryPeriod.Month) period.year to period.month else period
        periods.getOrPut(key) { period }
        grouped.getOrPut(key) { mutableListOf() } += entry
    }
    val order = listOf(LearnHistoryPeriod.Today, LearnHistoryPeriod.Yesterday, LearnHistoryPeriod.ThisWeek)
    val dated = periods.entries.filter { it.value is LearnHistoryPeriod.Month }
        .sortedByDescending { (it.value as LearnHistoryPeriod.Month).let { month -> month.year * MONTHS + month.month } }
    val keys = order.filter { it in periods } + dated.map { it.key } + listOf<Any>(LearnHistoryPeriod.Earlier).filter { it in periods }
    return keys.map { key -> LearnHistoryGroup(requireNotNull(periods[key]), requireNotNull(grouped[key])) }
}

private fun periodOf(atMs: Long, today: LocalDate, weekStart: LocalDate, zone: ZoneId): LearnHistoryPeriod {
    if (atMs <= 0L) return LearnHistoryPeriod.Earlier
    val day = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
    return when {
        !day.isBefore(today) -> LearnHistoryPeriod.Today
        day == today.minusDays(1) -> LearnHistoryPeriod.Yesterday
        !day.isBefore(weekStart) -> LearnHistoryPeriod.ThisWeek
        else -> LearnHistoryPeriod.Month(day.year, day.monthValue, atMs)
    }
}

private const val MONTHS = 12
