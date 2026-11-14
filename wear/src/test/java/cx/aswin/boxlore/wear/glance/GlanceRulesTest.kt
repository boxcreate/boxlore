package cx.aswin.boxlore.wear.glance

import cx.aswin.boxlore.wear.data.WatchLibraryState
import cx.aswin.boxlore.wear.model.WatchEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

        // The same episode at the same minute, renamed: the tile shows the name, so it is redrawn.
        val glance = GlanceRules.from(at10)!!
        assertNotEquals(key, GlanceRules.key(glance.copy(title = "Renamed")))
        assertNotEquals(key, GlanceRules.key(glance.copy(showTitle = "Other show")))
        assertNotEquals(key, GlanceRules.key(glance.copy(artworkUrl = "https://example.org/other.jpg")))
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

    // Regression: a Resume tap still waiting for the phone was lost when the screen was rebuilt, and
    // could start playback after a newer launch had asked for something else.
    @Test
    fun `a waiting Resume tap survives a rebuilt screen, gives way to a newer launch and is never acted on twice`() {
        val request = ResumeRequest()
        request.ask("a")
        assertEquals("a", request.pending)

        // The rebuilt screen carries it over and acts on it once.
        val rebuilt = ResumeRequest(request.pending)
        assertTrue(rebuilt.take("a"))
        assertFalse(rebuilt.take("a"))
        assertNull(rebuilt.pending)

        // A newer launch drops it: the older tap must not start playing afterwards.
        request.drop()
        assertFalse(request.take("a"))

        // A second tap for another episode replaces the first.
        request.ask("a")
        request.ask("b")
        assertFalse(request.take("a"))
        assertTrue(request.take("b"))
    }
}
