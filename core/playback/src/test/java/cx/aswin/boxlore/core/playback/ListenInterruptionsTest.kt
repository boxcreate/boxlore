package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The cut-off-listen note: how often it's written, and what the next launch reports from it. */
class ListenInterruptionsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit
    private val store = MemoryStore()

    private fun note(heardMs: Long, inBackground: Boolean = true,) =
        ListenNote(
            listenId = "l1",
            episodeId = "e1",
            podcastId = "p1",
            entryPoint = "queue",
            playbackMode = "download",
            controlledBy = "notification",
            heardMs = heardMs,
            elapsedMs = heardMs + 2_000L,
            inBackground = inBackground,
        )

    @BeforeEach
    fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
    }

    @Test
    fun `the note is written at the start, then at most every 30 seconds, and cleared at the end`() {
        val keeper = ListenNoteKeeper(store)
        keeper.started(nowMs = 0L, note = note(0L))
        assertEquals(1, store.saves)

        var built = 0
        keeper.progressed(nowMs = 29_999L) {
            built++
            note(29_000L)
        }
        assertEquals(0, built, "too soon: the note isn't even built")
        keeper.progressed(nowMs = 30_000L) {
            built++
            note(30_000L)
        }
        assertEquals(2, store.saves)
        assertEquals(30_000L, store.note?.heardMs)

        keeper.ended()
        assertNull(store.note)
        keeper.progressed(nowMs = 90_000L) { note(90_000L) }
        assertNull(store.note, "no listen, no note")
    }

    @Test
    fun `a note the last run left is reported once as playback_interrupted`() {
        store.save(note(heardMs = 125_000L))

        ListenInterruptions.reportLeftover(store)
        ListenInterruptions.reportLeftover(store)

        val event = recorder.single { it.first == "playback_interrupted" }.second
        assertEquals("l1", event["listen_id"])
        assertEquals("e1", event["episode_id"])
        assertEquals(125f, event["heard_seconds"])
        assertEquals(127f, event["elapsed_seconds"])
        assertEquals(true, event["in_background"])
        assertEquals("download", event["playback_mode"])
        assertEquals("notification", event["controlled_by"])
        assertNull(store.note)
    }

    @Test
    fun `no note means nothing to report`() {
        ListenInterruptions.reportLeftover(store)

        assertTrue(recorder.isEmpty())
    }

    private class MemoryStore : ListenNoteStore {
        var note: ListenNote? = null
        var saves = 0

        override fun load(): ListenNote? = note

        override fun save(note: ListenNote) {
            saves++
            this.note = note
        }

        override fun clear() {
            note = null
        }
    }
}
