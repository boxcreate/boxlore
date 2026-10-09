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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeSelectionPreferencesTest {
    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        clearThemeCache()
        repository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        clearThemeCache()
    }

    private fun clearThemeCache() {
        context
            .getSharedPreferences(PrefsFileMigrator.Files.THEME_FAST_CACHE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences(PrefsFileMigrator.LegacyFiles.THEME_FAST_CACHE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun artworkColorsDefaultOnAndOptOutSurvivesRecreationAndCacheRestore() = runTest {
        assertTrue(repository.artworkColorsEnabledStream.first())
        repository.setArtworkColorsEnabled(false)
        assertFalse(UserPreferencesRepository(context).artworkColorsEnabledStream.first())
        context.userPreferencesDataStore.edit { it.clear() }
        val restored = UserPreferencesRepository(context)
        assertFalse(restored.artworkColorsEnabledStream.first())
        restored.hydrateMissingDataStoreFromFastCache()
        assertEquals(false, context.userPreferencesDataStore.data.first()[Keys.ARTWORK_COLORS])
    }

    @Test
    fun completeThemeSelectionRemembersCustomWithoutChangingOtherPreferences() = runTest {
        repository.setThemeConfig("dark")
        repository.setFontRoundness("crisp")
        val custom = ThemeSelection("custom:1:#4422EE:#006677:auto", "dynamic_oled_white")
        repository.setThemeSelection(custom, custom)
        val material = ThemeSelection("violet", "standard", true)
        repository.setThemeSelection(material)
        assertEquals(material.brand, repository.themeBrandStream.first())
        assertEquals(material.surfaceStyle, repository.surfaceStyleStream.first())
        assertTrue(repository.useDynamicColorStream.first())
        assertEquals(custom, repository.customThemeStream.first())
        assertEquals("dark", repository.themeConfigStream.first())
        assertEquals("crisp", repository.fontRoundnessStream.first())
        repository.setThemeSelection(custom)
        assertEquals(custom.brand, repository.themeBrandStream.first())
        assertFalse(repository.useDynamicColorStream.first())
    }

    @Test
    fun legacyCustomThemeIsKeptExactlyAndHydratedFromFastCache() = runTest {
        val legacy = ThemeSelection("exact:#116677", "preset:paper", true)
        repository.setThemeSelection(ThemeSelection("violet", "classic_dynamic"), legacy)
        context.userPreferencesDataStore.edit { it.clear() }
        val restored = UserPreferencesRepository(context)
        assertEquals(legacy, restored.customThemeStream.first())
        restored.hydrateMissingDataStoreFromFastCache()
        assertEquals(legacy, restored.customThemeStream.first())
        assertEquals(legacy.brand, context.userPreferencesDataStore.data.first()[Keys.CUSTOM_THEME_BRAND])
    }
}
