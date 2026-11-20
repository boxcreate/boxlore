package cx.aswin.boxlore.core.playback

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Trim silence moves the playhead past each long pause. The service treats that as a jump, not a
 * seek ([PlaybackDiscontinuityPolicy]), so the listen neither counts the skipped pause as heard nor
 * as one of the listener's seeks.
 */
class PlaybackSilenceSkipTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit
    private var now = 5_000_000L
    private var positionMs = 0L
    private val player = mock(Player::class.java)
    private lateinit var session: PlaybackTelemetrySession

    private object NoNotes : ListenNoteStore {
        override fun load(): ListenNote? = null

        override fun save(note: ListenNote) = Unit

        override fun clear() = Unit
    }

    private object Idle : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    @BeforeEach
    fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(recorder)
        `when`(player.currentPosition).thenAnswer { positionMs }
        `when`(player.isPlaying).thenReturn(true)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters(1f))
        `when`(player.duration).thenReturn(3_600_000L)
        `when`(player.mediaItemCount).thenReturn(1)
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
                listens.listenNotes = ListenNoteKeeper(NoNotes)
                totalDurationMs = 3_600_000L
            }
    }

    @AfterEach
    fun tearDown() = restore()

    private fun play(seconds: Int) {
        repeat(seconds) {
            now += 1_000L
            positionMs += 1_000L
            session.updateConsumedAudio(player)
        }
    }

    /** What the service does with a discontinuity it isn't told is a seek. */
    private fun discontinuity(reason: Int, toMs: Long) {
        assertFalse(PlaybackDiscontinuityPolicy.isSeek(reason))
        session.onPositionJump(positionMs, toMs, sameEpisode = true)
        positionMs = toMs
    }

    @Test
    fun `only a seek counts as the listener's own, and silence skips aren't logged one by one`() {
        assertTrue(PlaybackDiscontinuityPolicy.isSeek(Player.DISCONTINUITY_REASON_SEEK))
        assertFalse(PlaybackDiscontinuityPolicy.isSeek(Player.DISCONTINUITY_REASON_SILENCE_SKIP))
        assertFalse(PlaybackDiscontinuityPolicy.isSeek(Player.DISCONTINUITY_REASON_AUTO_TRANSITION))
        assertFalse(PlaybackDiscontinuityPolicy.isWorthLogging(Player.DISCONTINUITY_REASON_SILENCE_SKIP))
        assertTrue(PlaybackDiscontinuityPolicy.isWorthLogging(Player.DISCONTINUITY_REASON_SEEK))
    }

    @Test
    fun `skipped silence is neither heard nor counted as a seek`() {
        session.start("e1", currentItem = null)
        session.totalDurationMs = 3_600_000L
        play(30)
        discontinuity(Player.DISCONTINUITY_REASON_SILENCE_SKIP, toMs = positionMs + 4_000L)
        play(30)
        discontinuity(Player.DISCONTINUITY_REASON_SILENCE_SKIP, toMs = positionMs + 6_000L)
        play(10)
        session.end(reason = PlaybackListenValues.END_PAUSED)

        val record = recorder.filter { it.first == "playback_session_ended" }.map { it.second }.single()
        assertEquals(70f, record["heard_seconds"], "only the audio actually played counts as heard")
        assertEquals(0, record["seeks_forward"])
        assertEquals(0, record["seeks_back"])
        assertEquals(0f, record["skipped_forward_seconds"])
    }
}
