package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Subscriptions Shows tab grid or list (`subscriptions_grid_view`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionsGridViewPreferenceTest {
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
    fun subscriptionsGridViewDefaultsToGridAndSurvivesAppRestart() = runTest {
        assertTrue(repository.subscriptionsGridViewStream.first())
        repository.setSubscriptionsGridView(false)
        // A fresh repository stands in for the app being closed from recents and opened again.
        assertFalse(UserPreferencesRepository(context).subscriptionsGridViewStream.first())
        repository.setSubscriptionsGridView(true)
        assertTrue(UserPreferencesRepository(context).subscriptionsGridViewStream.first())
    }
}
