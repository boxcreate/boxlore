package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationBlurPreferencesTest {
    private lateinit var context: Context

    private fun reset() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        context.getSharedPreferences(PrefsFileMigrator.Files.THEME_FAST_CACHE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PrefsFileMigrator.LegacyFiles.THEME_FAST_CACHE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        reset()
    }

    @After
    fun tearDown() = reset()

    @Test
    fun `the blur behind the floating bar starts soft and remembers its level`() = runTest {
        val repository = UserPreferencesRepository(context)
        assertEquals(NavigationBlurLevel.SOFT, repository.cachedNavigationBlurLevel)
        assertEquals(NavigationBlurLevel.SOFT, repository.navigationBlurLevelStream.first())

        repository.setNavigationBlurLevel(NavigationBlurLevel.STRONG)
        assertEquals(NavigationBlurLevel.STRONG, repository.navigationBlurLevelStream.first())
        // A fresh repository reads the fast cache before DataStore loads, and restores DataStore from it.
        assertEquals(NavigationBlurLevel.STRONG, UserPreferencesRepository(context).cachedNavigationBlurLevel)
        context.userPreferencesDataStore.edit { it.clear() }
        val restored = UserPreferencesRepository(context)
        restored.hydrateMissingDataStoreFromFastCache()
        assertEquals(NavigationBlurLevel.STRONG, restored.navigationBlurLevelStream.first())
    }

    @Test
    fun `unknown levels fall back to soft`() {
        assertEquals(NavigationBlurLevel.SOFT, NavigationBlurLevel.sanitize("glassy"))
        assertEquals(NavigationBlurLevel.SOFT, NavigationBlurLevel.sanitize(null))
        assertEquals(NavigationBlurLevel.OFF, NavigationBlurLevel.sanitize(" OFF "))
    }
}
