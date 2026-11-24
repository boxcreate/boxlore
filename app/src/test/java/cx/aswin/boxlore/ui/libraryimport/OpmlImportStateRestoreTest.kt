package cx.aswin.boxlore.ui.libraryimport

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The import picker opened from Welcome stays open, with where it was opened from, when the activity
 * is recreated (a fold, an unfold or a display-size change) and when the window only changes size.
 * Work in progress is the import's own, so a recreated screen shows nothing for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpmlImportStateRestoreTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreTestWindows.COVER_475)
    private var setState: (OpmlImportState) -> Unit = {}
    private var current: OpmlImportState = OpmlImportState.Idle
    private var source = ""

    /** The root's import state, held as BoxLoreAppRoot holds it, around the real import screens. */
    @Composable
    private fun Host() {
        val import = rememberOpmlImportState()
        var state by import.state
        var openedFrom by import.source
        setState = { next ->
            state = next
            openedFrom = OpenedFrom
        }
        current = state
        source = openedFrom
        BoxLoreTheme(dynamicColor = false) {
            // Echo rests, so its endless motion never keeps the test clock busy.
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalEchoMotion provides false) {
                Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                    OpmlImportDialog(state, OpmlImportDialogActions(onDismissRequest = { state = OpmlImportState.Idle }))
                }
            }
        }
    }

    private companion object {
        /** Not the root's starting value, so a source that isn't kept would show. */
        const val OpenedFrom = "home_import_banner"
    }

    @Test
    fun `the picker survives recreation with where it was opened from`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host() }
        compose.runOnIdle { setState(OpmlImportState.ShowSelector) }
        compose.onNodeWithTag("import_option_opml").assertExists()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("import_option_opml").assertExists()
        assertEquals(OpmlImportState.ShowSelector, current)
        assertEquals("where the picker was opened from", OpenedFrom, source)
    }

    @Test
    fun `an import in progress comes back as nothing open`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host() }
        compose.runOnIdle { setState(OpmlImportState.Importing("Show 1", 0.4f, 2, 5, emptyList())) }

        restoration.emulateSavedInstanceStateRestore()

        assertEquals(OpmlImportState.Idle, current)
        compose.onNodeWithTag("import_option_opml").assertDoesNotExist()
    }

    @Test
    fun `the picker stays open while the cover changes size`() {
        compose.setContent { Host() }
        compose.runOnIdle { setState(OpmlImportState.ShowSelector) }

        compose.walkWindows(window, BoxloreTestWindows.COVER_443, BoxloreTestWindows.COVER_475, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.COVER_475) {
            compose.onNodeWithTag("import_option_opml").assertExists()
            assertEquals(OpmlImportState.ShowSelector, current)
        }
    }
}
