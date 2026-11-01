package cx.aswin.boxlore.feature.settings.feedback

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.support.OutgoingFeedback
import cx.aswin.boxlore.core.support.SendResult
import cx.aswin.boxlore.core.support.SupportMeta
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the live feedback screen across the shared window gates in both directions and through a
 * recreation: the typed message, the picked type and the reachable Send survive, the message goes
 * out once, and the sent screen keeps its Done and notification card through the same changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackAdaptiveTransitionTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 960, false))

    private val window = mutableStateOf(COMPACT)
    private val sent = mutableListOf<OutgoingFeedback>()
    private var result: SendResult = SendResult.Sent("t1")
    private var backs = 0
    private lateinit var viewModel: FeedbackViewModel
    private lateinit var restoration: StateRestorationTester

    @Test
    fun `the draft, type and Send survive window changes and recreation, then the message goes out once`() {
        render()
        fixture.compose.onNodeWithText("Bug report").performClick()
        fixture.compose.onNodeWithTag(FEEDBACK_MESSAGE_TAG).performTextInput(MESSAGE)
        fixture.compose.waitForIdle()
        val owner = viewModel

        acrossWindowChanges {
            composeStateKept()
            assertSame("The same view model keeps the draft", owner, viewModel)
        }
        recreate(FOLD_TABLETOP)
        composeStateKept()

        reach(FEEDBACK_SEND_TAG).performClick()
        fixture.compose.waitUntil(5_000) { viewModel.uiState.value.isSuccess }
        fixture.compose.waitForIdle()
        assertEquals(1, sent.size)
        assertEquals(MESSAGE, sent.single().body)
        assertEquals("bug", sent.single().category)

        acrossWindowChanges {
            fixture.compose.onNodeWithTag(FEEDBACK_SENT_TAG).assertExists()
            reach(FEEDBACK_NOTIFY_CARD_TAG)
            reach("Done", byText = true)
        }
        reach("Not now", byText = true).performClick()
        fixture.compose.waitForIdle()
        acrossWindowChanges { fixture.compose.onNodeWithTag(FEEDBACK_NOTIFY_CARD_TAG).assertDoesNotExist() }
        recreate(COMPACT)
        fixture.compose.onNodeWithTag(FEEDBACK_NOTIFY_CARD_TAG).assertDoesNotExist()

        fixture.compose.onNodeWithText("Done").performClick()
        fixture.compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals(1, sent.size)
    }

    @Test
    fun `a message sent through the old form never offers notifications`() {
        result = SendResult.SentByEmailForm
        render()
        fixture.compose.onNodeWithTag(FEEDBACK_MESSAGE_TAG).performTextInput(MESSAGE)
        reach(FEEDBACK_SEND_TAG).performClick()
        fixture.compose.waitUntil(5_000) { viewModel.uiState.value.isSuccess }
        acrossWindowChanges {
            fixture.compose.onNodeWithText(feedbackSentBody(repliesInApp = false)).assertExists()
            fixture.compose.onNodeWithTag(FEEDBACK_NOTIFY_CARD_TAG).assertDoesNotExist()
        }
    }

    private fun composeStateKept() {
        fixture.compose.onNodeWithText(MESSAGE).assertExists()
        fixture.compose.onNodeWithText("Bug report").assertIsSelected()
        reach(FEEDBACK_SEND_TAG).assertIsEnabled()
        assertEquals(1, fixture.compose.onAllNodes(hasText(MESSAGE)).fetchSemanticsNodes().size)
    }

    /** Scrolls to the node when it sits in a scrolling column (short windows), then checks it is on screen. */
    private fun reach(key: String, byText: Boolean = false): SemanticsNodeInteraction {
        val matcher = if (byText) hasText(key) else hasTestTag(key)
        val scrolled = fixture.compose.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()
        val node = fixture.compose.onNode(matcher)
        if (scrolled) node.performScrollTo()
        return node.assertIsDisplayed()
    }

    private fun acrossWindowChanges(assertion: () -> Unit) {
        val start = window.value
        (TRANSITIONS + start).forEachIndexed { step, size ->
            fixture.compose.runOnIdle { window.value = size }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.waitForIdle()
            try {
                assertion()
            } catch (failure: AssertionError) {
                throw AssertionError("After window step $step ($size): ${failure.message}", failure)
            }
        }
    }

    private fun recreate(to: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = to }
        restoration.emulateSavedInstanceStateRestore()
        fixture.compose.waitForIdle()
    }

    private fun render() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BoxcastPrefs(context)
        val support = FeedbackSupport(
            meta = MutableStateFlow(SupportMeta.Offline),
            refresh = {},
            uploadPhoto = { _, _ -> null },
            send = { feedback ->
                sent += feedback
                result
            },
        )
        // Built outside composition, like the navigation entry's view model store.
        viewModel = FeedbackViewModel(support, prefs, context, ioDispatcher = Dispatchers.Unconfined)
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = window.value
            val state by viewModel.uiState.collectAsState()
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
                LocalEchoMotion provides false,
            ) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        FeedbackContent(
                            state = state,
                            actions = FeedbackComposeActions(
                                onBack = { backs++ },
                                onCategorySelected = viewModel::onCategorySelected,
                                onMessageChanged = viewModel::onMessageChanged,
                                onAttachDiagnosticsChanged = viewModel::onAttachDiagnosticsChanged,
                                onPhotosPicked = viewModel::onPhotosPicked,
                                onRemovePhoto = viewModel::onRemovePhoto,
                                onSubmit = viewModel::onSubmit,
                                onPreviewDiagnostics = {},
                                onDiscardDraft = viewModel::onDiscardDraft,
                            ),
                            boxcastPrefs = prefs,
                            bottomContentPadding = 96.dp,
                        )
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    companion object {
        private const val MESSAGE = "The sleep timer stops one episode early"
        private val COMPACT = BoxloreWindowLayout(599f, 800f)
        private val FOLD_FLAT = BoxloreWindowLayout(673f, 841f)
        private val FOLD_TABLETOP = FOLD_FLAT.copy(
            folds = listOf(
                BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true),
            ),
        )

        /** Each gate is crossed in both directions, including the short window that scrolls; the walk ends where it started. */
        private val TRANSITIONS = listOf(
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(600f, 960f),
            BoxloreWindowLayout(960f, 600f),
            BoxloreWindowLayout(600f, 960f),
            BoxloreWindowLayout(800f, 360f),
            BoxloreWindowLayout(360f, 800f),
            FOLD_FLAT,
            FOLD_TABLETOP,
            FOLD_FLAT,
        )
    }
}
