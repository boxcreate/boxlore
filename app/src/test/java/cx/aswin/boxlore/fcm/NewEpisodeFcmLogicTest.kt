package cx.aswin.boxlore.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    }
}
