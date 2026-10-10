package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class UserPreferencesRestoreHydrationTest {
    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        clearThemeCache()
        seedThemeCache(
            themeConfig = "dark",
            surfaceStyle = "amoled",
            themeBrand = "emerald",
            useDynamicColor = true,
            fontRoundness = "crisp",
            navigationStyle = "classic",
            openAppTo = OpenAppTo.DOWNLOADS,
            widgetAppearance = WidgetAppearance.SYSTEM,
            exploreDefaultTab = ExploreDefaultTab.TOP,
            subscriptionsDefaultTab = SubscriptionsDefaultTab.NEW_EPISODES,
        )
        repository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        clearThemeCache()
    }

    @Test
    fun themeStreamsKeepRestoredFastCacheWhenDataStoreIsEmpty() = runTest {
        assertEquals("dark", repository.themeConfigStream.first())
        assertEquals("amoled", repository.surfaceStyleStream.first())
        assertEquals("emerald", repository.themeBrandStream.first())
        assertTrue(repository.useDynamicColorStream.first())
        assertEquals("crisp", repository.fontRoundnessStream.first())
        assertEquals("classic", repository.navigationStyleStream.first())
        assertEquals(OpenAppTo.DOWNLOADS, repository.openAppToStream.first())
        assertEquals(WidgetAppearance.SYSTEM, repository.widgetAppearanceStream.first())
        assertEquals(ExploreDefaultTab.TOP, repository.exploreDefaultTabStream.first())
        assertEquals(SubscriptionsDefaultTab.NEW_EPISODES, repository.subscriptionsDefaultTabStream.first())
        assertEquals("dark", repository.cachedThemeConfig)
    }

    @Test
    fun hydrateMissingDataStoreFromFastCacheWritesRestoredAppearance() = runTest {
        repository.hydrateMissingDataStoreFromFastCache()

        assertEquals("dark", repository.themeConfigStream.first())
        assertEquals("amoled", repository.surfaceStyleStream.first())
        assertEquals("emerald", repository.themeBrandStream.first())
        assertTrue(repository.useDynamicColorStream.first())
        assertEquals("crisp", repository.fontRoundnessStream.first())
        assertEquals("classic", repository.navigationStyleStream.first())
        assertEquals(OpenAppTo.DOWNLOADS, repository.openAppToStream.first())
        assertEquals(WidgetAppearance.SYSTEM, repository.widgetAppearanceStream.first())
        assertEquals(ExploreDefaultTab.TOP, repository.exploreDefaultTabStream.first())
        assertEquals(SubscriptionsDefaultTab.NEW_EPISODES, repository.subscriptionsDefaultTabStream.first())
    }

    @Test
    fun concurrentAppearanceReadersKeepFastCacheDuringBackgroundHydration() = runTest {
        val reads = listOf(
            async { repository.themeConfigStream.first() },
            async { repository.surfaceStyleStream.first() },
            async { repository.navigationStyleStream.first() },
        )
        val hydration = async { repository.hydrateMissingDataStoreFromFastCache() }
        assertEquals(listOf("dark", "amoled", "classic"), reads.awaitAll())
        hydration.await()
        assertEquals("dark", repository.cachedThemeConfig)
        assertEquals("amoled", repository.cachedSurfaceStyle)
    }

    @Test
    fun repeatedHydrationPreservesStoredFalseAndExistingStrings() = runTest {
        context.userPreferencesDataStore.edit {
            it[Keys.USE_DYNAMIC_COLOR] = false
            it[Keys.THEME_BRAND] = "violet"
            it[Keys.OPEN_APP_TO] = OpenAppTo.HOME
        }

        repeat(2) { repository.hydrateMissingDataStoreFromFastCache() }

        assertFalse(repository.useDynamicColorStream.first())
        assertEquals("violet", repository.themeBrandStream.first())
        assertEquals(OpenAppTo.HOME, repository.openAppToStream.first())
        assertEquals("dark", repository.themeConfigStream.first())
        assertEquals("amoled", repository.surfaceStyleStream.first())
    }

    @Test
    fun backgroundHydrationDoesNotOverwriteAnExistingAppearanceChoice() = runTest {
        repository.setThemeConfig("light")
        repository.hydrateMissingDataStoreFromFastCache()
        assertEquals("light", repository.themeConfigStream.first())
        assertEquals("light", repository.cachedThemeConfig)
        assertEquals("amoled", repository.surfaceStyleStream.first())
    }

    private fun seedThemeCache(
        themeConfig: String,
        surfaceStyle: String,
        themeBrand: String,
        useDynamicColor: Boolean,
        fontRoundness: String,
        navigationStyle: String,
        openAppTo: String,
        widgetAppearance: String,
        exploreDefaultTab: String,
        subscriptionsDefaultTab: String,
    ) {
        context
            .getSharedPreferences(PrefsFileMigrator.Files.THEME_FAST_CACHE, Context.MODE_PRIVATE)
            .edit()
            .putString("theme_config", themeConfig)
            .putString("surface_style", surfaceStyle)
            .putString("theme_brand", themeBrand)
            .putBoolean("use_dynamic_color", useDynamicColor)
            .putString(FontRoundnessAxis.PREF_KEY, fontRoundness)
            .putString("navigation_style", navigationStyle)
            .putString("open_app_to", openAppTo)
            .putString(WidgetAppearance.PREF_KEY, widgetAppearance)
            .putString(ExploreDefaultTab.PREF_KEY, exploreDefaultTab)
            .putString(SubscriptionsDefaultTab.PREF_KEY, subscriptionsDefaultTab)
            .commit()
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
}
