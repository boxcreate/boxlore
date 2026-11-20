package cx.aswin.boxlore.core.designsystem.share

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The details line under a share card's names, in the listener's language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareCardDetailsTest {
    private val resources: Resources get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val today = LocalDate.of(2026, 11, 14)
    private val utc = ZoneOffset.UTC
    private val oct30 = LocalDate.of(2026, 10, 30).atStartOfDay(utc).toEpochSecond()
    private val lastYear = LocalDate.of(2024, 3, 2).atStartOfDay(utc).toEpochSecond()

    /** Details keep each part's words together with no-break spaces; compare them as plain text. */
    private fun String?.plain() = this?.replace('\u00A0', ' ')?.replace('\u202F', ' ')

    @Test
    fun episodeDetailsKeepEachPartOnOneLine() {
        val details = shareEpisodeDetails(4_320, 0, today, utc, Locale.UK)
        assertEquals("1\u00A0hr,\u00A012\u00A0mins", details)
        assertEquals("Technology · Every\u00A02\u00A0weeks", sharePodcastDetails("Technology", "biweekly", resources))
    }

    @Test
    fun episodeDetailsGiveTheLengthAndTheDay() {
        // British English shortens minutes to "mins".
        assertEquals("49 mins · 30 Oct", shareEpisodeDetails(2_968, oct30, today, utc, Locale.UK).plain())
        assertEquals("2 hrs · 2 Mar 2024", shareEpisodeDetails(7_200, lastYear, today, utc, Locale.UK).plain())
        assertEquals("1 hr, 12 mins", shareEpisodeDetails(4_320, 0, today, utc, Locale.UK).plain())
        assertEquals("30 Oct", shareEpisodeDetails(0, oct30, today, utc, Locale.UK).plain())
        assertEquals(null, shareEpisodeDetails(30, 0, today, utc, Locale.UK).plain())
    }

    @Test
    fun episodeDetailsFollowTheListenersLanguage() {
        assertEquals("49 min · Oct 30", shareEpisodeDetails(2_968, oct30, today, utc, Locale.US).plain())
        assertEquals("49 Min. · 30. Okt.", shareEpisodeDetails(2_968, oct30, today, utc, Locale.GERMANY).plain())
    }

    @Test
    fun showDetailsGiveTheGenreAndHowOftenItPosts() {
        assertEquals("Technology · Daily", sharePodcastDetails("Technology", "daily", resources).plain())
        assertEquals("History · Weekly", sharePodcastDetails("History", "1", resources).plain())
        assertEquals("History · Every 2 weeks", sharePodcastDetails("History", "Bi-weekly", resources).plain())
        assertEquals("Several times a week", sharePodcastDetails("Podcast", "3", resources).plain())
        assertEquals("Comedy", sharePodcastDetails("Comedy", "whenever", resources).plain())
        assertEquals(null, sharePodcastDetails(" ", null, resources).plain())
        assertEquals(null, sharePodcastDetails("podcast", "0.01", resources).plain())
    }
}
