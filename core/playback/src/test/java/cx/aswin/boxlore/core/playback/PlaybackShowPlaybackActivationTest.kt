package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeIdentityDao
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The guarded activation job reports the playing show's own speed and trim silence: the show's row
 * when it has one, nothing of its own when it has none, nothing for a briefing, and never a late
 * result for an episode that is no longer playing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackShowPlaybackActivationTest {
    private val reported = mutableListOf<ShowPlaybackOverrides?>()
    private val shows = mutableMapOf<String, PodcastEntity>()
    private val lookups = mutableMapOf<String, CompletableDeferred<String?>>()

    @Before fun clearBefore() = PlaybackActivationRequest.clear()

    @After fun clearAfter() = PlaybackActivationRequest.clear()

    private fun show(id: String, speed: Float? = null, trimSilence: Boolean? = null) = PodcastEntity(
        podcastId = id,
        title = "Show $id",
        author = "",
        imageUrl = "",
        description = null,
        playbackSpeedOverride = speed,
        skipSilenceOverride = trimSilence,
    )

    private fun TestScope.controller(): Pair<PlaybackIntroOutroController, Player> {
        val db = mock(BoxLoreDatabase::class.java)
        `when`(db.listeningHistoryDao()).thenReturn(mock(ListeningHistoryDao::class.java))
        `when`(db.episodeIdentityDao()).thenReturn(mock(EpisodeIdentityDao::class.java))
        val podcasts = object : PodcastDao by mock(PodcastDao::class.java) {
            override suspend fun getPodcast(id: String): PodcastEntity? = shows[id]
        }
        `when`(db.podcastDao()).thenReturn(podcasts)
        val player = mock(Player::class.java)
        `when`(player.duration).thenReturn(100_000L)
        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        val controller = PlaybackIntroOutroController(
            scope = this, database = db,
            globalSkipBeginningMs = { 0L }, globalSkipEndingMs = { 0L },
            staleRestartEnabled = { true }, lifecycleEpisodeId = { it?.mediaId },
            findPodcastIdForEpisode = { episodeId -> lookups[episodeId]?.await() ?: "show-of-$episodeId" },
            onActiveDurationResolved = { _, _ -> },
            onNaturalCompletion = { _, _ -> null },
            onClearEndOfEpisodeSleep = {},
            onShowPlaybackResolved = { reported += it },
        )
        return controller to player
    }

    private fun activate(controller: PlaybackIntroOutroController, player: Player, episodeId: String) {
        val item = MediaItem.Builder().setMediaId(episodeId).build()
        `when`(player.currentMediaItem).thenReturn(item)
        controller.onMediaActivated(player, item)
    }

    @Test fun `a show's own speed and trim silence arrive with its activation`() = runTest {
        shows["show-of-e1"] = show("show-of-e1", speed = 1.5f, trimSilence = true)
        val (controller, player) = controller()

        activate(controller, player, "e1")
        advanceUntilIdle()

        assertEquals(listOf(ShowPlaybackOverrides("show-of-e1", speed = 1.5f, trimSilence = true)), reported)
    }

    @Test fun `a show without its own settings, or without a row, reports none so the app's apply`() = runTest {
        shows["show-of-e1"] = show("show-of-e1")
        val (controller, player) = controller()

        activate(controller, player, "e1")
        advanceUntilIdle()
        activate(controller, player, "e2")
        advanceUntilIdle()

        assertEquals(listOf(ShowPlaybackOverrides("show-of-e1"), ShowPlaybackOverrides("show-of-e2")), reported)
    }

    @Test fun `a briefing never reads a show's settings`() = runTest {
        shows["show-of-briefing_1"] = show("show-of-briefing_1", speed = 2f, trimSilence = true)
        val (controller, player) = controller()

        activate(controller, player, "briefing_1")
        advanceUntilIdle()

        assertEquals(listOf<ShowPlaybackOverrides?>(null), reported)
    }

    @Test fun `a late lookup for an earlier episode never applies`() = runTest {
        shows["slow-show"] = show("slow-show", speed = 3f)
        shows["show-of-e2"] = show("show-of-e2", speed = 0.8f)
        val slow = CompletableDeferred<String?>()
        lookups["e1"] = slow
        val (controller, player) = controller()

        activate(controller, player, "e1")
        runCurrent()
        activate(controller, player, "e2")
        advanceUntilIdle()
        slow.complete("slow-show")
        advanceUntilIdle()

        assertEquals(listOf(ShowPlaybackOverrides("show-of-e2", speed = 0.8f)), reported)
    }

    @Test fun `a preference change re-resolves the playing show for the same activation`() = runTest {
        shows["show-of-e1"] = show("show-of-e1", speed = 1.25f)
        val (controller, player) = controller()
        activate(controller, player, "e1")
        advanceUntilIdle()

        shows["show-of-e1"] = show("show-of-e1", speed = 2f, trimSilence = false)
        controller.onSkipPreferencesChanged(player)
        advanceUntilIdle()

        assertEquals(
            listOf(
                ShowPlaybackOverrides("show-of-e1", speed = 1.25f),
                ShowPlaybackOverrides("show-of-e1", speed = 2f, trimSilence = false),
            ),
            reported,
        )
    }
}
