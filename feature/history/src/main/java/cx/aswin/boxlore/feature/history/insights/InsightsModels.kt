package cx.aswin.boxlore.feature.history.insights

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The four parts of the day boxlore times listening in. */
enum class DayPart {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

/** A listening type's time word: the part of the day most listening falls in, or all day. */
enum class TypeTime {
    MORNING,
    DAYTIME,
    EVENING,
    LATE_NIGHT,
    ALL_DAY,
}

/** A listening type's habit: finishing most episodes, sampling, or in between. */
enum class TypeHabit {
    FINISHER,
    LISTENER,
    SAMPLER,
}

/** "Evening finisher": [time] and [habit], with the facts behind them. */
data class ListeningType(val time: TypeTime, val habit: TypeHabit, val topPart: DayPart?, val topPartShare: Float, val finishRate: Float?,)

/** Time listened in the period: total, a day's average, against the period before (null for All), and the best run. */
data class TimeListened(val totalMs: Long, val perDayMs: Long, val previousMs: Long?, val bestRunDays: Int,)

/** One of the top shows by time: its share of the period's listening and episodes finished in it. */
data class TopShow(val podcastId: String, val name: String, val imageUrl: String?, val listenedMs: Long, val share: Float, val finishedCount: Int,)

/** A part of the day's share of listening. */
data class PartShare(val part: DayPart, val share: Float)

/** Listening per weekday (Monday first) and the biggest. */
data class Weekdays(val listenedMs: List<Long>, val biggest: DayOfWeek, val biggestMs: Long,)

/** A day of the month mosaic: the cover of the show listened to most that day, or none. */
data class MosaicDay(val date: LocalDate, val imageUrl: String?, val listened: Boolean,)

/** The current month's mosaic: Monday-first cells (null before the 1st), days listened and days so far. */
data class Mosaic(val month: YearMonth, val cells: List<MosaicDay?>, val listenedDays: Int, val daysSoFar: Int,)

/** The period's records; any may be missing. */
data class Records(
    val biggestDay: Pair<LocalDate, Long>?,
    val longestRun: Triple<Int, LocalDate, LocalDate>?,
    val longestSitting: Pair<Long, String>?,
    val mostInOneDay: MostInOneDay?,
)

/** The most episodes of one show listened to in a day. */
data class MostInOneDay(val episodes: Int, val showName: String, val date: LocalDate,)

/** A genre's share of the period's listening, by the genres of the shows played. */
data class GenreShare(val genre: String?, val share: Float)

/** Finished all time and the next milestone. */
data class FinishedMilestone(val finished: Int, val next: Int)

/** Episodes in progress and how many weren't played in over a month. */
data class InProgress(val count: Int, val stale: Int)

/** A show played most, by episodes (the little-data state). */
data class ShowByEpisodes(val podcastId: String, val name: String, val imageUrl: String?, val episodes: Int, val finished: Int, val isBrief: Boolean,)

/** The little-data state (board A6): what history alone can say while timing fills in. */
data class EarlyInsights(
    val finished: Int,
    val inProgress: Int,
    val playedDaysThisMonth: Int,
    val timingSince: LocalDate?,
    val showsByEpisodes: List<ShowByEpisodes>,
    val timedDays: Int,
    val timedDaysNeeded: Int,
)

/** Everything the Insights tab shows for a period ([InsightsLogic.build]). */
data class InsightsContent(
    val early: EarlyInsights,
    val hasEnoughTiming: Boolean,
    val time: TimeListened,
    val type: ListeningType?,
    val topShows: List<TopShow>,
    val parts: List<PartShare>,
    val weekdays: Weekdays?,
    val mosaic: Mosaic,
    val records: Records,
    val genres: List<GenreShare>,
    val milestone: FinishedMilestone,
    val inProgress: InProgress,
)
