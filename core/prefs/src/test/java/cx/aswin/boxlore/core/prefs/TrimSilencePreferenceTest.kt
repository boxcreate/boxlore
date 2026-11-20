package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app's trim silence setting: off by default, kept under its stable key, read by a new repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrimSilencePreferenceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
    }

    @After
    fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
    }

    @Test
    fun trimSilenceIsOffUntilTurnedOn() = runTest {
        val repository = UserPreferencesRepository(context)
        assertFalse(repository.trimSilenceEnabledStream.first())

        repository.setTrimSilenceEnabled(true)

        assertTrue(repository.trimSilenceEnabledStream.first())
        assertTrue(UserPreferencesRepository(context).trimSilenceEnabledStream.first())
        repository.setTrimSilenceEnabled(false)
        assertFalse(repository.trimSilenceEnabledStream.first())
    }

    @Test
    fun trimSilenceIsStoredUnderItsStableKey() = runTest {
        UserPreferencesRepository(context).setTrimSilenceEnabled(true)

        val stored = context.userPreferencesDataStore.data.first()[booleanPreferencesKey("trim_silence_enabled")]
        assertEquals(true, stored)
    }
}
