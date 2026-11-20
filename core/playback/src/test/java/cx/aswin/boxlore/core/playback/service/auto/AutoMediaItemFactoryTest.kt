package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The words on a car row's second line, read from resources in the car's language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoMediaItemFactoryTest {
    private val minute = 60_000L
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val english: Resources = resourcesFor(Locale.US)

    private fun resourcesFor(locale: Locale): Resources = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(locale) },
    ).resources

    @Test
    fun `time left is in minutes up to an hour, then hours and minutes`() {
        assertEquals("35 min left", AutoMediaItemFactory.formatTimeLeft(english, progressMs = 25 * minute, durationMs = 60 * minute))
        assertEquals("1 hr left", AutoMediaItemFactory.formatTimeLeft(english, progressMs = 0L, durationMs = 60 * minute))
        assertEquals("1 hr, 1 min left", AutoMediaItemFactory.formatTimeLeft(english, progressMs = 0L, durationMs = 61 * minute))
        assertEquals("2 hr left", AutoMediaItemFactory.formatTimeLeft(english, progressMs = 30 * minute, durationMs = 150 * minute))
    }

    @Test
    fun `under a minute left, or progress past the end, reads Almost done`() {
        assertEquals("Almost done", AutoMediaItemFactory.formatTimeLeft(english, progressMs = 59 * minute + 30_000L, durationMs = 60 * minute))
        assertEquals("Almost done", AutoMediaItemFactory.formatTimeLeft(english, progressMs = 70 * minute, durationMs = 60 * minute))
    }

    @Test
    fun `an episode's length uses the language's units, with a floor under a minute and blank when unknown`() {
        assertEquals("28 min", AutoMediaItemFactory.formatDuration(english, 28 * minute))
        assertEquals("1 hr, 5 min", AutoMediaItemFactory.formatDuration(english, 65 * minute))
        assertEquals("< 1 min", AutoMediaItemFactory.formatDuration(english, 30_000L))
        assertEquals("", AutoMediaItemFactory.formatDuration(english, 0L))
        assertEquals("28 Min.", AutoMediaItemFactory.formatDuration(resourcesFor(Locale.GERMANY), 28 * minute))
        assertEquals("Show · 28 min", AutoMediaItemFactory.buildDurationSubtitle(english, "Show", 28 * minute))
    }

    @Test
    fun `a subtitle joins the parts it has and skips blank ones`() {
        assertEquals("Show · Today · 28 min", AutoMediaItemFactory.joinSubtitle("Show", "Today", "28 min"))
        assertEquals("Show · 28 min", AutoMediaItemFactory.joinSubtitle("Show", null, "28 min"))
        assertEquals("28 min", AutoMediaItemFactory.joinSubtitle("", "  ", "28 min"))
        assertEquals("", AutoMediaItemFactory.joinSubtitle(null, ""))
    }

    @Test
    fun `the short date drops the year only within the same year, in the language's own order`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val published = ZonedDateTime.of(2025, 10, 14, 12, 0, 0, 0, zone).toEpochSecond()

        assertEquals("14 Oct", AutoDayLabels.shortDate(published, sameYear = true, locale = Locale.UK, zone = zone))
        assertEquals("14 Oct 2025", AutoDayLabels.shortDate(published, sameYear = false, locale = Locale.UK, zone = zone))
        assertEquals("Oct 14", AutoDayLabels.shortDate(published, sameYear = true, locale = Locale.US, zone = zone))
        assertEquals("14. Okt.", AutoDayLabels.shortDate(published, sameYear = true, locale = Locale.GERMANY, zone = zone))
    }
}
