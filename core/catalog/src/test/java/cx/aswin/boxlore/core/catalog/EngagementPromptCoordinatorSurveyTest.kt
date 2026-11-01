package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.prefs.NpsSurveyRule
import cx.aswin.boxlore.core.prefs.NpsSurveyStore
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.prefs.userPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The coordinator asks the NPS survey only at a finish moment in this run, with nothing playing and no
 * prompt yet this session; once it was on screen it waits 90 days, and the review prompt may follow.
 * (One story in one test: the survey's install-only store lives for the whole test run.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngagementPromptCoordinatorSurveyTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `the survey appears at a finish moment, once, then waits`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.userPreferencesDataStore.edit { it.clear() }
        val prefs = UserPreferencesRepository(context)
        val store = NpsSurveyStore(context)
        val now = System.currentTimeMillis()
        val processStart = now - 10 * 60 * 1000
        val scope = CoroutineScope(SupervisorJob())
        val coordinator = EngagementPromptCoordinator(prefs, store, processStartedAtMs = processStart, scope = scope)
        suspend fun settle() = scope.coroutineContext[Job]!!.children.forEach { it.join() }
        suspend fun due(lastFinishedAt: Long?, playing: Boolean = false) =
            coordinator.shouldShowNpsSurvey(playing, installedAtMs = now - 30 * day, finishedSinceInstall = 5, lastFinishedAtMs = lastFinishedAt, nowMs = now)

        coordinator.onAppLaunched(updatedInstall = false, nowMs = now)
        coordinator.onAppLaunched(updatedInstall = false, nowMs = now)
        // A restored or older install that never showed the old survey starts with no history.
        assertNull(store.record().lastShownAtMs)

        assertFalse("an open alone is not the moment", due(lastFinishedAt = processStart - 1))
        assertFalse("never during playback", due(lastFinishedAt = now - 60_000, playing = true))
        assertTrue(due(lastFinishedAt = now - 60_000))
        assertTrue(coordinator.isFinishMoment(now - 60_000, now))

        coordinator.reserveSessionForPrompt()
        assertFalse("one prompt per session", due(lastFinishedAt = now - 60_000))

        coordinator.onSurveyDisplayed(nowMs = now)
        coordinator.onNpsRatingSubmitted(9)
        settle()
        assertEquals(now, store.record().lastShownAtMs)
        assertTrue("the review prompt may follow later", prefs.hasNpsSurveyFired())
        assertEquals(9, prefs.npsLastScore())

        val nextRun = EngagementPromptCoordinator(prefs, store, processStartedAtMs = now, scope = scope)
        assertFalse(
            "not again within 90 days",
            nextRun.shouldShowNpsSurvey(false, now - 30 * day, 6, lastFinishedAtMs = now + 1, nowMs = now + 2),
        )
        assertEquals(listOf(NpsSurveyRule.Cooldown, NpsSurveyRule.AskAgainGap), nextRun.npsSurveyWaitsFor(now - 30 * day, 6, nowMs = now + 2))

        // The debug reset lets the next finish ask again, in this session too.
        nextRun.reserveSessionForPrompt()
        nextRun.resetNpsSurveyForTesting()
        assertEquals(emptyList<NpsSurveyRule>(), nextRun.npsSurveyWaitsFor(now - 30 * day, 6, nowMs = now + 2))
        assertTrue(nextRun.shouldShowNpsSurvey(false, now - 30 * day, 6, lastFinishedAtMs = now + 1, nowMs = now + 2))
        context.userPreferencesDataStore.edit { it.clear() }
    }
}
