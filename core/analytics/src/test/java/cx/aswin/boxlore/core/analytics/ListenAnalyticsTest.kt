package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Shared listen fixtures for analytics tests. */
internal object ListenAnalyticsFixtures {
    fun summary(
        heardMs: Long = 600_000L,
        controlledBy: String = PlaybackListenValues.CONTROL_APP,
        endReason: String = PlaybackListenValues.END_PAUSED,
        entryPointContext: Map<String, Any>? = null,
    ) = PlaybackListenSummary(
        listenId = "l1",
        episodeId = "e1",
        podcastId = "p1",
        podcastName = "Pod",
        podcastGenre = "News",
        episodeTitle = "Ep",
        entryPoint = "queue",
        entryPointContext = entryPointContext,
        endReason = endReason,
        pauseReason = "user_voluntary",
        heardMs = heardMs,
        elapsedMs = 640_000L,
        startPositionMs = 60_000L,
        endPositionMs = 660_000L,
        durationMs = 3_600_000L,
        completed = false,
        previouslyFinished = true,
        playbackMode = PlaybackListenValues.MODE_DOWNLOAD,
        speed = 1.5f,
        controlledBy = controlledBy,
        output = PlaybackListenValues.OUTPUT_WIRED,
        seeks = ListenSeeks(forward = 2, back = 1, forwardMs = 90_000L, backMs = 15_000L),
        startWaitMs = 900L,
        bufferingCount = 3,
        bufferingMs = 4_500L,
        queueSize = 4,
    )

    fun interrupted() =
        PlaybackInterruptedListen(
            listenId = "l2",
            episodeId = "e2",
            podcastId = null,
            entryPoint = null,
            playbackMode = PlaybackListenValues.MODE_STREAM,
            controlledBy = PlaybackListenValues.CONTROL_CAR,
            heardMs = 300_000L,
            elapsedMs = 310_000L,
            inBackground = true,
        )
}

/** `playback_session_ended`, `playback_interrupted`, and what a listen adds to start and error events. */
class ListenAnalyticsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    private fun props(event: String) = recorder.single { it.first == event }.second

    @BeforeEach
    fun setUp() {
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
    }

    @Test
    fun `the listen record carries minutes, settings, route, seeks and buffering`() {
        AnalyticsHelper.trackPlaybackSessionEnded(ListenAnalyticsFixtures.summary())

        val props = props("playback_session_ended")
        val expected =
            mapOf(
                "listen_id" to "l1",
                "episode_id" to "e1",
                "entry_point" to "queue",
                "end_reason" to "paused",
                "pause_reason" to "user_voluntary",
                "heard_seconds" to 600f,
                "elapsed_seconds" to 640f,
                "qualified" to true,
                "completed" to false,
                "previously_finished" to true,
                "playback_mode" to "download",
                "client_surface" to "phone",
                "controlled_by" to "app",
                "output" to "wired",
                "speed" to 1.5f,
                "seeks_forward" to 2,
                "seeks_back" to 1,
                "skipped_forward_seconds" to 90f,
                "skipped_back_seconds" to 15f,
                "buffering_count" to 3,
                "buffering_seconds" to 4.5f,
                "start_wait_ms" to 900L,
                "start_position_seconds" to 60f,
                "end_position_seconds" to 660f,
                "duration_seconds" to 3_600f,
                "queue_size" to 4,
                "podcast_id" to "p1",
            )
        expected.forEach { (key, value) -> assertEquals(value, props[key], key) }
    }

    @Test
    fun `a listen counts as qualified from 60 seconds heard`() {
        AnalyticsHelper.trackPlaybackSessionEnded(ListenAnalyticsFixtures.summary(heardMs = 59_999L))
        AnalyticsHelper.trackPlaybackSessionEnded(ListenAnalyticsFixtures.summary(heardMs = 60_000L))

        val qualified = recorder.filter { it.first == "playback_session_ended" }.map { it.second["qualified"] }
        assertEquals(listOf(false, true), qualified)
    }

    @Test
    fun `the car is a client surface only when it controls the listen, and context can't overwrite the record`() {
        AnalyticsHelper.trackPlaybackSessionEnded(
            ListenAnalyticsFixtures.summary(
                controlledBy = PlaybackListenValues.CONTROL_CAR,
                entryPointContext = mapOf("end_reason" to "spoofed", "section" to "continue"),
            ),
        )

        val props = props("playback_session_ended")
        assertEquals("car", props["client_surface"])
        assertEquals("paused", props["end_reason"])
        assertEquals("continue", props["section"])
    }

    @Test
    fun `a listen cut off by Android reports how long it played and whether it was in the background`() {
        AnalyticsHelper.trackPlaybackInterrupted(ListenAnalyticsFixtures.interrupted())

        val props = props("playback_interrupted")
        assertEquals("l2", props["listen_id"])
        assertEquals(300f, props["heard_seconds"])
        assertEquals(310f, props["elapsed_seconds"])
        assertEquals(true, props["in_background"])
        assertEquals("car", props["client_surface"])
        assertEquals("unknown", props["entry_point"])
        assertFalse("podcast_id" in props)
    }

    @Test
    fun `play starts get the listen ID and a correct already-finished field, keeping is_resume`() {
        AnalyticsHelper.trackPlaybackStarted(
            podcastId = "p1",
            podcastName = "Pod",
            podcastGenre = "News",
            episodeId = "e1",
            episodeTitle = "Ep",
            startPositionSeconds = 0f,
            totalDurationSeconds = 100f,
            isRepeating = true,
            isSubscribed = false,
            playbackMode = PlaybackListenValues.MODE_DOWNLOAD,
            speed = 2f,
            listen = ListenStart(listenId = "l1", controlledBy = PlaybackListenValues.CONTROL_NOTIFICATION),
        )

        val props = props("playback_started")
        assertEquals("l1", props["listen_id"])
        assertEquals(true, props["previously_finished"])
        assertEquals(true, props["is_resume"])
        assertEquals("notification", props["controlled_by"])
        assertEquals("download", props["playback_mode"])
        assertEquals(2f, props["speed"])
        assertEquals("phone", props["client_surface"])
    }

    @Test
    fun `a playback error with its cause also goes to error tracking`() {
        AnalyticsHelper.trackPlaybackError("ERROR_CODE_IO", "down", "p1", "e1", listenId = "l1", cause = IllegalStateException("down"))
        AnalyticsHelper.trackPlaybackError("ERROR_CODE_IO", "down", "p1", "e1")

        assertEquals(2, recorder.count { it.first == "playback_error" })
        val tracked = recorder.single { it.first == AnalyticsEmit.EXCEPTION_EVENT }.second
        assertEquals("l1", tracked["listen_id"])
        assertEquals("ERROR_CODE_IO", tracked["error_code"])
        assertNull(recorder.first { it.first == "playback_error" }.second["cause"])
    }
}
