package cx.aswin.boxlore.tv.cloud

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.tv.data.TvLibraryState
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvCloudRulesTest {
    private val minute = 60_000L

    private fun show(id: String) = TvShow(id, "Show $id", "", null)

    private fun episode(id: String, showId: String = "1") = TvEpisode(id, showId, "Episode $id", "Show $showId", null, 60, audioUrl = "https://example.com/$id.mp3")

    private fun pull(subscriptions: List<UserSubscriptionSyncDto> = emptyList(), history: List<ListeningHistorySyncDto> = emptyList()) =
        SyncPullResponse(subscriptions = subscriptions, history = history, syncedAt = 500L)

    private fun follow(id: String, at: Long, subscribed: Boolean = true) = UserSubscriptionSyncDto(podcastId = id, isSubscribed = subscribed, subscribedAt = at, updatedAt = at)

    @Test
    fun `a TV signed out notes nothing for the cloud, and signing in sends everything listened to here`() {
        val before = TvLibraryState().subscribe(show("1"), 10).progress(episode("a"), 5 * minute, 60 * minute, nowMs = 20).setLiked(episode("b"), true, 30)
        assertTrue(before.pendingEpisodes.isEmpty() && before.pendingShows.isEmpty())

        val linked = TvCloudRules.linked(before)
        val push = TvCloudRules.push(linked, now = 40)!!
        assertEquals(setOf("a", "b"), push.request.history.map { it.episodeId }.toSet())
        // Follows wait for the first pull, so the account's own follows and their settings are never touched.
        assertTrue(push.request.subscriptions.isEmpty())
        val row = push.request.history.single { it.episodeId == "b" }
        assertTrue(row.isLiked)
        assertEquals(30L, row.likedAt)
        assertEquals(0L, row.lastPlayedAt)
    }

    @Test
    fun `the first pull adopts the account's follows and sends up only shows the account never heard of`() {
        val tv = TvCloudRules.linked(TvLibraryState().subscribe(show("here-only"), 10).subscribe(show("both"), 11).subscribe(show("dropped"), 12))
        val pulled =
            TvCloudRules.withCloud(
                tv,
                pull(listOf(follow("both", 5), follow("phone", 6), follow("dropped", 7, subscribed = false))),
                shows = mapOf("phone" to show("phone")),
                playingId = null,
                now = 100,
            )
        assertEquals(setOf("here-only", "both", "phone"), pulled.shows.map { it.id }.toSet())
        assertEquals(500L, pulled.cloudSince)
        val sent = TvCloudRules.push(pulled, now = 101)!!.request.subscriptions
        assertEquals(listOf("here-only"), sent.map { it.podcastId })
        assertFalse(sent.single().notificationsEnabled)
        // A later pull no longer sends anything up by itself.
        val again = TvCloudRules.withCloud(TvCloudRules.pushed(pulled, pulled.revision), pull(), emptyMap(), null, now = 200)
        assertNull(TvCloudRules.push(again, now = 201))
    }

    @Test
    fun `a pulled row wins only where it is newer, play and like each on their own time`() {
        val tv =
            TvCloudRules.linked(TvLibraryState())
                .progress(episode("a"), 10 * minute, 60 * minute, nowMs = 1_000)
                .setLiked(episode("a"), true, nowMs = 3_000)
        val older = ListeningHistorySyncDto(episodeId = "a", podcastId = "1", progressMs = 50 * minute, durationMs = 60 * minute, lastPlayedAt = 900, isLiked = false, likedAt = 4_000, updatedAt = 4_000)
        val merged = TvCloudRules.withRow(tv, older, playingId = null)
        // The TV played later, so its place stands; the phone unliked later, so the like goes.
        assertEquals(10 * minute, merged.positions["a"])
        assertFalse("a" in merged.liked)

        val newer = older.copy(lastPlayedAt = 2_000, isCompleted = true)
        assertTrue("a" in TvCloudRules.withRow(tv, newer, playingId = null).played)
        // Never over the episode playing on the TV right now.
        assertFalse("a" in TvCloudRules.withRow(tv, newer, playingId = "a").played)

        // Marked unplayed elsewhere, later: the TV's finish and place go too.
        val finished = TvCloudRules.withRow(tv, newer, playingId = null)
        val reset = TvCloudRules.withRow(finished, older.copy(progressMs = 0, lastPlayedAt = 5_000), playingId = null)
        assertFalse("a" in reset.played)
        assertNull(reset.positions["a"])
        assertEquals(5_000L, reset.playedAt["a"])
    }

    @Test
    fun `episodes listened to elsewhere arrive with enough to play them`() {
        val row =
            ListeningHistorySyncDto(
                episodeId = "x", podcastId = "9", episodeTitle = "From the phone", podcastName = "Show 9", episodeAudioUrl = "https://example.com/x.mp3",
                progressMs = 6 * minute, durationMs = 30 * minute, lastPlayedAt = 50, updatedAt = 50, recipeId = null,
            )
        val tv = TvCloudRules.withCloud(TvCloudRules.linked(TvLibraryState()), pull(history = listOf(row)), emptyMap(), null, now = 60)
        val resumed = tv.jumpBackIn.single()
        assertEquals("From the phone", resumed.title)
        assertEquals("https://example.com/x.mp3", resumed.audioUrl)
        assertEquals(24, resumed.minutesLeft)
        // What came from the cloud is not sent back to it.
        assertTrue(tv.pendingEpisodes.isEmpty())
    }

    @Test
    fun `what the cloud confirmed stops waiting, and signing out keeps the library`() {
        val tv = TvCloudRules.linked(TvLibraryState()).subscribe(show("1"), 5).markPlayed(episode("a"), 6)
        val push = TvCloudRules.push(tv, now = 7)!!
        val confirmed = TvCloudRules.pushed(tv, push.covered)
        assertTrue(confirmed.pendingEpisodes.isEmpty() && confirmed.pendingShows.isEmpty())
        assertNull(TvCloudRules.push(confirmed, now = 8))

        val out = TvCloudRules.unlinked(tv)
        assertFalse(out.cloudLinked)
        assertEquals(listOf("1"), out.shows.map { it.id })
        assertTrue(out.pendingEpisodes.isEmpty())
    }
}
