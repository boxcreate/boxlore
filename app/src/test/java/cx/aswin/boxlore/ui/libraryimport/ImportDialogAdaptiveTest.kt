package cx.aswin.boxlore.ui.libraryimport

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The library import keeps its place while the window changes under it: the Start fresh selection and
 * list position, the running progress, and a failure with its way back all survive moving between the
 * phone layout, the tablet gates (600dp wide, 480dp tall), the 840dp split width, portrait and
 * landscape, and a half-opened fold, in both directions. Back and the buttons call the current
 * callbacks once, and nothing can be dismissed while an import runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportDialogAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val state = mutableStateOf<OpmlImportState>(OpmlImportState.ShowSelector)

    // Long enough that Show 25 is mid-list: a list scrolled to its end stays anchored to the end instead.
    private val shows = (1..80).map { Podcast("p$it", "Show $it", "Host $it", "") }
    private val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)
    private var dismissed = 0
    private var confirmed = 0
    private var skipped = 0
    private var retried = 0

    /** Phone → either side of each gate → split → short → portrait tablet → folded → phone. */
    private val transitions = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(839f, 600f),
        BoxloreWindowLayout(840f, 600f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(1280f, 480f),
        BoxloreWindowLayout(800f, 1280f),
        BoxloreWindowLayout(673f, 841f, listOf(fold)),
        BoxloreWindowLayout(673f, 841f),
        BoxloreWindowLayout(360f, 740f),
    )

    @Test fun `Start fresh keeps its selection and list position across every window change`() {
        state.value = OpmlImportState.AskCompleted(shows, shows.map { it.id }.toSet())
        render()
        val list = compose.onNodeWithTag("import_fresh_list")
        list.performScrollToNode(hasText("Show 3"))
        compose.onNodeWithText("Show 3").performClick()
        list.performScrollToNode(hasText("Show 7"))
        compose.onNodeWithText("Show 7").performClick()
        // The header and the All row come first, so Show 25 is item 26.
        list.performScrollToIndex(26)
        transitions.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("Show 25").assertIsDisplayed()
            compose.onNodeWithText("Mark 78 shows played").assertIsDisplayed()
        }
        list.performScrollToIndex(1)
        compose.onNodeWithText("78 selected").assertIsDisplayed()
        compose.onNodeWithTag("import_fresh_all").performClick()
        compose.onNodeWithText("Mark 80 shows played").assertIsDisplayed()
        compose.onNodeWithTag("import_fresh_all").performClick()
        compose.onNodeWithText("0 selected").assertIsDisplayed()
        compose.onNodeWithTag("import_fresh_skip").performClick()
        assertEquals(1, skipped)
        compose.onNodeWithTag("import_fresh_all").performClick()
        compose.onNodeWithTag("import_fresh_confirm").performClick()
        assertEquals(1, confirmed)
        back()
        assertEquals(1, dismissed)
    }

    @Test fun `a running import keeps its progress through every window change and cannot be dismissed`() {
        state.value = OpmlImportState.Importing("The Rest Is History", 11f / 48, 11, 48, emptyList())
        render()
        transitions.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("12 of 48 shows").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Subscribing to The Rest Is History").assertIsDisplayed()
            compose.onNodeWithTag("import_echo").assertExists()
        }
        compose.onNodeWithTag("import_close").assertDoesNotExist()
        back()
        assertEquals(0, dismissed)
        compose.runOnIdle { state.value = OpmlImportState.Importing("Radiolab", 30f / 48, 30, 48, emptyList()) }
        compose.onNodeWithText("31 of 48 shows").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("48 shows found").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = OpmlImportState.Success(importedCount = 48, completedCount = 46, importedPodcasts = shows) }
        compose.onNodeWithText("Your shows are in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("shows subscribed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("marked played").assertIsDisplayed()
        compose.runOnIdle { window.value = BoxloreWindowLayout(1280f, 800f) }
        compose.onNodeWithTag("import_continue").performScrollTo().performClick()
        assertEquals(1, dismissed)
    }

    @Test fun `failures say whether the library changed and lead back to the import options`() {
        state.value = OpmlImportState.Error(ImportFailure.NoFeeds)
        render()
        compose.onNodeWithText("No shows in that file").assertIsDisplayed()
        compose.onNodeWithTag("import_unchanged").assertIsDisplayed()
        compose.onNodeWithText("Use an .opml file from your podcast app, or a .json backup from boxlore.").performScrollTo().assertIsDisplayed()
        listOf(BoxloreWindowLayout(1280f, 800f), BoxloreWindowLayout(1280f, 479f), BoxloreWindowLayout(360f, 740f)).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.onNodeWithText("Choose another file").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("import_retry").performClick()
        assertEquals(1, retried)

        compose.runOnIdle { state.value = OpmlImportState.Error(ImportFailure.ImportStopped) }
        compose.onNodeWithText("Import stopped partway").assertIsDisplayed()
        compose.onNodeWithTag("import_unchanged").assertDoesNotExist()
        compose.onNodeWithText("Try again").performScrollTo().assertIsDisplayed()

        compose.runOnIdle { state.value = OpmlImportState.Error(ImportFailure.BackupFailed) }
        compose.onNodeWithText("Couldn’t restore that backup").assertIsDisplayed()
        compose.onNodeWithTag("import_unchanged").assertDoesNotExist()

        compose.runOnIdle { state.value = OpmlImportState.Error(ImportFailure.MarkingStopped) }
        compose.onNodeWithTag("import_retry").assertDoesNotExist()
        compose.onNodeWithTag("import_error_close").performScrollTo().performClick()
        assertEquals(1, dismissed)
        back()
        assertEquals(2, dismissed)
    }

    @Test fun `without a way back the failure offers only Close`() {
        state.value = OpmlImportState.Error(ImportFailure.FileUnreadable)
        render(withRetry = false)
        compose.onNodeWithText("Couldn’t open that file").assertIsDisplayed()
        compose.onNodeWithTag("import_unchanged").assertIsDisplayed()
        compose.onNodeWithTag("import_retry").assertDoesNotExist()
        compose.onNodeWithTag("import_error_close").performScrollTo().performClick()
        assertEquals(1, dismissed)
    }

    @Test fun `the import options stay reachable with large text in RTL on a small phone and Echo keeps its bounds`() {
        window.value = BoxloreWindowLayout(320f, 568f)
        render(direction = LayoutDirection.Rtl, fontScale = 2f)
        listOf("In my boxlore account", "In a boxlore backup", "In another podcast app").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Look for Export OPML in your podcast app’s settings.").performScrollTo().assertIsDisplayed()
        val echo = compose.onNodeWithTag("import_echo").getUnclippedBoundsInRoot()
        val width = echo.right - echo.left
        assertTrue("Echo is $width", width >= ImportEchoMinSize && width <= ImportEchoMaxSize)
        compose.runOnIdle { window.value = BoxloreWindowLayout(740f, 360f) }
        compose.onNodeWithText("In another podcast app").performScrollTo().assertIsDisplayed()
    }

    @Test fun `on a half-opened fold the import stays on one side of the hinge and spreads out again when flat`() {
        window.value = BoxloreWindowLayout(673f, 841f, listOf(fold))
        render()
        val folded = compose.onNodeWithTag("import_page").getUnclippedBoundsInRoot()
        assertTrue("Import straddles the hinge: $folded", folded.bottom <= 410.dp || folded.top >= 431.dp)
        compose.onNodeWithText("In another podcast app").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { window.value = BoxloreWindowLayout(673f, 841f) }
        compose.waitForIdle()
        val flat = compose.onNodeWithTag("import_page").getUnclippedBoundsInRoot()
        assertTrue("Flat import spans the hinge line: $flat", flat.top < 410.dp && flat.bottom > 431.dp)
        compose.onNodeWithText("Bring your shows").assertIsDisplayed()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun render(withRetry: Boolean = true, direction: LayoutDirection = LayoutDirection.Ltr, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalEchoMotion provides false,
                LocalLayoutDirection provides direction,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                    val layout = window.value
                    CompositionLocalProvider(LocalBoxloreWindowLayout provides layout) {
                        Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                            OpmlImportDialog(
                                state.value,
                                OpmlImportDialogActions(
                                    onDismissRequest = { dismissed++ },
                                    onSelectionChanged = { ids -> (state.value as? OpmlImportState.AskCompleted)?.let { state.value = it.copy(selectedIds = ids) } },
                                    onConfirmCompleted = { confirmed++ },
                                    onSkipCompleted = { skipped++ },
                                    onChooseAnotherFile = if (withRetry) ({ retried++ }) else null,
                                ),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
