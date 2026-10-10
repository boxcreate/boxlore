package cx.aswin.boxlore.updates

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AppUpdatesPanelTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `long notes scroll without hiding download or close actions`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val notes = (1..60).joinToString("\n\n") { "Sample section $it. Release-note layout content for checking scrolling and action visibility." }
            val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "0.0.29", updateManifest().copy(notes = notes))) }, MemoryUpdateStore(), 28, 34)
            runBlocking { checker.check() }
            val installer = object : ApkInstaller {
                override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File = error("This test only checks layout")
                override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = error("This test never installs")
            }
            val updates = AppUpdates(checker, false, installer, scope)
            updates.open()
            composeRule.setContent { MaterialTheme { AppUpdatesPanel(updates, {}, {}) } }
            composeRule.onNodeWithText("Download update").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Close updates").assertIsDisplayed()
            composeRule.onNodeWithText(notes.substringAfterLast("\n\n")).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Check for updates").assertDoesNotExist()
            composeRule.onNodeWithText("Download update").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Close updates").assertIsDisplayed()
        } finally {
            scope.cancel()
        }
    }

    @Test fun `available screen shows reviewed notes and download before installation`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            var downloads = 0
            var installClicks = 0
            val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "0.0.29", updateManifest())) }, MemoryUpdateStore(), 28, 34)
            runBlocking { checker.check() }
            val installer = object : ApkInstaller {
                override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File {
                    downloads++
                    return File("fixture")
                }
                override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = true
            }
            val updates = AppUpdates(checker, false, installer, scope)
            updates.open()
            composeRule.setContent { MaterialTheme { AppUpdatesPanel(updates, { installClicks++ }, {}) } }
            composeRule.onNodeWithText("Version 0.0.29").assertIsDisplayed()
            composeRule.onNodeWithText("Improved playback").assertIsDisplayed()
            assertEquals(0, downloads)
            composeRule.onNodeWithText("Download update").performClick()
            composeRule.onNodeWithText("Install update").assertIsDisplayed().performClick()
            assertEquals(1, downloads)
            assertEquals(1, installClicks)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `manual check failure has recovery and cannot claim up to date`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val checker = UpdateChecker(UpdateSource { error("offline fixture") }, MemoryUpdateStore(), 28, 34)
            runBlocking { checker.check() }
            val installer = object : ApkInstaller {
                override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File = error("No download during check")
                override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = error("No install during check")
            }
            val updates = AppUpdates(checker, false, installer, scope)
            updates.open()
            composeRule.setContent { MaterialTheme { AppUpdatesPanel(updates, {}, {}) } }
            composeRule.onNodeWithText("Couldn’t check for updates. Try again later.").assertIsDisplayed()
            composeRule.onNodeWithText("Check for updates").assertIsDisplayed()
            composeRule.onNodeWithText("You’re up to date").assertDoesNotExist()
        } finally {
            scope.cancel()
        }
    }

    @Test fun `interrupted installer verification stays ready and duplicate taps cannot launch twice`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            var launches = 0
            val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "0.0.29", updateManifest())) }, MemoryUpdateStore(), 28, 34)
            runBlocking { checker.check() }
            val installer = object : ApkInstaller {
                override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File = File("fixture")
                override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean {
                    launches++
                    awaitCancellation()
                }
            }
            val updates = AppUpdates(checker, false, installer, scope)
            updates.download()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = Robolectric.buildActivity(Activity::class.java).get()
            val install = scope.launch { updates.install(activity) }
            scope.launch { updates.install(activity) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, launches)
            assertEquals(ApkInstallStage.VERIFYING, updates.install.value.stage)
            install.cancel()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(ApkInstallStage.READY, updates.install.value.stage)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `unknown sources settings return keeps download and installation action`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            var allowed = false
            var downloads = 0
            var launches = 0
            val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "0.0.29", updateManifest())) }, MemoryUpdateStore(), 28, 34)
            runBlocking { checker.check() }
            val session = MemoryUpdateSessionStore()
            val installer = object : ApkInstaller {
                override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File {
                    downloads++
                    return File("verified-fixture")
                }
                override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean {
                    launches++
                    return allowed
                }
            }
            val updates = AppUpdates(checker, false, installer, scope, session)
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            updates.open()
            updates.download()
            shadowOf(Looper.getMainLooper()).idle()
            scope.launch { updates.install(activity) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(ApkInstallStage.PERMISSION, updates.install.value.stage)
            assertEquals(true, updates.visible.value)
            assertEquals(true, session.visible)
            allowed = true
            updates.foreground()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, launches) // Returning from Settings never auto-starts the installer.
            scope.launch { updates.install(activity) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(ApkInstallStage.READY, updates.install.value.stage)
            assertEquals(1, downloads)
            assertEquals(2, launches)
            assertEquals(true, updates.visible.value)
        } finally {
            scope.cancel()
        }
    }
}
