package cx.aswin.boxlore.wear.cloud

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.wearlink.PhoneLibrary
import cx.aswin.boxlore.core.wearlink.WearLinkShow
import cx.aswin.boxlore.wear.data.WatchLibraryState
import cx.aswin.boxlore.wear.link.WatchLinkRules
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchCloudRulesTest {
    private val minute = 60_000L

    private fun show(id: String) = WatchShow(id = id, title = "Show $id", publisher = "Pub", artworkUrl = null, subscribed = false)

    private fun episode(id: String) =
        WatchEpisode(id = id, showId = "1", title = "Episode $id", showTitle = "Show 1", artworkUrl = null, durationMinutes = 40, audioUrl = "https://example.org/$id.mp3")

    private fun follow(id: String, at: Long, subscribed: Boolean = true) =
        UserSubscriptionSyncDto(podcastId = id, isSubscribed = subscribed, subscribedAt = at, updatedAt = at)

    private fun row(id: String, at: Long, progressMs: Long = 10 * minute, completed: Boolean = false) =
        ListeningHistorySyncDto(
            episodeId = id,
            podcastId = "1",
            episodeTitle = "Episode $id",
            podcastName = "Show 1",
            episodeAudioUrl = "https://example.org/$id.mp3",
            progressMs = progressMs,
            durationMs = 40 * minute,
            isCompleted = completed,
            lastPlayedAt = at,
            updatedAt = at,
        )

    private val signedIn = WatchLibraryState(watchId = "w", cloudLinked = true)

    @Test
    fun `a signed-in watch sends its follows and listening up, and nothing when nothing waits`() {
        assertNull(WatchCloudRules.push(signedIn, now = 9L))

        val state = signedIn.subscribe(show("7"), nowMs = 100L).progress(episode("a"), 10 * minute, 40 * minute, nowMs = 200L)
        val push = WatchCloudRules.push(state, now = 9L)!!

        assertEquals(listOf("7"), push.request.subscriptions.map { it.podcastId })
        assertEquals(listOf("a"), push.request.history.map { it.episodeId })
        assertEquals(10 * minute, push.request.history.single().progressMs)
        assertEquals(9L, push.request.clientTimestamp)
        assertEquals(state.revision, push.covered)
    }

    @Test
    fun `what the cloud took is not sent again, and a later change is`() {
        val first = signedIn.progress(episode("a"), 10 * minute, 40 * minute, nowMs = 200L)
        val taken = WatchCloudRules.pushed(first, WatchCloudRules.push(first, now = 1L)!!.covered)

        assertNull(WatchCloudRules.push(taken, now = 2L))
        assertTrue(taken.pendingEpisodes.isEmpty())

        val later = taken.progress(episode("b"), 5 * minute, 40 * minute, nowMs = 300L)
        assertEquals(listOf("b"), WatchCloudRules.push(later, now = 3L)!!.request.history.map { it.episodeId })
    }

    @Test
    fun `with more listening than one push carries, the push covers only what it sent`() {
        var state = signedIn
        listOf("a", "b", "c").forEachIndexed { index, id -> state = state.progress(episode(id), minute, 40 * minute, nowMs = 100L + index) }

        val push = WatchCloudRules.push(state, now = 1L, maxHistory = 2)!!
        assertEquals(listOf("a", "b"), push.request.history.map { it.episodeId })

        val rest = WatchCloudRules.push(WatchCloudRules.pushed(state, push.covered), now = 2L, maxHistory = 2)!!
        assertEquals(listOf("c"), rest.request.history.map { it.episodeId })
    }

    @Test
    fun `a change waits for both the phone and the cloud once both are linked`() {
        val state = signedIn.copy(phoneLinked = true).progress(episode("a"), 10 * minute, 40 * minute, nowMs = 200L)

        // The cloud has it: it still goes to the phone, and is not sent to the cloud twice.
        val cloudHasIt = WatchCloudRules.pushed(state, state.revision)
        assertEquals(setOf("a"), cloudHasIt.pendingEpisodes.keys)
        assertNull(WatchCloudRules.push(cloudHasIt, now = 1L))
        assertEquals(listOf("a"), WatchLinkRules.changes(cloudHasIt).history.map { it.row.episodeId })

        // The phone has it too: nothing waits any more.
        val bothHaveIt = WatchLinkRules.withPhone(cloudHasIt, PhoneLibrary(acked = mapOf("w" to state.revision)), playingId = null)
        assertTrue(bothHaveIt.pendingEpisodes.isEmpty())
    }

    @Test
    fun `what the phone confirmed first still goes up to the cloud, and not back to the phone`() {
        val state = signedIn.copy(phoneLinked = true).progress(episode("a"), 10 * minute, 40 * minute, nowMs = 200L)
        val phoneHasIt = WatchLinkRules.withPhone(state, PhoneLibrary(acked = mapOf("w" to state.revision)), playingId = null)

        assertTrue(WatchLinkRules.changes(phoneHasIt).history.isEmpty())
        assertEquals(listOf("a"), WatchCloudRules.push(phoneHasIt, now = 1L)!!.request.history.map { it.episodeId })
    }

    @Test
    fun `a pull adds the account's follows, removes its unfollows and remembers the cloud's time`() {
        val state = signedIn.copy(shows = listOf(show("1").copy(subscribed = true), show("2").copy(subscribed = true)))
        val pull = SyncPullResponse(subscriptions = listOf(follow("3", at = 50L), follow("2", at = 60L, subscribed = false)), syncedAt = 777L)

        assertEquals(listOf("3"), WatchCloudRules.unknownShows(state, pull))
        val next = WatchCloudRules.withCloud(state, pull, shows = mapOf("3" to show("3")), playingId = null)

        assertEquals(listOf("3", "1"), next.shows.map { it.id })
        assertTrue(next.shows.all { it.subscribed })
        assertEquals(777L, next.cloudSince)
    }

    @Test
    fun `a followed show that could not be looked up is asked for again with the next pull`() {
        val pull = SyncPullResponse(subscriptions = listOf(follow("3", at = 50L)), syncedAt = 777L)

        val next = WatchCloudRules.withCloud(signedIn.copy(cloudSince = 5L), pull, shows = emptyMap(), playingId = null)

        assertTrue(next.shows.isEmpty())
        assertEquals(5L, next.cloudSince)
    }

    @Test
    fun `a follow or unfollow made here after the cloud's row stands until it has gone up`() {
        val state = signedIn.copy(shows = listOf(show("1").copy(subscribed = true))).unsubscribe("1", nowMs = 900L)
        val pull = SyncPullResponse(subscriptions = listOf(follow("1", at = 100L)), syncedAt = 1_000L)

        assertTrue(WatchCloudRules.unknownShows(state, pull).isEmpty())
        assertFalse(WatchCloudRules.withCloud(state, pull, shows = emptyMap(), playingId = null).isSubscribed("1"))
    }

    @Test
    fun `listening from the account moves an episode on, except the one playing here`() {
        val state = signedIn.progress(episode("a"), 5 * minute, 40 * minute, nowMs = 100L).progress(episode("b"), 5 * minute, 40 * minute, nowMs = 100L)
        val pull = SyncPullResponse(history = listOf(row("a", at = 500L, progressMs = 20 * minute), row("b", at = 500L, progressMs = 20 * minute)), syncedAt = 1L)

        val next = WatchCloudRules.withCloud(state, pull, shows = emptyMap(), playingId = "b")

        assertEquals(20 * minute, next.positions["a"])
        assertEquals(5 * minute, next.positions["b"])
    }

    @Test
    fun `a first pull keeps only the most recent listening of a long history`() {
        val history = (1..WatchCloudRules.MAX_PULLED_HISTORY + 50).map { row("e$it", at = it.toLong(), completed = true) }

        val next = WatchCloudRules.withCloud(signedIn, SyncPullResponse(history = history, syncedAt = 1L), shows = emptyMap(), playingId = null)

        assertEquals(WatchCloudRules.MAX_PULLED_HISTORY, next.played.size)
        assertTrue("e${WatchCloudRules.MAX_PULLED_HISTORY + 50}" in next.played)
        assertFalse("e1" in next.played)
    }

    // Regression: a follow the phone had confirmed, still waiting for the cloud, came back on the
    // watch after the listener unfollowed the show on the phone.
    @Test
    fun `a follow the phone confirmed is not put back over the phone's later unfollow`() {
        val followed = signedIn.copy(phoneLinked = true).subscribe(show("9"), nowMs = 100L)
        val phoneHasIt = WatchLinkRules.withPhone(followed, PhoneLibrary(shows = listOf(WearLinkShow("9", "Show 9", "Pub")), acked = mapOf("w" to followed.revision)), playingId = null)

        // Still waiting for the cloud, which is out of reach.
        assertEquals(setOf("9"), phoneHasIt.pendingShows.keys)

        val unfollowedOnPhone = WatchLinkRules.withPhone(phoneHasIt, PhoneLibrary(shows = emptyList(), acked = mapOf("w" to followed.revision)), playingId = null)

        assertFalse(unfollowedOnPhone.isSubscribed("9"))
        // The follow still goes up, as the listener made it; the cloud settles it by time.
        assertEquals(listOf("9"), WatchCloudRules.push(unfollowedOnPhone, now = 1L)!!.request.subscriptions.map { it.podcastId })
    }
}
