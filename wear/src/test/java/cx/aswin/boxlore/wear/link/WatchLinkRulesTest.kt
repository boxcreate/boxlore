package cx.aswin.boxlore.wear.link

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.wearlink.PhoneLibrary
import cx.aswin.boxlore.core.wearlink.WearLinkShow
import cx.aswin.boxlore.wear.data.WatchLibraryState
import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchLinkRulesTest {
    private val minute = 60_000L

    private fun show(id: String) = WatchShow(id = id, title = "Show $id", publisher = "Pub", artworkUrl = null, subscribed = false)

    private fun phoneShow(id: String) = WearLinkShow(podcastId = id, title = "Show $id", author = "Pub")

    private fun episode(id: String, showId: String = "1") =
        WatchEpisode(
            id = id,
            showId = showId,
            title = "Episode $id",
            showTitle = "Show $showId",
            artworkUrl = null,
            durationMinutes = 40,
            audioUrl = "https://example.org/$id.mp3",
        )

    private fun row(
        id: String,
        progressMs: Long = 10 * minute,
        at: Long = 1_000L,
        completed: Boolean = false,
        recipeId: Long? = null,
        audio: String? = "https://example.org/$id.mp3",
    ) = ListeningHistorySyncDto(
        episodeId = id,
        podcastId = "1",
        episodeTitle = "Episode $id",
        podcastName = "Show 1",
        episodeAudioUrl = audio,
        progressMs = progressMs,
        durationMs = 40 * minute,
        isCompleted = completed,
        lastPlayedAt = at,
        updatedAt = at,
        recipeId = recipeId,
    )

    private fun WatchLibraryState.heard(library: PhoneLibrary, playing: String? = null) = WatchLinkRules.withPhone(this, library, playing)

    @Test
    fun `a follow made on the watch waits for the phone and is sent with the show`() {
        val state = WatchLibraryState().subscribe(show("7"), nowMs = 500L)

        val changes = WatchLinkRules.changes(state)

        assertEquals(1L, changes.revision)
        val sent = changes.subscriptions.single()
        assertEquals("7", sent.change.podcastId)
        assertTrue(sent.change.isSubscribed)
        assertEquals(500L, sent.change.subscribedAt)
        assertEquals("Show 7", sent.show?.title)
    }

    @Test
    fun `an unfollow replaces the follow that was still waiting`() {
        val state = WatchLibraryState().subscribe(show("7"), 500L).unsubscribe("7", 900L)

        val sent = WatchLinkRules.changes(state).subscriptions.single()

        assertFalse(sent.change.isSubscribed)
        assertEquals(900L, sent.change.unsubscribedAt)
        assertEquals(2L, sent.revision)
    }

    @Test
    fun `listening on the watch is sent as one row per episode with its newest place`() {
        val state =
            WatchLibraryState()
                .progress(episode("a"), 5 * minute, 40 * minute, nowMs = 100L)
                .progress(episode("a"), 9 * minute, 40 * minute, nowMs = 200L)

        val sent = WatchLinkRules.changes(state).history.single()

        assertEquals("a", sent.row.episodeId)
        assertEquals(9 * minute, sent.row.progressMs)
        assertEquals(40 * minute, sent.row.durationMs)
        assertEquals(200L, sent.row.lastPlayedAt)
        assertFalse(sent.row.isCompleted)
        assertEquals(2L, sent.revision)
    }

    @Test
    fun `an episode finished on the watch is sent as completed and kept until the phone confirms`() {
        val state = WatchLibraryState().progress(episode("a"), 5 * minute, 40 * minute, 100L).markPlayed(episode("a"), nowMs = 300L)

        val sent = WatchLinkRules.changes(state).history.single().row
        assertTrue(sent.isCompleted)
        assertEquals(300L, sent.lastPlayedAt)

        val confirmed = state.heard(PhoneLibrary(acked = mapOf("" to state.revision)))
        assertTrue(confirmed.pendingEpisodes.isEmpty())
        assertFalse(confirmed.episodes.containsKey("a"))
        assertTrue("a" in confirmed.played)
    }

    @Test
    fun `a shared episode ID is sent as the identity the phone can match`() {
        val shared = "4503599627370500"
        val state = WatchLibraryState().progress(episode(shared).copy(catalogKey = "guid-1"), minute, 40 * minute, 10L)

        val sent = WatchLinkRules.changes(state).history.single().row

        assertEquals(shared.toLong(), sent.recipeId)
        assertEquals("guid-1", sent.catalogKey)
        assertNull(WatchLinkRules.changes(WatchLibraryState().progress(episode("12"), minute, 40 * minute, 10L)).history.single().row.recipeId)
    }

    @Test
    fun `a long backlog goes out in steps that only cover what was sent`() {
        var state = WatchLibraryState().subscribe(show("7"), 1L)
        repeat(5) { state = state.progress(episode("e$it"), minute, 40 * minute, 10L + it) }
        state = state.subscribe(show("8"), 99L)

        val first = WatchLinkRules.changes(state, maxHistory = 2)

        assertEquals(listOf("e0", "e1"), first.history.map { it.row.episodeId })
        assertEquals(3L, first.revision)
        assertEquals(listOf("7"), first.subscriptions.map { it.change.podcastId })

        val afterAck = state.heard(PhoneLibrary(shows = listOf(phoneShow("7")), acked = mapOf("" to first.revision)))
        val second = WatchLinkRules.changes(afterAck, maxHistory = 10)
        assertEquals(listOf("e2", "e3", "e4"), second.history.map { it.row.episodeId })
        assertEquals(listOf("8"), second.subscriptions.map { it.change.podcastId })
        assertEquals(state.revision, second.revision)
    }

    @Test
    fun `the phone's shows replace the watch's, with unconfirmed watch changes on top`() {
        val state =
            WatchLibraryState(shows = listOf(show("1").copy(subscribed = true, newEpisodes = 3), show("2").copy(subscribed = true)))
                .subscribe(show("9"), 50L)
                .unsubscribe("1", 60L)

        val merged = state.heard(PhoneLibrary(shows = listOf(phoneShow("1"), phoneShow("3"))))

        assertEquals(listOf("9", "3"), merged.shows.map { it.id })
        assertTrue(merged.shows.all { it.subscribed })
    }

    @Test
    fun `once the phone confirms, its list is the whole truth`() {
        val state = WatchLibraryState().subscribe(show("9"), 50L)

        val merged = state.heard(PhoneLibrary(shows = listOf(phoneShow("9"), phoneShow("3")), acked = mapOf("" to 1L)))

        assertEquals(listOf("9", "3"), merged.shows.map { it.id })
        assertTrue(merged.pendingShows.isEmpty())
        val unfollowedOnPhone = merged.heard(PhoneLibrary(shows = listOf(phoneShow("3")), acked = mapOf("" to 1L)))
        assertEquals(listOf("3"), unfollowedOnPhone.shows.map { it.id })
    }

    @Test
    fun `a cut list of shows never removes shows it does not mention`() {
        val state = WatchLibraryState(shows = listOf(show("1").copy(subscribed = true), show("2").copy(subscribed = true)))

        val merged = state.heard(PhoneLibrary(shows = listOf(phoneShow("1")), showsTruncated = true))

        assertEquals(listOf("1", "2"), merged.shows.map { it.id })
    }

    // Regression: after the phone paused, its merged listening made the watch look like the device used last.
    @Test
    fun `listening merged from the phone never counts as the watch having played`() {
        val state = WatchLibraryState().progress(episode("a"), 5 * minute, 40 * minute, nowMs = 1_000L)
        assertEquals(1_000L, state.playedHereAt)

        val merged = state.heard(PhoneLibrary(history = listOf(row("a", progressMs = 12 * minute, at = 9_000L))))

        assertEquals(12 * minute, merged.positions["a"])
        assertEquals(1_000L, merged.playedHereAt)
    }

    @Test
    fun `listening on the phone lands in Jump back in with its place`() {
        val merged = WatchLibraryState().heard(PhoneLibrary(history = listOf(row("a", progressMs = 10 * minute, at = 500L))))

        val episode = merged.jumpBackIn.single()
        assertEquals("a", episode.id)
        assertEquals(30, episode.minutesLeft)
        assertEquals(10 * minute, merged.positions["a"])
        assertEquals("https://example.org/a.mp3", episode.audioUrl)
        assertTrue(merged.pendingEpisodes.isEmpty())
        assertEquals(0L, merged.revision)
    }

    @Test
    fun `the newer listening wins, whichever device it happened on`() {
        val watch = WatchLibraryState().progress(episode("a"), 20 * minute, 40 * minute, nowMs = 900L)

        val olderPhone = watch.heard(PhoneLibrary(history = listOf(row("a", progressMs = 5 * minute, at = 400L))))
        assertEquals(20 * minute, olderPhone.positions["a"])

        val newerPhone = watch.heard(PhoneLibrary(history = listOf(row("a", progressMs = 30 * minute, at = 1_500L))))
        assertEquals(30 * minute, newerPhone.positions["a"])
        assertEquals(10, newerPhone.jumpBackIn.single().minutesLeft)
    }

    @Test
    fun `an episode finished on the phone leaves Jump back in and keeps its download for the caller to clear`() {
        val watch =
            WatchLibraryState()
                .progress(episode("a"), 20 * minute, 40 * minute, nowMs = 900L)
                .stored(episode("a"), 1_000L, 900L)

        val merged = watch.heard(PhoneLibrary(history = listOf(row("a", at = 2_000L, completed = true)), acked = mapOf("" to watch.revision)))

        assertTrue(merged.jumpBackIn.isEmpty())
        assertTrue("a" in merged.played)
        assertEquals(WatchDownload.Done, merged.episodes.getValue("a").download)
    }

    @Test
    fun `the phone never moves the episode in the watch's player`() {
        val watch = WatchLibraryState().progress(episode("a"), 20 * minute, 40 * minute, nowMs = 900L)

        val merged = watch.heard(PhoneLibrary(history = listOf(row("a", progressMs = 30 * minute, at = 5_000L))), playing = "a")

        assertEquals(20 * minute, merged.positions["a"])
    }

    @Test
    fun `the phone's row finds the watch's copy by shared ID or by audio address`() {
        val shared = "4503599627370500"
        val bySharedId = WatchLibraryState().progress(episode(shared), minute, 40 * minute, nowMs = 100L)
        val merged = bySharedId.heard(PhoneLibrary(history = listOf(row("phone-id", progressMs = 12 * minute, at = 900L, recipeId = shared.toLong()))))
        assertEquals(12 * minute, merged.positions[shared])
        assertFalse(merged.episodes.containsKey("phone-id"))

        val byAudio = WatchLibraryState().progress(episode("w1"), minute, 40 * minute, nowMs = 100L)
        val mergedByAudio = byAudio.heard(PhoneLibrary(history = listOf(row("p1", progressMs = 12 * minute, at = 900L, audio = "https://example.org/w1.mp3"))))
        assertEquals(12 * minute, mergedByAudio.positions["w1"])
        assertFalse(mergedByAudio.episodes.containsKey("p1"))
    }

    @Test
    fun `a show page copy of an episode the phone named adopts the saved copy's ID`() {
        val merged = WatchLibraryState().heard(PhoneLibrary(history = listOf(row("phone-id", progressMs = 12 * minute, at = 900L, audio = "https://example.org/x.mp3"))))

        val fromFeed = episode("4503599627370777").copy(audioUrl = "https://example.org/x.mp3")

        assertEquals("phone-id", merged.merged(fromFeed).id)
        assertEquals(28, merged.merged(fromFeed).minutesLeft)
    }

    @Test
    fun `Jump back in is ordered by when each episode was last played, on either device`() {
        val watch = WatchLibraryState().progress(episode("w"), minute, 40 * minute, nowMs = 500L)

        val merged = watch.heard(PhoneLibrary(history = listOf(row("old", at = 100L), row("new", at = 900L))))

        assertEquals(listOf("new", "w", "old"), merged.jumpBackIn.map { it.id })
    }

    @Test
    fun `pacing sends at once when idle and about once a minute while playing`() {
        assertEquals(PublishPacing.SETTLE_MS, PublishPacing.waitMs(isPlaying = false, sinceLastMs = 0L))
        assertEquals(PublishPacing.PLAYING_INTERVAL_MS, PublishPacing.waitMs(isPlaying = true, sinceLastMs = 0L))
        assertEquals(20_000L, PublishPacing.waitMs(isPlaying = true, sinceLastMs = 40_000L))
        assertEquals(PublishPacing.SETTLE_MS, PublishPacing.waitMs(isPlaying = true, sinceLastMs = 10 * minute))
    }
}
