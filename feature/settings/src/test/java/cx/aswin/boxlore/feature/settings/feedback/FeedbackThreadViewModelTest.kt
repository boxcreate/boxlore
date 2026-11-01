package cx.aswin.boxlore.feature.settings.feedback

import cx.aswin.boxlore.core.support.ConversationResult
import cx.aswin.boxlore.core.support.ReplyResult
import cx.aswin.boxlore.core.support.SupportConversation
import cx.aswin.boxlore.core.support.SupportEnding
import cx.aswin.boxlore.core.support.SupportMessage
import cx.aswin.boxlore.core.support.SupportStatus
import cx.aswin.boxlore.core.support.SupportThread
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackThreadViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private fun thread(unread: Boolean = false, canReply: Boolean = true) =
        SupportThread("t", "bug", "Bug report", "It stops", SupportStatus("replied", "Replied", true), 1, unread, canReply)

    private fun message(id: String, team: Boolean) = SupportMessage(id, team, if (team) "boxlore team" else "You", "Body $id", emptyList(), false, 1)

    /** Answers in order of [pages]; records what the screen did. */
    private class Fake(var pages: MutableList<ConversationResult>) {
        val loads = mutableListOf<String?>()
        val replies = mutableListOf<Pair<String, String>>()
        var replyResult: ReplyResult = ReplyResult.Sent(null, null)
        var reads = 0
        var solves = 0
        var photoCalls = 0
        var maxBody = 2000
        var lastUpTo: Long? = null
        val pushes = kotlinx.coroutines.flow.MutableSharedFlow<String?>(extraBufferCapacity = 4)

        fun support() = FeedbackThreadSupport(
            load = { _, before ->
                loads += before
                if (pages.size > 1) pages.removeAt(0) else pages.first()
            },
            reply = { _, body, key ->
                replies += body to key
                replyResult
            },
            markRead = { _, upTo ->
                reads++
                lastUpTo = upTo
            },
            solve = {
                solves++
                true
            },
            photo = {
                photoCalls++
                byteArrayOf(1)
            },
            maxBody = { maxBody },
            pushes = pushes,
        )
    }

    private fun loaded(vararg messages: SupportMessage, unread: Boolean = false, earlier: String? = null, ending: SupportEnding? = null, canReply: Boolean = true) =
        ConversationResult.Loaded(SupportConversation(thread(unread, canReply), messages.toList(), earlier, ending))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `an unread conversation is marked read once it shows, up to the newest message`() = runTest(dispatcher) {
        val fake = Fake(mutableListOf(loaded(message("m1", false), message("m2", true), unread = true)))
        val vm = FeedbackThreadViewModel(fake.support(), "t")
        vm.activate()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.conversation?.messages?.size)
        assertEquals("Nothing is read until it is on screen", 0, fake.reads)
        vm.onSeen()
        vm.onSeen()
        advanceUntilIdle()
        assertEquals(1, fake.reads)
        assertEquals(1L, fake.lastUpTo)
        assertTrue(vm.uiState.value.canSolve)
    }

    @Test fun `a gone conversation and a failed load say so, and a retry recovers`() = runTest(dispatcher) {
        val gone = FeedbackThreadViewModel(Fake(mutableListOf(ConversationResult.Gone)).support(), "t")
        gone.activate()
        advanceUntilIdle()
        assertTrue(gone.uiState.value.gone)

        val fake = Fake(mutableListOf(ConversationResult.Failed, loaded(message("m1", false))))
        val vm = FeedbackThreadViewModel(fake.support(), "t")
        vm.activate()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadFailed)
        vm.reload()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.loadFailed)
        assertEquals(1, vm.uiState.value.conversation?.messages?.size)
        assertFalse(vm.uiState.value.canSolve)
    }

    @Test fun `earlier messages go before the ones on screen`() = runTest(dispatcher) {
        val fake = Fake(mutableListOf(loaded(message("m3", false), earlier = "m3"), loaded(message("m1", false), message("m2", true))))
        val vm = FeedbackThreadViewModel(fake.support(), "t")
        vm.activate()
        advanceUntilIdle()
        vm.loadEarlier()
        advanceUntilIdle()
        assertEquals(listOf("m1", "m2", "m3"), vm.uiState.value.conversation?.messages?.map { it.id })
        assertNull(vm.uiState.value.conversation?.earlier)
        assertEquals(listOf(null, "m3"), fake.loads)
    }

    @Test fun `a failed reply keeps its text and retry key, sent clears both`() = runTest(dispatcher) {
        val fake = Fake(mutableListOf(loaded(message("m1", false), message("m2", true))))
        fake.replyResult = ReplyResult.Failed
        val vm = FeedbackThreadViewModel(fake.support(), "t")
        vm.activate()
        advanceUntilIdle()
        vm.onReplyOpen(true)
        vm.onReplyChanged("  Still happening  ")
        vm.sendReply()
        advanceUntilIdle()
        assertEquals("Couldn't send your reply. Check your connection and try again.", vm.uiState.value.replyError)
        assertEquals("  Still happening  ", vm.uiState.value.reply)

        fake.replyResult = ReplyResult.Sent(null, null)
        vm.sendReply()
        advanceUntilIdle()
        assertEquals("Still happening", fake.replies.first().first)
        assertEquals(fake.replies[0].second, fake.replies[1].second)
        assertEquals("", vm.uiState.value.reply)
        assertFalse(vm.uiState.value.replyOpen)
    }

    @Test fun `a refusal shows the server's words, a closed conversation reloads to show its ending`() = runTest(dispatcher) {
        val ending = SupportEnding("closed", "This conversation is closed", "", 2, "new", "Send new feedback")
        val fake = Fake(mutableListOf(loaded(message("m1", false)), loaded(message("m1", false), ending = ending, canReply = false)))
        fake.replyResult = ReplyResult.Refused("Try again tomorrow.")
        val vm = FeedbackThreadViewModel(fake.support(), "t")
        vm.activate()
        advanceUntilIdle()
        vm.onReplyOpen(true)
        vm.onReplyChanged("More")
        vm.sendReply()
        advanceUntilIdle()
        assertEquals("Try again tomorrow.", vm.uiState.value.replyError)

        fake.replyResult = ReplyResult.Closed(null)
        vm.sendReply()
        advanceUntilIdle()
        assertEquals(ending, vm.uiState.value.conversation?.ending)
        assertFalse(vm.uiState.value.canReply)
        vm.onReplyOpen(true)
        assertFalse(vm.uiState.value.replyOpen)
    }

    @Test fun `a closed answer shows its ending at once, even if the reload then fails, and gone clears the screen`() = runTest(dispatcher) {
        val ending = SupportEnding("closed", "This conversation is closed", "", 2, "new", "Send new feedback")
        val fake = Fake(mutableListOf(loaded(message("m1", false)), ConversationResult.Failed))
        fake.replyResult = ReplyResult.Closed(ending)
        val vm = FeedbackThreadViewModel(fake.support(), "t")
        vm.activate()
        advanceUntilIdle()
        vm.onReplyOpen(true)
        vm.onReplyChanged("Hello?")
        vm.sendReply()
        advanceUntilIdle()
        assertEquals(ending, vm.uiState.value.conversation?.ending)
        assertFalse(vm.uiState.value.canReply)
        assertTrue("The failed reload is shown beside what is kept", vm.uiState.value.loadFailed)

        fake.pages = mutableListOf(ConversationResult.Gone)
        vm.reload()
        advanceUntilIdle()
        assertNull(vm.uiState.value.conversation)
        assertTrue(vm.uiState.value.gone)
    }

    @Test fun `a reply push for this conversation reloads it, and the reply draft outlives the view model`() = runTest(dispatcher) {
        val fake = Fake(mutableListOf(loaded(message("m1", false)), loaded(message("m1", false), message("m2", true))))
        val handle = androidx.lifecycle.SavedStateHandle()
        val vm = FeedbackThreadViewModel(fake.support(), "t", handle)
        vm.activate()
        advanceUntilIdle()
        fake.pushes.emit("other")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.conversation?.messages?.size)
        fake.pushes.emit("t")
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.conversation?.messages?.size)

        vm.onReplyOpen(true)
        vm.onReplyChanged("Half a reply")
        val again = FeedbackThreadViewModel(fake.support(), "t", handle)
        assertEquals("Half a reply", again.uiState.value.reply)
        assertTrue(again.uiState.value.replyOpen)
    }

    @Test fun `a failed photo is fetched again, and a kept one isn't`() = runTest(dispatcher) {
        var calls = 0
        var fail = true
        val cache = PhotoCache {
            calls++
            if (fail) null else byteArrayOf(1)
        }
        assertNull(cache.get("p1"))
        fail = false
        assertEquals(1, cache.get("p1")?.size)
        cache.get("p1")
        assertEquals(2, calls)
    }

    @Test fun `mark solved, the server's limit for replies, and photos fetched once`() = runTest(dispatcher) {
        val fake = Fake(mutableListOf(loaded(message("m1", false), message("m2", true))))
        fake.maxBody = 5
        val vm = FeedbackThreadViewModel(fake.support(), "t")
        vm.activate()
        advanceUntilIdle()
        vm.solve()
        advanceUntilIdle()
        assertEquals(1, fake.solves)
        vm.onReplyOpen(true)
        vm.onReplyChanged("123456789")
        assertEquals("12345", vm.uiState.value.reply)
        vm.photo("p1")
        vm.photo("p1")
        assertEquals(1, fake.photoCalls)
    }
}
