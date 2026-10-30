package cx.aswin.boxlore.feature.history.story

import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.insights.InsightsLogic
import cx.aswin.boxlore.feature.history.insights.InsightsParts
import cx.aswin.boxlore.feature.history.insights.ListeningType
import cx.aswin.boxlore.feature.history.insights.MostInOneDay
import cx.aswin.boxlore.feature.history.insights.PartShare
import cx.aswin.boxlore.feature.history.insights.TypeTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Echo's outfit for a story card: one per genre and part of the day, plus the story's own. */
enum class EchoOutfit(val asset: String) {
    HELLO("story-hello"),
    HOURGLASS("story-hourglass"),
    TROPHY("story-trophy"),
    CELEBRATE("story-celebrate"),
    MORNING("story-morning"),
    DAYTIME("story-daytime"),
    NIGHT("story-night"),
    TRUE_CRIME("story-detective"),
    TECH("story-tech"),
    NEWS("story-news"),
    HISTORY("story-history"),
    FICTION("story-fiction"),
    COMEDY("story-comedy"),
    SCIENCE("story-science"),
    BUSINESS("story-business"),
    HEALTH("story-health"),
}

/** The month's top show and the episode of it to pick back up. */
data class StoryTopShow(
    val podcastId: String,
    val name: String,
    val imageUrl: String?,
    val listenedMs: Long,
    val share: Float,
    val finished: Int,
    val resume: ListeningHistoryItem?,
    val resumeRemainingMs: Long,
)

/** One month's story (boards A7–A12). */
data class MonthStory(
    val month: YearMonth,
    val totalMs: Long,
    val listenedDays: Int,
    val shows: Int,
    /** The weekday of the month's biggest day ("one very busy Wednesday"). */
    val busiestWeekday: DayOfWeek,
    /** Listening per day of the month, the 1st first; null for days still to come. */
    val perDay: List<Long?>,
    val biggestDay: Pair<LocalDate, Long>,
    val perDayAverageMs: Long,
    /** The month before's listening; null when listening wasn't timed yet then. */
    val previousMonthMs: Long?,
    val topShow: StoryTopShow?,
    /** Echo's outfit beside the top show: its genre's, else the month's top genre's. */
    val showOutfit: EchoOutfit,
    val type: ListeningType?,
    /** The month's listening by part of the day, biggest first (the listening clock). */
    val parts: List<PartShare>,
    /** The longest run of listening days, when longer than one day. */
    val bestRun: Triple<Int, LocalDate, LocalDate>?,
    /** Whether [bestRun] is the longest run so far, up to the month's end. */
    val bestRunIsRecord: Boolean,
    val mostInOneDay: MostInOneDay?,
    /** The month's top five shows' artwork, by time, top show first. */
    val covers: List<String?>,
)

/** The story's cards, in order. */
enum class StoryCard { HELLO, TIME, TOP_SHOW, TYPE, RECORDS, SHARE }

/** A month with a story: its name for the entry card and its time listened. */
data class StoryMonth(val month: YearMonth, val totalMs: Long, val outfit: EchoOutfit,)

/**
 * The monthly story: from the 1st of a month, the month before gets one when it has timed
 * listening (StoryLogic.latest); earlier months stay further down (StoryLogic.months).
 */
object StoryLogic {
    private const val COVERS = 5

    /** Every month before [today]'s with timed listening, newest first. */
    fun months(listening: List<ListeningDayEpisode>, played: List<ListeningPlayedShow>, today: LocalDate): List<StoryMonth> {
        val current = YearMonth.from(today)
        return listening.filter { it.consumedMs > 0L }
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) }
            .filterKeys { it < current }
            .toSortedMap(reverseOrder())
            .map { (month, rows) -> StoryMonth(month, rows.sumOf { it.consumedMs }, genreOutfit(topGenre(rows, played))) }
    }

    fun build(month: YearMonth, history: List<ListeningHistoryItem>, listening: List<ListeningDayEpisode>, played: List<ListeningPlayedShow>, today: LocalDate, zoneId: ZoneId,): MonthStory? {
        val rows = listening.filter { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) == month && it.consumedMs > 0L }
        if (rows.isEmpty()) return null
        val byDay = rows.groupBy { LocalDate.ofEpochDay(it.localDay) }.mapValues { (_, day) -> day.sumOf { it.consumedMs } }
        val total = rows.sumOf { it.consumedMs }
        val lastDay = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
        val biggest = byDay.maxWith(compareBy<Map.Entry<LocalDate, Long>> { it.value }.thenBy { it.key }).toPair()
        val previous = month.minusMonths(1)
        val input = InsightsLogic.Input(history, listening, played, ListeningPeriod.ALL, month.atEndOfMonth(), zoneId)
        val range = month.atDay(1)..month.atEndOfMonth()
        val info = InsightsParts.showInfo(history, played)
        val showTimes = rows.groupBy { it.podcastId }.mapValues { (_, show) -> show.sumOf { it.consumedMs } }.entries.sortedByDescending { it.value }
        val timedBefore = listening.filter { it.consumedMs > 0L && LocalDate.ofEpochDay(it.localDay) < month.atDay(1) }
        val bestRun = InsightsLogic.longestRun(byDay.keys)?.takeIf { it.first > 1 }
        val runsSoFar = listening.filter { it.consumedMs > 0L && LocalDate.ofEpochDay(it.localDay) <= month.atEndOfMonth() }.map { LocalDate.ofEpochDay(it.localDay) }
        val topGenre = played.firstOrNull { it.podcastId == showTimes.firstOrNull()?.key }?.genre
        return MonthStory(
            month = month,
            totalMs = total,
            listenedDays = byDay.size,
            shows = showTimes.size,
            busiestWeekday = biggest.first.dayOfWeek,
            perDay = (1..month.lengthOfMonth()).map { day -> if (day > lastDay) null else byDay[month.atDay(day)] ?: 0L },
            biggestDay = biggest,
            perDayAverageMs = total / lastDay,
            previousMonthMs = if (timedBefore.isEmpty()) null else listening.filter { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) == previous }.sumOf { it.consumedMs },
            topShow = topShow(showTimes.firstOrNull(), total, history, range, info, zoneId),
            showOutfit = genreOutfit(topGenre ?: topGenre(rows, played)),
            type = InsightsLogic.listeningTypeFor(input, range, rows),
            parts = InsightsLogic.partShares(rows),
            bestRun = bestRun,
            bestRunIsRecord = bestRun != null && bestRun.first >= (InsightsLogic.longestRun(runsSoFar)?.first ?: 0),
            mostInOneDay = InsightsParts.records(input, rows, info).mostInOneDay,
            covers = showTimes.take(COVERS).map { info[it.key]?.imageUrl },
        )
    }

    private fun topShow(
        top: Map.Entry<String, Long>?,
        total: Long,
        history: List<ListeningHistoryItem>,
        range: ClosedRange<LocalDate>,
        info: Map<String, InsightsParts.ShowMeta>,
        zoneId: ZoneId,
    ): StoryTopShow? {
        top ?: return null
        val rows = history.filter { it.podcastId == top.key }
        val finished = rows.count { ListeningCompletionLogic.isCompleted(it.isCompleted, it.progressMs, it.durationMs) && InsightsLogic.date(it.finishedAt?.takeIf { at -> at > 0L } ?: it.lastPlayedAt, zoneId) in range }
        val resume = rows.filter { ListeningCompletionLogic.isInProgress(it.isCompleted, it.progressMs, it.durationMs) }.maxByOrNull { it.lastPlayedAt }
        return StoryTopShow(
            podcastId = top.key,
            name = info[top.key]?.name.orEmpty(),
            imageUrl = info[top.key]?.imageUrl,
            listenedMs = top.value,
            share = top.value.toFloat() / total,
            finished = finished,
            resume = resume,
            resumeRemainingMs = resume?.let { (it.durationMs - it.progressMs).coerceAtLeast(0L) } ?: 0L,
        )
    }

    private fun topGenre(rows: List<ListeningDayEpisode>, played: List<ListeningPlayedShow>): String? {
        val genreOf = played.associate { it.podcastId to it.genre }
        return rows.groupBy { genreOf[it.podcastId] }.filterKeys { it != null }.maxByOrNull { (_, day) -> day.sumOf { it.consumedMs } }?.key
    }

    /** The month's cards: all six, less the top show when no show is known and the listening type when parts of the day weren't timed. */
    fun cards(story: MonthStory): List<StoryCard> = StoryCard.entries.filter { card ->
        when (card) {
            StoryCard.TOP_SHOW -> story.topShow != null
            StoryCard.TYPE -> story.type != null && story.parts.isNotEmpty()
            else -> true
        }
    }

    /** Echo's still for a story month's tile: its outfit at rest. */
    fun tileSequence(outfit: EchoOutfit): String = outfit.asset + "-still"

    /** Podcast Index genre names to Echo's genre outfits; anything else wears the hello outfit. */
    fun genreOutfit(genre: String?): EchoOutfit {
        val name = genre?.lowercase().orEmpty()
        return when {
            "crime" in name -> EchoOutfit.TRUE_CRIME
            "tech" in name -> EchoOutfit.TECH
            "news" in name || "politic" in name -> EchoOutfit.NEWS
            "history" in name -> EchoOutfit.HISTORY
            "fiction" in name || "drama" in name || "book" in name -> EchoOutfit.FICTION
            "comedy" in name -> EchoOutfit.COMEDY
            "science" in name || "education" in name -> EchoOutfit.SCIENCE
            "business" in name || "invest" in name || "career" in name -> EchoOutfit.BUSINESS
            "health" in name || "fitness" in name || "wellness" in name -> EchoOutfit.HEALTH
            else -> EchoOutfit.HELLO
        }
    }

    /** The listening type card's outfit: morning, daytime, or night for evenings, late nights and all day. */
    fun timeOutfit(time: TypeTime?): EchoOutfit = when (time) {
        TypeTime.MORNING -> EchoOutfit.MORNING
        TypeTime.DAYTIME -> EchoOutfit.DAYTIME
        else -> EchoOutfit.NIGHT
    }
}
