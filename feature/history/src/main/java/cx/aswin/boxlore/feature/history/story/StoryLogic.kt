package cx.aswin.boxlore.feature.history.story

import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.insights.InsightsDetails
import cx.aswin.boxlore.feature.history.insights.InsightsLogic
import cx.aswin.boxlore.feature.history.insights.InsightsParts
import cx.aswin.boxlore.feature.history.insights.ListeningSources
import cx.aswin.boxlore.feature.history.insights.ListeningType
import cx.aswin.boxlore.feature.history.insights.MostInOneDay
import cx.aswin.boxlore.feature.history.insights.OutputShare
import cx.aswin.boxlore.feature.history.insights.PartShare
import cx.aswin.boxlore.feature.history.insights.TypeHabit
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

/** A show behind the top one (the top show card's runners-up). */
data class StoryShow(val podcastId: String, val name: String, val imageUrl: String?, val listenedMs: Long,)

/** The hello card's counts: episodes played in the month and episodes finished in it. */
data class StoryCounts(val episodes: Int, val finished: Int)

/** Of the episodes started in the month (or, before listening dates, played in it), how many were finished. */
data class StoryFinishes(val finished: Int, val started: Int)

/** How the month was listened to (the How you listened card): where, and new or catching up with what started it. */
data class StoryHow(val outputs: List<OutputShare>, val sources: ListeningSources)

/** One month's story (the refreshed boards S1–S7). */
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
    /** Whether [bestRun] is a new record: longer than any run before the month, and the longest up to its end (a tie isn't). */
    val bestRunIsRecord: Boolean,
    val mostInOneDay: MostInOneDay?,
    /** The month's top five shows' artwork, by time, top show first. */
    val covers: List<String?>,
    val counts: StoryCounts = StoryCounts(0, 0),
    /** The two shows after the top one, by time. */
    val runnersUp: List<StoryShow> = emptyList(),
    /** Null until enough of the month's listening has its details ([InsightsDetails]). */
    val how: StoryHow? = null,
    val finishes: StoryFinishes? = null,
    /** The month's longest single sitting and its show's name. */
    val longestSitting: Pair<Long, String>? = null,
    /** The weekday with the most listening in the month, and how much. */
    val bigWeekday: Pair<DayOfWeek, Long>? = null,
    /** The longest run of listening days up to the month's end, this month's included. */
    val bestRunEver: Int = 0,
    val echo: StoryEchoFacts = StoryEchoFacts(),
) {
    /** No listening was timed before this month: the first story. */
    val isFirst: Boolean get() = previousMonthMs == null
}

/** The story's cards, in order. */
enum class StoryCard { HELLO, TIME, TOP_SHOW, HOW, TYPE, RECORDS, SHARE }

/**
 * A month with a story: its name, time listened and outfit for the entry card and Earlier stories,
 * with the entry card's teaser: the top show's name, the top three covers and how many cards it has.
 */
data class StoryMonth(
    val month: YearMonth,
    val totalMs: Long,
    val outfit: EchoOutfit,
    val topShowName: String? = null,
    val covers: List<String?> = emptyList(),
    val cards: Int = StoryCard.entries.size,
)

/**
 * The monthly story: from the 1st of a month, the month before gets one when it has timed
 * listening (StoryLogic.latest); earlier months stay further down (StoryLogic.months).
 */
object StoryLogic {
    private const val COVERS = 5
    private const val ENTRY_COVERS = 3
    private const val RUNNERS_UP = 2

    /** Every month before [today]'s with timed listening, newest first. */
    fun months(listening: List<ListeningDayEpisode>, played: List<ListeningPlayedShow>, today: LocalDate): List<StoryMonth> {
        val current = YearMonth.from(today)
        val shows = played.associateBy { it.podcastId }
        return listening.filter { it.consumedMs > 0L }
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) }
            .filterKeys { it < current }
            .toSortedMap(reverseOrder())
            .map { (month, rows) ->
                val top = rows.groupBy { it.podcastId }.mapValues { (_, show) -> show.sumOf { it.consumedMs } }.entries.sortedByDescending { it.value }
                val cards = StoryCard.entries.count { card ->
                    when (card) {
                        // The same test the card is built by: details alone (a known release date, say) without an output don't make one.
                        StoryCard.HOW -> how(rows) != null
                        StoryCard.TYPE -> InsightsLogic.partShares(rows).isNotEmpty() && partsMs(rows) >= TYPE_MIN_MS
                        else -> true
                    }
                }
                StoryMonth(
                    month = month,
                    totalMs = rows.sumOf { it.consumedMs },
                    outfit = genreOutfit(topGenre(rows, played)),
                    topShowName = top.firstOrNull()?.let { shows[it.key]?.podcastName },
                    covers = top.take(ENTRY_COVERS).map { shows[it.key]?.imageUrl },
                    cards = cards,
                )
            }
    }

    fun build(month: YearMonth, history: List<ListeningHistoryItem>, listening: List<ListeningDayEpisode>, played: List<ListeningPlayedShow>, today: LocalDate, zoneId: ZoneId,): MonthStory? {
        val rows = listening.filter { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) == month && it.consumedMs > 0L }
        if (rows.isEmpty()) return null
        val byDay = rows.groupBy { LocalDate.ofEpochDay(it.localDay) }.mapValues { (_, day) -> day.sumOf { it.consumedMs } }
        val total = rows.sumOf { it.consumedMs }
        val lastDay = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
        val biggest = byDay.maxWith(compareBy<Map.Entry<LocalDate, Long>> { it.value }.thenBy { it.key }).toPair()
        val input = InsightsLogic.Input(history, listening, played, ListeningPeriod.ALL, month.atEndOfMonth(), zoneId)
        val range = month.atDay(1)..month.atEndOfMonth()
        val info = InsightsParts.showInfo(history, played)
        val showTimes = rows.groupBy { it.podcastId }.mapValues { (_, show) -> show.sumOf { it.consumedMs } }.entries.sortedByDescending { it.value }
        val bestRun = InsightsLogic.longestRun(byDay.keys)?.takeIf { it.first > 1 }
        val runsSoFar = listening.filter { it.consumedMs > 0L && LocalDate.ofEpochDay(it.localDay) <= month.atEndOfMonth() }.map { LocalDate.ofEpochDay(it.localDay) }
        val topGenre = played.firstOrNull { it.podcastId == showTimes.firstOrNull()?.key }?.genre
        val bestRunEver = InsightsLogic.longestRun(runsSoFar)?.first ?: 0
        val echo = StoryEchoFactsLogic.of(month, rows, listening, played, showTimes)
        val finishes = finishes(history, range, zoneId)
        return MonthStory(
            month = month,
            totalMs = total,
            listenedDays = byDay.size,
            shows = showTimes.size,
            busiestWeekday = biggest.first.dayOfWeek,
            perDay = (1..month.lengthOfMonth()).map { day -> if (day > lastDay) null else byDay[month.atDay(day)] ?: 0L },
            biggestDay = biggest,
            perDayAverageMs = total / lastDay,
            previousMonthMs = previousMonthMs(listening, month),
            topShow = topShow(showTimes.firstOrNull(), total, history, range, info, zoneId),
            showOutfit = genreOutfit(topGenre ?: topGenre(rows, played)),
            type = typeOf(input, range, rows, finishes),
            parts = InsightsLogic.partShares(rows),
            bestRun = bestRun,
            bestRunIsRecord = bestRun != null && bestRun.first > echo.bestBefore && bestRun.first >= bestRunEver,
            mostInOneDay = InsightsParts.records(input, rows, info).mostInOneDay,
            covers = showTimes.take(COVERS).map { info[it.key]?.imageUrl },
            counts = StoryCounts(rows.map { it.episodeId }.distinct().size, history.count { finishedIn(it, range, zoneId) }),
            runnersUp = showTimes.drop(1).take(RUNNERS_UP).map { StoryShow(it.key, info[it.key]?.name.orEmpty(), info[it.key]?.imageUrl, it.value) },
            how = how(rows),
            finishes = finishes,
            longestSitting = longestSitting(rows, info),
            bigWeekday = InsightsParts.weekdays(rows)?.let { it.biggest to it.biggestMs },
            bestRunEver = bestRunEver,
            echo = echo,
        )
    }

    /** The month before's listening, or null when nothing was timed before [month]. */
    private fun previousMonthMs(listening: List<ListeningDayEpisode>, month: YearMonth): Long? {
        val start = month.atDay(1)
        if (listening.none { it.consumedMs > 0L && LocalDate.ofEpochDay(it.localDay) < start }) return null
        val previous = month.minusMonths(1)
        return listening.filter { YearMonth.from(LocalDate.ofEpochDay(it.localDay)) == previous }.sumOf { it.consumedMs }
    }

    private fun how(rows: List<ListeningDayEpisode>): StoryHow? {
        val outputs = InsightsDetails.outputs(rows)
        val sources = InsightsDetails.sources(rows)
        return if (outputs.isNotEmpty() && sources != null) StoryHow(outputs, sources) else null
    }

    private fun longestSitting(rows: List<ListeningDayEpisode>, info: Map<String, InsightsParts.ShowMeta>): Pair<Long, String>? =
        InsightsParts.longestSitting(rows)?.let { it.longestSessionMs to info[it.podcastId]?.name.orEmpty() }

    private fun finishedIn(row: ListeningHistoryItem, range: ClosedRange<LocalDate>, zoneId: ZoneId): Boolean =
        ListeningCompletionLogic.isCompleted(row.isCompleted, row.progressMs, row.durationMs) && InsightsLogic.date(row.finishedAt?.takeIf { it > 0L } ?: row.lastPlayedAt, zoneId) in range

    /** As [InsightsLogic.finishRate], as counts: the episodes first played in [range] and how many were finished, else those played in it. */
    internal fun finishes(history: List<ListeningHistoryItem>, range: ClosedRange<LocalDate>, zoneId: ZoneId): StoryFinishes? {
        val started = history.filter { (it.firstPlayedAt ?: 0L) > 0L && InsightsLogic.date(it.firstPlayedAt!!, zoneId) in range }
        if (started.isNotEmpty()) return StoryFinishes(started.count { it.finishedAt != null }, started.size)
        val played = history.filter { InsightsLogic.date(it.lastPlayedAt, zoneId) in range }
        val done = played.count { InsightsLogic.finished(it) }
        val going = played.count { ListeningCompletionLogic.isInProgress(it.isCompleted, it.progressMs, it.durationMs) }
        return if (done + going == 0) null else StoryFinishes(done, done + going)
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
        val finished = rows.count { finishedIn(it, range, zoneId) }
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

    /**
     * The month's cards: all seven, less the top show when no show is known, How you listened until
     * enough listening has its details, and the listening type until half an hour is timed by part of the day.
     */
    fun cards(story: MonthStory): List<StoryCard> = StoryCard.entries.filter { card ->
        when (card) {
            StoryCard.TOP_SHOW -> story.topShow != null
            StoryCard.HOW -> story.how != null
            StoryCard.TYPE -> story.type != null && story.parts.isNotEmpty() && story.echo.partsMs >= TYPE_MIN_MS
            else -> true
        }
    }

    /** Echo's still for a story month's tile: its outfit at rest. */
    fun tileSequence(outfit: EchoOutfit): String = outfit.asset + "-still"

    /** Podcast Index genre names to Echo's genre outfits; anything else wears the hello outfit. */
    fun genreOutfit(genre: String?): EchoOutfit {
        val name = genre?.lowercase().orEmpty()
        return GENRE_OUTFITS.firstOrNull { (words, _) -> words.any { it in name } }?.second ?: EchoOutfit.HELLO
    }

    /** Words in a genre's name, first match wins. */
    private val GENRE_OUTFITS = listOf(
        listOf("crime") to EchoOutfit.TRUE_CRIME,
        listOf("tech") to EchoOutfit.TECH,
        listOf("news", "politic") to EchoOutfit.NEWS,
        listOf("history") to EchoOutfit.HISTORY,
        listOf("fiction", "drama", "book") to EchoOutfit.FICTION,
        listOf("comedy") to EchoOutfit.COMEDY,
        listOf("science", "education") to EchoOutfit.SCIENCE,
        listOf("business", "invest", "career") to EchoOutfit.BUSINESS,
        listOf("health", "fitness", "wellness") to EchoOutfit.HEALTH,
    )

    /** The listening type card's outfit: morning, daytime, or night for evenings, late nights and all day. */
    fun timeOutfit(time: TypeTime?): EchoOutfit = when (time) {
        TypeTime.MORNING -> EchoOutfit.MORNING
        TypeTime.DAYTIME -> EchoOutfit.DAYTIME
        else -> EchoOutfit.NIGHT
    }
}

/** The listening type card needs this much listening timed by part of the day. */
private const val TYPE_MIN_MS = 30 * 60_000L

/** Finishing or sampling is a habit only across this many episodes; fewer, and the type is just a listener. */
private const val TYPE_MIN_EPISODES = 3

/** The month's listening type; finishing or sampling only across [TYPE_MIN_EPISODES] episodes or more. */
private fun typeOf(input: InsightsLogic.Input, range: ClosedRange<LocalDate>, rows: List<ListeningDayEpisode>, finishes: StoryFinishes?): ListeningType? {
    val type = InsightsLogic.listeningTypeFor(input, range, rows) ?: return null
    return if ((finishes?.started ?: 0) < TYPE_MIN_EPISODES) type.copy(habit = TypeHabit.LISTENER) else type
}

private fun partsMs(rows: List<ListeningDayEpisode>) = rows.sumOf { it.morningMs + it.afternoonMs + it.eveningMs + it.nightMs }
