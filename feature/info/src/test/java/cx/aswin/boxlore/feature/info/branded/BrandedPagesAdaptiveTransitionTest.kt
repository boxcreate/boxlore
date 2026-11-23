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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneSpec
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.info.PodcastInfoScreen
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.PODCAST_INFO_BRANDED_SPOTLIGHT_TAG
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.branded.rottenmango.RottenMangoPage
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformPage
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import cx.aswin.boxlore.feature.info.stubArchive
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    private val pane = mutableStateOf<BoxloreLayoutBounds?>(null)
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
    private val titlesOnly = MutableStateFlow(false)
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
            fixture.compose.onNodeWithContentDescription(SEAL_LABEL).assertExists()
        }
        recreate(FOLD_TABLETOP)
        assertTrue("The description stays open after recreation", descriptionOpen())
        popupShown()
        spotlightOnce()

        // Back closes only the popup; the page stays.
        pressBackOnPopup()
        fixture.compose.onNodeWithText(COPYRIGHT_TITLE).assertDoesNotExist()
        fixture.compose.onNodeWithContentDescription(SEAL_LABEL).assertExists()

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

    @Test
    fun `titles only rows replace the episode cards across window changes and recreation, and the spotlight stays`() {
        titlesOnly.value = true
        render()
        // The page keeps its own dress: the seal sits in the top bar until the list scrolls.
        fixture.compose.onNodeWithContentDescription(SEAL_LABEL).assertExists()
        val titleRows = {
            fixture.compose.onNodeWithTag(LIST_TAG).performScrollToNode(hasTestTag(TITLE_ROW_TAG))
            fixture.compose.onAllNodesWithTag(TITLE_ROW_TAG).onFirst().assertIsDisplayed()
            spotlightOnce()
        }
        titleRows()
        acrossWindowChanges(titleRows)
        recreate(FOLD_TABLETOP)
        titleRows()
    }

    @Test
    fun `the seal's note survives window changes and recreation, then closes on Back`() {
        render()
        fixture.compose.onNodeWithContentDescription(SEAL_LABEL).performClick()
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText(SEAL_TITLE).assertExists()
        acrossWindowChanges { fixture.compose.onNodeWithText(SEAL_TITLE).assertExists() }
        recreate(FOLD_TABLETOP)
        fixture.compose.onNodeWithText(SEAL_TITLE).assertExists()
        pressBackOnPopup()
        fixture.compose.onNodeWithText(SEAL_TITLE).assertDoesNotExist()
        fixture.compose.onNodeWithContentDescription(SEAL_LABEL).assertExists()
    }

    @Test
    fun `in tabletop the header starts at the top of the lower half, and flat keeps the status bar`() {
        render()
        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets.TABLETOP_STATUS) }
        fixture.compose.onNodeWithText(DESCRIPTION).performClick()
        fixture.compose.waitForIdle()
        val lowerHalf = BoxloreLayoutBounds(0f, 431f, FOLD_FLAT.widthDp, FOLD_FLAT.heightDp)
        val tops = listOf(FOLD_FLAT to null, FOLD_TABLETOP to lowerHalf, FOLD_FLAT to null).map { (size, bounds) ->
            fixture.compose.runOnIdle {
                pane.value = bounds
                window.value = size
            }
            fixture.compose.waitForIdle()
            // Robolectric sends the window's own (empty) insets again when a page sets the status
            // bar's icon colour, which a device never does; send the fake status bar again.
            fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets.TABLETOP_STATUS) }
            fixture.compose.waitForIdle()
            assertTrue("The description stays open at $size", descriptionOpen())
            fixture.compose.onNodeWithContentDescription("Copyright information").getBoundsInRoot().top.value
        }
        assertEquals("No status-bar band in the lower half", SystemInsets.TABLETOP_STATUS.statusTop.toFloat(), tops[0] - tops[1], 0.5f)
        assertEquals("Flat again matches flat before", tops[0], tops[2], 0.5f)
    }

    @Test
    fun `Rotten Mango keeps a phone-sized photo on the Fold 8 cover and keeps the open description`() {
        assumeTrue("Only Rotten Mango's photo grows with the width", page == RottenMangoPage)
        window.value = BoxloreTestWindows.PHONE
        render()
        fixture.compose.onNodeWithText(DESCRIPTION).performClick()
        fixture.compose.waitForIdle()
        val heights = mutableMapOf<BoxloreWindowLayout, Float>()
        fixture.compose.walkWindows(
            window,
            BoxloreTestWindows.COVER_475,
            BoxloreWindowLayout(600f, 960f),
            BoxloreTestWindows.COVER_475,
            BoxloreTestWindows.PHONE,
        ) { size ->
            val heading = fixture.compose.onNode(hasContentDescription(podcast.title) and isHeading()).getBoundsInRoot()
            heights[size] = heading.bottom.value - heading.top.value
            assertTrue("The description stays open at $size", descriptionOpen())
        }
        val phone = requireNotNull(heights[BoxloreTestWindows.PHONE])
        assertEquals("The cover's photo is a phone's", phone, requireNotNull(heights[BoxloreTestWindows.COVER_475]), 1f)
        assertTrue("A tablet still gets the larger photo", requireNotNull(heights[BoxloreWindowLayout(600f, 960f)]) > phone + 1f)
    }

    @Test
    fun `the spotlight stops at a comfortable width on wide pages and keeps its episode`() {
        window.value = BoxloreTestWindows.PHONE
        render()
        fixture.compose.walkWindows(
            window,
            BoxloreTestWindows.UPRIGHT_BOOK,
            BoxloreWindowLayout(600f, 960f),
            BoxloreTestWindows.PHONE,
        ) { size ->
            fixture.compose.onNodeWithTag(LIST_TAG).performScrollToNode(hasTestTag(PODCAST_INFO_BRANDED_SPOTLIGHT_TAG))
            val spotlight = fixture.compose.onNodeWithTag(PODCAST_INFO_BRANDED_SPOTLIGHT_TAG).getBoundsInRoot()
            val width = spotlight.right.value - spotlight.left.value
            if (size == BoxloreTestWindows.PHONE) {
                // Some spotlights keep their own 16dp sides inside the tagged card.
                assertTrue("A phone's spotlight spans the page, was $width", width >= size.widthDp - 32.5f)
            } else {
                assertTrue("At most 512dp at $size, was $width", width <= 512.5f)
            }
            spotlightOnce()
        }
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
        `when`(model.episodeTitlesOnly).thenReturn(titlesOnly)
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.isHidden).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalPlaybackSpeed).thenReturn(MutableStateFlow(1f))
        `when`(model.globalTrimSilence).thenReturn(MutableStateFlow(false))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        stubArchive(model)
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = window.value
            val bounds = pane.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides (bounds?.width ?: size.widthDp),
                LocalBoxlorePaneBounds provides bounds,
            ) {
                Box(Modifier.width((bounds?.width ?: size.widthDp).dp).height((bounds?.height ?: size.heightDp).dp)) {
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
        private const val SEAL_LABEL = "About this Showcase page"
        private const val SEAL_TITLE = "A Showcase page"
        private const val LIST_TAG = "podcast_info_episode_list"
        private const val TITLE_ROW_TAG = "podcast_info_episode_title_row"
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
        fun pages(): List<Array<Any>> = BrandedShowPages.all.map { arrayOf<Any>(it) }
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
        render()
        assertFalse("White icons over the dark page", lightStatusBarIcons())

        fixture.compose.runOnIdle { shown.value = false }
        fixture.compose.waitForIdle()
        assertTrue("The light theme's dark icons return", lightStatusBarIcons())
    }

    @Test
    fun `white icons only while the dark page fills the top of the screen`() {
        val tabletSpec = boxlorePaneSpec(BoxloreTestWindows.TABLET, contextOpen = true)
        val tabletopSpec = boxlorePaneSpec(BoxloreTestWindows.FOLD_TABLETOP, contextOpen = true)
        window.value = BoxloreTestWindows.TABLET
        pane.value = tabletSpec.context
        render()
        listOf(
            Triple(BoxloreTestWindows.TABLET, tabletSpec.context, true),
            Triple(BoxloreWindowLayout(599f, 800f), null, false),
            Triple(BoxloreTestWindows.TABLET, tabletSpec.context, true),
            Triple(BoxloreTestWindows.FOLD_TABLETOP, tabletopSpec.primary, true),
            Triple(BoxloreTestWindows.FOLD_FLAT, null, false),
        ).forEach { (size, bounds, lightIcons) ->
            fixture.compose.runOnIdle {
                window.value = size
                pane.value = bounds
            }
            fixture.compose.waitForIdle()
            assertEquals("Light-theme icons at $size in $bounds", lightIcons, lightStatusBarIcons())
        }
    }

    private val shown = mutableStateOf(true)
    private val window = mutableStateOf(BoxloreWindowLayout.Unspecified)
    private val pane = mutableStateOf<BoxloreLayoutBounds?>(null)

    private fun render() {
        val podcast = TestFixtures.podcast(id = WaveformPage.ids.podcastIndexId, title = "Waveform", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        val model = mock(PodcastInfoViewModel::class.java)
        `when`(model.uiState).thenReturn(MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, emptyList(), isSubscribed = false)))
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.episodeTitlesOnly).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.isHidden).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalPlaybackSpeed).thenReturn(MutableStateFlow(1f))
        `when`(model.globalTrimSilence).thenReturn(MutableStateFlow(false))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        stubArchive(model)
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalBoxlorePaneBounds provides pane.value) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                    if (shown.value) Surface { PodcastInfoScreen(podcast.id, model, {}, { _, _, _ -> }, {}, {}) }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    private fun lightStatusBarIcons(): Boolean {
        val activity: Activity = fixture.compose.activity
        return WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars
    }
}
