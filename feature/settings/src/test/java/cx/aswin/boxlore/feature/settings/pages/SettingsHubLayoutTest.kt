package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.feature.settings.ProfileSettingsDestination
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hub: cards that grow with large text and sit two to a row on wide windows, Check for updates
 * in its own card, and the Cloud Sync card saying whether you're signed in and synced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w1400dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsHubLayoutTest {
    @get:Rule val compose = createComposeRule()

    private var window by mutableStateOf(BoxloreTestWindows.PHONE)
    private var fontScale by mutableStateOf(1f)
    private var account by mutableStateOf<SettingsHubAccount?>(null)
    private val updates = mutableListOf<Unit>()

    private fun render() {
        compose.setContent {
            val size = window
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
                LocalDensity provides Density(1f, fontScale),
            ) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        SettingsHub(onBack = {}, onNavigate = {}, onCheckForUpdates = { updates += Unit }, account = account)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun cardBounds(titleId: Int) = compose.onNodeWithText(text(titleId)).fetchSemanticsNode().boundsInRoot

    private fun overflows(label: String): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(label)).performScrollTo().fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        // Robolectric can flag fitting text as overflowing; a cut line is what listeners see.
        val layout = layouts.last()
        return (0 until layout.lineCount).any(layout::isLineEllipsized)
    }

    // Regression: at 130% text every hub description was cut to one line on phones and the Fold 8 cover.
    @Test fun `at large text on the cover screen every description fits`() {
        window = BoxloreTestWindows.COVER_443
        render()
        for (scale in listOf(1.3f, 2f)) {
            compose.runOnIdle { fontScale = scale }
            compose.waitForIdle()
            listOf(R.string.settings_hub_sync_description, R.string.settings_hub_library_description, R.string.settings_hub_appearance_description).forEach {
                assertFalse("${text(it)} at $scale", overflows(text(it)))
            }
        }
    }

    @Test fun `cards sit one to a row on phones and covers, two on sideways phones and portrait tablets, keeping order`() {
        render()
        for ((size, columns) in listOf(BoxloreTestWindows.PHONE to 1, BoxloreTestWindows.COVER_475 to 1, BoxloreTestWindows.PHONE_LAND to 2, BoxloreTestWindows.TABLET_PORTRAIT to 2, BoxloreTestWindows.PHONE to 1)) {
            compose.runOnIdle { window = size }
            compose.waitForIdle()
            val sync = cardBounds(R.string.settings_page_sync)
            val library = cardBounds(R.string.settings_page_library)
            if (columns == 1) {
                assertTrue("$size: Library under Cloud Sync", library.top > sync.bottom)
            } else {
                assertEquals("$size: Library beside Cloud Sync", sync.top, library.top, 1f)
                assertTrue("$size: Library after Cloud Sync", library.left > sync.right)
            }
        }
    }

    @Test fun `paired cards share a height at large German-length text`() {
        window = BoxloreTestWindows.PHONE_LAND
        fontScale = 1.3f
        account = SettingsHubAccount("a.listener.with.a.very.long.address@example.test", CloudSyncUiStatus.Syncing)
        render()
        val sync = compose.onNode(hasTestTag(HUB_CARD_TAG_PREFIX + ProfileSettingsDestination.SyncAndBackups.name)).fetchSemanticsNode().boundsInRoot
        val library = compose.onNode(hasTestTag(HUB_CARD_TAG_PREFIX + ProfileSettingsDestination.Library.name)).fetchSemanticsNode().boundsInRoot
        assertEquals(sync.height, library.height, 1f)
    }

    @Test fun `Check for updates sits in its own card and runs once`() {
        render()
        val row = compose.onNode(hasText(text(R.string.settings_check_for_updates)) and hasAnyAncestor(hasTestTag(SETTINGS_HUB_UPDATES_TAG)))
        row.performScrollTo().performClick()
        assertEquals(1, updates.size)
    }

    @Test fun `the Cloud Sync card says who is signed in and the sync state, or asks to sign in`() {
        render()
        compose.onNodeWithText(text(R.string.settings_hub_sync_description)).assertExists()
        compose.runOnIdle { account = SettingsHubAccount(null, CloudSyncUiStatus.Idle) }
        compose.onNodeWithText(text(R.string.settings_sync_sign_in_prompt)).assertExists()
        for ((status, badge) in listOf(CloudSyncUiStatus.Success(1L) to R.string.settings_sync_badge_active, CloudSyncUiStatus.Syncing to R.string.settings_sync_badge_syncing, CloudSyncUiStatus.Error("x") to R.string.settings_sync_badge_issue)) {
            compose.runOnIdle { account = SettingsHubAccount("listener@example.test", status) }
            compose.onNodeWithText(text(badge), substring = true).assertExists()
            compose.onNodeWithText("listener@example.test", substring = true).assertExists()
        }
    }

    @Test fun `a long email keeps the Cloud Sync card to two description lines`() {
        account = SettingsHubAccount("a.listener.with.a.very.very.long.address.indeed@example.test", CloudSyncUiStatus.Success(1L))
        render()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("a.listener", substring = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertTrue(layouts.last().lineCount <= 2)
    }

    @Test fun `the hub keeps its scroll position when a phone turns sideways and back`() {
        render()
        compose.onNodeWithText(text(R.string.settings_category_about)).performScrollTo()
        compose.waitForIdle()
        for (size in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE)) {
            compose.runOnIdle { window = size }
            compose.waitForIdle()
        }
        val about = cardBounds(R.string.settings_category_about)
        assertTrue("About stays on screen (${about.top})", about.top < BoxloreTestWindows.PHONE.heightDp && about.bottom > 0f)
    }
}
