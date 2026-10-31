package cx.aswin.boxlore.feature.history.insights

import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.timeline.HistoryCalendarLogic
import cx.aswin.boxlore.feature.history.timeline.HistoryTimelineLogic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The Insights cards' parts ([InsightsLogic.build]): top shows, weekdays, the mosaic, records, genres, milestones, in progress and the little-data state. */
internal object InsightsParts {
    private const val TOP_SHOWS = 5
    private const val TOP_GENRES = 5
    private const val SHOWS_BY_EPISODES = 3
    private const val STALE_DAYS = 30L
    private val MILESTONES = listOf(10, 25, 50, 100, 200, 300, 500, 1_000)
    private const val MILESTONE_STEP_AFTER = 500

    internal data class ShowMeta(val name: String, val imageUrl: String?)

    fun showInfo(history: List<ListeningHistoryItem>, played: List<ListeningPlayedShow>): Map<String, ShowMeta> {
        val fromHistory = history.groupBy { it.podcastId }.mapValues { (_, rows) -> rows.maxBy { it.lastPlayedAt }.let { ShowMeta(it.podcastName, it.podcastImageUrl ?: it.episodeImageUrl) } }
        val fromPlayed = played.associate { it.podcastId to ShowMeta(it.podcastName, it.imageUrl) }
        return fromPlayed + fromHistory.mapValues { (id, meta) -> meta.copy(imageUrl = meta.imageUrl ?: fromPlayed[id]?.imageUrl) }
    }

    fun topShows(input: InsightsLogic.Input, range: ClosedRange<LocalDate>, inPeriod: List<ListeningDayEpisode>, info: Map<String, ShowMeta>): List<TopShow> {
        val total = inPeriod.sumOf { it.consumedMs }.takeIf { it > 0L } ?: return emptyList()
        return inPeriod.groupBy { it.podcastId }
            .map { (podcastId, rows) -> podcastId to rows.sumOf { it.consumedMs } }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .take(TOP_SHOWS)
            .map { (podcastId, ms) ->
                val meta = info[podcastId]
                TopShow(podcastId, meta?.name.orEmpty(), meta?.imageUrl, ms, ms.toFloat() / total, finishedIn(input, podcastId, range))
            }
    }

    private fun finishedIn(input: InsightsLogic.Input, podcastId: String, range: ClosedRange<LocalDate>): Int = input.history.count { row ->
        row.podcastId == podcastId && InsightsLogic.finished(row) && InsightsLogic.date(finishTime(row), input.zoneId) in range
    }

    /** When an episode was finished: its finish date, else its last play. */
    private fun finishTime(row: ListeningHistoryItem): Long = row.finishedAt?.takeIf { it > 0L } ?: row.lastPlayedAt

    fun weekdays(inPeriod: List<ListeningDayEpisode>): Weekdays? {
        val perDay = LongArray(7)
        inPeriod.forEach { perDay[LocalDate.ofEpochDay(it.localDay).dayOfWeek.value - 1] += it.consumedMs }
        val best = perDay.indices.maxBy { perDay[it] }
        if (perDay[best] <= 0L) return null
        return Weekdays(perDay.toList(), DayOfWeek.of(best + 1), perDay[best])
    }

    fun mosaic(input: InsightsLogic.Input, info: Map<String, ShowMeta>): Mosaic {
        val month = YearMonth.from(input.today)
        val byDay = input.listening.filter { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) == month }.groupBy { LocalDate.ofEpochDay(it.localDay) }
        val cells =
            HistoryCalendarLogic.cells(month).map { date ->
                date?.let {
                    val top = byDay[it]?.groupBy { row -> row.podcastId }?.maxByOrNull { (_, rows) -> rows.sumOf { row -> row.consumedMs } }?.key
                    MosaicDay(it, top?.let { id -> info[id]?.imageUrl }, top != null && byDay[it].orEmpty().sumOf { row -> row.consumedMs } > 0L)
                }
            }
        return Mosaic(month, cells, cells.count { it?.listened == true }, input.today.dayOfMonth)
    }

    fun records(input: InsightsLogic.Input, inPeriod: List<ListeningDayEpisode>, info: Map<String, ShowMeta>): Records {
        val perDay = inPeriod.groupBy { LocalDate.ofEpochDay(it.localDay) }.mapValues { (_, rows) -> rows.sumOf { it.consumedMs } }.filterValues { it > 0L }
        val biggest = perDay.maxByOrNull { it.value }?.toPair()
        val sitting = inPeriod.filter { it.longestSessionMs > 0L }.maxByOrNull { it.longestSessionMs }
        val titles = input.history.associate { it.episodeId to it.episodeTitle }
        val most =
            inPeriod.groupBy { LocalDate.ofEpochDay(it.localDay) to it.podcastId }
                .map { (key, rows) -> Triple(key.first, key.second, rows.map { it.episodeId }.distinct().size) }
                .filter { it.third > 1 }
                .maxWithOrNull(compareBy<Triple<LocalDate, String, Int>> { it.third }.thenBy { it.first })
        return Records(
            biggestDay = biggest,
            longestRun = InsightsLogic.longestRun(perDay.keys)?.takeIf { it.first > 1 },
            longestSitting = sitting?.let { it.longestSessionMs to (titles[it.episodeId] ?: info[it.podcastId]?.name.orEmpty()) },
            mostInOneDay = most?.let { MostInOneDay(it.third, info[it.second]?.name.orEmpty(), it.first) },
        )
    }

    /** By listening time in the period, the genres of the shows played: the top four and the rest together, or all five. */
    fun genres(inPeriod: List<ListeningDayEpisode>, played: List<ListeningPlayedShow>): List<GenreShare> {
        val genreOf = played.associate { it.podcastId to it.genre }
        val byGenre = inPeriod.groupBy { genreOf[it.podcastId] }.mapValues { (_, rows) -> rows.sumOf { it.consumedMs } }.filter { it.key != null && it.value > 0L }
        val total = byGenre.values.sum().takeIf { it > 0L } ?: return emptyList()
        val sorted = byGenre.entries.sortedByDescending { it.value }
        val shown = if (sorted.size <= TOP_GENRES) sorted else sorted.take(TOP_GENRES - 1)
        val rest = sorted.drop(shown.size).sumOf { it.value }
        return shown.map { GenreShare(it.key, it.value.toFloat() / total) } + if (rest > 0L) listOf(GenreShare(null, rest.toFloat() / total)) else emptyList()
    }

    fun milestone(finished: Int): FinishedMilestone {
        val next = MILESTONES.firstOrNull { it > finished } ?: ((finished / MILESTONE_STEP_AFTER) + 1) * MILESTONE_STEP_AFTER
        return FinishedMilestone(finished, next)
    }

    fun inProgress(input: InsightsLogic.Input): InProgress {
        val going = input.history.filter { ListeningCompletionLogic.isInProgress(it.isCompleted, it.progressMs, it.durationMs) }
        val cutoff = input.today.minusDays(STALE_DAYS)
        return InProgress(going.size, going.count { InsightsLogic.date(it.lastPlayedAt, input.zoneId) < cutoff })
    }

    fun early(input: InsightsLogic.Input, timedDays: Collection<LocalDate>): EarlyInsights {
        val month = YearMonth.from(input.today)
        val shows =
            input.history.groupBy { it.podcastId }.map { (podcastId, rows) ->
                val latest = rows.maxBy { it.lastPlayedAt }
                ShowByEpisodes(
                    podcastId = podcastId,
                    name = latest.podcastName,
                    imageUrl = latest.podcastImageUrl ?: latest.episodeImageUrl,
                    episodes = rows.size,
                    finished = rows.count { InsightsLogic.finished(it) },
                    isBrief = rows.all { HistoryTimelineLogic.isBrief(it) },
                )
            }.sortedWith(compareByDescending<ShowByEpisodes> { it.episodes }.thenByDescending { it.finished })
        val timingSince = timedDays.minOrNull()
        val firstPlay = input.history.minOfOrNull { InsightsLogic.date(it.firstPlayedAt?.takeIf { at -> at > 0L } ?: it.lastPlayedAt, input.zoneId) }
        return EarlyInsights(
            finished = input.history.count { InsightsLogic.finished(it) },
            inProgress = input.history.count { ListeningCompletionLogic.isInProgress(it.isCompleted, it.progressMs, it.durationMs) },
            playedDaysThisMonth = input.history.map { InsightsLogic.date(it.lastPlayedAt, input.zoneId) }.filter { YearMonth.from(it) == month }.distinct().size,
            timingSince = timingSince,
            showsByEpisodes = shows.take(SHOWS_BY_EPISODES),
            timedDays = timedDays.size,
            timedDaysNeeded = (InsightsLogic.TIMED_DAYS_FOR_INSIGHTS - timedDays.size).coerceAtLeast(0),
            historyBeforeTiming = firstPlay != null && (timingSince == null || firstPlay < timingSince),
        )
    }
}
