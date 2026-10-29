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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The listener's languages and "Include English" choice (D9) in [UserPreferencesRepository]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentLanguagePreferencesTest {
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
    fun changingCountryKeepsTheListenersLanguagesAndEnglishChoice() = runTest {
        repository.setRegion("de")
        repository.setContentLanguages(listOf("de", "fr"))
        repository.setContentEnglishOff(true)
        repository.setRegion("in")
        assertEquals(listOf("de", "fr"), repository.contentLanguagesStream.first())
        assertTrue(repository.contentEnglishOffStream.first())
    }

    @Test
    fun aListenerWhoNeverPickedLanguagesGetsTheNewCountrysSet() = runTest {
        repository.setRegion("in")
        assertEquals(listOf("en", "hi"), repository.contentLanguagesStream.first())
    }

    @Test
    fun englishOffHoldsOnlyWhileThePicksAllowIt() = runTest {
        repository.setRegion("in")
        repository.setContentEnglishOff(true)
        assertFalse("Hindi keeps English on", repository.contentEnglishOffStream.first())
        assertEquals(listOf("en", "hi"), repository.contentLanguagesStream.first())

        repository.setContentLanguages(listOf("de"))
        repository.setContentEnglishOff(true)
        assertEquals(listOf("de"), repository.contentLanguagesStream.first())

        repository.setContentLanguages(listOf("de", "hi"))
        assertFalse("adding Hindi switches English back on", repository.contentEnglishOffStream.first())
        assertEquals(listOf("en", "de", "hi"), repository.contentLanguagesStream.first())
    }

    @Test
    fun englishsSlotTakesAFourthPickWhileItIsOff() = runTest {
        repository.setRegion("de")
        repository.setContentLanguages(listOf("de"))
        repository.setContentEnglishOff(true)
        repository.setContentLanguages(listOf("de", "fr", "es", "nl"))
        assertEquals(listOf("de", "fr", "es", "nl"), repository.contentLanguagesStream.first())
        repository.setContentEnglishOff(false)
        assertEquals(listOf("en", "de", "fr", "es"), repository.contentLanguagesStream.first())
    }

    @Test
    fun aRestoreWritesFourPicksAndEnglishOffTogether() = runTest {
        repository.setRegion("in")
        repository.setContentLanguages(listOf("de", "fr", "es", "nl"), englishOff = true)
        assertEquals(listOf("de", "fr", "es", "nl"), repository.contentLanguagesStream.first())
        assertTrue(repository.contentEnglishOffStream.first())
    }
}
