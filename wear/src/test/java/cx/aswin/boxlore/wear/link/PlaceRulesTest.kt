package cx.aswin.boxlore.wear.link

import cx.aswin.boxlore.core.wearlink.PhoneNowPlaying
import cx.aswin.boxlore.core.wearlink.WearLinkEpisode
import cx.aswin.boxlore.wear.data.WatchLibraryState
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.model.WatchEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaceRulesTest {
    private val minute = 60_000L

    private fun onWatch(playing: Boolean) =
        NowPlaying(
            episode = WatchEpisode("w", "1", "Watch episode", "Show", artworkUrl = null, durationMinutes = 40),
            isPlaying = playing,
            place = PlaybackPlace.Watch,
            progress = 0.5f,
            minutesLeft = 20,
        )

    private fun onPhone(playing: Boolean, at: Long, positionMs: Long = 10 * minute) =
        PhoneNowPlaying(
            episode = WearLinkEpisode("p", "1", "Phone episode", durationMs = 40 * minute),
            isPlaying = playing,
            positionMs = positionMs,
            speed = 1f,
            at = at,
        )

    @Test
    fun `with no phone state the watch's own player is shown`() {
        assertEquals(onWatch(false), PlaceRules.shown(onWatch(false), 0L, null, now = 1_000L))
        assertNull(PlaceRules.shown(null, 0L, null, now = 1_000L))
        assertNull(PlaceRules.shown(null, 0L, PhoneNowPlaying(), now = 1_000L))
    }

    @Test
    fun `a phone that went out of reach while playing is held where it was last heard`() {
        val playing = onPhone(playing = true, at = 1_000L)

        // In reach, its report stands; so does a paused one whether in reach or not.
        assertEquals(playing, PlaceRules.heard(playing, reachable = true, lostAt = 0L))
        assertEquals(onPhone(playing = false, at = 1_000L), PlaceRules.heard(onPhone(playing = false, at = 1_000L), reachable = false, lostAt = 9_000L))
        assertNull(PlaceRules.heard(null, reachable = false, lostAt = 0L))

        // Lost two minutes after its report: paused there, not still running on.
        val lost = PlaceRules.heard(playing, reachable = false, lostAt = 1_000L + 2 * minute)!!
        assertFalse(lost.isPlaying)
        assertEquals(12 * minute, lost.positionMs)
        assertEquals(12 * minute, lost.positionAt(now = 90 * minute))

        // Out of reach from the start, the moment it was lost is unknown: the reported position stands.
        assertEquals(10 * minute, PlaceRules.heard(playing, reachable = false, lostAt = 0L)!!.positionMs)
    }

    @Test
    fun `a playing watch is shown even while the phone plays too`() {
        val shown = PlaceRules.shown(onWatch(true), 0L, onPhone(playing = true, at = 900L), now = 1_000L)

        assertEquals(PlaybackPlace.Watch, shown?.place)
    }

    @Test
    fun `a playing phone is shown over a paused watch, with its position moved on`() {
        val shown = PlaceRules.shown(onWatch(false), 5_000L, onPhone(playing = true, at = 1L), now = 5 * minute + 1)!!

        assertEquals(PlaybackPlace.Phone, shown.place)
        assertTrue(shown.isPlaying)
        assertEquals("Phone episode", shown.episode.title)
        assertEquals(25, shown.minutesLeft)
        assertEquals(15f / 40f, shown.progress, 0.001f)
    }

    @Test
    fun `when both are paused the one used last is shown`() {
        val phoneLater = PlaceRules.shown(onWatch(false), watchAt = 100L, phone = onPhone(playing = false, at = 900L), now = 1_000L)
        val watchLater = PlaceRules.shown(onWatch(false), watchAt = 900L, phone = onPhone(playing = false, at = 100L), now = 1_000L)

        assertEquals(PlaybackPlace.Phone, phoneLater?.place)
        assertFalse(phoneLater!!.isPlaying)
        assertEquals(30, phoneLater.minutesLeft)
        assertEquals(PlaybackPlace.Watch, watchLater?.place)
    }

    @Test
    fun `a paused phone is shown when the watch has nothing`() {
        assertEquals(PlaybackPlace.Phone, PlaceRules.shown(null, 0L, onPhone(playing = false, at = 5L), now = 1_000L)?.place)
    }

    @Test
    fun `the watch's own copy of the phone's episode gives its ID and a length the phone did not send`() {
        // The phone knows the episode under another ID and has not learned its length yet.
        val phone = onPhone(playing = false, at = 5L, positionMs = 83 * minute).let { it.copy(episode = it.episode!!.copy(durationMs = 0L)) }
        val known =
            WatchEpisode(id = "mine", showId = "1", title = "Phone episode", showTitle = "", artworkUrl = null, durationMinutes = 104, durationMs = 104 * minute)

        val shown = PlaceRules.shown(null, 0L, phone, now = 1_000L, known = known)!!

        assertEquals("mine", shown.episode.id)
        assertEquals(21, shown.minutesLeft)
        assertEquals(83f / 104f, shown.progress, 0.001f)
    }

    @Test
    fun `Up next lists the phone's queue only while the phone's player is the one shown`() {
        val phone = onPhone(playing = true, at = 1L).copy(upNext = listOf(WearLinkEpisode("q1", "1", "Next"), WearLinkEpisode("q2", "1", "After")))
        val shown = PlaceRules.shown(null, 0L, phone, now = 1_000L)

        assertEquals(2, shown?.upNextCount)
        assertEquals(listOf("q1", "q2"), PlaceRules.phoneQueue(shown, phone, WatchLibraryState())?.map { it.id })
        assertNull(PlaceRules.phoneQueue(onWatch(true), phone, WatchLibraryState()))
        assertNull(PlaceRules.phoneQueue(null, phone, WatchLibraryState()))
    }

    @Test
    fun `a phone with nothing queued shows an empty Up next, not the watch's own`() {
        val phone = onPhone(playing = true, at = 1L)

        assertEquals(emptyList<String>(), PlaceRules.phoneQueue(PlaceRules.shown(null, 0L, phone, now = 1_000L), phone, WatchLibraryState())?.map { it.id })
    }

    @Test
    fun `play on a paused phone that is out of reach carries on here, everything else is the phone's`() {
        assertEquals(PhoneTap.PlayHere, PlaceRules.onToggle(phonePlaying = false, phoneReachable = false))
        assertEquals(PhoneTap.TellPhone, PlaceRules.onToggle(phonePlaying = false, phoneReachable = true))
        assertEquals(PhoneTap.TellPhone, PlaceRules.onToggle(phonePlaying = true, phoneReachable = true))
        assertEquals(PhoneTap.TellPhone, PlaceRules.onToggle(phonePlaying = true, phoneReachable = false))
    }

    @Test
    fun `taking the phone's episode over is always possible, handing one over needs the phone in reach`() {
        assertTrue(PlaceRules.canSwitch(onPhone = true, phoneReachable = false))
        assertTrue(PlaceRules.canSwitch(onPhone = false, phoneReachable = true))
        assertFalse(PlaceRules.canSwitch(onPhone = false, phoneReachable = false))
    }

    @Test
    fun `the phone's position never runs past the end`() {
        val phone = onPhone(playing = true, at = 1L, positionMs = 39 * minute)

        assertEquals(40 * minute, phone.positionAt(now = 30 * minute))
        assertEquals(0, PlaceRules.shown(null, 0L, phone, now = 30 * minute)?.minutesLeft)
    }
}
