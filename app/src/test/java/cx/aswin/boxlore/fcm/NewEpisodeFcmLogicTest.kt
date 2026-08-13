package cx.aswin.boxlore.fcm

import android.app.Application
import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewEpisodeFcmLogicTest {
    @Test
    fun usableEpisodeIdDropsBlankAndZero() {
        assertNull(NewEpisodeFcmLogic.usableEpisodeId(null))
        assertNull(NewEpisodeFcmLogic.usableEpisodeId(" "))
        assertNull(NewEpisodeFcmLogic.usableEpisodeId("0"))
        assertEquals("-12", NewEpisodeFcmLogic.usableEpisodeId("-12"))
        assertEquals("99", NewEpisodeFcmLogic.usableEpisodeId("99"))
    }

    @Test
    fun routeUsesEpisodeWhenPresentOtherwisePodcastPage() {
        assertEquals(
            "boxlore://episode/-12?autoplay=false&podcastId=123&podcastTitle=Show%20Name",
            NewEpisodeFcmLogic.route("123", "-12", "Show Name"),
        )
        assertEquals(
            "boxlore://podcast/123",
            NewEpisodeFcmLogic.route("123", "0", "Show"),
        )
    }

    @Test
    fun durationMinutesPrefersLocalSeconds() {
        assertEquals(30, NewEpisodeFcmLogic.durationMinutes(1800, "9"))
        assertEquals(9, NewEpisodeFcmLogic.durationMinutes(null, "9"))
        assertEquals(0, NewEpisodeFcmLogic.durationMinutes(0, null))
        assertEquals(0, NewEpisodeFcmLogic.durationMinutes(null, "invalid"))
        assertEquals(0, NewEpisodeFcmLogic.durationMinutes(null, "-1"))
    }

    @Test
    fun pickHydratedEpisodePrefersEnclosureThenNewestTip() {
        val extra =
            Episode(
                id = "-8",
                title = "Matched",
                description = "d",
                audioUrl = "https://cdn.example.com/ep.mp3",
                podcastId = "123",
                publishedDate = 100L,
                duration = 60,
            )
        val newest =
            extra.copy(
                id = "-9",
                title = "Newest",
                audioUrl = "https://cdn.example.com/newer.mp3",
                publishedDate = 200L,
            )
        assertEquals(
            "-8",
            NewEpisodeFcmLogic.pickHydratedEpisode(
                extras = listOf(extra, newest),
                newestTip = newest,
                enclosureUrl = "https://cdn.example.com/ep.mp3",
            )
                ?.id,
        )
        assertEquals(
            "-9",
            NewEpisodeFcmLogic.pickHydratedEpisode(
                extras = listOf(extra, newest),
                newestTip = newest,
                enclosureUrl = "",
            )
                ?.id,
        )
    }
}
