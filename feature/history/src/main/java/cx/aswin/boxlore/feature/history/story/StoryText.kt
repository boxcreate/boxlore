package cx.aswin.boxlore.feature.history.story

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.insights.DayPart
import cx.aswin.boxlore.feature.history.insights.InsightsWording
import cx.aswin.boxlore.feature.history.insights.ListeningType
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
    fun countUp(totalMs: Long, clock: StoryClock): Long = countUp(totalMs / 60_000L, clock, delay = 0)

    /** [value] counting up from 0 over 1.1s after [delay] (cubic ease out); the value itself with motion off. */
    fun countUp(value: Long, clock: StoryClock, delay: Int): Long {
        if (clock.motionOff) return value
        val p = clock.linear(delay, StoryMotion.COUNT_UP_MS)
        return (value * (1.0 - (1.0 - p).pow(3))).roundToLong()
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

    /** "at night": where the type card's leading share of the listening fell. */
    @Composable
    fun partPhrase(part: DayPart?): String = stringResource(
        when (part) {
            DayPart.MORNING -> R.string.history_story_type_morning
            DayPart.AFTERNOON -> R.string.history_story_type_afternoon
            DayPart.NIGHT -> R.string.history_story_type_night
            DayPart.EVENING, null -> R.string.history_story_type_evening
        },
    )

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
