package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.episodeListenerStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The idle mini-player's cloud handoff (`PlaybackRepository.updateIdlePlaybackSession` decides
 * through [IdleSessionHandoff.shouldHandOver]): a newer cloud session never replaces the player's
 * episode with the same episode under another ID.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdleSessionHandoffTest {
    private lateinit var database: BoxLoreDatabase

    @Before
    fun setUp() {
        database = EpisodeAliasFixtures.directDatabase(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theSameEpisodeUnderAnyOfItsIdsIsNotHandedOver() = runTest {
        EpisodeAliasFixtures.twoIds(database)
        val store = database.episodeListenerStore()

        // Cloud sync hands over the owner "A" while the player holds "B".
        assertTrue(IdleSessionHandoff.isSameEpisode(currentId = "B", pulledId = "A", listenerStore = store))
        assertTrue(IdleSessionHandoff.isSameEpisode(currentId = "A", pulledId = "A", listenerStore = store))
    }

    @Test
    fun anotherEpisodeOrAnEmptyPlayerIsHandedOver() = runTest {
        EpisodeAliasFixtures.twoIds(database)
        val store = database.episodeListenerStore()

        assertFalse(IdleSessionHandoff.isSameEpisode(currentId = "C", pulledId = "A", listenerStore = store))
        assertFalse(IdleSessionHandoff.isSameEpisode(currentId = null, pulledId = "A", listenerStore = store))
        // An episode stored under one ID compares by that ID, as before.
        assertFalse(IdleSessionHandoff.isSameEpisode(currentId = "x", pulledId = "y", listenerStore = store))
    }

    @Test
    fun aNewerSessionOfTheSameEpisodeUnderItsOwnerIdLeavesTheIdlePlayerAsItIs() = runTest {
        EpisodeAliasFixtures.twoIds(database)
        database.listeningHistoryDao().upsert(EpisodeAliasFixtures.history("B", progress = 5_000L, playedAt = 100L))
        val store = database.episodeListenerStore()

        // The player holds B; cloud sync hands over the owner A, played later on another device.
        assertFalse(IdleSessionHandoff.shouldHandOver(currentId = "B", pulledId = "A", pulledLastPlayedAt = 900L, listenerStore = store))
        assertFalse(IdleSessionHandoff.shouldHandOver(currentId = "A", pulledId = "B", pulledLastPlayedAt = 900L, listenerStore = store))
    }

    @Test
    fun anotherEpisodeTakesTheIdlePlayerOnlyWhenItsSessionIsNewer() = runTest {
        EpisodeAliasFixtures.twoIds(database)
        database.listeningHistoryDao().upsert(EpisodeAliasFixtures.history("B", progress = 5_000L, playedAt = 100L))
        val store = database.episodeListenerStore()

        assertTrue(IdleSessionHandoff.shouldHandOver(currentId = "B", pulledId = "C", pulledLastPlayedAt = 101L, listenerStore = store))
        assertFalse(IdleSessionHandoff.shouldHandOver(currentId = "B", pulledId = "C", pulledLastPlayedAt = 100L, listenerStore = store))
        // The player's episode is compared by its projected last play, under any of its IDs.
        assertFalse(IdleSessionHandoff.shouldHandOver(currentId = "A", pulledId = "C", pulledLastPlayedAt = 99L, listenerStore = store))
        assertTrue(IdleSessionHandoff.shouldHandOver(currentId = null, pulledId = "C", pulledLastPlayedAt = 1L, listenerStore = store))
    }
}
