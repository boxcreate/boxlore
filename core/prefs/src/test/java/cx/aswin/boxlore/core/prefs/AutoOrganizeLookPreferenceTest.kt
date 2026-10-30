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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The look auto-organize gives genre folders (`auto_organize_folder_size`, `auto_organize_tile_covers`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoOrganizeLookPreferenceTest {
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
    fun lookDefaultsToLetBoxloreChooseWithCovers() = runTest {
        assertNull(repository.autoOrganizeFolderSizeStream.first())
        assertTrue(repository.autoOrganizeTileCoversStream.first())
    }

    @Test
    fun aChosenLookIsKeptAndChoosingAgainClearsIt() = runTest {
        repository.setAutoOrganizeLook("COMPACT", tileCovers = false)
        val reopened = UserPreferencesRepository(context)
        assertEquals("COMPACT", reopened.autoOrganizeFolderSizeStream.first())
        assertFalse(reopened.autoOrganizeTileCoversStream.first())

        repository.setAutoOrganizeLook(null, tileCovers = true)
        assertNull(UserPreferencesRepository(context).autoOrganizeFolderSizeStream.first())
        assertTrue(UserPreferencesRepository(context).autoOrganizeTileCoversStream.first())
    }
}
