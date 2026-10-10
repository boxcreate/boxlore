package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
class AnnouncementPreferencesTest {
    private lateinit var repository: UserPreferencesRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        context.getSharedPreferences(PrefsFileMigrator.Files.THEME_FAST_CACHE, Context.MODE_PRIVATE).edit().clear().commit()
        repository = UserPreferencesRepository(context)
    }

    @After fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        context.getSharedPreferences(PrefsFileMigrator.Files.THEME_FAST_CACHE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    // ---- Announcement ----

    @Test
    fun announcementDefaultsNullAndRoundTrips() = runTest {
        assertNull(repository.activeAnnouncementStream.first())

        val announcement =
            UserPreferencesRepository.Announcement(
                title = "Hello",
                body = "World",
                route = "boxcast://home",
                imageUrl = "https://example.com/x.jpg",
                actionLabel = "Open",
                showActionInApp = true,
                timestamp = 42L,
                category = "WHAT'S NEW",
                presentation = "fullscreen",
                tone = "tertiary",
                imageStyle = "cover",
                releaseAlert = true,
                includePlay = true,
                testOnly = true,
                releaseVersionCode = 29,
                releaseUrl = "https://github.com/boxcreate/boxlore/releases/tag/v29",
            )
        repository.setAnnouncement(announcement)

        val stored = repository.activeAnnouncementStream.first()!!
        assertEquals("Hello", stored.title)
        assertEquals("boxcast://home", stored.route)
        assertEquals(42L, stored.timestamp)
        assertEquals("fullscreen", stored.presentation)
        assertEquals("tertiary", stored.tone)
        assertEquals("cover", stored.imageStyle)
        assertEquals(true, stored.releaseAlert)
        assertEquals(true, stored.includePlay)
        assertEquals(true, stored.testOnly)
        assertEquals(29L, stored.releaseVersionCode)
        assertEquals("https://github.com/boxcreate/boxlore/releases/tag/v29", stored.releaseUrl)

        repository.clearAnnouncement()
        assertNull(repository.activeAnnouncementStream.first())
    }

    @Test
    fun legacyAnnouncementKeepsDefaultsAndClearingPreservesOtherPreferences() = runTest {
        context.userPreferencesDataStore.edit {
            it[stringPreferencesKey("announcement_title")] = "Legacy"
            it[stringPreferencesKey("announcement_body")] = "Message"
            it[stringPreferencesKey("theme_brand")] = "classic"
            it[stringPreferencesKey("subscription_manual_order")] = "123"
        }
        val restored = UserPreferencesRepository(context).activeAnnouncementStream.first()!!
        assertEquals("compact", restored.presentation)
        assertEquals("primary", restored.tone)
        assertNull(restored.releaseAlert)
        assertEquals(false, restored.includePlay)
        repository.clearAnnouncement()
        assertNull(repository.activeAnnouncementStream.first())
        assertEquals("classic", repository.themeBrandStream.first())
        assertEquals(listOf("123"), repository.subscriptionManualOrderStream.first())
        assertEquals("cx.aswin.boxlore.core.prefs.UserPreferencesRepository$" + "Announcement", restored.javaClass.name)
    }

    @Test
    fun announcementWithBlankTitleIsNotSurfaced() = runTest {
        repository.setAnnouncement(
            UserPreferencesRepository.Announcement(
                title = "   ",
                body = "body",
                route = null,
                imageUrl = null,
                actionLabel = null,
                showActionInApp = false,
                timestamp = 1L,
                category = "X",
            ),
        )
        assertNull(repository.activeAnnouncementStream.first())
    }
}
