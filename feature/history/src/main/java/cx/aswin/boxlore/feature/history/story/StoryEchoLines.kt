package cx.aswin.boxlore.feature.history.story

import androidx.annotation.ArrayRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.insights.InsightsWording
import cx.aswin.boxlore.feature.history.insights.ListeningOutput
import cx.aswin.boxlore.feature.history.insights.TypeTime
import cx.aswin.boxlore.feature.history.insights.outputName
import cx.aswin.boxlore.feature.history.insights.weekdayPlural
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

/**
 * Echo's line on each story card. Each card has situations (`res/values/story_echo_lines.xml`, twelve
 * lines each): a special one (the first month on record, a tie, a new record…) speaks whenever it
 * fits; otherwise the story's seed picks one of the situations that fit, and then one of its lines,
 * so the same month can sound different each time it's watched. Lines name facts with `{placeholders}`;
 * a line whose fact is missing gives way to the card's usual lines.
 */
internal object StoryEchoLines {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val QUIET_MS = 5 * HOUR_MS
    private const val BIG_MS = 25 * HOUR_MS
    private const val LARGE_CHANGE = 0.25
    private const val LARGE_CHANGE_MIN_MS = 15 * MINUTE_MS
    private const val BIG_DAY_MS = 3 * HOUR_MS
    private const val DOMINANT_SHARE = 0.6f
    private const val SPREAD_SHARE = 0.25f
    private const val WIDE_MIX_SHOWS = 5
    private const val OUTPUT_LEADS = 0.6f
    private const val NEW_LEADS = 0.6f
    private const val CATCHING_UP_BELOW = 0.4f
    private const val LORE_SHARE = 0.2f
    private const val SMART_QUEUE_SHARE = 0.3f
    private const val PARTIAL_DETAILS = 0.8f
    private const val THIN_PARTS_MS = 30 * MINUTE_MS
    private const val THIN_EPISODES = 3
    private const val FINISHER_RATE = 0.6f
    private const val SAMPLER_RATE = 0.35f
    private const val LONG_SITTING_MS = 2 * HOUR_MS
    private const val SHORT_SITTING_MS = 20 * MINUTE_MS
    private const val BINGE_EPISODES = 3

    /** Spreads one seed across a story's cards, so each card lands on a different line. */
    private const val CARD_STEP = 7

    /** Lines a situation has: the seed's next digits pick the situation, so line and situation vary apart. */
    private const val SITUATION_SPREAD = 12

    /** The longest a filled line may be: two or three lines of the bubble. */
    private const val LONGEST = 70

    /** The line array for [card] in [story]'s situation: a special one that fits, else one the [seed] picks. */
    @ArrayRes
    fun lines(card: StoryCard, story: MonthStory, seed: Int = 0): Int {
        val fitting = situations(card).filter { it.fits(story) }
        fitting.firstOrNull { it.special }?.let { return it.lines }
        return if (fitting.isEmpty()) usual(card) else fitting[(seed / SITUATION_SPREAD + card.ordinal).mod(fitting.size)].lines
    }

    /** The card's usual lines: they fit any month the card shows. */
    @ArrayRes
    fun usual(card: StoryCard): Int = when (card) {
        StoryCard.HELLO -> R.array.history_echo_hello_usual
        StoryCard.TIME -> R.array.history_echo_time_usual
        StoryCard.TOP_SHOW -> R.array.history_echo_top_usual
        StoryCard.HOW -> R.array.history_echo_how_usual
        StoryCard.TYPE -> R.array.history_echo_type_usual
        StoryCard.RECORDS -> R.array.history_echo_records_usual
        StoryCard.SHARE -> R.array.history_echo_wrap_usual
    }

    /** Which of [count] lines [card] says for [seed]. */
    fun index(count: Int, seed: Int, card: StoryCard): Int = (seed + card.ordinal * CARD_STEP).mod(count.coerceAtLeast(1))

    /**
     * The line to say: [lines] from [start] on, then the card's [usual] ones, the first whose facts are all
     * in [values] and that still fits once filled (a long show or genre name can push it over).
     */
    fun choose(lines: List<String>, usual: List<String>, start: Int, values: Map<String, String>): String {
        val filled = (lines.indices.map { lines[(start + it).mod(lines.size)] } + usual.indices.map { usual[(start + it).mod(usual.size)] }).map { fill(it, values) }.filter { '{' !in it }
        return filled.firstOrNull { it.length <= LONGEST } ?: filled.minByOrNull { it.length }.orEmpty()
    }

    /** [line] with each `{name}` replaced from [values]; a name without a value stays as it is. */
    fun fill(line: String, values: Map<String, String>): String = PLACEHOLDER.replace(line) { match -> values[match.groupValues[1]] ?: match.value }

    private val PLACEHOLDER = Regex("""\{(\w+)}""")

    /** A situation: its lines, whether it speaks over the others, and when it fits. */
    private class Situation(@ArrayRes val lines: Int, val special: Boolean, val fits: (MonthStory) -> Boolean)

    private fun special(@ArrayRes lines: Int, fits: (MonthStory) -> Boolean) = Situation(lines, special = true, fits)

    private fun regular(@ArrayRes lines: Int, fits: (MonthStory) -> Boolean) = Situation(lines, special = false, fits)

    private fun situations(card: StoryCard): List<Situation> = when (card) {
        StoryCard.HELLO -> HELLO
        StoryCard.TIME -> TIME
        StoryCard.TOP_SHOW -> TOP_SHOW
        StoryCard.HOW -> HOW
        StoryCard.TYPE -> TYPE
        StoryCard.RECORDS -> RECORDS
        StoryCard.SHARE -> WRAP
    }

    private val MonthStory.quiet get() = totalMs < QUIET_MS
    private val MonthStory.big get() = totalMs >= BIG_MS
    private val MonthStory.gap get() = previousMonthMs == 0L

    /** This month against the month before, when that had listening. */
    private val MonthStory.change: Long? get() = previousMonthMs?.takeIf { it > 0L }?.let { totalMs - it }
    private val MonthStory.largeChange: Boolean
        get() {
            val delta = change ?: return false
            return abs(delta) >= max(LARGE_CHANGE_MIN_MS, ((previousMonthMs ?: 0L) * LARGE_CHANGE).toLong())
        }
    private val MonthStory.up get() = (change ?: 0L) >= MINUTE_MS
    private val MonthStory.down get() = (change ?: 0L) <= -MINUTE_MS
    private val MonthStory.hadPrevious get() = (previousMonthMs ?: 0L) > 0L

    /** The month's longest run of listening days (one with listening but no run). */
    private val MonthStory.run: Int get() = bestRun?.first ?: if (listenedDays > 0) 1 else 0

    private val HELLO = listOf(
        special(R.array.history_echo_hello_one_episode) { it.counts.episodes == 1 },
        special(R.array.history_echo_hello_one_day) { it.listenedDays == 1 },
        special(R.array.history_echo_hello_first_quiet) { it.isFirst && it.quiet },
        special(R.array.history_echo_hello_first_big) { it.isFirst && it.big },
        special(R.array.history_echo_hello_first_regular) { it.isFirst },
        special(R.array.history_echo_hello_gap) { it.gap },
        regular(R.array.history_echo_hello_days_sparse) { it.listenedDays > 1 && it.listenedDays * 4 <= it.month.lengthOfMonth() },
        regular(R.array.history_echo_hello_days_frequent) { it.listenedDays * 4 >= it.month.lengthOfMonth() * 3 },
        regular(R.array.history_echo_hello_quiet_month) { it.quiet },
        regular(R.array.history_echo_hello_big_month) { it.big },
    )

    private val TIME = listOf(
        special(R.array.history_echo_time_first) { it.isFirst },
        special(R.array.history_echo_time_gap) { it.gap },
        regular(R.array.history_echo_time_up_large) { it.up && it.largeChange },
        regular(R.array.history_echo_time_up_small) { it.up && !it.largeChange },
        regular(R.array.history_echo_time_down_large) { it.down && it.largeChange },
        regular(R.array.history_echo_time_down_small) { it.down && !it.largeChange },
        regular(R.array.history_echo_time_same) { it.change?.let { delta -> abs(delta) < MINUTE_MS } == true },
        regular(R.array.history_echo_time_days_more) { it.hadPrevious && it.listenedDays > (it.echo.previousDays ?: Int.MAX_VALUE) },
        regular(R.array.history_echo_time_days_fewer) { it.hadPrevious && it.listenedDays < (it.echo.previousDays ?: 0) },
        regular(R.array.history_echo_time_big_day) { it.biggestDay.second >= BIG_DAY_MS },
    )

    private val GENRES = listOf(
        EchoOutfit.TRUE_CRIME to R.array.history_echo_top_genre_true_crime,
        EchoOutfit.TECH to R.array.history_echo_top_genre_tech,
        EchoOutfit.NEWS to R.array.history_echo_top_genre_news,
        EchoOutfit.HISTORY to R.array.history_echo_top_genre_history,
        EchoOutfit.FICTION to R.array.history_echo_top_genre_fiction,
        EchoOutfit.COMEDY to R.array.history_echo_top_genre_comedy,
        EchoOutfit.SCIENCE to R.array.history_echo_top_genre_science,
        EchoOutfit.BUSINESS to R.array.history_echo_top_genre_business,
        EchoOutfit.HEALTH to R.array.history_echo_top_genre_health,
    )

    private val TOP_SHOW = listOf(
        special(R.array.history_echo_top_tie) { it.echo.topTied },
        special(R.array.history_echo_top_one_show) { it.shows == 1 },
    ) + GENRES.map { (outfit, lines) -> regular(lines) { story -> story.echo.topGenre?.let(StoryLogic::genreOutfit) == outfit } } + listOf(
        regular(R.array.history_echo_top_dominant) { it.shows > 1 && (it.topShow?.share ?: 0f) >= DOMINANT_SHARE },
        regular(R.array.history_echo_top_wide_mix) { it.shows >= WIDE_MIX_SHOWS && (it.topShow?.share ?: 1f) < SPREAD_SHARE },
        regular(R.array.history_echo_top_three_one_genre) { it.topThree { genres -> genres.size == 1 } },
        regular(R.array.history_echo_top_three_mixed) { it.topThree { genres -> genres.size == 3 } },
        regular(R.array.history_echo_top_genre_changed) { it.hadPrevious && it.echo.previousLeadingGenre != null && it.echo.leadingGenre != null && it.echo.leadingGenre != it.echo.previousLeadingGenre },
        regular(R.array.history_echo_top_genre_same) { it.hadPrevious && it.echo.leadingGenre != null && it.echo.leadingGenre == it.echo.previousLeadingGenre },
        regular(R.array.history_echo_top_finishes) { (it.topShow?.finished ?: 0) >= 2 },
        regular(R.array.history_echo_top_resume) { it.topShow?.resume != null },
    )

    /** Whether the top three shows all have a genre and their distinct genres pass [test]. */
    private fun MonthStory.topThree(test: (Set<String>) -> Boolean): Boolean {
        val genres = echo.topThreeGenres
        return genres.size == 3 && genres.all { it != null } && test(genres.filterNotNull().toSet())
    }

    private val OUTPUTS = listOf(
        ListeningOutput.HEADPHONES to R.array.history_echo_how_output_headphones,
        ListeningOutput.BLUETOOTH to R.array.history_echo_how_output_bluetooth,
        ListeningOutput.CAR to R.array.history_echo_how_output_car,
        ListeningOutput.SPEAKER to R.array.history_echo_how_output_speaker,
        ListeningOutput.CAST to R.array.history_echo_how_output_cast,
    )

    private val HOW = OUTPUTS.map { (output, lines) ->
        regular(lines) { story -> story.how?.outputs?.firstOrNull()?.let { it.output == output && it.share >= OUTPUT_LEADS } == true }
    } + listOf(
        regular(R.array.history_echo_how_output_mix) { story -> story.how?.outputs?.let { it.size >= 2 && it.first().share < OUTPUT_LEADS } == true },
        regular(R.array.history_echo_how_new_lead) { (it.how?.sources?.newShare ?: 0f) >= NEW_LEADS },
        regular(R.array.history_echo_how_catching_up) { (it.how?.sources?.newShare ?: 1f) <= CATCHING_UP_BELOW },
        regular(R.array.history_echo_how_lore_high) { (it.how?.sources?.loreShare ?: 0f) >= LORE_SHARE },
        regular(R.array.history_echo_how_smart_queue_high) { (it.how?.sources?.smartQueueShare ?: 0f) >= SMART_QUEUE_SHARE },
        regular(R.array.history_echo_how_partial) { it.how != null && it.echo.detailsCoverage < PARTIAL_DETAILS },
    )

    private val TYPE = listOf(
        special(R.array.history_echo_type_thin) { it.echo.partsMs < THIN_PARTS_MS || (it.finishes?.started ?: THIN_EPISODES) < THIN_EPISODES },
        special(R.array.history_echo_type_tied) { it.parts.size >= 2 && it.parts[0].share > 0f && it.parts[0].share == it.parts[1].share },
        regular(R.array.history_echo_type_morning) { it.type?.time == TypeTime.MORNING },
        regular(R.array.history_echo_type_daytime) { it.type?.time == TypeTime.DAYTIME },
        regular(R.array.history_echo_type_evening) { it.type?.time == TypeTime.EVENING },
        regular(R.array.history_echo_type_late_night) { it.type?.time == TypeTime.LATE_NIGHT },
        regular(R.array.history_echo_type_all_day) { it.type?.time == TypeTime.ALL_DAY },
        regular(R.array.history_echo_type_finisher) { (it.type?.finishRate ?: 0f) >= FINISHER_RATE },
        regular(R.array.history_echo_type_sampler) { it.type?.finishRate?.let { rate -> rate > 0f && rate < SAMPLER_RATE } == true },
    )

    private val RECORDS = listOf(
        special(R.array.history_echo_records_one_day) { it.listenedDays == 1 },
        special(R.array.history_echo_records_first_run) { it.isFirst && it.run >= 2 },
        special(R.array.history_echo_records_new_record) { it.bestRunIsRecord },
        special(R.array.history_echo_records_tied_record) { it.run >= 2 && it.run == it.echo.bestBefore && it.run == it.bestRunEver },
        regular(R.array.history_echo_records_longer_run) { it.hadPrevious && it.run >= 2 && it.run > (it.echo.previousRun ?: Int.MAX_VALUE) },
        regular(R.array.history_echo_records_shorter_run) { it.hadPrevious && it.run < (it.echo.previousRun ?: 0) },
        regular(R.array.history_echo_records_below_best) { it.run >= 2 && it.run < it.bestRunEver },
        regular(R.array.history_echo_records_bigger_peak) { story -> story.echo.previousBiggestDayMs?.let { story.biggestDay.second - it >= MINUTE_MS } == true },
        regular(R.array.history_echo_records_long_sitting) { (it.longestSitting?.first ?: 0L) >= LONG_SITTING_MS },
        regular(R.array.history_echo_records_short_sittings) { it.longestSitting?.first?.let { ms -> ms in 1 until SHORT_SITTING_MS } == true },
        regular(R.array.history_echo_records_binge) { (it.mostInOneDay?.episodes ?: 0) >= BINGE_EPISODES },
        regular(R.array.history_echo_records_scattered) { it.listenedDays > 1 && it.bestRun == null },
        regular(R.array.history_echo_records_weekday) { it.bigWeekday != null },
    )

    private val WRAP = listOf(
        special(R.array.history_echo_wrap_december) { it.month.monthValue == 12 },
        special(R.array.history_echo_wrap_january) { it.month.monthValue == 1 },
        special(R.array.history_echo_wrap_first) { it.isFirst },
    )

    /** `{genre}`: the month's leading genre where the lines compare months, the top three's where they share one, else the top show's. */
    fun genre(@ArrayRes lines: Int, story: MonthStory): String? = when (lines) {
        R.array.history_echo_top_genre_changed, R.array.history_echo_top_genre_same -> story.echo.leadingGenre
        R.array.history_echo_top_three_one_genre -> story.echo.topThreeGenres.firstOrNull()
        else -> story.echo.topGenre
    }
}

/** What Echo says on [card]: a line of its situation's array, picked by [seed], with the month's facts filled in. */
@Composable
internal fun storyEchoLine(card: StoryCard, story: MonthStory, seed: Int): String {
    val array = StoryEchoLines.lines(card, story, seed)
    val lines = stringArrayResource(array).toList()
    val usual = stringArrayResource(StoryEchoLines.usual(card)).toList()
    val values = storyEchoValues(story, StoryEchoLines.genre(array, story))
    return StoryEchoLines.choose(lines, usual, StoryEchoLines.index(lines.size, seed, card), values)
}

/**
 * The facts Echo's lines can name, by placeholder. A fact the month doesn't have is left out, and so are
 * counts under two (lines say "days", not "day"), so a line naming one gives way to another.
 */
@Composable
private fun storyEchoValues(story: MonthStory, genre: String?): Map<String, String> {
    val locale = currentLocale()
    val top = story.topShow
    val how = story.how
    val previous = story.previousMonthMs
    return buildMap {
        put("month", StoryText.monthName(story.month, locale))
        put("previous", StoryText.monthName(story.month.minusMonths(1), locale))
        putCount("days", story.listenedDays)
        putCount("hours", (story.totalMs / 3_600_000L).toInt())
        put("time", durationText(story.totalMs))
        if (previous != null && previous > 0L) put("diff", durationText(abs(story.totalMs - previous)))
        putCount("episodes", story.counts.episodes)
        putCount("shows", story.shows)
        put("bigDay", story.biggestDay.first.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)))
        put("bigDayTime", durationText(story.biggestDay.second))
        putCount("best", story.bestRunEver)
        top?.let {
            put("show", it.name)
            put("share", InsightsWording.percent(it.share).toString())
            putCount("finished", it.finished)
        }
        genre?.let { put("genre", it) }
        story.echo.previousLeadingGenre?.let { put("previousGenre", it) }
        how?.outputs?.firstOrNull()?.let { put("output", outputName(it.output)) }
        how?.sources?.let {
            put("newShare", InsightsWording.percent(it.newShare).toString())
            put("lore", InsightsWording.percent(it.loreShare).toString())
            put("smartQueue", InsightsWording.percent(it.smartQueueShare).toString())
        }
        story.bestRun?.let { put("run", it.first.toString()) }
        story.longestSitting?.let { put("sitting", durationText(it.first)) }
        story.mostInOneDay?.let { putCount("count", it.episodes) }
        story.bigWeekday?.let { put("weekday", weekdayPlural(it.first)) }
    }
}

private fun MutableMap<String, String>.putCount(name: String, count: Int) {
    if (count >= 2) put(name, count.toString())
}
