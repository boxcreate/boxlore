package cx.aswin.boxlore.wear.glance

import cx.aswin.boxlore.wear.data.WatchLibraryState
import cx.aswin.boxlore.wear.model.WatchEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GlanceRulesTest {
    private val minute = 60_000L

    private fun episode(id: String, show: String = "Show") =
        WatchEpisode(id = id, showId = "1", title = "Episode $id", showTitle = show, artworkUrl = "https://example.org/$id.jpg", durationMinutes = 40, audioUrl = "https://example.org/$id.mp3")

    @Test
    fun `with nothing in progress there is nothing to jump back into`() {
        assertNull(GlanceRules.from(WatchLibraryState()))
        // A finished episode is not something to resume.
        assertNull(GlanceRules.from(WatchLibraryState().progress(episode("a"), 40 * minute, 40 * minute, nowMs = 1L)))
    }

    @Test
    fun `the episode played most recently is shown with its time left and how far in it is`() {
        val state =
            WatchLibraryState()
                .progress(episode("old"), 5 * minute, 40 * minute, nowMs = 1L)
                .progress(episode("new", show = "Darknet Diaries"), 10 * minute, 40 * minute, nowMs = 2L)

        val glance = GlanceRules.from(state)!!

        assertEquals("new", glance.episodeId)
        assertEquals("Episode new", glance.title)
        assertEquals("Darknet Diaries", glance.showTitle)
        assertEquals(30, glance.minutesLeft)
        assertEquals(0.25f, glance.progress, 0.001f)
        assertEquals("https://example.org/new.jpg", glance.artworkUrl)
    }

    @Test
    fun `the tile and the watch face are redrawn when the episode or its time left changes, not on every saved second`() {
        val at10 = WatchLibraryState().progress(episode("a"), 10 * minute, 40 * minute, nowMs = 1L)
        val seconds = at10.progress(episode("a"), 10 * minute + 20_000L, 40 * minute, nowMs = 2L)
        val at11 = at10.progress(episode("a"), 11 * minute, 40 * minute, nowMs = 3L)
        val other = at10.progress(episode("b"), 10 * minute, 40 * minute, nowMs = 4L)

        val key = GlanceRules.key(GlanceRules.from(at10))
        assertEquals(key, GlanceRules.key(GlanceRules.from(seconds)))
        assertNotEquals(key, GlanceRules.key(GlanceRules.from(at11)))
        assertNotEquals(key, GlanceRules.key(GlanceRules.from(other)))
        assertEquals("", GlanceRules.key(null))
    }

    @Test
    fun `the detail line names the show before the time left, and the short slot shows minutes only`() {
        val join: (String, String) -> String = { show, time -> "$show · $time" }

        assertEquals("Darknet Diaries · 26 min left", GlanceRules.detail("Darknet Diaries", "26 min left", join))
        assertEquals("26 min left", GlanceRules.detail(" ", "26 min left", join))
        assertEquals("26m", GlanceRules.shortTime(26))
    }

    @Test
    fun `Resume plays the named episode here, asks first while the phone plays, and leaves one already playing alone`() {
        assertEquals(ResumeTap.PlayHere, GlanceRules.onResume(playingHereId = null, episodeId = "a", phoneIsPlaying = false))
        // Another episode playing here is replaced by the one the tile named.
        assertEquals(ResumeTap.PlayHere, GlanceRules.onResume(playingHereId = "b", episodeId = "a", phoneIsPlaying = false))
        assertEquals(ResumeTap.AskWhere, GlanceRules.onResume(playingHereId = null, episodeId = "a", phoneIsPlaying = true))
        assertEquals(ResumeTap.ShowPlayer, GlanceRules.onResume(playingHereId = "a", episodeId = "a", phoneIsPlaying = true))
    }

    @Test
    fun `the tile asks for a new picture when the episode changes, and for none when there is no artwork`() {
        val first = GlanceRules.from(WatchLibraryState().progress(episode("a"), 10 * minute, 40 * minute, nowMs = 1L))!!
        val second = GlanceRules.from(WatchLibraryState().progress(episode("b"), 10 * minute, 40 * minute, nowMs = 1L))!!

        assertEquals(TileRules.artworkId(first), TileRules.resourcesVersion(first, hasArtwork = true))
        assertNotEquals(TileRules.resourcesVersion(first, hasArtwork = true), TileRules.resourcesVersion(second, hasArtwork = true))
        assertEquals(TileRules.NO_ARTWORK, TileRules.resourcesVersion(first, hasArtwork = false))
        assertEquals(TileRules.NO_ARTWORK, TileRules.resourcesVersion(null, hasArtwork = false))
    }
}
