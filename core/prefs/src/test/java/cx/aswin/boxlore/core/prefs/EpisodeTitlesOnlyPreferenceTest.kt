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

/** Show pages' Episode titles only (`episode_titles_only`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeTitlesOnlyPreferenceTest {
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
    fun episodeTitlesOnlyDefaultsOffAndSurvivesAppRestart() = runTest {
        assertFalse(repository.episodeTitlesOnlyStream.first())
        repository.setEpisodeTitlesOnly(true)
        // A fresh repository stands in for the app being closed from recents and opened again.
        assertTrue(UserPreferencesRepository(context).episodeTitlesOnlyStream.first())
        repository.setEpisodeTitlesOnly(false)
        assertFalse(UserPreferencesRepository(context).episodeTitlesOnlyStream.first())
    }

    @Test
    fun episodeTitlesOnlyIsStoredUnderItsStableKeyAndLeavesHideCompletedAlone() = runTest {
        repository.setEpisodeTitlesOnly(true)
        val stored = context.userPreferencesDataStore.data.first()
        assertEquals(true, stored[Keys.EPISODE_TITLES_ONLY])
        assertEquals("episode_titles_only", Keys.EPISODE_TITLES_ONLY.name)
        assertFalse(repository.hideCompletedInShowDetailsStream.first())
    }
}
