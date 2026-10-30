package cx.aswin.boxlore.feature.history.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/** Rounding a share or rate to words: "almost half", "3 in 4". */
object InsightsWording {
    /** Shares from this are "Most"; from [HALF_FROM] "Almost half"; below, a percentage. */
    const val MOST_FROM = 0.5f
    const val HALF_FROM = 0.42f

    /** "Almost every episode" from this finish rate. */
    const val ALMOST_ALL_FROM = 0.9f

    private val FRACTIONS = listOf(1 to 5, 1 to 4, 1 to 3, 1 to 2, 2 to 3, 3 to 4, 4 to 5)

    /** The plain fraction nearest [rate], e.g. 0.74 → 3 in 4. */
    fun nearestFraction(rate: Float): Pair<Int, Int> = FRACTIONS.minBy { (a, b) -> abs(a.toFloat() / b - rate) }

    fun percent(share: Float): Int = (share * 100).roundToInt()
}

@Composable
internal fun typeName(type: ListeningType): String = stringResource(TYPE_NAMES.getValue(type.time to type.habit))

private val TYPE_NAMES =
    mapOf(
        (TypeTime.MORNING to TypeHabit.FINISHER) to R.string.history_type_morning_finisher,
        (TypeTime.MORNING to TypeHabit.LISTENER) to R.string.history_type_morning_listener,
        (TypeTime.MORNING to TypeHabit.SAMPLER) to R.string.history_type_morning_sampler,
        (TypeTime.DAYTIME to TypeHabit.FINISHER) to R.string.history_type_daytime_finisher,
        (TypeTime.DAYTIME to TypeHabit.LISTENER) to R.string.history_type_daytime_listener,
        (TypeTime.DAYTIME to TypeHabit.SAMPLER) to R.string.history_type_daytime_sampler,
        (TypeTime.EVENING to TypeHabit.FINISHER) to R.string.history_type_evening_finisher,
        (TypeTime.EVENING to TypeHabit.LISTENER) to R.string.history_type_evening_listener,
        (TypeTime.EVENING to TypeHabit.SAMPLER) to R.string.history_type_evening_sampler,
        (TypeTime.LATE_NIGHT to TypeHabit.FINISHER) to R.string.history_type_late_night_finisher,
        (TypeTime.LATE_NIGHT to TypeHabit.LISTENER) to R.string.history_type_late_night_listener,
        (TypeTime.LATE_NIGHT to TypeHabit.SAMPLER) to R.string.history_type_late_night_sampler,
        (TypeTime.ALL_DAY to TypeHabit.FINISHER) to R.string.history_type_all_day_finisher,
        (TypeTime.ALL_DAY to TypeHabit.LISTENER) to R.string.history_type_all_day_listener,
        (TypeTime.ALL_DAY to TypeHabit.SAMPLER) to R.string.history_type_all_day_sampler,
    )

/** "Almost half your listening happens in the evening." */
@Composable
internal fun partFact(part: DayPart, share: Float): String {
    val (most, half, percent) =
        when (part) {
            DayPart.MORNING -> Triple(R.string.history_part_most_morning, R.string.history_part_half_morning, R.string.history_part_share_morning)
            DayPart.AFTERNOON -> Triple(R.string.history_part_most_afternoon, R.string.history_part_half_afternoon, R.string.history_part_share_afternoon)
            DayPart.EVENING -> Triple(R.string.history_part_most_evening, R.string.history_part_half_evening, R.string.history_part_share_evening)
            DayPart.NIGHT -> Triple(R.string.history_part_most_night, R.string.history_part_half_night, R.string.history_part_share_night)
        }
    return when {
        share >= InsightsWording.MOST_FROM -> stringResource(most)
        share >= InsightsWording.HALF_FROM -> stringResource(half)
        else -> stringResource(percent, InsightsWording.percent(share))
    }
}

/** "You finish about 3 in 4 episodes you start." */
@Composable
internal fun finishFact(rate: Float): String {
    if (rate >= InsightsWording.ALMOST_ALL_FROM) return stringResource(R.string.history_finish_almost_all)
    val (finished, of) = InsightsWording.nearestFraction(rate)
    return stringResource(R.string.history_finish_rate, finished, of)
}

@Composable
internal fun partName(part: DayPart): String = stringResource(
    when (part) {
        DayPart.MORNING -> R.string.history_bucket_morning
        DayPart.AFTERNOON -> R.string.history_bucket_afternoon
        DayPart.EVENING -> R.string.history_bucket_evening
        DayPart.NIGHT -> R.string.history_bucket_night
    },
)

/** "Mostly evenings", or "All through the day" when no part leads. */
@Composable
internal fun mostlyText(parts: List<PartShare>): String {
    val top = parts.firstOrNull()
    if (top == null || top.share < 0.35f) return stringResource(R.string.history_ins_all_through_day)
    return stringResource(
        when (top.part) {
            DayPart.MORNING -> R.string.history_ins_mostly_morning
            DayPart.AFTERNOON -> R.string.history_ins_mostly_afternoon
            DayPart.EVENING -> R.string.history_ins_mostly_evening
            DayPart.NIGHT -> R.string.history_ins_mostly_night
        },
    )
}

/** "2 h 05 min more than the 30 days before", or null for All and when nothing changed. */
@Composable
internal fun comparisonText(time: TimeListened, period: ListeningPeriod): String? {
    val previous = time.previousMs ?: return null
    val delta = time.totalMs - previous
    if (abs(delta) < 60_000L) return null
    val amount = durationText(abs(delta))
    val id =
        when (period) {
            ListeningPeriod.DAYS_7 -> if (delta > 0) R.string.history_ins_more_than_7 else R.string.history_ins_less_than_7
            ListeningPeriod.DAYS_30 -> if (delta > 0) R.string.history_ins_more_than_30 else R.string.history_ins_less_than_30
            ListeningPeriod.DAYS_180 -> if (delta > 0) R.string.history_ins_more_than_180 else R.string.history_ins_less_than_180
            ListeningPeriod.ALL -> return null
        }
    return stringResource(id, amount)
}

/** "About 29 minutes a day". */
@Composable
internal fun perDayText(perDayMs: Long): String {
    val minutes = (perDayMs / 60_000L).toInt()
    return if (minutes <= 0) stringResource(R.string.history_ins_per_day_under_minute) else pluralStringResource(R.plurals.history_ins_per_day_minutes, minutes, minutes)
}

/** "Wednesdays" — the weekday's plural as the board writes it. */
@Composable
internal fun weekdayPlural(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL_STANDALONE, currentLocale()) + "s"

/** "4 h 23 min on Wednesdays this month". */
@Composable
internal fun bigDayMeta(weekdays: Weekdays, period: ListeningPeriod): String {
    val id =
        when (period) {
            ListeningPeriod.DAYS_7 -> R.string.history_ins_big_day_7
            ListeningPeriod.DAYS_30 -> R.string.history_ins_big_day_30
            ListeningPeriod.DAYS_180 -> R.string.history_ins_big_day_180
            ListeningPeriod.ALL -> R.string.history_ins_big_day_all
        }
    return stringResource(id, durationText(weekdays.biggestMs), weekdayPlural(weekdays.biggest))
}
