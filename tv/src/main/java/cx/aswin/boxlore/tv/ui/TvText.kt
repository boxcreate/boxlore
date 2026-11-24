package cx.aswin.boxlore.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.model.SleepTimerChoice
import cx.aswin.boxlore.tv.ui.home.TimeRules
import java.text.DateFormat
import java.util.Date

/** "20m left" or "1h 5m left". */
@Composable
fun timeLeftText(minutes: Int): String {
    val (hours, rest) = TimeRules.split(minutes)
    return if (hours > 0) stringResource(R.string.tv_hours_minutes_left, hours, rest) else stringResource(R.string.tv_minutes_left, rest)
}

/** "35m" or "17h 57m". */
@Composable
fun lengthText(minutes: Int): String {
    val (hours, rest) = TimeRules.split(minutes)
    return if (hours > 0) stringResource(R.string.tv_hours_minutes, hours, rest) else stringResource(R.string.tv_minutes, rest)
}

/** "Nov 9, 2026", in the TV's language. */
@Composable
fun dateText(millis: Long?): String {
    if (millis == null) return ""
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(millis))
}

/** "Nov 9, 2026 · 39m", or the length alone when the date is unknown. */
@Composable
fun dateAndLength(millis: Long?, minutes: Int): String {
    val date = dateText(millis)
    val length = lengthText(minutes)
    return if (date.isEmpty()) length else stringResource(R.string.tv_date_and_length, date, length)
}

@Composable
fun sleepTimerText(choice: SleepTimerChoice): String = when (choice) {
    SleepTimerChoice.Off -> stringResource(R.string.tv_sleep_off)
    SleepTimerChoice.EndOfEpisode -> stringResource(R.string.tv_sleep_end_of_episode)
    SleepTimerChoice.Hour1, SleepTimerChoice.Hours2 -> {
        val hours = (choice.minutes ?: 0) / 60
        pluralStringResource(R.plurals.tv_sleep_hours, hours, hours)
    }
    else -> {
        val minutes = choice.minutes ?: 0
        pluralStringResource(R.plurals.tv_sleep_minutes, minutes, minutes)
    }
}
