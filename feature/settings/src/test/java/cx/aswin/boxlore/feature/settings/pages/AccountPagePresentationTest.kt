package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.echo.parseEchoCharacterManifest
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.BoxLoreUser
import java.io.File
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
 * The Account & sync page (design C) in every state: signed out (three ways to sign in, one form at a
 * time, Back returns to the choices), checking email (a sign-in link, or confirming a new account),
 * signed in from onboarding (the library coming to this phone, then Continue) and from Settings (sync
 * status, what syncs, the account). Large text and right-to-left keep everything readable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h900dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountPagePresentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val status = mutableStateOf<CloudSyncUiStatus>(CloudSyncUiStatus.Idle)
    private var synced = 0
    private var continued = 0
    private var signedOut = 0
    private var resets = 0
    private lateinit var view: View
    private val user = BoxLoreUser(uid = "test", email = "listener@example.test", displayName = "A listener with a longer account name", providerId = "google.com", isEmailVerified = true)

    @Test fun `onboarding shows the library coming to this phone and Continue, without account management`() {
        renderSignedIn(onboarding = true)
        composeRule.onNodeWithText("You’re signed in").assertIsDisplayed()
        composeRule.onNodeWithText("listener@example.test").assertIsDisplayed()
        composeRule.onNodeWithText("Bringing your library to this phone").assertIsDisplayed()
        assertEquals(3, composeRule.onAllNodesWithText("Syncing").fetchSemanticsNodes().size)
        // There's no need to wait, said above the tiles so it isn't missed.
        val hint = composeRule.onNodeWithTag("account_continue_hint").assertIsDisplayed().getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("No need to wait").assertIsDisplayed()
        assertTrue("the hint sits above the tiles", hint.bottom <= composeRule.onNodeWithTag("account_sync_tiles").getUnclippedBoundsInRoot().top)
        composeRule.onNodeWithText("Sign out").assertDoesNotExist()
        composeRule.runOnIdle { status.value = CloudSyncUiStatus.Success(System.currentTimeMillis()) }
        composeRule.onNodeWithText("Your library is on this phone").assertIsDisplayed()
        assertEquals(3, composeRule.onAllNodesWithText("Done").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("account_continue_hint").assertDoesNotExist()
        composeRule.runOnIdle { status.value = CloudSyncUiStatus.Error("Private backend detail") }
        composeRule.onNodeWithTag("account_continue_hint").assertDoesNotExist()
        composeRule.onNodeWithText("Couldn’t sync").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Private backend detail").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(1, synced)
        composeRule.onNodeWithTag("account_continue").performClick()
        assertEquals(1, continued)
    }

    @Test fun `settings shows sync status, what syncs and the account, with large text in RTL`() {
        renderSignedIn(onboarding = false, accessible = true)
        composeRule.onNodeWithTag("account_settings_page").assertExists()
        composeRule.onNodeWithText("Sync is on").assertIsDisplayed()
        composeRule.waitForIdle()
        assertLabelFits(composeRule.onNodeWithText("Sync now"))
        composeRule.onNodeWithTag("account_sync_now").performScrollTo().performClick()
        assertEquals(1, synced)
        composeRule.runOnIdle { status.value = CloudSyncUiStatus.Syncing }
        composeRule.onNodeWithText("Syncing your library").assertIsDisplayed()
        composeRule.onNodeWithTag("account_sync_now").assertDoesNotExist()
        composeRule.runOnIdle { status.value = CloudSyncUiStatus.Success(System.currentTimeMillis()) }
        composeRule.onNodeWithText("Up to date").assertIsDisplayed()
        composeRule.onNodeWithText("Synced just now").assertIsDisplayed()
        composeRule.runOnIdle { status.value = CloudSyncUiStatus.Error("Private backend detail") }
        composeRule.onNodeWithText("Couldn’t sync").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(2, synced)
        composeRule.onNodeWithText("Signed in with Google").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reset password").assertDoesNotExist()
        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(1, signedOut)
        composeRule.onNodeWithTag("account_continue").assertDoesNotExist()
        composeRule.onNodeWithTag("account_deletion_info").performScrollTo().assertIsDisplayed()
    }

    @Test fun `password accounts can reset their password`() {
        renderSignedIn(onboarding = false, account = user.copy(providerId = "password"))
        composeRule.onNodeWithText("Signed in with a password").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reset password").performScrollTo().performClick()
        assertEquals(1, resets)
    }

    @Test fun `signed out offers three ways to sign in and opens one form at a time`() {
        renderSignedOut()
        composeRule.onNodeWithText("Your library, on every device").assertIsDisplayed()
        composeRule.onNodeWithTag("account_not_now").assertIsDisplayed()
        composeRule.onNodeWithTag("account_method_google").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_email_field").assertDoesNotExist()
        composeRule.onNodeWithTag("account_method_link").performScrollTo().performClick()
        composeRule.onNodeWithTag("account_email_field").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_send_link").assertIsNotEnabled()
        composeRule.onNodeWithTag("account_method_password").assertDoesNotExist()
        composeRule.onNodeWithTag("account_other_ways").performScrollTo().performClick()
        composeRule.onNodeWithTag("account_method_password").performScrollTo().performClick()
        composeRule.onNode(hasText("Create account") and hasRole(Role.RadioButton)).performScrollTo().performClick()
        assertLabelFits(composeRule.onNode(hasText("Create account") and hasRole(Role.RadioButton)))
        composeRule.onNodeWithText("Confirm password").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("At least 6 characters").assertIsDisplayed()
    }

    @Test fun `Back from a sign-in form returns to the ways to sign in and keeps what was typed`() {
        renderSignedOut()
        composeRule.onNodeWithTag("account_method_link").performScrollTo().performClick()
        composeRule.onNodeWithTag("account_email_field").performTextReplacement("listener@example.test")
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("account_method_link").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_method_link").performClick()
        composeRule.onNodeWithText("listener@example.test").assertIsDisplayed()
    }

    @Test fun `ways to sign in stay readable with enlarged text`() {
        renderSignedOut(accessible = true)
        assertLabelFits(composeRule.onNodeWithText("Email me a sign-in link").performScrollTo())
        assertLabelFits(composeRule.onNodeWithText("Use email and password").performScrollTo())
    }

    @Test fun `a sent sign-in link says where it went, opens email and can change the address`() {
        lateinit var state: AccountAuthState
        composeRule.setContent {
            EchoStillTheme(darkTheme = false, dynamicColor = false) {
                val context = LocalContext.current
                val focus = LocalFocusManager.current
                val scope = rememberCoroutineScope()
                state = remember { AccountAuthState(null, context, null, focus, scope, SavedAccountAuthState(email = "listener@gmail.com", magicLinkSent = true, methodChosen = true)) }
                AccountPageScaffold({}, isOnboarding = true, scroll = rememberScrollState()) { AccountCheckEmailContent(AccountEmailCheck.Link, state) }
            }
        }
        composeRule.onNodeWithText("Check your email").assertIsDisplayed()
        composeRule.onNodeWithText("listener@gmail.com").assertIsDisplayed()
        composeRule.onNodeWithText("Open Gmail").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Resend link").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Not in your inbox? Check your spam folder.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_change_email").performClick()
        composeRule.runOnIdle { assertFalse(state.magicLinkSent) }
    }

    @Test fun `confirming a new account offers I've confirmed and waits before resending`() {
        composeRule.setContent {
            EchoStillTheme(darkTheme = true, dynamicColor = false) {
                val context = LocalContext.current
                val focus = LocalFocusManager.current
                val scope = rememberCoroutineScope()
                val state = remember { AccountAuthState(null, context, null, focus, scope, SavedAccountAuthState(email = "listener@example.test", isAwaitingVerification = true, resendCooldownSeconds = 30)) }
                AccountPageScaffold({}, isOnboarding = false, scroll = rememberScrollState()) { AccountCheckEmailContent(AccountEmailCheck.Confirm, state) }
            }
        }
        composeRule.onNodeWithText("Confirm your email").assertIsDisplayed()
        composeRule.onNodeWithText("Open email app").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("I’ve confirmed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_resend").performScrollTo().assertIsNotEnabled()
    }

    @Test fun `pressing Echo does not paint a highlight around its transparent artwork`() {
        renderSignedIn(onboarding = true)
        val avatar = composeRule.onNodeWithTag("account_echo_avatar").performScrollTo()
        val bounds = avatar.getUnclippedBoundsInRoot()
        fun cornerPixel(): Int = composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(bounds.left.value.toInt() + 2, bounds.top.value.toInt() + 2).also { bitmap.recycle() }
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
    }

    @Test fun `every pose the account Echo uses ships in the Echo manifest`() {
        val spec = parseEchoCharacterManifest(File("../../core/designsystem/src/main/assets/echo-character/manifest.json").readText())
        (AccountEchoOutfits + AccountEchoSyncing).forEach { assertTrue("missing $it", it in spec.sequences) }
        (AccountEchoOutfits + AccountEchoSyncing).forEach { assertTrue("no stage reach for $it", spec.reach(it) != null) }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    /** The label is fully shown: nothing clipped sideways or below, no ellipsis or extra lines. */
    private fun assertLabelFits(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("Text overflow: width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, size=${layout.size}", layout.hasVisualOverflow)
        assertFalse("Text runs past its lines", layout.multiParagraph.didExceedMaxLines)
    }

    private fun renderSignedOut(accessible: Boolean = false) {
        composeRule.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (accessible) 2f else 1f)) {
                EchoStillTheme(darkTheme = false, dynamicColor = false) {
                    AccountSettingsPage(authRepository = null, onBack = {}, isOnboarding = true)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun renderSignedIn(onboarding: Boolean, accessible: Boolean = false, account: BoxLoreUser = user) {
        composeRule.setContent {
            val density = LocalDensity.current
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (accessible) 2f else 1f), LocalLayoutDirection provides if (accessible) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                EchoStillTheme(darkTheme = !onboarding, dynamicColor = false) {
                    val actions = SignedInActions({ resets++ }, { signedOut++ }, {}, {}, { synced++ })
                    AccountPageScaffold(
                        {},
                        onboarding,
                        rememberScrollState(),
                        bottomBar = if (onboarding) {
                        { AccountContinueButton { continued++ } }
                    } else {
                        null
                    }
                    ) {
                        if (onboarding) AccountSignedInOnboarding(account, status.value, actions.onSyncNow) else AccountSignedInSettings(account, status.value, actions)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}

/** The app theme with Echo held on its resting pose, so the screen can go idle under test. */
@androidx.compose.runtime.Composable
private fun EchoStillTheme(darkTheme: Boolean? = null, dynamicColor: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion provides false) {
        if (darkTheme == null) BoxLoreTheme(dynamicColor = dynamicColor, content = content) else BoxLoreTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
    }
}
