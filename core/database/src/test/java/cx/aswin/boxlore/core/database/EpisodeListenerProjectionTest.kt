package cx.aswin.boxlore.core.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Projected listener state for an episode stored under one or several IDs (docs/EPISODE_IDS.md §5). */
class EpisodeListenerProjectionTest {
    private val shared = EpisodeGroup("B", SHOW, KEY, memberIds = listOf("A", "B"), ownerId = "A")

    @Test
    fun anEpisodeWithOneIdReadsItsRowUnchangedWhateverTheGroupSettingsSay() {
        val row = row("A", progress = 40L, liked = true, likedAt = 10L, completed = true, playedAt = 20L)
        val keyedSingle = EpisodeGroup("A", SHOW, KEY, listOf("A"), "A")
        val clearedLater = EpisodeGroupStateEntity(SHOW, KEY, likeClearedAt = 99L, completeClearedAt = 99L)

        assertSame(row, EpisodeListenerProjection.project(keyedSingle, listOf(row), clearedLater))
        assertSame(row, EpisodeListenerProjection.project(EpisodeGroup.single("A"), listOf(row), clearedLater))
        assertNull(EpisodeListenerProjection.project(EpisodeGroup.single("A"), emptyList(), null))
    }

    @Test
    fun progressComesFromTheOwnerAndTheLatestLikeDecisionWins() {
        val owner = row("A", progress = 300L, liked = false, likedAt = 50L, playedAt = 100L)
        val other = row("B", progress = 900L, liked = true, likedAt = 20L, playedAt = 200L)

        val projected = EpisodeListenerProjection.project(shared, listOf(owner, other), null)!!

        assertEquals("A", projected.episodeId)
        assertEquals(300L, projected.progressMs)
        assertFalse(projected.isLiked)
        assertEquals(50L, projected.likedAt)
    }

    @Test
    fun aClearedLikeInTheGroupSettingsBeatsAnOlderAliasRow() {
        val owner = row("A", liked = false, likedAt = 0L, playedAt = 10L)
        val pulledAgain = row("B", liked = true, likedAt = 40L, playedAt = 10L)
        val state = EpisodeGroupStateEntity(SHOW, KEY, likedAt = 30L, likeClearedAt = 50L)

        assertFalse(EpisodeListenerProjection.project(shared, listOf(owner, pulledAgain), state)!!.isLiked)
        // A like decided after the clear wins again.
        val likedLater = pulledAgain.copy(likedAt = 60L)
        assertTrue(EpisodeListenerProjection.project(shared, listOf(owner, likedLater), state)!!.isLiked)
    }

    @Test
    fun aGroupSettingWinsATieWithARowAndTiedRowsCountAsSetWhenAnyIs() {
        val state = EpisodeGroupStateEntity(SHOW, KEY, completeClearedAt = 70L)
        val rows = listOf(row("A", completed = true, playedAt = 70L), row("B", completed = false, playedAt = 70L))

        assertFalse(EpisodeListenerProjection.completionDecision(rows, state).value)
        assertTrue(EpisodeListenerProjection.completionDecision(rows, null).value)
        // Legacy rows with no like time: either row's like counts.
        val legacy = listOf(row("A", liked = false, likedAt = 0L), row("B", liked = true, likedAt = 0L))
        assertTrue(EpisodeListenerProjection.likeDecision(legacy, null).value)
    }

    @Test
    fun theLatestPlayDecidesCompletionAndCompletedByFlagsFollowTheCompletingRow() {
        val owner = row("A", progress = 0L, completed = false, playedAt = 10L)
        val completedLater = row("B", completed = true, playedAt = 30L, manual = true)

        val projected = EpisodeListenerProjection.project(shared, listOf(owner, completedLater), null)!!

        assertTrue(projected.isCompleted)
        assertTrue(projected.isManualCompletion)
        assertFalse(projected.isBulkCompletion)
        val replayedLater = owner.copy(progressMs = 50L, lastPlayedAt = 40L)
        val reopened = EpisodeListenerProjection.project(shared, listOf(replayedLater, completedLater), null)!!
        assertFalse(reopened.isCompleted)
        assertFalse(reopened.isManualCompletion)
    }

    @Test
    fun withoutAnOwnerRowTheMostRecentlyPlayedRowIsTheBase() {
        val older = row("B", progress = 5L, playedAt = 10L)
        val newer = row("C", progress = 7L, playedAt = 20L)
        val group = EpisodeGroup("B", SHOW, KEY, listOf("A", "B", "C"), "A")

        assertEquals("C", EpisodeListenerProjection.project(group, listOf(older, newer), null)!!.episodeId)
    }

    @Test
    fun aNewDecisionIsStampedAfterEveryRecordedOneWhenTheClockReadsEarlier() {
        val state = EpisodeGroupStateEntity(SHOW, KEY, likedAt = 500L, completedAt = 900L)
        val rows = listOf(row("A", likedAt = 600L, playedAt = 950L))

        assertEquals(601L, EpisodeListenerProjection.likeTime(100L, rows, state))
        assertEquals(951L, EpisodeListenerProjection.completionTime(100L, rows, state))
        assertEquals(2_000L, EpisodeListenerProjection.likeTime(2_000L, rows, state))
    }

    @Test
    fun groupSettingsRecordTheDecisionAndCountVersions() {
        val first = EpisodeListenerProjection.withLike(null, SHOW, KEY, liked = true, at = 10L)
        val cleared = EpisodeListenerProjection.withLike(first, SHOW, KEY, liked = false, at = 20L)
        val completed = EpisodeListenerProjection.withCompletion(cleared, SHOW, KEY, completed = true, at = 30L)

        assertEquals(EpisodeGroupStateEntity(SHOW, KEY, likedAt = 10L, likeClearedAt = 20L, completedAt = 30L, version = 3L), completed)
    }

    private fun row(
        id: String,
        progress: Long = 0L,
        liked: Boolean = false,
        likedAt: Long = 0L,
        completed: Boolean = false,
        playedAt: Long = 0L,
        manual: Boolean = false,
    ) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show",
        progressMs = progress,
        durationMs = 1_000L,
        isCompleted = completed,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
        isManualCompletion = manual,
    )

    private companion object {
        const val SHOW = "100"
        const val KEY = "guid-a"
    }
}
