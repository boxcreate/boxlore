package cx.aswin.boxlore.feature.history.timeline

import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The Timeline tab's status chips. */
enum class TimelineFilter {
    ALL,
    IN_PROGRESS,
    FINISHED,
    LIKED,
}

/** How the Timeline tab groups each month: by day played, or by show. */
enum class TimelineGrouping {
    DAYS,
    SHOWS,
}

/** One episode row: [item] with its status worked out once. */
data class TimelineEpisode(val item: ListeningHistoryItem, val isFinished: Boolean, val remainingMs: Long, val progress: Float,) {
    val isUnfinished: Boolean get() = !isFinished
}

/** A day of the Timeline: its rows, newest first, and the time listened that day (0 when not timed). */
data class TimelineDay(val date: LocalDate, val listenedMs: Long, val episodes: List<TimelineEpisode>,)

/**
 * A show within a month: its episodes played that month, finished first (the segment bar), how
 * much of them was timed that month, and the most recently played unfinished one to resume.
 */
data class TimelineShow(
    val podcastId: String,
    val podcastName: String,
    val imageUrl: String?,
    val episodeCount: Int,
    val finishedCount: Int,
    val listenedMs: Long,
    val segments: List<Boolean>,
    val resume: TimelineEpisode?,
    val isBrief: Boolean,
    val lastPlayedAt: Long,
) {
    val allFinished: Boolean get() = episodeCount > 0 && finishedCount == episodeCount
}

/** A month of the Timeline: [days] or [shows] by the grouping, with its episode count and time listened. */
data class TimelineMonth(
    val month: YearMonth,
    val episodeCount: Int,
    val listenedMs: Long,
    val days: List<TimelineDay>,
    val shows: List<TimelineShow>,
)

/** What the Timeline tab shows, built by [HistoryTimelineLogic.build]. */
data class TimelineContent(
    val jumpBackIn: List<TimelineEpisode>,
    val inProgressCount: Int,
    val months: List<TimelineMonth>,
    val listenedByDay: Map<LocalDate, Long>,
    val playedDays: Set<LocalDate>,
    val firstListenDate: LocalDate?,
    val isHistoryEmpty: Boolean,
)

/**
 * Pure Timeline building: filters, search, grouping by day or show per month, the Jump back in row
 * and the calendar's listening per day. Timed listening comes from every phone
 * ([ListeningDayEpisode]); days before timing began show their episodes without a time.
 */
object HistoryTimelineLogic {
    /** How many unfinished episodes the Jump back in row shows. */
    const val JUMP_BACK_IN_LIMIT = 20

    /** Episode IDs of The boxlore brief start with this. */
    private const val BRIEFING_ID_PREFIX = "briefing_"

    data class Input(
        val history: List<ListeningHistoryItem>,
        val listening: List<ListeningDayEpisode>,
        val filter: TimelineFilter = TimelineFilter.ALL,
        val query: String = "",
        val zoneId: ZoneId = ZoneId.systemDefault(),
    )

    fun build(input: Input): TimelineContent {
        val all = input.history.map { episodeOf(it) }
        val unfinished = all.filter { it.isUnfinished && it.item.progressMs > 0L }
        val listenedByDay = input.listening.groupBy { LocalDate.ofEpochDay(it.localDay) }.mapValues { (_, rows) -> rows.sumOf { it.consumedMs } }
        val shown = all.filter { matches(it, input.filter) && matchesQuery(it.item, input.query) }
        val playedDays = all.mapTo(HashSet()) { dateOf(it.item.lastPlayedAt, input.zoneId) } + listenedByDay.filterValues { it > 0L }.keys
        return TimelineContent(
            jumpBackIn = unfinished.sortedByDescending { it.item.lastPlayedAt }.take(JUMP_BACK_IN_LIMIT),
            inProgressCount = unfinished.size,
            months = months(shown, input.listening, listenedByDay, input.zoneId),
            listenedByDay = listenedByDay,
            playedDays = playedDays,
            firstListenDate = playedDays.minOrNull(),
            isHistoryEmpty = all.isEmpty(),
        )
    }

    fun episodeOf(item: ListeningHistoryItem): TimelineEpisode {
        val finished = ListeningCompletionLogic.isCompleted(item.isCompleted, item.progressMs, item.durationMs)
        val remaining = if (finished || item.durationMs <= 0L) 0L else (item.durationMs - item.progressMs).coerceAtLeast(0L)
        val progress = if (item.durationMs > 0L) (item.progressMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
        return TimelineEpisode(item, finished, remaining, progress)
    }

    fun matches(episode: TimelineEpisode, filter: TimelineFilter): Boolean = when (filter) {
        TimelineFilter.ALL -> true
        TimelineFilter.IN_PROGRESS -> episode.isUnfinished && episode.item.progressMs > 0L
        TimelineFilter.FINISHED -> episode.isFinished
        TimelineFilter.LIKED -> episode.item.isLiked
    }

    /** Every word of [query] appears in the episode's or the show's title, ignoring case. */
    fun matchesQuery(item: ListeningHistoryItem, query: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val text = item.episodeTitle + " " + item.podcastName
        return words.all { text.contains(it, ignoreCase = true) }
    }

    fun dateOf(epochMs: Long, zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()

    fun isBrief(item: ListeningHistoryItem): Boolean = item.episodeId.startsWith(BRIEFING_ID_PREFIX) || item.podcastId.startsWith(BRIEFING_ID_PREFIX)

    private fun months(
        episodes: List<TimelineEpisode>,
        listening: List<ListeningDayEpisode>,
        listenedByDay: Map<LocalDate, Long>,
        zoneId: ZoneId,
    ): List<TimelineMonth> {
        val byMonth = episodes.groupBy { YearMonth.from(dateOf(it.item.lastPlayedAt, zoneId)) }
        val listeningByMonth = listening.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) }
        return byMonth.keys.sortedDescending().map { month ->
            val rows = byMonth.getValue(month).sortedWith(compareByDescending<TimelineEpisode> { it.item.lastPlayedAt }.thenBy { it.item.episodeId })
            val monthListening = listeningByMonth[month].orEmpty()
            TimelineMonth(
                month = month,
                episodeCount = rows.size,
                listenedMs = monthListening.sumOf { it.consumedMs },
                days =
                rows.groupBy { dateOf(it.item.lastPlayedAt, zoneId) }.map { (date, dayRows) ->
                    TimelineDay(date, listenedByDay[date] ?: 0L, dayRows)
                },
                shows = shows(rows, monthListening),
            )
        }
    }

    private fun shows(rows: List<TimelineEpisode>, monthListening: List<ListeningDayEpisode>): List<TimelineShow> {
        val listenedByShow = monthListening.groupBy { it.podcastId }.mapValues { (_, days) -> days.sumOf { it.consumedMs } }
        return rows.groupBy { it.item.podcastId }.map { (podcastId, showRows) ->
            val latest = showRows.first()
            val finished = showRows.count { it.isFinished }
            TimelineShow(
                podcastId = podcastId,
                podcastName = latest.item.podcastName,
                imageUrl = latest.item.podcastImageUrl ?: latest.item.episodeImageUrl,
                episodeCount = showRows.size,
                finishedCount = finished,
                listenedMs = listenedByShow[podcastId] ?: 0L,
                segments = List(finished) { true } + List(showRows.size - finished) { false },
                resume = showRows.firstOrNull { it.isUnfinished },
                isBrief = showRows.all { isBrief(it.item) },
                lastPlayedAt = latest.item.lastPlayedAt,
            )
        }.sortedByDescending { it.lastPlayedAt }
    }
}
