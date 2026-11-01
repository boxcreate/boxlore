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

/** The NPS survey's install-only history: launches, when it last appeared, and the one-time carry-over. */
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
    fun anUpdatedInstallThatShowedTheOldSurveyWaitsFromTheUpdate() = runTest {
        store.carryOverLegacy(legacyShown = true, installedAtMs = now - 40 * day, nowMs = now)
        assertEquals(now, store.record().lastShownAtMs)
    }

    @Test
    fun aRestoredPhoneIgnoresTheBackedUpFlag() = runTest {
        // Installed an hour ago: the old flag came in with a backup, so this phone is asked afresh.
        store.carryOverLegacy(legacyShown = true, installedAtMs = now - 60 * 60 * 1000, nowMs = now)
        assertNull(store.record().lastShownAtMs)
    }

    @Test
    fun theCarryOverRunsOnce() = runTest {
        store.carryOverLegacy(legacyShown = false, installedAtMs = now - 40 * day, nowMs = now)
        store.carryOverLegacy(legacyShown = true, installedAtMs = now - 40 * day, nowMs = now + day)
        assertNull(store.record().lastShownAtMs)
    }

    @Test
    fun theCarryOverNeverMovesARealShowing() = runTest {
        store.markShown(now - 10 * day)
        store.carryOverLegacy(legacyShown = true, installedAtMs = now - 40 * day, nowMs = now)
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
