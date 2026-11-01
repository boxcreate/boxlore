package cx.aswin.boxlore.feature.settings.feedback

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.network.SupportApi
import cx.aswin.boxlore.core.network.model.SupportConversationResponse
import cx.aswin.boxlore.core.network.model.SupportListResponse
import cx.aswin.boxlore.core.network.model.SupportMessageDto
import cx.aswin.boxlore.core.network.model.SupportMetaDto
import cx.aswin.boxlore.core.network.model.SupportNewThreadRequest
import cx.aswin.boxlore.core.network.model.SupportPhotoResponse
import cx.aswin.boxlore.core.network.model.SupportReadRequest
import cx.aswin.boxlore.core.network.model.SupportReplyRequest
import cx.aswin.boxlore.core.network.model.SupportReplyResponse
import cx.aswin.boxlore.core.network.model.SupportStatusDto
import cx.aswin.boxlore.core.network.model.SupportThreadDto
import cx.aswin.boxlore.core.network.model.SupportThreadResponse
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.support.SupportLocalStore
import cx.aswin.boxlore.core.support.SupportRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import kotlinx.coroutines.Dispatchers
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Response

/**
 * The whole Feedback page across window changes: one pane on phones, list and conversation side by
 * side on landscape tablets, with the open conversation kept through resizes in both directions,
 * a fold and recreation; Back walks to the list; a notification opens its conversation directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackConversationsAdaptiveTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 960, false))

    private val window = mutableStateOf(PHONE)
    private val backs = mutableListOf<Unit>()
    private lateinit var restoration: StateRestorationTester

    private class FakeApi(private val threads: List<SupportThreadDto>) : SupportApi {
        val reads = mutableListOf<String>()
        override suspend fun threads() = Response.success(SupportListResponse(meta = SupportMetaDto(), threads = threads))
        override suspend fun startThread(request: SupportNewThreadRequest) = Response.success(SupportThreadResponse(threads.firstOrNull()))
        override suspend fun uploadPhoto(photo: RequestBody) = Response.error<SupportPhotoResponse>(403, "{}".toResponseBody())
        override suspend fun conversation(id: String, before: String?) = Response.success(
            SupportConversationResponse(
                thread = threads.first { it.id == id },
                messages = listOf(SupportMessageDto("m$id", "you", "You", "Message for $id", createdAt = 1), SupportMessageDto("r$id", "team", "boxlore team", "Reply for $id", createdAt = 2)),
            ),
        )
        override suspend fun reply(id: String, request: SupportReplyRequest) = Response.success(SupportReplyResponse())
        override suspend fun markRead(id: String, request: SupportReadRequest): Response<Unit> {
            reads += id
            return Response.success(Unit)
        }
        override suspend fun solve(id: String) = Response.success(SupportConversationResponse())
        override suspend fun photo(id: String) = Response.error<ResponseBody>(404, "{}".toResponseBody())
    }

    private fun dto(id: String, title: String, unread: Boolean = false) =
        SupportThreadDto(id, "bug", "Bug report", title, SupportStatusDto(key = if (unread) "new_reply" else "replied", label = if (unread) "New reply" else "Replied", showTime = true), 1, unread, true)

    private val first = "a1b2c3d4e5f6a1b2c3d4e5f6"
    private val second = "b1b2c3d4e5f6a1b2c3d4e5f6"
    private lateinit var api: FakeApi

    @Test
    fun `the open conversation stays open through window changes and recreation, and Back returns to the list`() {
        render()
        fixture.compose.onNodeWithText("Second problem").performClick()
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Message for $second").assertExists()

        for (size in TRANSITIONS + PHONE) {
            fixture.compose.runOnIdle { window.value = size }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.waitForIdle()
            fixture.compose.onNodeWithText("Message for $second").assertExists()
            val split = feedbackUsesSplit(size.widthDp, size)
            fixture.compose.onNodeWithTag(FEEDBACK_LIST_TAG).apply { if (split) assertExists() else assertDoesNotExist() }
        }
        fixture.compose.runOnIdle { window.value = TABLET }
        restoration.emulateSavedInstanceStateRestore()
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Message for $second").assertExists()

        fixture.compose.runOnIdle { window.value = PHONE }
        fixture.compose.waitForIdle()
        fixture.compose.runOnUiThread { fixture.compose.activity.onBackPressedDispatcher.onBackPressed() }
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithTag(FEEDBACK_LIST_TAG).assertExists()
        fixture.compose.onNodeWithText("Message for $second").assertDoesNotExist()
        assertEquals(0, backs.size)
        fixture.compose.runOnUiThread { fixture.compose.activity.onBackPressedDispatcher.onBackPressed() }
        fixture.compose.waitForIdle()
        assertEquals(1, backs.size)
    }

    @Test
    fun `a reply being written keeps its text and focus across the split gate, and the shown conversation stays when narrowing`() {
        window.value = TABLET
        render()
        // Entered side by side: the newest conversation shows, and stays when the window narrows.
        fixture.compose.runOnIdle { window.value = PHONE }
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Message for $first").assertExists()
        fixture.compose.onNodeWithText("Reply").performClick()
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithTag(FEEDBACK_REPLY_FIELD_TAG).performTextInput("Still happening")
        fixture.compose.waitForIdle()
        for (size in listOf(TABLET, PHONE, BoxloreWindowLayout(840f, 600f), BoxloreWindowLayout(839f, 600f))) {
            fixture.compose.runOnIdle { window.value = size }
            fixture.compose.waitForIdle()
            fixture.compose.onNodeWithTag(FEEDBACK_REPLY_FIELD_TAG).assertIsFocused()
            fixture.compose.onNodeWithText("Still happening").assertExists()
        }
    }

    @Test
    fun `side by side, the newest conversation shows until another is picked, and an unread one is marked read`() {
        window.value = TABLET
        render(firstUnread = true)
        fixture.compose.onNodeWithTag(FEEDBACK_LIST_TAG).assertExists()
        fixture.compose.onNodeWithText("Message for $first").assertExists()
        fixture.compose.onNodeWithText("Second problem").performClick()
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Message for $second").assertExists()
        assertEquals(listOf(first), api.reads)
    }

    @Test
    fun `a reply notification opens its conversation, and a phone without conversations starts writing`() {
        render(openThreadId = second)
        fixture.compose.onNodeWithText("Message for $second").assertExists()
        fixture.compose.onNodeWithTag(FEEDBACK_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun `without conversations Feedback opens on Share your thoughts`() {
        render(threads = emptyList(), hasConversations = false)
        fixture.compose.onNodeWithText("Share your thoughts").assertExists()
        fixture.compose.onNodeWithTag(FEEDBACK_LIST_TAG).assertDoesNotExist()
    }

    private fun render(
        threads: List<SupportThreadDto>? = null,
        hasConversations: Boolean = true,
        firstUnread: Boolean = false,
        openThreadId: String? = null,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        api = FakeApi(threads ?: listOf(dto(first, "First problem", unread = firstUnread), dto(second, "Second problem")))
        val repository = SupportRepository(
            api = api,
            legacySubmit = { _, _, _, _, _ -> true },
            ioDispatcher = Dispatchers.Unconfined,
            local = SupportLocalStore.InMemory().apply { this.hasConversations = hasConversations },
        )
        val prefs = BoxcastPrefs(context)
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp, LocalEchoMotion provides false) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        FeedbackScreen(repository, prefs, onBack = { backs += Unit }, bottomContentPadding = 96.dp, openThreadId = openThreadId)
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    companion object {
        private val PHONE = BoxloreWindowLayout(412f, 900f)
        private val TABLET = BoxloreWindowLayout(1_280f, 800f)

        /** Across the split gate both ways, a portrait tablet, a short window and a fold. */
        private val TRANSITIONS = listOf(
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(839f, 600f),
            TABLET,
            BoxloreWindowLayout(800f, 1_280f),
            BoxloreWindowLayout(900f, 479f),
            BoxloreWindowLayout(673f, 841f),
            TABLET,
        )
    }
}
