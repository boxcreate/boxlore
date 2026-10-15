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
class HeroCardStylePreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clear()
    }

    @After
    fun tearDown() = clear()

    private fun clear() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        context.getSharedPreferences(PrefsFileMigrator.Files.THEME_FAST_CACHE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun heroCardStyleDefaultsToStandardAndPersistsClassic() = runTest {
        val repository = UserPreferencesRepository(context)
        assertEquals(HeroCardStyle.STANDARD, repository.heroCardStyleStream.first())
        assertEquals(HeroCardStyle.STANDARD, repository.cachedHeroCardStyle)

        repository.setHeroCardStyle(HeroCardStyle.CLASSIC)
        assertEquals(HeroCardStyle.CLASSIC, repository.heroCardStyleStream.first())
        assertEquals(HeroCardStyle.CLASSIC, UserPreferencesRepository(context).cachedHeroCardStyle)

        repository.setHeroCardStyle("unknown")
        assertEquals(HeroCardStyle.STANDARD, repository.heroCardStyleStream.first())
    }
}
