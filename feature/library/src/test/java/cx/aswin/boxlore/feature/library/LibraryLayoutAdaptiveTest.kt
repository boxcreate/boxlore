package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.feature.library.subscriptions.EditFolderActions
import cx.aswin.boxlore.feature.library.subscriptions.EditFolderDraft
import cx.aswin.boxlore.feature.library.subscriptions.EditFolderPage
import cx.aswin.boxlore.feature.library.subscriptions.EditFolderShows
import cx.aswin.boxlore.feature.library.subscriptions.folderCoversMaxWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library's pages on sideways phones, wide compact windows and tablets: controls stay clear of side
 * system bars and cutouts, folder covers stop at poster size, each screen keeps one width, and
 * lists end just above the player on tablets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryLayoutAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE_LAND)
    private val fixtures = LibraryCaptureFixtures

    private fun show(insets: SystemInsets? = null, content: @Composable () -> Unit) {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) { content() }
                }
            }
        }
        if (insets != null) compose.runOnUiThread { applyFakeSystemInsets(compose.activity, insets) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `a sideways phone's editor Save and Play All stay clear of the cutout and the side buttons`() {
        val folder = fixtures.folders.first { it.id == "news" }
        show(SystemInsets.SIDEWAYS_3_BUTTON) {
            Box(Modifier.fillMaxSize()) {
                EditFolderPage(
                    folder = folder,
                    shows = EditFolderShows(fixtures.podcasts.take(3), fixtures.podcasts),
                    draft = EditFolderDraft.from(folder),
                    onDraftChange = {},
                    actions = EditFolderActions({}, {}, {}, {}, {}),
                    bottomPadding = 0.dp,
                )
                LibraryPlaybackLayout(Modifier.fillMaxSize(), content = {}, playbackAction = { PlayAllFab({}, false, Modifier.testTag("play_all")) })
            }
        }
        val width = window.value.widthDp
        val save = compose.onNodeWithText("Save").fetchSemanticsNode().boundsInRoot
        val play = bounds("play_all")
        val close = compose.onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot
        for ((name, rect) in listOf("Save" to save, "Play All" to play, "Close" to close)) {
            assertTrue("$name ($rect) clear of the cutout", rect.left >= 32f)
            assertTrue("$name ($rect) clear of the side buttons", rect.right <= width - 48f)
        }
    }

    @Test
    fun `enlarged folder covers stop at poster size on wide windows and fill the row on phones`() {
        val caps = mutableMapOf<BoxloreWindowLayout, Dp?>()
        val step = mutableStateOf(BoxloreTestWindows.PHONE)
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides step.value) { caps[step.value] = folderCoversMaxWidth(3) }
        }
        for (each in with(BoxloreTestWindows) { listOf(PHONE_LAND, TABLET, COVER_475, PHONE) }) {
            compose.runOnIdle { step.value = each }
            compose.waitForIdle()
        }
        assertEquals(208.dp * 3 + 16.dp, caps[BoxloreTestWindows.PHONE_LAND])
        assertEquals(208.dp * 3 + 16.dp, caps[BoxloreTestWindows.TABLET])
        // The Fold 8 cover's covers are far narrower than the cap, so they fill the row as before.
        assertTrue(requireNotNull(caps[BoxloreTestWindows.COVER_475]) > 475.dp)
        assertNull(caps[BoxloreTestWindows.PHONE])
    }

    @Test
    fun `Downloads select mode keeps Close where Back was on a tablet`() {
        window.value = BoxloreTestWindows.TABLET
        show {
            DownloadedEpisodesScreen(fixtures.viewModel(fixtures.loaded()), fixtures.smartDownloadPrefs(), onBack = {}, onExploreClick = {}, onPodcastShowClick = { _, _ -> })
        }
        val back = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Select podcasts").performClick()
        val close = compose.onNodeWithContentDescription("Cancel selection").fetchSemanticsNode().boundsInRoot
        assertEquals(back.left, close.left, 1f)
    }
}
