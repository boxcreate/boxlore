package cx.aswin.boxlore.feature.settings.feedback

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.FeedbackDraft
import cx.aswin.boxlore.core.support.OutgoingFeedback
import cx.aswin.boxlore.core.support.SendResult
import cx.aswin.boxlore.core.support.SupportMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var prefs: BoxcastPrefs

    /** Records what the screen sends; [result] and [photoIds] decide the answers. */
    private class FakeSupport(meta: SupportMeta = SupportMeta.Offline) {
        val meta = MutableStateFlow(meta)
        val sent = mutableListOf<OutgoingFeedback>()
        val uploads = mutableListOf<Int>()
        var result: SendResult = SendResult.Sent("t1")
        var photoIds: (Int) -> String? = { "p$it" }
        var refreshes = 0

        fun asSupport() = FeedbackSupport(
            meta = meta,
            refresh = { refreshes++ },
            uploadPhoto = { bytes, _ ->
                uploads += bytes.size
                photoIds(uploads.size)
            },
            send = { feedback ->
                sent += feedback
                result
            },
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = BoxcastPrefs(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(support: FakeSupport, readPhoto: (Uri) -> ByteArray? = { ByteArray(10) }) =
        FeedbackViewModel(support.asSupport(), prefs, context, ioDispatcher = testDispatcher, readPhoto = readPhoto)

    @Test
    fun an_old_draft_comes_back_with_its_steps_joined_to_the_message() {
        prefs.saveFeedbackDraft(
            FeedbackDraft(
                category = "bug",
                message = "Player stops after 3 minutes",
                email = "listener@example.com",
                stepsToReproduce = "1. Play\n2. Wait",
                attachDiagnostics = true,
            ),
        )
        val state = viewModel(FakeSupport()).uiState.value

        assertEquals(FeedbackCategory.BUG, state.category)
        assertEquals("Player stops after 3 minutes\n\n1. Play\n2. Wait", state.message)
        assertTrue(state.attachDiagnostics)
        assertTrue(state.isDraftRestored)
    }

    @Test
    fun typing_saves_the_draft_and_an_empty_message_clears_it() {
        val vm = viewModel(FakeSupport())
        vm.onMessageChanged("Half a thought")
        assertEquals("Half a thought", prefs.getFeedbackDraft()?.message)
        assertEquals("", prefs.getFeedbackDraft()?.email)

        vm.onMessageChanged("")
        assertNull(prefs.getFeedbackDraft())
    }

    @Test
    fun the_message_is_cut_at_the_servers_limit() = runTest(testDispatcher) {
        val support = FakeSupport(SupportMeta.Offline.copy(maxBody = 5))
        val vm = viewModel(support)
        advanceUntilIdle()
        vm.onMessageChanged("1234567890")
        assertEquals("12345", vm.uiState.value.message)
        assertEquals(1, support.refreshes)
    }

    @Test
    fun a_conversation_send_clears_the_draft_and_promises_a_reply_in_the_app() = runTest(testDispatcher) {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsHelper.installRecordingSink(events)
        try {
            val support = FakeSupport()
            val vm = viewModel(support)
            vm.onCategorySelected(FeedbackCategory.AUDIO)
            vm.onMessageChanged("  Stream cuts off at end of track  ")

            vm.onSubmit()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isSuccess)
            assertTrue(state.repliesInApp)
            assertNull(prefs.getFeedbackDraft())
            val sent = support.sent.single()
            assertEquals("audio", sent.category)
            assertEquals("Stream cuts off at end of track", sent.body)
            assertTrue(sent.diagnostics!!.contains("System Diagnostics"))
            assertNotNull(sent.device)
            val event = events.single { it.first == "feedback_sent" }.second
            assertEquals("bug", event["category"])
            assertEquals(false, event["with_email"])
            assertTrue(event.values.none { it == "Stream cuts off at end of track" })
        } finally {
            restore()
        }
    }

    @Test
    fun a_send_through_the_old_form_does_not_promise_a_reply_in_the_app() = runTest(testDispatcher) {
        val support = FakeSupport().apply { result = SendResult.SentByEmailForm }
        val vm = viewModel(support)
        vm.onMessageChanged("An idea")

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSuccess)
        assertFalse(vm.uiState.value.repliesInApp)
    }

    @Test
    fun a_refusal_shows_the_servers_words_and_keeps_the_draft() = runTest(testDispatcher) {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsHelper.installRecordingSink(events)
        try {
            val support = FakeSupport().apply { result = SendResult.Refused("You've sent a lot today. Try again tomorrow.") }
            val vm = viewModel(support)
            vm.onMessageChanged("Another one")

            vm.onSubmit()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isSuccess)
            assertFalse(state.isSubmitting)
            assertEquals("You've sent a lot today. Try again tomorrow.", state.errorMessage)
            assertEquals("Another one", prefs.getFeedbackDraft()?.message)
            assertTrue(events.none { it.first == "feedback_sent" })
        } finally {
            restore()
        }
    }

    @Test
    fun a_failed_send_keeps_the_message_and_can_be_retried() = runTest(testDispatcher) {
        val support = FakeSupport().apply { result = SendResult.Failed }
        val vm = viewModel(support)
        vm.onMessageChanged("Try me twice")

        vm.onSubmit()
        advanceUntilIdle()
        assertEquals("Couldn't send your feedback. Check your connection and try again.", vm.uiState.value.errorMessage)
        assertEquals("Try me twice", vm.uiState.value.message)

        support.result = SendResult.Sent("t2")
        vm.onSubmit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSuccess)
        assertEquals(2, support.sent.size)
    }

    @Test
    fun a_resend_of_the_same_message_keeps_its_retry_key_and_an_edit_gets_a_new_one() = runTest(testDispatcher) {
        val support = FakeSupport().apply { result = SendResult.Failed }
        val vm = viewModel(support)
        vm.onMessageChanged("Try me")

        vm.onSubmit()
        advanceUntilIdle()
        vm.onSubmit()
        advanceUntilIdle()
        vm.onMessageChanged("Try me, edited")
        vm.onSubmit()
        advanceUntilIdle()
        support.result = SendResult.Sent("t1")
        vm.onSubmit()
        advanceUntilIdle()

        val keys = support.sent.map { it.requestId }
        assertEquals(4, keys.size)
        assertTrue(keys.all { !it.isNullOrBlank() })
        assertEquals(keys[0], keys[1])
        assertTrue(keys[1] != keys[2])
        assertEquals(keys[2], keys[3])
    }

    @Test
    fun retry_keys_change_with_content_and_start_over_once_cleared() {
        var n = 0
        val keys = RetryKeys { "key-${++n}" }
        assertEquals("key-1", keys.keyFor(listOf("bug", "a")))
        assertEquals("key-1", keys.keyFor(listOf("bug", "a")))
        assertEquals("key-2", keys.keyFor(listOf("bug", "b")))
        keys.clear()
        assertEquals("key-3", keys.keyFor(listOf("bug", "b")))
    }

    @Test
    fun a_blank_message_is_never_sent() = runTest(testDispatcher) {
        val support = FakeSupport()
        val vm = viewModel(support)
        vm.onMessageChanged("   ")

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(support.sent.isEmpty())
        assertEquals("Write a message first.", vm.uiState.value.errorMessage)
    }

    @Test
    fun photos_are_ignored_while_the_server_has_them_off() = runTest(testDispatcher) {
        val vm = viewModel(FakeSupport())
        advanceUntilIdle()
        vm.onPhotosPicked(listOf(Uri.parse("content://a")))
        assertTrue(vm.uiState.value.photos.isEmpty())
        assertFalse(vm.uiState.value.canAddPhotos)
    }

    @Test
    fun photos_upload_in_order_before_the_message_and_stop_at_the_limit() = runTest(testDispatcher) {
        val support = FakeSupport(SupportMeta.Offline.copy(photosEnabled = true, maxPhotos = 2, maxPhotoBytes = 100))
        val vm = viewModel(support)
        advanceUntilIdle()
        val a = Uri.parse("content://a")
        vm.onPhotosPicked(listOf(a, a, Uri.parse("content://b"), Uri.parse("content://c")))
        assertEquals(listOf("content://a", "content://b"), vm.uiState.value.photos.map { it.uri.toString() })
        assertFalse(vm.uiState.value.canAddPhotos)

        vm.onMessageChanged("See the screenshot")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("p1", "p2"), support.sent.single().photoIds)
        assertTrue(vm.uiState.value.photos.isEmpty())
    }

    @Test
    fun a_failed_photo_upload_sends_nothing() = runTest(testDispatcher) {
        val support = FakeSupport(SupportMeta.Offline.copy(photosEnabled = true, maxPhotos = 3, maxPhotoBytes = 100))
        support.photoIds = { index -> if (index == 2) null else "p$index" }
        val vm = viewModel(support)
        advanceUntilIdle()
        vm.onPhotosPicked(listOf(Uri.parse("content://a"), Uri.parse("content://b")))
        vm.onMessageChanged("With photos")

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(support.sent.isEmpty())
        assertFalse(vm.uiState.value.isSuccess)
        assertEquals("Couldn't attach your photos. Check your connection and try again.", vm.uiState.value.errorMessage)
        assertEquals(2, vm.uiState.value.photos.size)
    }

    @Test
    fun a_photo_over_the_size_limit_sends_nothing() = runTest(testDispatcher) {
        val support = FakeSupport(SupportMeta.Offline.copy(photosEnabled = true, maxPhotos = 3, maxPhotoBytes = 5))
        val vm = viewModel(support) { ByteArray(6) }
        advanceUntilIdle()
        vm.onPhotosPicked(listOf(Uri.parse("content://a")))
        vm.onMessageChanged("Big photo")

        vm.onSubmit()
        advanceUntilIdle()

        assertTrue(support.uploads.isEmpty())
        assertTrue(support.sent.isEmpty())
    }

    @Test
    fun paused_sending_disables_send_and_explains_why() = runTest(testDispatcher) {
        val support = FakeSupport(SupportMeta.Offline.copy(canStartNew = false))
        val vm = viewModel(support)
        advanceUntilIdle()
        vm.onMessageChanged("Waiting")

        assertFalse(vm.uiState.value.canSend)
        assertEquals(PAUSED_NOTICE, vm.uiState.value.shownNotice)

        support.meta.value = SupportMeta.Offline.copy(canStartNew = false, notice = "Back on Monday.")
        advanceUntilIdle()
        assertEquals("Back on Monday.", vm.uiState.value.shownNotice)
    }

    @Test
    fun discarding_the_draft_resets_everything() = runTest(testDispatcher) {
        val support = FakeSupport(SupportMeta.Offline.copy(photosEnabled = true, maxPhotos = 3, maxPhotoBytes = 100))
        val vm = viewModel(support)
        advanceUntilIdle()
        vm.onCategorySelected(FeedbackCategory.BUG)
        vm.onMessageChanged("Never mind")
        vm.onPhotosPicked(listOf(Uri.parse("content://a")))

        vm.onDiscardDraft()

        val state = vm.uiState.value
        assertEquals(FeedbackCategory.FEATURE, state.category)
        assertEquals("", state.message)
        assertTrue(state.photos.isEmpty())
        assertNull(prefs.getFeedbackDraft())
    }

    @Test
    fun buildGitHubIssueUrl_creates_valid_URL_with_encoded_title() {
        val vm = viewModel(FakeSupport())
        vm.onCategorySelected(FeedbackCategory.BUG)
        vm.onMessageChanged("Crash when clicking download button")

        val url = buildFeedbackGitHubIssueUrl(vm.uiState.value)
        assertTrue(url.startsWith("https://github.com/boxcreate/boxlore/issues/new?title="))
        assertTrue(url.contains("Crash"))
        assertTrue(url.contains("body="))
    }

    @Test
    fun category_selection_toggles_attachDiagnostics_default_for_bug_reports() {
        val vm = viewModel(FakeSupport())
        assertEquals(FeedbackCategory.FEATURE, vm.uiState.value.category)
        assertFalse(vm.uiState.value.attachDiagnostics)

        vm.onCategorySelected(FeedbackCategory.BUG)
        assertTrue(vm.uiState.value.attachDiagnostics)

        vm.onCategorySelected(FeedbackCategory.AUDIO)
        assertTrue(vm.uiState.value.attachDiagnostics)

        vm.onCategorySelected(FeedbackCategory.FEATURE)
        assertFalse(vm.uiState.value.attachDiagnostics)

        vm.onCategorySelected(FeedbackCategory.OTHER)
        assertFalse(vm.uiState.value.attachDiagnostics)
    }
}
