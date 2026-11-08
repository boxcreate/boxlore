package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.feature.onboarding.OnboardingViewModel
import cx.aswin.boxlore.feature.onboarding.markOnboardingCompletedSilent
import kotlinx.coroutines.isActive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The onboarding view model lives in the activity's view model store: every onboarding flow shares
 * one instance (so picks carry over between search, Echo and the import sheet), and finishing the
 * activity clears it. Created with `remember` it was never cleared, so an unfinished onboarding was
 * never reported as abandoned and its Echo work was never cancelled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnboardingViewModelScopeTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var database: BoxLoreDatabase
    private val store = ViewModelStore()
    private val activityOwner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
    }
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restoreAnalytics: () -> Unit
    private var builds = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        RssPodcastRepository.clearInstanceForTests()
        BoxcastPrefs(context).setOnboardingCompleted(false)
        restoreAnalytics = AnalyticsHelper.installRecordingSink(events)
    }

    @After
    fun tearDown() {
        store.clear()
        restoreAnalytics()
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    private fun build(): OnboardingViewModel {
        builds++
        val podcastRepository =
            PodcastRepository(
                baseUrl = "http://localhost/",
                publicKey = "test-app-key",
                context = context,
                rssRepository = RssPodcastRepository.createForTests(context = context, database = database),
            )
        return OnboardingViewModel(context, podcastRepository, SubscriptionRepository(database.podcastDao()), UserPreferencesRepository(context))
    }

    @Test
    fun `every onboarding flow gets the same instance while the activity lives`() {
        var showImportSheet by mutableStateOf(false)
        var showOnboarding by mutableStateOf(true)
        val seen = mutableListOf<OnboardingViewModel>()
        composeRule.setContent {
            // Separate call sites stand for the onboarding route, the import sheet and Home's AI entry.
            if (showOnboarding) seen += rememberOnboardingViewModel(activityOwner, ::build)
            if (showImportSheet) seen += rememberOnboardingViewModel(activityOwner, ::build)
        }

        composeRule.runOnIdle { showImportSheet = true }
        // Leaving onboarding (e.g. for the sign-in detour) and coming back keeps the same instance.
        composeRule.runOnIdle { showOnboarding = false }
        composeRule.runOnIdle { showOnboarding = true }
        composeRule.waitForIdle()

        assertEquals(1, builds)
        assertTrue(seen.size >= 3)
        seen.forEach { assertSame(seen.first(), it) }
    }

    @Test
    fun `finishing the activity mid-onboarding reports it abandoned once and stops its work`() {
        lateinit var viewModel: OnboardingViewModel
        composeRule.setContent { viewModel = rememberOnboardingViewModel(activityOwner, ::build) }
        composeRule.runOnIdle { viewModel.onWelcomeScreenViewed() }

        composeRule.runOnIdle { store.clear() }

        val abandoned = events.filter { it.first == "onboarding_abandoned" }
        assertEquals(1, abandoned.size)
        assertTrue(abandoned.single().second.containsKey("last_step"))
        assertFalse(viewModel.viewModelScope.isActive)
    }

    @Test
    fun `a finished onboarding is not reported when the activity finishes`() {
        lateinit var viewModel: OnboardingViewModel
        composeRule.setContent { viewModel = rememberOnboardingViewModel(activityOwner, ::build) }
        composeRule.runOnIdle {
            viewModel.onWelcomeScreenViewed()
            viewModel.markOnboardingCompletedSilent {}
        }

        composeRule.runOnIdle { store.clear() }

        assertTrue(events.none { it.first == "onboarding_abandoned" })
    }

    @Test
    fun `an onboarding never started is not reported when the activity finishes`() {
        composeRule.setContent { rememberOnboardingViewModel(activityOwner, ::build) }

        composeRule.runOnIdle { store.clear() }

        assertTrue(events.none { it.first == "onboarding_abandoned" })
    }
}
