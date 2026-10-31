package cx.aswin.boxlore.feature.history.insights

import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The Insights tab's stories for a period (boards A4–A6), from the whole history, every phone's
 * timed listening per day and episode, and the played shows' genres. Pure, for JVM tests; the
 * cards' parts are in [InsightsParts].
 */
object InsightsLogic {
    /** Timed days needed before time, patterns and records replace the little-data state. */
    const val TIMED_DAYS_FOR_INSIGHTS = 5

    /** A part of the day leads the listening type from this share of it. */
    private const val TYPE_PART_SHARE = 0.35f
    private const val FINISHER_RATE = 0.6f
    private const val SAMPLER_RATE = 0.35f

    data class Input(
        val history: List<ListeningHistoryItem>,
        val listening: List<ListeningDayEpisode>,
        val playedShows: List<ListeningPlayedShow>,
        val period: ListeningPeriod,
        val today: LocalDate,
        val zoneId: ZoneId,
    )

    fun build(input: Input): InsightsContent {
        val timedDays = input.listening.filter { it.consumedMs > 0L }.map { LocalDate.ofEpochDay(it.localDay) }.toSortedSet()
        val range = periodRange(input.period, input.today, timedDays.firstOrNull())
        val inPeriod = input.listening.filter { LocalDate.ofEpochDay(it.localDay) in range }
        val showInfo = InsightsParts.showInfo(input.history, input.playedShows)
        return InsightsContent(
            early = InsightsParts.early(input, timedDays),
            hasEnoughTiming = timedDays.size >= TIMED_DAYS_FOR_INSIGHTS,
            time = timeListened(input, range, inPeriod),
            type = listeningTypeFor(input, range, inPeriod),
            topShows = InsightsParts.topShows(input, range, inPeriod, showInfo),
            parts = partShares(inPeriod),
            weekdays = InsightsParts.weekdays(inPeriod),
            mosaic = InsightsParts.mosaic(input, showInfo),
            records = InsightsParts.records(input, inPeriod, showInfo),
            genres = InsightsParts.genres(inPeriod, input.playedShows),
            outputs = InsightsDetails.outputs(inPeriod),
            sources = InsightsDetails.sources(inPeriod),
            milestone = InsightsParts.milestone(input.history.count { finished(it) }),
            inProgress = InsightsParts.inProgress(input),
        )
    }

    /**
     * The period's days, today last: 7, 30 or 180 days, or from the first timed day for All. A first
     * timed day after today (a clock change, or a phone in a timezone ahead) starts All today.
     */
    fun periodRange(period: ListeningPeriod, today: LocalDate, firstTimedDay: LocalDate?): ClosedRange<LocalDate> = when (period) {
        ListeningPeriod.DAYS_7 -> today.minusDays(6)..today
        ListeningPeriod.DAYS_30 -> today.minusDays(29)..today
        ListeningPeriod.DAYS_180 -> today.minusDays(179)..today
        ListeningPeriod.ALL -> minOf(firstTimedDay ?: today, today)..today
    }

    private fun days(range: ClosedRange<LocalDate>): Long = (ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1).coerceAtLeast(1L)

    private fun timeListened(input: Input, range: ClosedRange<LocalDate>, inPeriod: List<ListeningDayEpisode>): TimeListened {
        val total = inPeriod.sumOf { it.consumedMs }
        val previous =
            if (input.period == ListeningPeriod.ALL) {
                null
            } else {
                val length = days(range)
                val before = range.start.minusDays(length)..range.start.minusDays(1)
                input.listening.filter { LocalDate.ofEpochDay(it.localDay) in before }.sumOf { it.consumedMs }
            }
        val listenedDays = inPeriod.filter { it.consumedMs > 0L }.map { LocalDate.ofEpochDay(it.localDay) }.toSortedSet()
        return TimeListened(total, total / days(range), previous, longestRun(listenedDays)?.first ?: 0)
    }

    /** The longest run of consecutive [days]: its length, first and last day. */
    fun longestRun(days: Collection<LocalDate>): Triple<Int, LocalDate, LocalDate>? {
        val sorted = days.toSortedSet().toList()
        if (sorted.isEmpty()) return null
        var best = Triple(1, sorted.first(), sorted.first())
        var start = sorted.first()
        for (index in 1 until sorted.size) {
            if (sorted[index] != sorted[index - 1].plusDays(1)) start = sorted[index]
            val length = ChronoUnit.DAYS.between(start, sorted[index]).toInt() + 1
            if (length > best.first) best = Triple(length, start, sorted[index])
        }
        return best
    }

    fun partShares(rows: List<ListeningDayEpisode>): List<PartShare> {
        val parts =
            mapOf(
                DayPart.MORNING to rows.sumOf { it.morningMs },
                DayPart.AFTERNOON to rows.sumOf { it.afternoonMs },
                DayPart.EVENING to rows.sumOf { it.eveningMs },
                DayPart.NIGHT to rows.sumOf { it.nightMs },
            )
        val total = parts.values.sum()
        if (total <= 0L) return emptyList()
        return parts.map { (part, ms) -> PartShare(part, ms.toFloat() / total) }.sortedByDescending { it.share }
    }

    /** The listening type of [inPeriod]'s listening, with the finish rate of episodes started in [range]. */
    internal fun listeningTypeFor(input: Input, range: ClosedRange<LocalDate>, inPeriod: List<ListeningDayEpisode>): ListeningType? {
        val parts = partShares(inPeriod)
        val top = parts.firstOrNull() ?: return null
        val rate = finishRate(input.history, range, input.zoneId)
        val time =
            if (top.share < TYPE_PART_SHARE) {
                TypeTime.ALL_DAY
            } else {
                when (top.part) {
                    DayPart.MORNING -> TypeTime.MORNING
                    DayPart.AFTERNOON -> TypeTime.DAYTIME
                    DayPart.EVENING -> TypeTime.EVENING
                    DayPart.NIGHT -> TypeTime.LATE_NIGHT
                }
            }
        val habit =
            when {
                rate == null -> TypeHabit.LISTENER
                rate >= FINISHER_RATE -> TypeHabit.FINISHER
                rate < SAMPLER_RATE -> TypeHabit.SAMPLER
                else -> TypeHabit.LISTENER
            }
        return ListeningType(time, habit, top.part, top.share, rate)
    }

    /**
     * Of the episodes first played in [range], the share finished; before listening dates were
     * recorded, of the episodes played in it, the finished ones among finished and in progress.
     */
    fun finishRate(history: List<ListeningHistoryItem>, range: ClosedRange<LocalDate>, zoneId: ZoneId): Float? {
        val started = history.filter { (it.firstPlayedAt ?: 0L) > 0L && date(it.firstPlayedAt!!, zoneId) in range }
        if (started.isNotEmpty()) return started.count { it.finishedAt != null }.toFloat() / started.size
        val played = history.filter { date(it.lastPlayedAt, zoneId) in range }
        val done = played.count { finished(it) }
        val going = played.count { ListeningCompletionLogic.isInProgress(it.isCompleted, it.progressMs, it.durationMs) }
        return if (done + going == 0) null else done.toFloat() / (done + going)
    }

    internal fun finished(row: ListeningHistoryItem): Boolean = ListeningCompletionLogic.isCompleted(row.isCompleted, row.progressMs, row.durationMs)

    internal fun date(epochMs: Long, zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
}
