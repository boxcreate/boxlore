package cx.aswin.boxlore.feature.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import java.time.DayOfWeek
import java.time.LocalDate

@Immutable
data class DiscoveryGreeting(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val daypart: ContentDaypart,
) {
    /** The title as analytics records it (`time_block_title`); stays in English whatever the app language. */
    val analyticsTitle: String get() = ANALYTICS_TITLES.getValue(daypart)
}

private val ANALYTICS_TITLES = mapOf(
    ContentDaypart.MORNING to "Good Morning",
    ContentDaypart.AFTERNOON to "Afternoon Break",
    ContentDaypart.EVENING to "Evening Unwind",
    ContentDaypart.LATE_NIGHT to "Late Night Listen",
)

/** Pure daypart → home discovery greeting (weekend / Friday copy variants). */
internal fun discoveryGreetingFor(
    daypart: ContentDaypart,
    date: LocalDate = LocalDate.now(),
): DiscoveryGreeting = when (daypart) {
    ContentDaypart.MORNING ->
        DiscoveryGreeting(
            title = R.string.home_greeting_morning_title,
            subtitle =
            if (date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                R.string.home_greeting_morning_weekend
            } else {
                R.string.home_greeting_morning_weekday
            },
            daypart = daypart,
        )
    ContentDaypart.AFTERNOON ->
        DiscoveryGreeting(
            title = R.string.home_greeting_afternoon_title,
            subtitle = R.string.home_greeting_afternoon_subtitle,
            daypart = daypart,
        )
    ContentDaypart.EVENING ->
        DiscoveryGreeting(
            title = R.string.home_greeting_evening_title,
            subtitle =
            if (date.dayOfWeek == DayOfWeek.FRIDAY) {
                R.string.home_greeting_evening_friday
            } else {
                R.string.home_greeting_evening_other
            },
            daypart = daypart,
        )
    ContentDaypart.LATE_NIGHT ->
        DiscoveryGreeting(
            title = R.string.home_greeting_late_night_title,
            subtitle = R.string.home_greeting_late_night_subtitle,
            daypart = daypart,
        )
}
