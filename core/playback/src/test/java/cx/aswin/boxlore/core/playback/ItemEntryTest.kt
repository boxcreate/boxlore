package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which entry point a listen from a media item reports. */
class ItemEntryTest {
    @Test
    fun `a queue moving on names the move and keeps where the queue started`() {
        // Regression: every item of a queue started from a show page carried "podcast_detail", so
        // auto-advance and skips were never reported as such.
        val (entryPoint, context) = itemEntry("podcast_detail", null, transition = "queue_auto_advance")

        assertEquals("queue_auto_advance", entryPoint)
        assertEquals(mapOf("source_entry_point" to "podcast_detail"), context)
    }

    @Test
    fun `an item's own source wins over the queue's starting surface`() {
        val (_, context) = itemEntry("podcast_detail", mapOf("source_entry_point" to "library_latest_episodes"), transition = "queue_skip")

        assertEquals("library_latest_episodes", context?.get("source_entry_point"))
    }

    @Test
    fun `the first item of a queue keeps its own entry point`() {
        val (entryPoint, context) = itemEntry("podcast_detail", mapOf("screen" to "podcast"), transition = null)

        assertEquals("podcast_detail", entryPoint)
        assertEquals(mapOf("screen" to "podcast"), context)
    }
}
