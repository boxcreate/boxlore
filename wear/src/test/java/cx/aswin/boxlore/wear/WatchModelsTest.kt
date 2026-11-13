package cx.aswin.boxlore.wear

import cx.aswin.boxlore.wear.data.EmptyWatchLibrary
import cx.aswin.boxlore.wear.data.IdleWatchPlayback
import cx.aswin.boxlore.wear.model.PlaybackSpeeds
import cx.aswin.boxlore.wear.model.SleepTimerChoice
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.ui.WatchFormat
import cx.aswin.boxlore.wear.ui.WatchRoutes
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchModelsTest {
    @Test
    fun `speeds match the phone player's list`() {
        assertEquals(listOf(0.5f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f), PlaybackSpeeds.all)
    }

    @Test
    fun `stepping the speed walks the list and stops at both ends`() {
        assertEquals(1.1f, PlaybackSpeeds.step(1f, up = true))
        assertEquals(0.9f, PlaybackSpeeds.step(1f, up = false))
        assertEquals(1.5f, PlaybackSpeeds.step(1.25f, up = true))
        assertEquals(3f, PlaybackSpeeds.step(3f, up = true))
        assertEquals(0.5f, PlaybackSpeeds.step(0.5f, up = false))
    }

    @Test
    fun `stepping from a speed off the list lands on a listed speed`() {
        assertEquals(1.25f, PlaybackSpeeds.step(1.3f, up = false))
        assertEquals(1.5f, PlaybackSpeeds.step(1.3f, up = true))
        assertEquals(3f, PlaybackSpeeds.step(9f, up = true))
        assertEquals(0.5f, PlaybackSpeeds.step(0.1f, up = false))
    }

    @Test
    fun `speed labels drop a trailing zero`() {
        assertEquals("1", PlaybackSpeeds.label(1f))
        assertEquals("2", PlaybackSpeeds.label(2f))
        assertEquals("1.25", PlaybackSpeeds.label(1.25f))
        assertEquals("0.5", PlaybackSpeeds.label(0.5f))
    }

    @Test
    fun `sleep timer choices match the phone player's`() {
        assertEquals(listOf(null, 15, 30, 45, 60, 120, null), SleepTimerChoice.entries.map { it.minutes })
    }

    @Test
    fun `minutes left never reads zero while some is left`() {
        assertEquals(39, WatchFormat.minutesLeft(39, 0f))
        assertEquals(24, WatchFormat.minutesLeft(39, 0.36f))
        assertEquals(1, WatchFormat.minutesLeft(39, 0.999f))
        assertEquals(0, WatchFormat.minutesLeft(39, 1f))
        assertEquals(0, WatchFormat.minutesLeft(39, 4f))
        assertEquals(39, WatchFormat.minutesLeft(39, -1f))
    }

    @Test
    fun `short dates follow the listener's language and time zone`() {
        val sixthOfNovember = 1_793_923_200_000L

        assertEquals("6 Nov", WatchFormat.shortDate(sixthOfNovember, ZoneId.of("UTC"), Locale.UK))
        assertEquals("5 Nov", WatchFormat.shortDate(sixthOfNovember, ZoneId.of("America/Los_Angeles"), Locale.UK))
        assertEquals("6 nov", WatchFormat.shortDate(sixthOfNovember, ZoneId.of("UTC"), Locale.forLanguageTag("es")))
    }

    @Test
    fun `a launch can open a known screen and nothing else`() {
        assertEquals(WatchRoutes.PLAYER, WatchRoutes.launchRoute("player"))
        assertEquals(WatchRoutes.DOWNLOADS, WatchRoutes.launchRoute("downloads"))
        assertNull(WatchRoutes.launchRoute(null))
        assertNull(WatchRoutes.launchRoute("home"))
        assertNull(WatchRoutes.launchRoute("show/darknet"))
        assertNull(WatchRoutes.launchRoute("anything else"))
    }

    @Test
    fun `an empty library has nothing and cannot search`() =
        runTest {
            val library = EmptyWatchLibrary()

            assertTrue(library.shows.value.isEmpty())
            assertTrue(library.jumpBackIn.value.isEmpty())
            assertTrue(library.newEpisodes.value.isEmpty())
            assertTrue(library.upNext.value.isEmpty())
            assertTrue(library.downloads.value.isEmpty())
            assertNull(library.show("any").first())
            assertNull(library.episode("any").first())
            assertTrue(library.episodes("any").first().isEmpty())
            assertEquals(WatchSearchResult.NoConnection("history"), library.search("history"))
        }

    @Test
    fun `idle playback has nothing loaded and ignores controls`() {
        val playback = IdleWatchPlayback()

        playback.play("any")
        playback.playDailyMix()
        playback.togglePlayPause()
        playback.seekForward()
        playback.setSleepTimer(SleepTimerChoice.Minutes30)

        assertNull(playback.nowPlaying.value)
    }
}
