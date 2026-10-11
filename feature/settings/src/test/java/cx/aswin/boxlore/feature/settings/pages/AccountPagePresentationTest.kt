package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.BoxLoreUser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountPagePresentationTest {
    @get:Rule val composeRule = createComposeRule()
    private val status = mutableStateOf<CloudSyncUiStatus>(CloudSyncUiStatus.Idle)
    private var synced = 0
    private var continued = 0
    private var signedOut = 0
    private lateinit var view: View
    private val user = BoxLoreUser(uid = "test", email = "listener@example.test", displayName = "A listener with a longer account name", providerId = "google.com", isEmailVerified = true)

    @Test fun `onboarding focuses on restore and continue without account management`() {
        render(onboarding = true)
        writeScreenshot("onboarding")
        composeRule.onNodeWithText("You’re signed in").assertIsDisplayed()
        composeRule.onNodeWithText("Sign Out").assertDoesNotExist()
        composeRule.onNodeWithText("Sync now").performScrollTo().performClick()
        assertEquals(1, synced)
        composeRule.runOnIdle { status.value = CloudSyncUiStatus.Syncing }
        composeRule.onNodeWithText("Syncing your library").performScrollTo().assertIsDisplayed()
        writeScreenshot("syncing")
        composeRule.onNodeWithText("Sync now").assertDoesNotExist()
        composeRule.onNodeWithTag("account_continue").performScrollTo().performClick()
        assertEquals(1, continued)
    }

    @Test fun `settings shows sync retry and account actions with large text in RTL`() {
        render(onboarding = false, accessible = true)
        writeScreenshot("settings-large-rtl")
        composeRule.onNodeWithTag("account_settings_page").assertExists()
        composeRule.runOnIdle { status.value = CloudSyncUiStatus.Error("Private backend detail") }
        composeRule.onNodeWithText("Couldn’t sync your library").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Private backend detail").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()
        writeScreenshot("retry-large-rtl")
        assertEquals(1, synced)
        composeRule.onNodeWithText("Sign Out").performScrollTo().performClick()
        assertEquals(1, signedOut)
        composeRule.onNodeWithTag("account_continue").assertDoesNotExist()
    }

    @Test fun `signed out account keeps all existing sign in methods`() {
        composeRule.setContent {
            view = LocalView.current
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                AccountSettingsPage(authRepository = null, onBack = {}, isOnboarding = true)
            }
        }
        awaitEchoArtwork()
        composeRule.onNodeWithText("Your library, everywhere").assertIsDisplayed()
        writeScreenshot("sign-in")
        composeRule.onNodeWithText("Continue with Google").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Email address").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performScrollTo().performClick()
        assertLabelFits(composeRule.onNodeWithText("Create account"))
        composeRule.onNodeWithText("Confirm password").performScrollTo().assertIsDisplayed()
    }

    @Test fun `email selector labels remain readable with enlarged text`() {
        composeRule.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                    AccountSettingsPage(authRepository = null, onBack = {}, isOnboarding = true)
                }
            }
        }
        awaitEchoArtwork()
        val selector = composeRule.onNodeWithText("Create account").performScrollTo()
        assertLabelFits(selector)
        writeScreenshot("sign-in-large")
    }

    @Test fun `ready and success states keep a full width readable sync action`() {
        render(onboarding = false, accessible = true)
        val button = composeRule.onNodeWithTag("account_sync_now").performScrollTo()
        val card = composeRule.onNodeWithTag("account_sync_status")
        val cardBounds = card.getUnclippedBoundsInRoot()
        val buttonBounds = button.getUnclippedBoundsInRoot()
        assertEquals(cardBounds.right.value - cardBounds.left.value - 40f, buttonBounds.right.value - buttonBounds.left.value, 0.1f)
        assertLabelFits(composeRule.onNodeWithText("Sync now"))
        composeRule.onNodeWithText("Ready to sync").assertDoesNotExist()
        composeRule.runOnIdle { status.value = CloudSyncUiStatus.Success(System.currentTimeMillis()) }
        composeRule.onNodeWithText("Cloud sync").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Library up to date").assertDoesNotExist()
        composeRule.onNodeWithText("Synced just now").assertIsDisplayed()
        composeRule.onNodeWithText("Sync now").performScrollTo().performClick()
        assertEquals(1, synced)
        writeScreenshot("success-large-rtl")
    }

    @Test fun `pressing Echo does not paint a highlight around its transparent artwork`() {
        render(onboarding = true)
        val avatar = composeRule.onNodeWithTag("account_echo_avatar").performScrollTo()
        val bounds = avatar.getUnclippedBoundsInRoot()
        fun cornerPixel(): Int = composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(bounds.left.value.toInt() + 5, bounds.bottom.value.toInt() - 5).also { bitmap.recycle() }
        }
        val before = cornerPixel()
        composeRule.mainClock.autoAdvance = false
        avatar.performTouchInput { down(center) }
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()
        assertEquals(before, cornerPixel())
        avatar.performTouchInput { cancel() }
        composeRule.mainClock.autoAdvance = true
        avatar.performClick()
    }

    private fun assertLabelFits(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("Text overflow: width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, size=${layout.size}, paragraph=${layout.multiParagraph.height}", layout.hasVisualOverflow)
    }

    private fun awaitEchoArtwork() {
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithTag("account_echo_avatar").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun render(onboarding: Boolean, accessible: Boolean = false) {
        composeRule.setContent {
            val density = LocalDensity.current
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (accessible) 2f else 1f), LocalLayoutDirection provides if (accessible) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                BoxLoreTheme(darkTheme = !onboarding, dynamicColor = false) {
                    AccountPageScaffold({}, onboarding, rememberScrollState()) {
                        AccountSignedInContent(user, status.value, SignedInActions({}, { signedOut++ }, {}, {}, { synced++ }, { continued++ }), onboarding)
                    }
                }
            }
        }
        awaitEchoArtwork()
    }

    private fun writeScreenshot(name: String) {
        val directory = System.getenv("BOXLORE_UI_TEST_ARTIFACTS") ?: return
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "boxlore-account-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
