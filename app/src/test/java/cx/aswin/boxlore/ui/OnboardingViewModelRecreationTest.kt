package cx.aswin.boxlore.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.onboarding.OnboardingViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * A real activity recreated mid-onboarding keeps its onboarding view model and the shows picked so
 * far, and reports nothing; only finishing it for good clears the view model and reports the
 * unfinished onboarding as abandoned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnboardingViewModelRecreationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var database: BoxLoreDatabase
    private lateinit var controller: ActivityController<ComponentActivity>
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restoreAnalytics: () -> Unit
    private var builds = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        RssPodcastRepository.clearInstanceForTests()
        BoxcastPrefs(context).setOnboardingCompleted(false)
        restoreAnalytics = AnalyticsHelper.installRecordingSink(events)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    }

    @After
    fun tearDown() {
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

    /** Sets the activity's content the way the app root does and returns the view model it gets. */
    private fun showOnboarding(): OnboardingViewModel {
        var viewModel: OnboardingViewModel? = null
        val activity = controller.get()
        activity.setContent { viewModel = rememberOnboardingViewModel(owner = activity, build = ::build) }
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(viewModel)
        return viewModel!!
    }

    @Test
    fun `recreating the activity mid-onboarding keeps the view model and its picks and reports nothing`() {
        val before = showOnboarding()
        before.onWelcomeScreenViewed()
        before.setShowsSelected(listOf(TestFixtures.podcast(id = "show-1"), TestFixtures.podcast(id = "show-2")), selected = true)

        controller.recreate()
        val after = showOnboarding()

        assertSame(before, after)
        assertEquals(1, builds)
        assertEquals(setOf("show-1", "show-2"), after.uiState.value.selectedPodcasts.keys)
        assertTrue(events.none { it.first == "onboarding_abandoned" })
    }

    @Test
    fun `finishing the activity after a recreation reports the unfinished onboarding once`() {
        showOnboarding().onWelcomeScreenViewed()
        controller.recreate()
        showOnboarding()

        controller.get().finish()
        controller.pause().stop().destroy()

        assertEquals(1, events.count { it.first == "onboarding_abandoned" })
    }
}
