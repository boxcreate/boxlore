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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
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
        // The card merges its title and description; read the one text asked for.
        compose.onNode(hasText(label), useUnmergedTree = true).performScrollTo().fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
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

    @Config(qualifiers = "de-w1400dp-h1400dp-mdpi")
    @Test
    fun `paired cards share a height at large German text`() {
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

    // Regression: switching between one and two columns reused the old scroll offset, landing on another card.
    @Test fun `the hub keeps the first card in view when a phone turns sideways and back`() {
        render()
        val scroller = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        // The header slims as the page scrolls, so scroll until Appearance sits at the top.
        repeat(3) {
            val appearance = compose.onNode(hasTestTag(HUB_CARD_TAG_PREFIX + ProfileSettingsDestination.Appearance.name)).fetchSemanticsNode().boundsInRoot
            val delta = appearance.top - scroller.fetchSemanticsNode().boundsInRoot.top
            if (delta != 0f) scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
            compose.waitForIdle()
        }
        assertEquals(ProfileSettingsDestination.Appearance, firstVisibleCard())
        for (size in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND)) {
            compose.runOnIdle { window = size }
            compose.waitForIdle()
            assertEquals("first card in view at $size", ProfileSettingsDestination.Appearance, firstVisibleCard())
        }
    }

    // Regression: on wide windows two cards share a row, and one title line or two description lines
    // cut longer languages.
    @Config(qualifiers = "es-w1400dp-h1400dp-mdpi")
    @Test
    fun `in Spanish no title or description is cut on sideways phones and portrait tablets`() = assertNoDescriptionCut()

    @Config(qualifiers = "de-w1400dp-h1400dp-mdpi")
    @Test
    fun `in German no title or description is cut on sideways phones, portrait tablets and small phones`() = assertNoDescriptionCut()

    private fun assertNoDescriptionCut() {
        render()
        for (size in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreWindowLayout(360f, 800f))) {
            compose.runOnIdle { window = size }
            compose.waitForIdle()
            (HUB_TITLES + HUB_DESCRIPTIONS).forEach { assertFalse("${text(it)} at $size", overflows(text(it))) }
        }
    }

    /** The card nearest the top of the scrolled page that is fully in view (the leftmost on a shared row). */
    private fun firstVisibleCard(): ProfileSettingsDestination {
        val top = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNode().boundsInRoot.top
        return ProfileSettingsDestination.entries.mapNotNull { destination ->
            compose.onAllNodes(hasTestTag(HUB_CARD_TAG_PREFIX + destination.name)).fetchSemanticsNodes().firstOrNull()?.let { destination to it.boundsInRoot }
        }.filter { it.second.top >= top - 1f }.minWith(compareBy({ it.second.top }, { it.second.left })).first
    }

    private companion object {
        val HUB_TITLES = listOf(
            R.string.settings_page_sync,
            R.string.settings_page_library,
            R.string.settings_page_appearance,
            R.string.settings_page_playback,
            R.string.settings_page_downloads,
            R.string.settings_privacy_title,
            R.string.settings_category_about,
        )
        val HUB_DESCRIPTIONS = listOf(
            R.string.settings_hub_sync_description,
            R.string.settings_hub_library_description,
            R.string.settings_hub_appearance_description,
            R.string.settings_hub_playback_description,
            R.string.settings_hub_downloads_description,
            R.string.settings_hub_privacy_description,
            R.string.settings_hub_about_description,
        )
    }
}
