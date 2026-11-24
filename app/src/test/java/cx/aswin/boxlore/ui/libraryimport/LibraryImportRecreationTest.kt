package cx.aswin.boxlore.ui.libraryimport

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cx.aswin.boxlore.ui.rememberLibraryImportViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The import page and a running import live in the activity's view model store: recreating the
 * activity (text or display size, a foldable's density change) keeps the page and its progress, and
 * a backup restore under way finishes exactly once instead of being cancelled part-way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w475dp-h695dp")
class LibraryImportRecreationTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private val restoreFinished = CompletableDeferred<Unit>()
    private var builds = 0
    private var jsonRuns = 0

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    }

    private fun build(): LibraryImportViewModel {
        builds++
        return LibraryImportViewModel(
            runJson = { _, onStateChange ->
                jsonRuns++
                onStateChange(OpmlImportState.ImportingJson(currentTitle = "Radiolab", progress = 0.4f, currentCount = 4, totalCount = 10))
                restoreFinished.await()
                onStateChange(OpmlImportState.Success(importedCount = 10, completedCount = 0, isJson = true))
            },
            runOpmlStep = { _, _ -> },
        )
    }

    /** Sets the activity's content the way the app root does: the import page bound to the view model. */
    private fun showImport(): LibraryImportViewModel {
        var viewModel: LibraryImportViewModel? = null
        val activity = controller.get()
        activity.setContent {
            val model = rememberLibraryImportViewModel(owner = activity, build = ::build)
            viewModel = model
            OpmlImportDialog(model.state, OpmlImportDialogActions(onDismissRequest = { model.state = OpmlImportState.Idle }))
        }
        idle()
        assertNotNull(viewModel)
        return viewModel!!
    }

    @Test
    fun `a backup restore under way carries on through a recreation and finishes once`() {
        val before = showImport()
        before.source = "home_import_banner"
        before.importJson(Uri.parse("content://boxlore/backup.json"))
        idle()
        assertEquals(0.4f, (before.state as OpmlImportState.ImportingJson).progress)

        controller.recreate()
        val after = showImport()

        assertSame(before, after)
        assertEquals(1, builds)
        assertEquals("home_import_banner", after.source)
        assertTrue(after.state is OpmlImportState.ImportingJson)
        back()
        assertTrue("Back closed a running restore", after.state is OpmlImportState.ImportingJson)

        restoreFinished.complete(Unit)
        idle()
        assertTrue(after.state is OpmlImportState.Success)
        assertEquals(1, jsonRuns)
    }

    @Test
    fun `the open import options survive recreation and a density change, and Back still closes them`() {
        val before = showImport()
        before.state = OpmlImportState.ShowSelector
        idle()

        controller.recreate()
        assertEquals(OpmlImportState.ShowSelector, showImport().state)

        // The Fold 8 cover and its neighbour differ in density, which recreates the activity.
        RuntimeEnvironment.setQualifiers("w443dp-h649dp-xhdpi")
        controller.recreate()
        assertEquals(OpmlImportState.ShowSelector, showImport().state)
        RuntimeEnvironment.setQualifiers("w475dp-h695dp-mdpi")
        controller.recreate()
        val after = showImport()
        assertEquals(OpmlImportState.ShowSelector, after.state)
        assertSame(before, after)

        back()
        assertEquals(OpmlImportState.Idle, after.state)
    }

    @Test
    fun `each import phase is marked once, even when the root runs again after a recreation`() {
        val model = showImport()
        assertTrue(model.claimPhaseMark("running"))
        assertFalse("Marked twice after a recreation", model.claimPhaseMark("running"))
        assertTrue(model.claimPhaseMark("done"))
        assertFalse(model.claimPhaseMark("done"))
        assertFalse(model.claimPhaseMark(null))
        assertTrue("A new import is marked again", model.claimPhaseMark("running"))
    }

    private fun back() {
        controller.get().onBackPressedDispatcher.onBackPressed()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
