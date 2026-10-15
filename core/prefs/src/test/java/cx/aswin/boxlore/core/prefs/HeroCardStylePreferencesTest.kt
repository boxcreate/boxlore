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
    fun heroCardStyleDefaultsToBlockAndPersistsCover() = runTest {
        val repository = UserPreferencesRepository(context)
        assertEquals(HeroCardStyle.BLOCK, repository.heroCardStyleStream.first())
        assertEquals(HeroCardStyle.BLOCK, repository.cachedHeroCardStyle)

        repository.setHeroCardStyle(HeroCardStyle.COVER)
        assertEquals(HeroCardStyle.COVER, repository.heroCardStyleStream.first())
        assertEquals(HeroCardStyle.COVER, UserPreferencesRepository(context).cachedHeroCardStyle)

        repository.setHeroCardStyle(" Duotone ")
        assertEquals(HeroCardStyle.DUOTONE, repository.heroCardStyleStream.first())
        assertEquals(true, HeroCardStyle.usesArtworkColors(HeroCardStyle.DUOTONE))
        assertEquals(false, HeroCardStyle.usesArtworkColors(HeroCardStyle.BLOCK))

        repository.setHeroCardStyle("unknown")
        assertEquals(HeroCardStyle.BLOCK, repository.heroCardStyleStream.first())
    }

    @Test
    fun savedClassicAndStandardChoicesBecomeCoverAndBlock() = runTest {
        context.userPreferencesDataStore.edit { it[Keys.HERO_CARD_STYLE] = "classic" }
        assertEquals(HeroCardStyle.COVER, UserPreferencesRepository(context).heroCardStyleStream.first())

        context.userPreferencesDataStore.edit { it[Keys.HERO_CARD_STYLE] = "standard" }
        assertEquals(HeroCardStyle.BLOCK, UserPreferencesRepository(context).heroCardStyleStream.first())
    }

    @Test
    fun coverShadeDefaultsToArtworkPersistsAndRejectsUnknownValues() = runTest {
        val repository = UserPreferencesRepository(context)
        assertEquals(HeroCoverShade.ARTWORK, repository.heroCoverShadeStream.first())
        assertEquals(HeroCoverShade.ARTWORK, repository.cachedHeroCoverShade)

        repository.setHeroCoverShade(HeroCoverShade.THEME)
        assertEquals(HeroCoverShade.THEME, repository.heroCoverShadeStream.first())
        assertEquals(HeroCoverShade.THEME, UserPreferencesRepository(context).cachedHeroCoverShade)

        repository.setHeroCoverShade(" DARK ")
        assertEquals(HeroCoverShade.DARK, repository.heroCoverShadeStream.first())

        repository.setHeroCoverShade("sepia")
        assertEquals(HeroCoverShade.ARTWORK, repository.heroCoverShadeStream.first())
    }
}
