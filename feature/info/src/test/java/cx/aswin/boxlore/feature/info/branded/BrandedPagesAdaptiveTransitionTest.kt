package cx.aswin.boxlore.feature.info.branded

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.PodcastInfoScreen
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyPage
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastPage
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformPage
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives each live Showcase page across the shared window gates in both directions, then recreates
 * it with saved state: the open description, the open © popup and the single spotlight carry
 * through, and the popup still closes on Back or a tap outside.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class BrandedPagesAdaptiveTransitionTest(private val page: BrandedShowPage) {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))

    private val window = mutableStateOf(COMPACT)
    private val podcast = TestFixtures.podcast(id = page.ids.podcastIndexId, title = "Showcase", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        .copy(description = DESCRIPTION)
    private val episodes = List(12) { index ->
        TestFixtures.episode(
            id = "e$index",
            title = "Episode $index",
            podcastId = podcast.id,
            podcastTitle = podcast.title,
            publishedDate = 1_700_000_000L - index * 86_400L,
        )
    }
    private val model = mock(PodcastInfoViewModel::class.java)
    private lateinit var restoration: StateRestorationTester

    @Test
    fun `the open description and copyright popup survive window changes and recreation, then close as usual`() {
        render()
        assertFalse("The description starts on two lines", descriptionOpen())
        fixture.compose.onNodeWithText(DESCRIPTION).performClick()
        fixture.compose.onNodeWithContentDescription("Copyright information").performClick()
        fixture.compose.waitForIdle()
        assertTrue(descriptionOpen())
        popupShown()

        acrossWindowChanges {
            assertTrue("The description stays open", descriptionOpen())
            popupShown()
            spotlightOnce()
            fixture.compose.onNodeWithText("boxlore pick").assertExists()
        }
        recreate(FOLD_TABLETOP)
        assertTrue("The description stays open after recreation", descriptionOpen())
        popupShown()
        spotlightOnce()

        // Back closes only the popup; the page stays.
        pressBackOnPopup()
        fixture.compose.onNodeWithText(COPYRIGHT_TITLE).assertDoesNotExist()
        fixture.compose.onNodeWithText("boxlore pick").assertExists()

        // A tap outside closes it too, on a compact and a tablet window.
        for (host in listOf(COMPACT, TABLET)) {
            fixture.compose.runOnIdle { window.value = host }
            fixture.compose.waitForIdle()
            fixture.compose.onNodeWithContentDescription("Copyright information").performClick()
            popupShown()
            tapOutsidePopup()
            fixture.compose.onNodeWithText(COPYRIGHT_TITLE).assertDoesNotExist()
        }
        assertTrue(descriptionOpen())
        fixture.compose.runOnIdle { window.value = COMPACT }
        fixture.compose.waitForIdle()
        assertEquals(1, fixture.compose.onAllNodes(hasText("Episode 0")).fetchSemanticsNodes().size)
    }

    private fun descriptionOpen(): Boolean {
        val node = fixture.compose.onNode(hasText(DESCRIPTION)).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        return layout.lineCount > 2 && !layout.hasVisualOverflow
    }

    private fun popupShown() {
        fixture.compose.onNodeWithText(COPYRIGHT_TITLE).assertExists()
    }

    /**
     * The spotlight's episode is never repeated in the list, whatever the window. A short window
     * with the description open can push it below the screen, where it is not drawn at all.
     */
    private fun spotlightOnce() {
        val shown = fixture.compose.onAllNodes(hasText("Episode 0")).fetchSemanticsNodes().size
        assertTrue("Episode 0 shown $shown times", shown <= 1)
    }

    private fun pressBackOnPopup() {
        fixture.compose.runOnUiThread {
            val popup = popupView()
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { popup.dispatchKeyEvent(KeyEvent(it, KeyEvent.KEYCODE_BACK)) }
        }
        fixture.compose.waitForIdle()
    }

    /** A focusable popup's window takes every touch; one beyond its bounds is an outside tap. */
    private fun tapOutsidePopup() {
        fixture.compose.runOnUiThread {
            val popup = popupView()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(0L, 0L, action, -20f, -20f, 0)
                popup.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        fixture.compose.waitForIdle()
    }

    /** The © popup's own window: the newest Compose popup window on screen. */
    private fun popupView(): View {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>
        return views.filterIsInstance<View>().last { it.javaClass.name.endsWith("PopupLayout") }
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
        `when`(model.uiState).thenReturn(MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, episodes, isSubscribed = true)))
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        Surface { PodcastInfoScreen(podcast.id, model, {}, { _, _, _ -> }, {}, {}) }
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    companion object {
        private const val COPYRIGHT_TITLE = "Artwork and logos"
        private val DESCRIPTION = List(12) { "A show about the things people make and why they matter." }.joinToString(" ")
        private val COMPACT = BoxloreWindowLayout(599f, 800f)
        private val TABLET = BoxloreWindowLayout(1_280f, 800f)
        private val FOLD_FLAT = BoxloreWindowLayout(673f, 841f)
        private val FOLD_TABLETOP = FOLD_FLAT.copy(
            folds = listOf(
                BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true),
            ),
        )

        /** Each gate is crossed in both directions; the walk ends where it started. */
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
            FOLD_FLAT,
            FOLD_TABLETOP,
            FOLD_FLAT,
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun pages(): List<Array<Any>> = listOf(VergecastPage, ConspiracyPage, WaveformPage).map { arrayOf<Any>(it) }
    }
}

/**
 * Waveform stays dark in light mode, so its page turns the status bar icons white and hands the
 * app's own setting back when it leaves.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DarkOnlyPageStatusBarTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(369, 821, false))

    @Test
    fun `a dark-only page keeps white status bar icons in light mode and restores them when it leaves`() {
        val shown = mutableStateOf(true)
        val podcast = TestFixtures.podcast(id = WaveformPage.ids.podcastIndexId, title = "Waveform", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        val model = mock(PodcastInfoViewModel::class.java)
        `when`(model.uiState).thenReturn(MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, emptyList(), isSubscribed = false)))
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                if (shown.value) Surface { PodcastInfoScreen(podcast.id, model, {}, { _, _, _ -> }, {}, {}) }
            }
        }
        fixture.compose.waitForIdle()
        assertFalse("White icons over the dark page", lightStatusBarIcons())

        fixture.compose.runOnIdle { shown.value = false }
        fixture.compose.waitForIdle()
        assertTrue("The light theme's dark icons return", lightStatusBarIcons())
    }

    private fun lightStatusBarIcons(): Boolean {
        val activity: Activity = fixture.compose.activity
        return WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars
    }
}
