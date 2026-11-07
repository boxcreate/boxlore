package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The NPS survey's install-only history: launches, when it last appeared, the last finish, and the one-time carry-over. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NpsSurveyStoreTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private lateinit var context: Context
    private lateinit var store: NpsSurveyStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.npsSurveyDataStore.edit { it.clear() } }
        store = NpsSurveyStore(context)
    }

    @After
    fun tearDown() {
        runBlocking { context.npsSurveyDataStore.edit { it.clear() } }
    }

    @Test
    fun countsLaunchesAndRemembersWhenShown() = runTest {
        assertEquals(NpsSurveyRecord(lastShownAtMs = null, launches = 0), store.record())
        store.recordLaunch()
        store.recordLaunch()
        store.markShown(now)
        assertEquals(NpsSurveyRecord(lastShownAtMs = now, launches = 2), store.record())
    }

    @Test
    fun keepsTheLatestFinishAcrossRestarts() = runTest {
        store.recordFinish(now - day)
        store.recordFinish(now)
        store.recordFinish(now - 2 * day)
        assertEquals(now, NpsSurveyStore(context).record().lastFinishedAtMs)
    }

    @Test
    fun anUpdatedInstallWhoseListenerAnsweredTheOldSurveyWaitsFromTheUpdate() = runTest {
        carry(legacyShown = true, legacyAnswered = true)
        assertEquals(now, store.record().lastShownAtMs)
    }

    @Test
    fun anUpdatedInstallThatOnlyShowedTheOldSurveyIsAskedLikeANewOne() = runTest {
        // Regression: seen, dismissed, or marked shown before it ever appeared, it waited 90 days too.
        carry(legacyShown = true, legacyAnswered = false)
        assertNull(store.record().lastShownAtMs)
    }

    @Test
    fun theRevisionLiftsVersion30sWaitWhereTheListenerNeverAnswered() = runTest {
        legacyCheckedLikeVersion30(lastShownAt = now - 5 * day)
        carry(legacyShown = true, legacyAnswered = false, lastPromptAt = now - 40 * day)
        assertNull(store.record().lastShownAtMs)
        // It runs once.
        store.markShown(now)
        carry(legacyShown = true, legacyAnswered = false, nowMs = now + day)
        assertEquals(now, store.record().lastShownAtMs)
    }

    @Test
    fun theRevisionKeepsVersion30sWaitForAnAnswerAndARealShowing() = runTest {
        legacyCheckedLikeVersion30(lastShownAt = now - 5 * day)
        carry(legacyShown = true, legacyAnswered = true)
        assertEquals(now - 5 * day, store.record().lastShownAtMs)

        context.npsSurveyDataStore.edit { it.clear() }
        // Shown on 0.0.30 and dismissed: the shared prompt time was written with it.
        legacyCheckedLikeVersion30(lastShownAt = now - 5 * day)
        carry(legacyShown = true, legacyAnswered = false, lastPromptAt = now - 5 * day + 200)
        assertEquals(now - 5 * day, store.record().lastShownAtMs)
    }

    /** What 0.0.30 left on an updated phone: the carry-over done once, and its wait. */
    private suspend fun legacyCheckedLikeVersion30(lastShownAt: Long) {
        context.npsSurveyDataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("legacy_checked")] = true
            prefs[androidx.datastore.preferences.core.longPreferencesKey("last_shown_at")] = lastShownAt
        }
    }

    private suspend fun carry(
        legacyShown: Boolean,
        legacyAnswered: Boolean = false,
        updatedInstall: Boolean = true,
        lastPromptAt: Long? = null,
        nowMs: Long = now,
    ) = store.carryOverLegacy(legacyShown, legacyAnswered, updatedInstall, lastPromptAt, nowMs)

    @Test
    fun aRestoredPhoneIgnoresTheBackedUpFlagHoweverLateItFirstOpens() = runTest {
        // Installed fresh (never updated) with the old flag from a backup, first opened days later:
        // regression, the old 24-hour guess counted this as an updated install and waited 90 days.
        carry(legacyShown = true, legacyAnswered = true, updatedInstall = false, nowMs = now + 2 * day)
        assertNull(store.record().lastShownAtMs)
    }

    @Test
    fun theCarryOverRunsOnce() = runTest {
        carry(legacyShown = false)
        carry(legacyShown = true, legacyAnswered = true, nowMs = now + day)
        assertNull(store.record().lastShownAtMs)
    }

    @Test
    fun theCarryOverNeverMovesARealShowing() = runTest {
        store.markShown(now - 10 * day)
        carry(legacyShown = true, legacyAnswered = true)
        assertEquals(now - 10 * day, store.record().lastShownAtMs)
    }

    @Test
    fun theDebugResetForgetsTheLastShowingAndCountsTwoStarts() = runTest {
        store.markShown(now)
        store.resetSchedule()
        assertEquals(NpsSurveyRecord(lastShownAtMs = null, launches = 2), store.record())
        repeat(3) { store.recordLaunch() }
        store.resetSchedule()
        assertEquals(5, store.record().launches)
    }
}
