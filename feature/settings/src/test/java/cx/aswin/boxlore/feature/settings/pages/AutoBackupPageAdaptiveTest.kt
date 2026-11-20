package cx.aswin.boxlore.feature.settings.pages

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.AutoBackupFrequency
import cx.aswin.boxlore.core.prefs.AutoBackupProblem
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import cx.aswin.boxlore.feature.settings.AdaptiveSettingsLayout
import cx.aswin.boxlore.feature.settings.ProfileSettingsDestination
import cx.aswin.boxlore.feature.settings.SettingsScreenConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cloud Sync & Backups › Automatic backup: the folder picker opened at one window size still hands its folder
 * back once (and to the current callbacks) after phone, tablet, split, short and narrow windows and recreation,
 * and the group keeps its stored state through the same changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoBackupPageAdaptiveTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val state = MutableStateFlow(AutoBackupUiState())
    private val registry = RecordingRegistry()
    private val picked = mutableListOf<Uri>()
    private val calls = mutableListOf<String>()
    private val folder = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ASync")

    private val control = AutoBackupControl(
        state = state,
        onFolderPicked = { picked += it },
        onTurnOff = { calls += "off" },
        onSetFrequency = { calls += "frequency:${it.storedValue}" },
        onSetKeepCount = { calls += "keep:$it" },
        onBackUpNow = { calls += "now" },
    )

    @Composable
    private fun Host() {
        BoxLoreTheme(dynamicColor = false) {
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides owner,
                LocalBoxloreWindowLayout provides window.value,
            ) {
                Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                    // As in SettingsScreen: the picker lives above the adaptive layout.
                    val page = rememberAutoBackupPage(control)
                    AdaptiveSettingsLayout(ProfileSettingsDestination.SyncAndBackups, SettingsScreenConfig({}, {}), {}) {
                        SyncAndBackupsPage(null, CloudSyncUiStatus.Idle, LibraryBackupActions({}, {}, {}, {}), {}, {}, page)
                    }
                }
            }
        }
    }

    private val windows = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(840f, 600f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(839f, 1200f),
        BoxloreWindowLayout(360f, 740f),
    )

    @Test fun `turning on without a folder opens the picker, and cancelling leaves it off`() {
        compose.setContent { Host() }
        compose.onNodeWithText("Back up automatically").performScrollTo().performClick()
        assertEquals(1, registry.launches.size)
        assertNull(registry.launches.single().second)
        compose.runOnIdle { registry.dispatchResult(registry.launches.single().first, Activity.RESULT_CANCELED, null) }
        assertEquals(emptyList<Uri>(), picked)
        compose.onNodeWithText("Folder").assertDoesNotExist()
    }

    @Test fun `a picker opened on a phone delivers its folder once after every window change`() {
        compose.setContent { Host() }
        compose.onNodeWithText("Back up automatically").performScrollTo().performClick()
        windows.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("Back up automatically").performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { registry.dispatchResult(registry.launches.single().first, Activity.RESULT_OK, Intent().setData(folder)) }
        assertEquals(listOf(folder), picked)
    }

    @Test fun `a picker opened in the split delivers its folder after recreation`() {
        window.value = BoxloreWindowLayout(1280f, 800f)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host() }
        compose.onNodeWithText("Back up automatically").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { window.value = BoxloreWindowLayout(360f, 740f) }
        compose.runOnIdle { registry.dispatchResult(registry.launches.single().first, Activity.RESULT_OK, Intent().setData(folder)) }
        assertEquals(listOf(folder), picked)
    }

    @Test fun `stored settings and a folder problem stay through window changes and their actions still work`() {
        state.value = AutoBackupUiState(
            AutoBackupSettings(
                enabled = true,
                folderUri = folder.toString(),
                folderName = "Sync",
                lastError = AutoBackupProblem.FOLDER_MISSING,
            ),
        )
        compose.setContent { Host() }
        (windows + windows.reversed()).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("Sync").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("This folder was moved or deleted.").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag(AutoBackupTestTags.FIX).performScrollTo().performClick()
        // The picker starts in the current folder.
        assertEquals(folder, registry.launches.single().second)
        compose.onNodeWithText("Daily").performScrollTo().performClick()
        compose.onNodeWithText("10").performScrollTo().performClick()
        compose.onNodeWithText("Back up now").performScrollTo().performClick()
        compose.onNodeWithText("Back up automatically").performScrollTo().performClick()
        assertEquals(listOf("frequency:${AutoBackupFrequency.DAILY.storedValue}", "keep:10", "now", "off"), calls)
    }

    @Test fun `a running backup shows its status and ignores another tap`() {
        state.value = AutoBackupUiState(AutoBackupSettings(enabled = true, folderUri = folder.toString(), folderName = "Sync"), isBackingUp = true)
        compose.setContent { Host() }
        compose.onNodeWithText("Backing up…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back up now").performScrollTo().performClick()
        assertEquals(emptyList<String>(), calls)
    }

    private class RecordingRegistry : ActivityResultRegistry() {
        val launches = mutableListOf<Pair<Int, Any?>>()

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launches += requestCode to input
        }
    }
}
