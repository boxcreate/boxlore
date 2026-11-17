package cx.aswin.boxlore.fcm

import cx.aswin.boxlore.fcm.NewEpisodeFcmLogic.PostedEpisodeNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Posts and cancels Android has not listed yet still count when planning the next one. */
class RecentEpisodePostsTest {
    private val grace = RecentEpisodePosts.GRACE_MILLIS
    private val a = PostedEpisodeNotification("A", slot = 0, order = 1, releaseKey = "a-1")
    private val b = PostedEpisodeNotification("B", slot = 1, order = 2, releaseKey = "b-1")

    @Test
    fun aPostAndroidHasNotListedCountsUntilItIsListedOrTheGraceEnds() {
        val recent = RecentEpisodePosts()
        recent.recordPost(a, nowMillis = 0)
        assertEquals(listOf(a), recent.merge(emptyList(), nowMillis = 10))
        // Listed: Android's copy is used from then on, so a dismissal after it is seen.
        assertEquals(listOf(a), recent.merge(listOf(a), nowMillis = 20))
        assertEquals(emptyList<PostedEpisodeNotification>(), recent.merge(emptyList(), nowMillis = 30))

        recent.recordPost(b, nowMillis = 100)
        assertEquals(emptyList<PostedEpisodeNotification>(), recent.merge(emptyList(), nowMillis = 100 + grace))
    }

    @Test
    fun twoShowsPostedBeforeAndroidListsEitherGetTheirOwnSlots() {
        val recent = RecentEpisodePosts()
        val first = NewEpisodeFcmLogic.placeEpisodeNotification("A", recent.merge(emptyList(), 0), isUpdate = false)
        recent.recordPost(PostedEpisodeNotification("A", first.slot, first.order, "a-1"), 0)
        val posted = recent.merge(emptyList(), 5)
        val second = NewEpisodeFcmLogic.placeEpisodeNotification("B", posted, isUpdate = false)
        assertTrue(first.slot != second.slot)
        // With A on its way, B's post keeps the summary and adds to it.
        assertEquals(NewEpisodeFcmLogic.SummaryPlan(false, 2), NewEpisodeFcmLogic.planSummary(posted.count { it.podcastId != "B" }, summaryShowing = true, isUpdate = false))
    }

    @Test
    fun aNewerPostReplacesTheOlderOneAndroidStillLists() {
        val recent = RecentEpisodePosts()
        val newer = a.copy(order = 3, releaseKey = "a-2")
        recent.recordPost(newer, 0)
        assertEquals(listOf(b, newer), recent.merge(listOf(a, b), 5))
        assertTrue(recent.supersedes("A", "a-1", 5))
        assertFalse(recent.supersedes("A", "a-2", 5))
        assertFalse(recent.supersedes("A", "a-1", grace))
    }

    @Test
    fun aCancelAndroidHasNotListedTakesTheShowOutUntilItGoes() {
        val recent = RecentEpisodePosts()
        recent.recordCancel("A", 0)
        assertEquals(listOf(b), recent.merge(listOf(a, b), 5))
        assertTrue(recent.supersedes("A", "a-1", 5))
        // Gone from Android's list: the cancel has landed, so a later post of A counts again.
        assertEquals(listOf(b), recent.merge(listOf(b), 6))
        assertEquals(listOf(a, b), recent.merge(listOf(a, b), 7))
    }

    @Test
    fun theSummaryCountsAsPostedOrCancelledUntilAndroidListsIt() {
        val recent = RecentEpisodePosts()
        recent.recordSummary(showing = true, 0)
        assertTrue(recent.summaryShowing(activeListsSummary = false, 5))
        assertTrue(recent.summaryShowing(activeListsSummary = true, 6))
        // Listed: a later dismissal is seen.
        assertFalse(recent.summaryShowing(activeListsSummary = false, 7))

        recent.recordSummary(showing = false, 10)
        assertFalse(recent.summaryShowing(activeListsSummary = true, 15))
        assertTrue(recent.summaryShowing(activeListsSummary = true, 10 + grace))
    }
}
