package cx.aswin.boxlore.feature.library.subscriptions

import android.app.Application
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Dates and lengths on Subscriptions rows, resolved from the module's English resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SubscriptionRowLabelsTest {

    private fun UiText.en(): String = resolve(RuntimeEnvironment.getApplication())

    private val nowMs = System.currentTimeMillis()

    private fun secondsAgo(days: Long): Long = (nowMs - TimeUnit.DAYS.toMillis(days)) / 1000L

    @Test
    fun relativeLabelReadsAsALineOfItsOwn() {
        assertEquals("Today", formatRelativeUpdateLabel(nowMs / 1000L, Locale.US, nowMs)!!.en())
        assertEquals("Yesterday", formatRelativeUpdateLabel(secondsAgo(1), Locale.US, nowMs)!!.en())
        assertEquals("3 days ago", formatRelativeUpdateLabel(secondsAgo(3), Locale.US, nowMs)!!.en())
        assertEquals("1 week ago", formatRelativeUpdateLabel(secondsAgo(8), Locale.US, nowMs)!!.en())
        assertEquals("2 weeks ago", formatRelativeUpdateLabel(secondsAgo(15), Locale.US, nowMs)!!.en())
        assertEquals("Just now", formatRelativeUpdateLabel((nowMs + 60_000) / 1000L, Locale.US, nowMs)!!.en())
    }

    @Test
    fun olderThanAMonthShowsTheDateWithItsYear() {
        val cal = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
        val label = formatRelativeUpdateLabel(cal.timeInMillis / 1000L, Locale.US, nowMs)!!.en()
        assertTrue(label.contains(cal.get(Calendar.YEAR).toString()))
        assertFalse(label.startsWith("Updated"))
    }

    @Test
    fun updatedLabelIsOneWholeSentence() {
        assertEquals("Updated today", formatUpdatedLabel(nowMs / 1000L, Locale.US, nowMs)!!.en())
        assertEquals("Updated yesterday", formatUpdatedLabel(secondsAgo(1), Locale.US, nowMs)!!.en())
        assertEquals("Updated 3 days ago", formatUpdatedLabel(secondsAgo(3), Locale.US, nowMs)!!.en())
        assertEquals("Updated 1 week ago", formatUpdatedLabel(secondsAgo(7), Locale.US, nowMs)!!.en())
        val cal = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
        val dated = formatUpdatedLabel(cal.timeInMillis / 1000L, Locale.US, nowMs)!!.en()
        assertTrue(dated.startsWith("Updated "))
        assertTrue(dated.contains(cal.get(Calendar.YEAR).toString()))
    }

    @Test
    fun durationShowsLengthOrWhatIsLeft() {
        val episode = Episode(
            id = "e",
            title = "T",
            description = "",
            audioUrl = "https://example.com/a.mp3",
            imageUrl = null,
            publishedDate = 1L,
            duration = 3660,
            podcastId = "p",
        )
        assertEquals("1h 1m", episodeMetaDurationLabel(episode, isInProgress = false, progress = 0f, locale = Locale.US).en())
        assertEquals("45m", episodeMetaDurationLabel(episode.copy(duration = 2700), isInProgress = false, progress = 0f, locale = Locale.US).en())
        assertEquals("1h left", episodeMetaDurationLabel(episode.copy(duration = 7200), isInProgress = true, progress = 0.5f, locale = Locale.US).en())
        assertEquals("15m left", episodeMetaDurationLabel(episode.copy(duration = 1800), isInProgress = true, progress = 0.5f, locale = Locale.US).en())
    }

    @Test
    fun weekdayAndDateHeadersFollowTheLocale() {
        val fourDaysAgo = secondsAgo(4)
        val english = getChronologicalHeader(fourDaysAgo, Locale.US).en()
        val german = getChronologicalHeader(fourDaysAgo, Locale.GERMANY).en()
        assertTrue(english.isNotBlank())
        assertTrue(english != german)
    }
}
