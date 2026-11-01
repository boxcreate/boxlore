package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The milestone review prompt: when it may show, the quiet days, and milestones counting once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewPromptPreferencesTest {
    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        repository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
    }

    @Test
    fun shouldShowReviewPromptFalseWhilePlaying() = runTest {
        reachReviewMilestone()
        assertFalse(repository.shouldShowReviewPrompt(isPlaying = true, installedAtMs = INSTALLED_LONG_AGO))
    }

    @Test
    fun shouldShowReviewPromptFalseWithoutPendingMilestone() = runTest {
        repository.markNpsSurveyFired()
        assertFalse(repository.shouldShowReviewPrompt(isPlaying = false, installedAtMs = INSTALLED_LONG_AGO))
    }

    @Test
    fun reviewPromptShowsOnceMilestoneIsReached() = runTest {
        reachReviewMilestone()
        assertTrue(repository.shouldShowReviewPrompt(isPlaying = false, installedAtMs = INSTALLED_LONG_AGO))
    }

    @Test
    fun reviewPromptWaitsTwoDaysAfterInstall() = runTest {
        reachReviewMilestone()
        val now = System.currentTimeMillis()
        assertFalse(repository.shouldShowReviewPrompt(isPlaying = false, installedAtMs = now - DAY_MS, nowMs = now))
        assertTrue(repository.shouldShowReviewPrompt(isPlaying = false, installedAtMs = now - 2 * DAY_MS, nowMs = now))
    }

    @Test
    fun reviewPromptWaitsTwoDaysAfterLibraryImport() = runTest {
        reachReviewMilestone()
        val now = System.currentTimeMillis()
        repository.markLibraryImported(nowMs = now - DAY_MS)
        assertTrue(repository.isInReviewQuietPeriod(installedAtMs = INSTALLED_LONG_AGO, nowMs = now))
        assertFalse(repository.shouldShowReviewPrompt(isPlaying = false, installedAtMs = INSTALLED_LONG_AGO, nowMs = now))

        val later = now + DAY_MS
        assertFalse(repository.isInReviewQuietPeriod(installedAtMs = INSTALLED_LONG_AGO, nowMs = later))
        assertTrue(repository.shouldShowReviewPrompt(isPlaying = false, installedAtMs = INSTALLED_LONG_AGO, nowMs = later))
    }

    @Test
    fun usedReviewMilestoneNeverComesBack() = runTest {
        // Regression: the same milestone was stored again on the next Home load and re-asked 30 days later.
        reachReviewMilestone(completedCount = 7)
        repository.markReviewPromptShown()
        assertNull(repository.reviewMilestonePending())

        repository.syncReviewMilestonePending(7)
        assertNull(repository.reviewMilestonePending())

        repository.syncReviewMilestonePending(15)
        assertEquals(15, repository.reviewMilestonePending())
    }

    private suspend fun reachReviewMilestone(completedCount: Int = 5) {
        repository.markNpsSurveyFired()
        repository.syncReviewMilestonePending(completedCount)
    }

    @Test
    fun markReviewedBlocksFuturePrompts() = runTest {
        assertFalse(repository.reviewHasReviewed.first())
        repository.markReviewed()
        assertTrue(repository.reviewHasReviewed.first())
        assertTrue(repository.hasReviewedSync())
    }

    @Test
    fun reviewMilestonePendingTracksHighestReachedTier() = runTest {
        assertNull(repository.reviewMilestonePending())

        repository.syncReviewMilestonePending(3)
        assertNull(repository.reviewMilestonePending())

        repository.syncReviewMilestonePending(5)
        assertEquals(5, repository.reviewMilestonePending())

        repository.syncReviewMilestonePending(30)
        assertEquals(30, repository.reviewMilestonePending())

        // Lower milestone does not downgrade.
        repository.syncReviewMilestonePending(15)
        assertEquals(30, repository.reviewMilestonePending())

        repository.clearReviewMilestonePending()
        assertNull(repository.reviewMilestonePending())
    }

    @Test
    fun reviewMilestoneNotStoredAfterReview() = runTest {
        repository.markReviewed()
        repository.syncReviewMilestonePending(30)
        assertNull(repository.reviewMilestonePending())
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val INSTALLED_LONG_AGO = 0L
