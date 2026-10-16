package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
    fun heroCardStyleDefaultsToCoverAndPersistsPicks() = runTest {
        val repository = UserPreferencesRepository(context)
        assertEquals(HeroCardStyle.COVER, repository.heroCardStyleStream.first())
        assertEquals(HeroCardStyle.COVER, repository.cachedHeroCardStyle)

        repository.setHeroCardStyle(HeroCardStyle.BLOCK)
        assertEquals(HeroCardStyle.BLOCK, repository.heroCardStyleStream.first())
        assertEquals(HeroCardStyle.BLOCK, UserPreferencesRepository(context).cachedHeroCardStyle)

        repository.setHeroCardStyle(" Duotone ")
        assertEquals(HeroCardStyle.DUOTONE, repository.heroCardStyleStream.first())
        assertEquals(true, HeroCardStyle.usesArtworkColors(HeroCardStyle.DUOTONE))
        assertEquals(false, HeroCardStyle.usesArtworkColors(HeroCardStyle.BLOCK))

        repository.setHeroCardStyle(HeroCardStyle.HALO)
        assertEquals(HeroCardStyle.HALO, UserPreferencesRepository(context).cachedHeroCardStyle)
        assertEquals(true, HeroCardStyle.usesArtworkColors(HeroCardStyle.HALO))

        repository.setHeroCardStyle(" TIDE ")
        assertEquals(HeroCardStyle.TIDE, repository.heroCardStyleStream.first())
        assertEquals(true, HeroCardStyle.usesArtworkColors(HeroCardStyle.TIDE))

        repository.setHeroCardStyle(HeroCardStyle.MARQUEE)
        assertEquals(HeroCardStyle.MARQUEE, UserPreferencesRepository(context).cachedHeroCardStyle)
        assertEquals(true, HeroCardStyle.usesArtworkColors(HeroCardStyle.MARQUEE))

        repository.setHeroCardStyle("unknown")
        assertEquals(HeroCardStyle.COVER, repository.heroCardStyleStream.first())
    }

    @Test
    fun unpickedDefaultsStayUnsavedSoALaterDefaultReachesThem() = runTest {
        val repository = UserPreferencesRepository(context)
        // Reading and hydrating must not save the default as if the listener had picked it.
        repository.heroCardStyleStream.first()
        repository.heroCoverShadeStream.first()
        repository.hydrateMissingDataStoreFromFastCache()
        val stored = context.userPreferencesDataStore.data.first()
        assertNull(stored[Keys.HERO_CARD_STYLE])
        assertNull(stored[Keys.HERO_COVER_SHADE])
        val cache = context.getSharedPreferences(PrefsFileMigrator.Files.THEME_FAST_CACHE, Context.MODE_PRIVATE)
        assertNull(cache.getString(HeroCardStyle.PREF_KEY, null))

        // A pick restored by Google Backup into the cache alone still reaches DataStore.
        cache.edit().putString(HeroCardStyle.PREF_KEY, HeroCardStyle.HALO).commit()
        repository.hydrateMissingDataStoreFromFastCache()
        assertEquals(HeroCardStyle.HALO, context.userPreferencesDataStore.data.first()[Keys.HERO_CARD_STYLE])
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
        // Unpicked reads as unset: artwork for the artwork styles, the app theme Block always had.
        assertEquals(HeroCoverShade.UNSET, repository.heroCoverShadeStream.first())
        assertEquals(HeroCoverShade.UNSET, repository.cachedHeroCoverShade)
        assertEquals(HeroCoverShade.ARTWORK, HeroCoverShade.sanitize(repository.cachedHeroCoverShade))
        assertEquals(HeroCoverShade.ARTWORK, HeroCoverShade.forStyle(HeroCardStyle.COVER, repository.cachedHeroCoverShade))
        assertEquals(HeroCoverShade.THEME, HeroCoverShade.forStyle(HeroCardStyle.BLOCK, repository.cachedHeroCoverShade))

        repository.setHeroCoverShade(HeroCoverShade.THEME)
        assertEquals(HeroCoverShade.THEME, repository.heroCoverShadeStream.first())
        assertEquals(HeroCoverShade.THEME, UserPreferencesRepository(context).cachedHeroCoverShade)

        repository.setHeroCoverShade(" DARK ")
        assertEquals(HeroCoverShade.DARK, repository.heroCoverShadeStream.first())
        // Once picked, Block follows the pick like every other style.
        assertEquals(HeroCoverShade.DARK, HeroCoverShade.forStyle(HeroCardStyle.BLOCK, repository.heroCoverShadeStream.first()))

        repository.setHeroCoverShade("sepia")
        assertEquals(HeroCoverShade.ARTWORK, repository.heroCoverShadeStream.first())
    }

    @Test
    fun tideProgressDefaultsToWavyPersistsAndRejectsUnknownValues() = runTest {
        val repository = UserPreferencesRepository(context)
        assertEquals(HeroTideProgress.WAVY, repository.heroTideProgressStream.first())
        assertEquals(HeroTideProgress.WAVY, repository.cachedHeroTideProgress)

        repository.setHeroTideProgress(" Circle ")
        assertEquals(HeroTideProgress.CIRCLE, repository.heroTideProgressStream.first())
        assertEquals(HeroTideProgress.CIRCLE, UserPreferencesRepository(context).cachedHeroTideProgress)

        repository.setHeroTideProgress("zigzag")
        assertEquals(HeroTideProgress.WAVY, repository.heroTideProgressStream.first())
    }
}
