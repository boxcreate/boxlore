package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.PlaybackListenValues
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * One record per listen through [PlaybackTelemetrySession], on a fake player and clock: pause,
 * resume, seek, finish, switching episode, the app closing, stalls, errors and the cut-off note.
 * Background work (show lookups, the local listening record) never runs here.
 */
class PlaybackTelemetrySessionListenTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    private var now = 5_000_000L
    private var positionMs = 0L
    private var isPlaying = true
    private var speed = 1f
    private var durationMs = 3_600_000L
    private val player = mock(Player::class.java)
    private val notes = FakeNoteStore()
    private lateinit var session: PlaybackTelemetrySession

    /** Runs nothing: the session's background work is out of scope here. */
    private object Idle : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    @BeforeEach
    fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(recorder)
        `when`(player.currentPosition).thenAnswer { positionMs }
        `when`(player.isPlaying).thenAnswer { isPlaying }
        `when`(player.playbackParameters).thenAnswer { PlaybackParameters(speed) }
        `when`(player.duration).thenAnswer { durationMs }
        `when`(player.mediaItemCount).thenReturn(3)
        session =
            PlaybackTelemetrySession(
                scope = CoroutineScope(Job() + Idle),
                mainDispatcher = Idle,
                database = mock(BoxLoreDatabase::class.java),
                podcastRepository = mock(PodcastRepository::class.java),
                subscriptionRepository = mock(SubscriptionRepository::class.java),
                rankingFeedbackRepository = mock(RankingFeedbackRepository::class.java),
                queueSkipMemory = mock(QueueSkipMemory::class.java),
                userPreferencesRepository = mock(UserPreferencesRepository::class.java),
                findPodcastIdForEpisode = { null },
                effectiveSkipEndingMs = { 0L },
                markCompletionTelemetryDispatched = { true },
                playerProvider = { player },
                removeCompletedDownload = {},
            ).apply {
                listens.clock = { now }
                listens.analyticsOutput = { PlaybackListenValues.OUTPUT_BLUETOOTH }
                listens.listenNotes = ListenNoteKeeper(notes)
                totalDurationMs = durationMs
            }
    }

    @AfterEach
    fun tearDown() {
        restore()
    }

    /** Plays for [seconds], one ticker sample a second. */
    private fun play(seconds: Int) {
        repeat(seconds) {
            now += 1_000L
            positionMs += (1_000L * speed).toLong()
            session.updateConsumedAudio(player)
        }
    }

    private fun startListen(episodeId: String = "e1") {
        isPlaying = true
        session.start(episodeId, currentItem = null)
        session.totalDurationMs = durationMs
    }

    private fun ended(): List<Map<String, Any>> = recorder.filter { it.first == "playback_session_ended" }.map { it.second }

    @Test
    fun `a ten minute listen that pauses sends one record with ten minutes heard`() {
        session.listens.notePlayRequested()
        now += 1_200L
        startListen()
        play(600)
        isPlaying = false
        session.end(reason = PlaybackListenValues.END_PAUSED)

        val record = ended().single()
        assertEquals(PlaybackListenValues.END_PAUSED, record["end_reason"])
        assertEquals(600f, record["heard_seconds"])
        assertEquals(600f, record["elapsed_seconds"])
        assertEquals(true, record["qualified"])
        assertEquals(false, record["completed"])
        assertEquals(1_200L, record["start_wait_ms"])
        assertEquals("user_voluntary", record["pause_reason"])
        assertEquals(PlaybackListenValues.OUTPUT_BLUETOOTH, record["output"])
        assertEquals(3, record["queue_size"])
    }

    @Test
    fun `a pause between samples still counts the last stretch, so a 60 second listen qualifies`() {
        startListen()
        play(59)
        now += 1_000L
        positionMs += 1_000L
        isPlaying = false
        session.end(reason = PlaybackListenValues.END_PAUSED)

        val record = ended().single()
        assertEquals(60f, record["heard_seconds"])
        assertEquals(true, record["qualified"])
    }

    @Test
    fun `an episode that hands over to the next one reports where it ended, not the next one's start`() {
        durationMs = 600_000L
        positionMs = 590_000L
        startListen("e1")
        play(10)
        session.onPositionJump(fromMs = 600_000L, toMs = 0L, sameEpisode = false)
        positionMs = 0L
        session.end(isTransition = true)

        assertEquals(600f, ended().single()["end_position_seconds"])
    }

    @Test
    fun `a listen that ends before the download lookup answers says unknown, and Cast always streams`() {
        startListen()
        play(5)
        session.end(reason = PlaybackListenValues.END_PAUSED)
        assertEquals(PlaybackListenValues.MODE_UNKNOWN, ended().last()["playback_mode"])

        startListen()
        session.listens.resolvedPlaybackMode(requireNotNull(session.listens.current).listenId, PlaybackListenValues.MODE_DOWNLOAD)
        session.listens.analyticsOutput = { PlaybackListenValues.OUTPUT_CAST }
        play(5)
        session.end(reason = PlaybackListenValues.END_PAUSED)
        assertEquals(PlaybackListenValues.MODE_STREAM, ended().last()["playback_mode"])
    }

    @Test
    fun `the wait before sound counts from the first request of a press, resumption included`() {
        session.listens.notePlayRequested()
        now += 3_000L
        session.listens.notePlayRequested()
        now += 500L
        startListen()
        session.end(reason = PlaybackListenValues.END_PAUSED)

        assertEquals(3_500L, ended().single()["start_wait_ms"])
    }

    @Test
    fun `a headset button stays the controller although the session hands it over as the notification`() {
        session.listens.noteMediaButton(PlaybackListenValues.CONTROL_OTHER)
        session.listens.noteController(PlaybackListenValues.CONTROL_NOTIFICATION, viaNotificationController = true)
        startListen("e1")
        session.end(reason = PlaybackListenValues.END_PAUSED)

        now += 60_000L
        session.listens.noteController(PlaybackListenValues.CONTROL_NOTIFICATION, viaNotificationController = true)
        startListen("e2")
        session.end(reason = PlaybackListenValues.END_PAUSED)

        assertEquals(listOf("other", "notification"), ended().map { it["controlled_by"] })
    }

    @Test
    fun `resuming starts a new listen with its own ID and no wait from the old request`() {
        startListen()
        play(30)
        session.end(reason = PlaybackListenValues.END_PAUSED)
        startListen()
        play(30)
        session.end(reason = PlaybackListenValues.END_PAUSED)

        val (first, second) = ended()
        assertNotEquals(first["listen_id"], second["listen_id"])
        assertEquals(false, first["qualified"], "30 seconds isn't a qualified listen")
        assertNull(second["start_wait_ms"])
    }

    @Test
    fun `seeks are counted, and the jump isn't heard`() {
        startListen()
        play(60)
        session.onPositionJump(fromMs = positionMs, toMs = positionMs + 120_000L, userSeek = true)
        positionMs += 120_000L
        play(60)
        session.onPositionJump(fromMs = positionMs, toMs = positionMs - 30_000L, userSeek = true)
        positionMs -= 30_000L
        session.onPositionJump(fromMs = positionMs, toMs = 0L)
        positionMs = 0L
        play(10)
        session.end(reason = PlaybackListenValues.END_PAUSED)

        val record = ended().single()
        assertEquals(130f, record["heard_seconds"])
        assertEquals(1, record["seeks_forward"])
        assertEquals(1, record["seeks_back"], "a jump that isn't the listener's seek isn't counted")
        assertEquals(120f, record["skipped_forward_seconds"])
        assertEquals(30f, record["skipped_back_seconds"])
    }

    @Test
    fun `reaching the end finishes the listen`() {
        durationMs = 600_000L
        positionMs = 540_000L
        startListen()
        play(60)
        session.end(reason = PlaybackListenValues.END_PAUSED)

        val record = ended().single()
        assertEquals(PlaybackListenValues.END_FINISHED, record["end_reason"])
        assertEquals(true, record["completed"])
        assertNull(record["pause_reason"])
        assertTrue(recorder.any { it.first == "playback_completed" })
    }

    @Test
    fun `switching episode ends the listen as switched, and the next one starts`() {
        startListen("e1")
        play(90)
        startListen("e2")
        play(10)
        session.end(isTransition = true)

        val (first, second) = ended()
        assertEquals("e1", first["episode_id"])
        assertEquals(PlaybackListenValues.END_SWITCHED, first["end_reason"])
        assertEquals("e2", second["episode_id"])
        assertEquals(PlaybackListenValues.END_SWITCHED, second["end_reason"])
    }

    @Test
    fun `the app closing ends the listen as app_closed and clears the note`() {
        startListen()
        assertEquals("e1", notes.note?.episodeId, "a listen leaves a note")
        play(45)
        assertEquals(30_000L, notes.note?.heardMs, "the note is refreshed every 30 seconds")

        session.end(reason = PlaybackListenValues.END_APP_CLOSED)

        assertEquals(PlaybackListenValues.END_APP_CLOSED, ended().single()["end_reason"])
        assertNull(notes.note)
    }

    @Test
    fun `a car-controlled listen says so, and the listen after it keeps the car`() {
        session.listens.noteController(PlaybackListenValues.CONTROL_CAR)
        startListen("e1")
        play(5)
        startListen("e2")
        play(5)
        session.end(reason = PlaybackListenValues.END_PAUSED)

        ended().forEach { record ->
            assertEquals(PlaybackListenValues.CONTROL_CAR, record["controlled_by"])
            assertEquals("car", record["client_surface"])
        }
    }

    @Test
    fun `only stalls of five seconds or more are sent alone, but every spell counts in the listen`() {
        startListen()
        session.onBufferingStarted()
        now += 6_000L
        session.onBufferingEnded()
        session.onBufferingStarted()
        now += 2_000L
        session.onBufferingEnded()
        session.end(reason = PlaybackListenValues.END_PAUSED)

        val stalls = recorder.filter { it.first == "playback_buffering" }
        assertEquals(listOf(6_000L), stalls.map { it.second["buffer_duration_ms"] })
        val record = ended().single()
        assertEquals(2, record["buffering_count"])
        assertEquals(8f, record["buffering_seconds"])
    }

    @Test
    fun `a playback error is sent once per cause until a listen starts`() {
        val network = PlaybackException("down", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        val decoder = PlaybackException("bad", null, PlaybackException.ERROR_CODE_DECODING_FAILED)
        startListen()
        repeat(5) { session.trackPlayerError(network) }
        session.trackPlayerError(decoder)
        session.end(reason = PlaybackListenValues.END_PAUSED)
        // Offline, trying A, B and A again before any sound: each episode's failure is sent once.
        var current = "e1"
        `when`(player.currentMediaItem).thenAnswer { MediaItem.Builder().setMediaId(current).build() }
        listOf("e1", "e2", "e1").forEach {
            current = it
            session.trackPlayerError(network)
        }
        current = "e1"
        startListen()
        session.trackPlayerError(network)

        val errors = recorder.filter { it.first == "playback_error" }.map { it.second["episode_id"] to it.second["error_code"] }
        val io = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
        assertEquals(listOf("e1" to io, "e1" to "ERROR_CODE_DECODING_FAILED", "e2" to io, "e1" to io), errors)
        assertEquals(4, recorder.count { it.first == "\$exception" }, "each one also goes to error tracking")
    }

    private class FakeNoteStore : ListenNoteStore {
        var note: ListenNote? = null

        override fun load(): ListenNote? = note

        override fun save(note: ListenNote) {
            this.note = note
        }

        override fun clear() {
            note = null
        }
    }
}
