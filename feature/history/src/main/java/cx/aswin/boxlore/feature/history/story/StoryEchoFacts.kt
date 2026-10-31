package cx.aswin.boxlore.feature.history.story

import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.insights.InsightsLogic
import java.time.LocalDate
import java.time.YearMonth

/**
 * What Echo's lines check beyond what the cards show ([StoryEchoLines]): the month before's days, run
 * and biggest day, the best run before the month, ties, genres, and how much of the month has details.
 */
data class StoryEchoFacts(
    /** Listening days in the month before; null with no listening timed before the month. */
    val previousDays: Int? = null,
    /** The month before's longest run of listening days; null with no listening timed before the month. */
    val previousRun: Int? = null,
    /** The month before's biggest day; null when it had no listening. */
    val previousBiggestDayMs: Long? = null,
    /** The longest run of listening days before the month. */
    val bestBefore: Int = 0,
    /** Two or more shows share the top time. */
    val topTied: Boolean = false,
    /** The top show's genre, as the catalog names it. */
    val topGenre: String? = null,
    /** The top three shows' genres, top first (null where unknown). */
    val topThreeGenres: List<String?> = emptyList(),
    /** The genre with the most listening, this month and the month before; null when unknown or tied. */
    val leadingGenre: String? = null,
    val previousLeadingGenre: String? = null,
    /** The share of the month's listening heard on a known output. */
    val detailsCoverage: Float = 0f,
    /** The month's listening timed by part of the day. */
    val partsMs: Long = 0L,
)

internal object StoryEchoFactsLogic {
    private const val TOP_THREE = 3

    /** [rows] is the month's timed listening, [showTimes] its shows by time, most first. */
    fun of(
        month: YearMonth,
        rows: List<ListeningDayEpisode>,
        listening: List<ListeningDayEpisode>,
        played: List<ListeningPlayedShow>,
        showTimes: List<Map.Entry<String, Long>>,
    ): StoryEchoFacts {
        val before = listening.filter { it.consumedMs > 0L && LocalDate.ofEpochDay(it.localDay) < month.atDay(1) }
        val previous = month.minusMonths(1)
        val previousRows = before.filter { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) == previous }
        val previousByDay = previousRows.groupBy { LocalDate.ofEpochDay(it.localDay) }.mapValues { (_, day) -> day.sumOf { it.consumedMs } }
        val genreOf = played.associate { show -> show.podcastId to show.genre?.takeIf { it.isNotBlank() } }
        val timedBefore = before.isNotEmpty()
        val total = rows.sumOf { it.consumedMs }
        return StoryEchoFacts(
            previousDays = previousByDay.size.takeIf { timedBefore },
            previousRun = (InsightsLogic.longestRun(previousByDay.keys)?.first ?: 0).takeIf { timedBefore },
            previousBiggestDayMs = previousByDay.values.maxOrNull(),
            bestBefore = InsightsLogic.longestRun(before.map { LocalDate.ofEpochDay(it.localDay) })?.first ?: 0,
            topTied = showTimes.size > 1 && showTimes[0].value == showTimes[1].value,
            topGenre = showTimes.firstOrNull()?.let { genreOf[it.key] },
            topThreeGenres = showTimes.take(TOP_THREE).map { genreOf[it.key] },
            leadingGenre = leadingGenre(rows, genreOf),
            previousLeadingGenre = leadingGenre(previousRows, genreOf),
            detailsCoverage = if (total > 0L) rows.sumOf { it.outputKnownMs }.toFloat() / total else 0f,
            partsMs = rows.sumOf { it.morningMs + it.afternoonMs + it.eveningMs + it.nightMs },
        )
    }

    /** The genre with the most of [rows]' time, or null when none is known or two tie. */
    private fun leadingGenre(rows: List<ListeningDayEpisode>, genreOf: Map<String, String?>): String? {
        val byGenre = rows.mapNotNull { row -> genreOf[row.podcastId]?.let { it to row.consumedMs } }.groupBy({ it.first }, { it.second }).mapValues { (_, ms) -> ms.sum() }
        val top = byGenre.maxByOrNull { it.value } ?: return null
        return top.key.takeIf { byGenre.count { it.value == top.value } == 1 }
    }
}
