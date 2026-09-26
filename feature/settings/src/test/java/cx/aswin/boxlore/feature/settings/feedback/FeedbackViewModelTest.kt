package cx.aswin.boxlore.feature.settings.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.FeedbackDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var prefs: BoxcastPrefs
    private lateinit var dummyPodcastRepository: PodcastRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = BoxcastPrefs(context)
        dummyPodcastRepository = mock(PodcastRepository::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_restores_draft_from_BoxcastPrefs_when_present() {
        prefs.saveFeedbackDraft(
            FeedbackDraft(
                category = "bug",
                message = "Player stops after 3 minutes",
                email = "listener@example.com",
                stepsToReproduce = "1. Play\n2. Wait",
                attachDiagnostics = true,
            ),
        )

        val vm = FeedbackViewModel(dummyPodcastRepository, prefs, context)
        val state = vm.uiState.value

        assertEquals(FeedbackCategory.BUG, state.category)
        assertEquals("Player stops after 3 minutes", state.message)
        assertEquals("listener@example.com", state.email)
        assertEquals("1. Play\n2. Wait", state.stepsToReproduce)
        assertTrue(state.attachDiagnostics)
        assertTrue(state.isDraftRestored)
    }

    @Test
    fun editing_fields_updates_state_and_persists_draft() {
        val vm = FeedbackViewModel(dummyPodcastRepository, prefs, context)

        vm.onCategorySelected(FeedbackCategory.FEATURE)
        vm.onMessageChanged("Add dark AMOLED theme")
        vm.onEmailChanged("fan@example.com")
        vm.onAttachDiagnosticsChanged(false)

        val state = vm.uiState.value
        assertEquals(FeedbackCategory.FEATURE, state.category)
        assertEquals("Add dark AMOLED theme", state.message)
        assertEquals("fan@example.com", state.email)
        assertFalse(state.attachDiagnostics)

        val savedDraft = prefs.getFeedbackDraft()
        assertNotNull(savedDraft)
        assertEquals("feature", savedDraft?.category)
        assertEquals("Add dark AMOLED theme", savedDraft?.message)
        assertEquals("fan@example.com", savedDraft?.email)
        assertEquals(false, savedDraft?.attachDiagnostics)
    }

    @Test
    fun onDiscardDraft_resets_state_and_clears_draft_from_BoxcastPrefs() {
        prefs.saveFeedbackDraft(
            FeedbackDraft(
                category = "other",
                message = "Something random",
                stepsToReproduce = "step 1",
            ),
        )

        val vm = FeedbackViewModel(dummyPodcastRepository, prefs, context)
        assertTrue(vm.uiState.value.isDraftRestored)

        vm.onDiscardDraft()

        val state = vm.uiState.value
        assertEquals("", state.message)
        assertEquals("", state.stepsToReproduce)
        assertFalse(state.isDraftRestored)
        assertNull(prefs.getFeedbackDraft())
    }

    @Test
    fun onSubmit_with_blank_message_sets_error_and_does_not_call_repository() = runTest(testDispatcher) {
        var called = false
        val vm = FeedbackViewModel(
            dummyPodcastRepository,
            prefs,
            context,
            submitFeedbackAction = { _, _, _, _ ->
                called = true
                true
            },
        )
        vm.onMessageChanged("   ")

        vm.onSubmit()

        val state = vm.uiState.value
        assertNotNull(state.errorMessage)
        assertFalse(state.isSubmitting)
        assertFalse(called)
    }

    @Test
    fun onSubmit_success_clears_draft_and_marks_success() = runTest(testDispatcher) {
        var capturedCategory: String? = null
        var capturedEmail: String? = null

        val vm = FeedbackViewModel(
            dummyPodcastRepository,
            prefs,
            context,
            submitFeedbackAction = { cat, _, _, mail ->
                capturedCategory = cat
                capturedEmail = mail
                true
            },
        )
        vm.onCategorySelected(FeedbackCategory.FEATURE)
        vm.onMessageChanged("Please support OPML tagging")
        vm.onEmailChanged("dev@example.com")

        vm.onSubmit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isSubmitting)
        assertTrue(state.isSuccess)
        assertEquals("feature", capturedCategory)
        assertEquals("dev@example.com", capturedEmail)
        assertNull(prefs.getFeedbackDraft())
    }

    @Test
    fun onSubmit_with_audio_category_maps_to_bug_on_wire() = runTest(testDispatcher) {
        var capturedCategory: String? = null

        val vm = FeedbackViewModel(
            dummyPodcastRepository,
            prefs,
            context,
            submitFeedbackAction = { cat, _, _, _ ->
                capturedCategory = cat
                true
            },
        )
        vm.onCategorySelected(FeedbackCategory.AUDIO)
        vm.onMessageChanged("Stream cuts off at end of track")

        vm.onSubmit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isSuccess)
        assertEquals("bug", capturedCategory)
    }

    @Test
    fun buildGitHubIssueUrl_creates_valid_URL_with_encoded_title() {
        val vm = FeedbackViewModel(dummyPodcastRepository, prefs, context)
        vm.onCategorySelected(FeedbackCategory.BUG)
        vm.onMessageChanged("Crash when clicking download button")
        vm.onStepsChanged("1. Tap download\n2. Observe crash")

        val url = buildFeedbackGitHubIssueUrl(vm.uiState.value)
        assertTrue(url.startsWith("https://github.com/boxcreate/boxlore/issues/new?title="))
        assertTrue(url.contains("Crash"))
        assertTrue(url.contains("body="))
    }

    @Test
    fun category_selection_toggles_attachDiagnostics_default_for_bug_reports() {
        val vm = FeedbackViewModel(dummyPodcastRepository, prefs, context)
        // Default category is FEATURE -> attachDiagnostics should be false
        assertEquals(FeedbackCategory.FEATURE, vm.uiState.value.category)
        assertFalse(vm.uiState.value.attachDiagnostics)

        // Switching to BUG should default attachDiagnostics to true
        vm.onCategorySelected(FeedbackCategory.BUG)
        assertEquals(FeedbackCategory.BUG, vm.uiState.value.category)
        assertTrue(vm.uiState.value.attachDiagnostics)

        // Switching to AUDIO should keep attachDiagnostics true
        vm.onCategorySelected(FeedbackCategory.AUDIO)
        assertEquals(FeedbackCategory.AUDIO, vm.uiState.value.category)
        assertTrue(vm.uiState.value.attachDiagnostics)

        // Switching back to FEATURE should set attachDiagnostics to false
        vm.onCategorySelected(FeedbackCategory.FEATURE)
        assertEquals(FeedbackCategory.FEATURE, vm.uiState.value.category)
        assertFalse(vm.uiState.value.attachDiagnostics)

        // Switching to OTHER should set attachDiagnostics to false
        vm.onCategorySelected(FeedbackCategory.OTHER)
        assertEquals(FeedbackCategory.OTHER, vm.uiState.value.category)
        assertFalse(vm.uiState.value.attachDiagnostics)
    }
}
