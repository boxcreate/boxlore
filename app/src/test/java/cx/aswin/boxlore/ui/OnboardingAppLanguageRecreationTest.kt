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
import cx.aswin.boxlore.core.prefs.AppLanguage
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.feature.onboarding.OnboardingStep
import cx.aswin.boxlore.feature.onboarding.OpeningHandoff
import cx.aswin.boxlore.feature.onboarding.OnboardingViewModel
import cx.aswin.boxlore.feature.onboarding.chooseAppLanguage
import cx.aswin.boxlore.feature.onboarding.finishOpeningHandoff
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Onboarding's language question in a real activity: it opens onboarding when the phone suggests a
 * translation, the question, its answer and the panel hand-off to Welcome all survive the recreation
 * a language change causes, and the pick is reported once with the existing settings event.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnboardingAppLanguageRecreationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var database: BoxLoreDatabase
    private lateinit var controller: ActivityController<ComponentActivity>
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restoreAnalytics: () -> Unit
    private var suggestionAsked = 0

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
        val podcastRepository = PodcastRepository(
            baseUrl = "http://localhost/",
            publicKey = "test-app-key",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context = context, database = database),
        )
        return OnboardingViewModel(
            context,
            podcastRepository,
            SubscriptionRepository(database.podcastDao()),
            UserPreferencesRepository(context),
            appLanguageSuggestion = {
                suggestionAsked++
                AppLanguage.SPANISH
            },
        )
    }

    private fun showOnboarding(): OnboardingViewModel {
        var viewModel: OnboardingViewModel? = null
        val activity = controller.get()
        activity.setContent { viewModel = rememberOnboardingViewModel(owner = activity, build = ::build) }
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(viewModel)
        return viewModel!!
    }

    @Test
    fun `the question opens onboarding and its answer survives the language change's recreation`() {
        val before = showOnboarding()
        assertEquals(OnboardingStep.APP_LANGUAGE, before.uiState.value.currentStep)
        assertEquals(AppLanguage.SPANISH, before.uiState.value.appLanguageQuestion?.suggested)

        controller.recreate()
        val reopened = showOnboarding()
        assertSame(before, reopened)
        assertEquals(AppLanguage.SPANISH, reopened.uiState.value.appLanguageQuestion?.suggested)

        val handoff = OpeningHandoff(0.42f, twoPane = false, AppLanguage.SPANISH)
        reopened.chooseAppLanguage(AppLanguage.SPANISH, handoff)
        controller.recreate()
        val after = showOnboarding()

        assertSame(before, after)
        assertEquals(OnboardingStep.WELCOME, after.uiState.value.currentStep)
        assertNull(after.uiState.value.appLanguageQuestion)
        // Welcome takes over the question's panel after the recreation, then shows as usual.
        assertEquals(handoff, after.uiState.value.openingHandoff)
        after.finishOpeningHandoff()
        assertNull(after.uiState.value.openingHandoff)
        assertEquals(1, suggestionAsked)
        val pick = events.single { it.first == "settings_interaction" }.second
        assertEquals("onboarding_app_language_chosen", pick["action"])
        assertEquals("es", pick["value"])
        // A second pick (a late tap during the change) does nothing.
        after.chooseAppLanguage(AppLanguage.ENGLISH)
        assertEquals(1, events.count { it.first == "settings_interaction" })
    }

    @Test
    fun `a finished onboarding never asks`() {
        BoxcastPrefs(context).setOnboardingCompleted(true)
        val viewModel = showOnboarding()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
        assertEquals(0, suggestionAsked)
        BoxcastPrefs(context).setOnboardingCompleted(false)
    }
}
