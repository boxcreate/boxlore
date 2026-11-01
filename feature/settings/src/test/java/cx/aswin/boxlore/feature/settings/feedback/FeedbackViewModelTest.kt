package cx.aswin.boxlore.feature.settings.feedback

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.FeedbackDraft
import cx.aswin.boxlore.core.support.OutgoingFeedback
import cx.aswin.boxlore.core.support.PhotoUpload
import cx.aswin.boxlore.core.support.SupportCategory
import cx.aswin.boxlore.core.support.SendResult
import cx.aswin.boxlore.core.support.SupportMeta
import kotlinx.coroutines.CompletableDeferred
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
        var photoIds: (Int) -> PhotoUpload = { PhotoUpload.Uploaded("p$it") }
        /** While set, send waits for it. */
        var sendGate: CompletableDeferred<Unit>? = null
        var refreshes = 0

        fun asSupport() = FeedbackSupport(
            meta = meta,
            refresh = { refreshes++ },
            uploadPhoto = { bytes, _ ->
                uploads += bytes.size
                photoIds(uploads.size)
            },
            send = { feedback ->
                sendGate?.await()
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

        assertEquals("bug", state.choice.id)
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
            vm.onCategorySelected(FeedbackCategory.AUDIO.id)
            vm.onMessageChanged("  Stream cuts off at end of track  ")

            vm.onSubmit()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isSuccess)
            assertTrue(state.repliesInApp)
            assertEquals("Refreshed on open and again after the first conversation", 2, support.refreshes)
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
    fun a_retry_key_outlives_the_screen_and_photos_upload_once() = runTest(testDispatcher) {
        val saved = object : RetryKeyStore {
            var value: Pair<String, String>? = null
            override fun load() = value
            override fun save(fingerprint: String, key: String) { value = fingerprint to key }
            override fun clear() { value = null }
        }
        val support = FakeSupport(SupportMeta.Offline.copy(photosEnabled = true, maxPhotos = 3, maxPhotoBytes = 100)).apply { result = SendResult.Failed }
        val first = FeedbackViewModel(support.asSupport(), prefs, context, ioDispatcher = testDispatcher, readPhoto = { ByteArray(10) }, retryKeys = RetryKeys(store = saved))
        advanceUntilIdle()
        first.onPhotosPicked(listOf(Uri.parse("content://a")))
        first.onMessageChanged("Lost answer")
        first.onSubmit()
        advanceUntilIdle()
        first.onSubmit()
        advanceUntilIdle()
        assertEquals("One upload for two tries", 1, support.uploads.size)

        // The screen (or the app) went away: a new one sending the same message uses the same key.
        val second = FeedbackViewModel(support.asSupport(), prefs, context, ioDispatcher = testDispatcher, readPhoto = { ByteArray(10) }, retryKeys = RetryKeys(store = saved))
        advanceUntilIdle()
        second.onPhotosPicked(listOf(Uri.parse("content://a")))
        support.result = SendResult.Sent("t1")
        second.onSubmit()
        advanceUntilIdle()
        assertEquals(1, support.sent.map { it.requestId }.distinct().size)
        assertNull("Cleared once it was saved", saved.value)
    }

    @Test
    fun retry_keys_change_with_content_and_start_over_once_cleared() {
        var n = 0
        val keys = RetryKeys(newKey = { "key-${++n}" })
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
        support.photoIds = { index -> if (index == 2) PhotoUpload.Failed else PhotoUpload.Uploaded("p$index") }
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
        vm.onCategorySelected(FeedbackCategory.BUG.id)
        vm.onMessageChanged("Never mind")
        vm.onPhotosPicked(listOf(Uri.parse("content://a")))

        vm.onDiscardDraft()

        val state = vm.uiState.value
        assertEquals("feature", state.choice.id)
        assertEquals("", state.message)
        assertTrue(state.photos.isEmpty())
        assertNull(prefs.getFeedbackDraft())
    }

    @Test
    fun an_old_draft_longer_than_the_limit_is_kept_whole_and_must_be_shortened_to_send() = runTest(testDispatcher) {
        val message = "m".repeat(2000)
        val steps = "s".repeat(1000)
        prefs.saveFeedbackDraft(FeedbackDraft(category = "bug", message = message, email = "", stepsToReproduce = steps, attachDiagnostics = true))
        val support = FakeSupport()
        val vm = viewModel(support)
        advanceUntilIdle()
        val joined = "$message\n\n$steps"
        assertEquals(joined, vm.uiState.value.message)
        assertTrue(vm.uiState.value.overLimit)
        assertFalse(vm.uiState.value.canSend)
        assertEquals("This is over the 2,000-character limit. Shorten it to send.", vm.uiState.value.limitMessage)

        // Nothing is cut off behind the listener's back: a category change saves it whole, typing can't grow it.
        vm.onCategorySelected(FeedbackCategory.AUDIO.id)
        assertEquals(joined, prefs.getFeedbackDraft()?.message)
        vm.onMessageChanged(joined + "x")
        assertEquals(joined, vm.uiState.value.message)
        vm.onSubmit()
        advanceUntilIdle()
        assertTrue(support.sent.isEmpty())

        // Shortening works, and once under the limit it can be sent.
        vm.onMessageChanged(joined.dropLast(10))
        assertEquals(joined.length - 10, vm.uiState.value.message.length)
        vm.onMessageChanged(message)
        assertTrue(vm.uiState.value.canSend)
    }

    @Test
    fun a_higher_server_limit_applies_as_is() = runTest(testDispatcher) {
        val vm = viewModel(FakeSupport(SupportMeta.Offline.copy(maxBody = 4000)))
        advanceUntilIdle()
        vm.onMessageChanged("x".repeat(3500))
        assertEquals(3500, vm.uiState.value.message.length)
        assertTrue(vm.uiState.value.canSend)
        vm.onMessageChanged("x".repeat(4500))
        assertEquals(4000, vm.uiState.value.message.length)
    }

    @Test
    fun the_draft_is_fixed_while_it_is_being_sent() = runTest(testDispatcher) {
        val support = FakeSupport().apply { sendGate = CompletableDeferred() }
        val vm = viewModel(support)
        vm.onMessageChanged("What I meant")
        vm.onSubmit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSubmitting)

        vm.onMessageChanged("What I meant, and more")
        vm.onCategorySelected(FeedbackCategory.BUG.id)
        vm.onAttachDiagnosticsChanged(true)
        vm.onDiscardDraft()
        assertEquals("What I meant", vm.uiState.value.message)
        assertEquals("feature", vm.uiState.value.choice.id)
        assertFalse(vm.uiState.value.attachDiagnostics)

        support.sendGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("What I meant", support.sent.single().body)
        assertTrue(vm.uiState.value.isSuccess)
        assertNull(prefs.getFeedbackDraft())
    }

    @Test
    fun the_phone_model_goes_only_with_diagnostics() = runTest(testDispatcher) {
        val support = FakeSupport()
        val vm = viewModel(support)
        vm.onMessageChanged("An idea")
        vm.onSubmit()
        advanceUntilIdle()
        assertNull(support.sent.single().device)
        assertNull(support.sent.single().diagnostics)

        val withDiagnostics = viewModel(support)
        withDiagnostics.onMessageChanged("A bug")
        withDiagnostics.onAttachDiagnosticsChanged(true)
        withDiagnostics.onSubmit()
        advanceUntilIdle()
        assertNotNull(support.sent.last().device)
    }

    @Test
    fun the_servers_categories_are_the_choices() = runTest(testDispatcher) {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsHelper.installRecordingSink(events)
        try {
            val support = FakeSupport(SupportMeta.Offline.copy(categories = listOf(SupportCategory("bug", "Something broke"), SupportCategory("account", "Account and sync"))))
            val vm = viewModel(support)
            advanceUntilIdle()
            val state = vm.uiState.value
            assertEquals(listOf("Something broke", "Account and sync"), state.choices.map { it.label })
            // The default "feature" isn't offered any more, so the first choice stands in.
            assertEquals("bug", state.choice.id)

            vm.onCategorySelected("account")
            vm.onCategorySelected("not-offered")
            assertEquals("account", vm.uiState.value.choice.id)
            assertEquals(FeedbackCategory.OTHER.placeholder, vm.uiState.value.choice.placeholder)
            vm.onMessageChanged("Sync stopped")
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals("account", support.sent.single().category)
            assertEquals("other", events.single { it.first == "feedback_sent" }.second["category"])
        } finally {
            restore()
        }
    }

    @Test
    fun photos_are_left_out_when_support_cant_take_them_and_the_listener_is_told() = runTest(testDispatcher) {
        val support = FakeSupport(SupportMeta.Offline.copy(photosEnabled = true, maxPhotos = 3, maxPhotoBytes = 100))
        support.photoIds = { PhotoUpload.Unavailable }
        support.result = SendResult.SentByEmailForm
        val vm = viewModel(support)
        advanceUntilIdle()
        vm.onPhotosPicked(listOf(Uri.parse("content://a")))
        vm.onMessageChanged("See the screenshot")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), support.sent.single().photoIds)
        assertTrue(vm.uiState.value.isSuccess)
        assertTrue(vm.uiState.value.photosDropped)
    }

    @Test
    fun buildGitHubIssueUrl_creates_valid_URL_with_encoded_title() {
        val vm = viewModel(FakeSupport())
        vm.onCategorySelected(FeedbackCategory.BUG.id)
        vm.onMessageChanged("Crash when clicking download button")

        val url = buildFeedbackGitHubIssueUrl(vm.uiState.value)
        assertTrue(url.startsWith("https://github.com/boxcreate/boxlore/issues/new?title="))
        assertTrue(url.contains("Crash"))
        assertTrue(url.contains("body="))
    }

    @Test
    fun category_selection_toggles_attachDiagnostics_default_for_bug_reports() {
        val vm = viewModel(FakeSupport())
        assertEquals("feature", vm.uiState.value.choice.id)
        assertFalse(vm.uiState.value.attachDiagnostics)

        vm.onCategorySelected(FeedbackCategory.BUG.id)
        assertTrue(vm.uiState.value.attachDiagnostics)

        vm.onCategorySelected(FeedbackCategory.AUDIO.id)
        assertTrue(vm.uiState.value.attachDiagnostics)

        vm.onCategorySelected(FeedbackCategory.FEATURE.id)
        assertFalse(vm.uiState.value.attachDiagnostics)

        vm.onCategorySelected(FeedbackCategory.OTHER.id)
        assertFalse(vm.uiState.value.attachDiagnostics)
    }
}
