package cx.aswin.boxlore.feature.history.story

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.insights.DayPart
import cx.aswin.boxlore.feature.history.insights.InsightsWording
import cx.aswin.boxlore.feature.history.insights.ListeningType
import cx.aswin.boxlore.feature.history.insights.TypeTime
import cx.aswin.boxlore.feature.history.insights.perDayText
import cx.aswin.boxlore.feature.history.timeline.durationText
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** The story's words: month and weekday names, the time card's count-up and lines, Echo's lines. */
internal object StoryText {
    /** "October", as a title. */
    fun monthTitle(month: YearMonth, locale: Locale): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /** "October", inside a sentence. */
    fun monthName(month: YearMonth, locale: Locale): String = month.month.getDisplayName(TextStyle.FULL, locale)

    fun weekday(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.FULL, locale)

    /** The time card's total in whole minutes, counting up over 1.1s as the card opens (cubic ease out). */
    fun countUp(totalMs: Long, clock: StoryClock): Long {
        val minutes = totalMs / 60_000L
        if (clock.motionOff) return minutes
        val p = clock.linear(0, StoryMotion.COUNT_UP_MS)
        return (minutes * (1.0 - (1.0 - p).pow(3))).roundToLong()
    }

    /** "About 29 minutes a day, and 2 h 05 min more than September." */
    @Composable
    fun timeLine(story: MonthStory, locale: Locale): String {
        val perDay = perDayText(story.perDayAverageMs)
        val previous = story.previousMonthMs ?: return stringResource(R.string.history_story_time_only, perDay)
        val delta = story.totalMs - previous
        if (abs(delta) < 60_000L) return stringResource(R.string.history_story_time_only, perDay)
        val before = monthName(story.month.minusMonths(1), locale)
        val id = if (delta > 0) R.string.history_story_time_more else R.string.history_story_time_less
        return stringResource(id, perDay, durationText(abs(delta)), before)
    }

    /** What Echo says beside the top show, by its outfit. */
    @StringRes
    fun genreLine(outfit: EchoOutfit): Int = when (outfit) {
        EchoOutfit.TRUE_CRIME -> R.string.history_story_echo_crime
        EchoOutfit.TECH -> R.string.history_story_echo_tech
        EchoOutfit.NEWS -> R.string.history_story_echo_news
        EchoOutfit.HISTORY -> R.string.history_story_echo_history
        EchoOutfit.FICTION -> R.string.history_story_echo_fiction
        EchoOutfit.COMEDY -> R.string.history_story_echo_comedy
        EchoOutfit.SCIENCE -> R.string.history_story_echo_science
        EchoOutfit.BUSINESS -> R.string.history_story_echo_business
        EchoOutfit.HEALTH -> R.string.history_story_echo_health
        else -> R.string.history_story_echo_any
    }

    /** What Echo says on the listening-type card, by the time of day. */
    @StringRes
    fun typeEchoLine(time: TypeTime): Int = when (time) {
        TypeTime.MORNING -> R.string.history_story_echo_morning
        TypeTime.DAYTIME -> R.string.history_story_echo_daytime
        TypeTime.EVENING -> R.string.history_story_echo_evening
        TypeTime.LATE_NIGHT -> R.string.history_story_echo_late_night
        TypeTime.ALL_DAY -> R.string.history_story_echo_all_day
    }

    /** "46% of your listening was in the evening, and you finished about 3 in 4 episodes you started." */
    @Composable
    fun typeLine(type: ListeningType): String {
        val part = stringResource(
            when (type.topPart) {
                DayPart.MORNING -> R.string.history_story_type_morning
                DayPart.AFTERNOON -> R.string.history_story_type_afternoon
                DayPart.NIGHT -> R.string.history_story_type_night
                DayPart.EVENING, null -> R.string.history_story_type_evening
            },
        )
        val share = InsightsWording.percent(type.topPartShare)
        val rate = type.finishRate ?: return stringResource(R.string.history_story_type_only, share, part)
        if (rate >= InsightsWording.ALMOST_ALL_FROM) return stringResource(R.string.history_story_type_finish_all, share, part)
        if (rate <= 0f) return stringResource(R.string.history_story_type_finish_none, share, part)
        if (rate < InsightsWording.FEW_BELOW) return stringResource(R.string.history_story_type_finish_few, share, part)
        val (finished, of) = InsightsWording.nearestFraction(rate)
        return stringResource(R.string.history_story_type_finish, share, part, finished, of)
    }
}
