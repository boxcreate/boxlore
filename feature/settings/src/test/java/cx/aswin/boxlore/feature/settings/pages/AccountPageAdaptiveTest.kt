package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.auth.EmailLinkSignInStatus
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.feature.settings.AdaptiveSettingsLayout
import cx.aswin.boxlore.feature.settings.ProfileSettingsDestination
import cx.aswin.boxlore.feature.settings.SettingsScreenConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Signing in survives the window changing under it: the way to sign in the listener picked, what they
 * typed and which password mode they chose stay put while the Account page moves between the phone
 * layout, the tablet single pane (600dp wide and 480dp tall) and the tablet split (wide landscape), in
 * both directions and either side of each gate, and when the activity is recreated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountPageAdaptiveTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private var backs = 0

    @Test fun `the picked sign-in method and typed email survive phone, tablet and split windows both ways`() {
        render()
        compose.onNodeWithTag("account_method_link").performScrollTo().performClick()
        compose.onNodeWithTag("account_email_field").performTextReplacement("listener@example.test")
        // Phone → just under the tablet width → tablet single pane → split → short (height gate) → phone.
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(1280f, 480f),
            BoxloreWindowLayout(839f, 1180f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithTag("account_email_field").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("listener@example.test").assertIsDisplayed()
        }
        compose.onNodeWithTag("account_other_ways").performScrollTo().performClick()
        compose.onNodeWithTag("account_method_password").performScrollTo().assertIsDisplayed()
    }

    @Test fun `the password form and its mode survive moving into the split and back`() {
        render()
        compose.onNodeWithTag("account_method_password").performScrollTo().performClick()
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.onNodeWithTag("account_email_field").performTextReplacement("new@example.test")
        compose.runOnIdle { window.value = BoxloreWindowLayout(1280f, 800f) }
        compose.waitForIdle()
        compose.onNodeWithText("Confirm password").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("new@example.test").assertIsDisplayed()
        compose.runOnIdle { window.value = BoxloreWindowLayout(360f, 740f) }
        compose.waitForIdle()
        compose.onNodeWithText("Confirm password").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("account_not_now").assertDoesNotExist()
        assertEquals(0, backs)
    }

    @Test fun `the picked method and typed email survive the activity being recreated`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            EchoStillTheme(dynamicColor = false) { AccountSettingsPage(authRepository = null, onBack = {}, isOnboarding = true) }
        }
        compose.onNodeWithTag("account_method_link").performScrollTo().performClick()
        compose.onNodeWithTag("account_email_field").performTextReplacement("listener@example.test")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("account_email_field").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("listener@example.test").assertIsDisplayed()
    }

    @Test fun `the syncing page keeps its note on top and finishes the tiles while the window changes`() {
        val status = mutableStateOf<CloudSyncUiStatus>(CloudSyncUiStatus.Syncing)
        var continued = 0
        val user = BoxLoreUser(uid = "test", email = "listener@example.test", displayName = null, providerId = "google.com", isEmailVerified = true)
        compose.setContent {
            EchoStillTheme(dynamicColor = false) {
                val layout = window.value
                CompositionLocalProvider(LocalBoxloreWindowLayout provides layout) {
                    Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                        AccountPageScaffold({}, isOnboarding = true, scroll = rememberScrollState(), bottomBar = { AccountContinueButton { continued++ } }) {
                            AccountSignedInOnboarding(user, status.value, onSyncNow = {})
                        }
                    }
                }
            }
        }
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(1280f, 480f),
            BoxloreWindowLayout(839f, 1180f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            // On top and on screen without scrolling, above the tiles, with Continue reachable.
            val hint = compose.onNodeWithTag("account_continue_hint").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("hint below the tiles at $next", hint.bottom <= compose.onNodeWithTag("account_sync_tiles").getUnclippedBoundsInRoot().top)
            assertEquals(3, compose.onAllNodesWithText("Syncing").fetchSemanticsNodes().size)
            compose.onNodeWithTag("account_continue").assertIsDisplayed()
        }
        compose.runOnIdle { window.value = BoxloreWindowLayout(1280f, 800f) }
        compose.runOnIdle { status.value = CloudSyncUiStatus.Success(System.currentTimeMillis()) }
        compose.runOnIdle { window.value = BoxloreWindowLayout(360f, 740f) }
        compose.waitForIdle()
        assertEquals(3, compose.onAllNodesWithText("Done").fetchSemanticsNodes().size)
        compose.onNodeWithTag("account_continue_hint").assertDoesNotExist()
        compose.onNodeWithTag("account_continue").performClick()
        assertEquals(1, continued)
    }

    @Test fun `signing in from a link, then its failure, hold through every window both ways`() {
        val status = mutableStateOf<EmailLinkSignInStatus>(EmailLinkSignInStatus.SigningIn)
        var retries = 0
        renderWithLink(status, onRetry = { retries++ })
        val windows = listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(1280f, 480f),
            BoxloreWindowLayout(839f, 1180f),
            BoxloreWindowLayout(360f, 740f),
        )
        windows.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("Signing you in").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("account_signing_in").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("account_method_link").assertDoesNotExist()
        }

        compose.runOnIdle { status.value = EmailLinkSignInStatus.Failed(expired = true, email = "listener@example.test") }
        windows.reversed().forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("This link has expired").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("account_link_send_new").performScrollTo().assertIsDisplayed()
        }

        compose.runOnIdle { status.value = EmailLinkSignInStatus.Failed(expired = false, email = "listener@example.test") }
        compose.onNodeWithText("Couldn’t sign you in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("account_link_try_again").performScrollTo().performClick()
        assertEquals(1, retries)

        compose.onNodeWithTag("account_link_other_ways").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(EmailLinkSignInStatus.Idle, status.value)
        compose.onNodeWithTag("account_method_link").performScrollTo().assertIsDisplayed()
        assertEquals(0, backs)
    }

    @Test fun `a failed link stays on the page when the activity is recreated`() {
        val status = mutableStateOf<EmailLinkSignInStatus>(EmailLinkSignInStatus.Failed(expired = true, email = "listener@example.test"))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            EchoStillTheme(dynamicColor = false) {
                AccountSettingsPage(authRepository = null, onBack = {}, emailLink = AccountEmailLinkUi(status = status.value))
            }
        }
        compose.onNodeWithTag("account_link_send_new").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("This link has expired").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("account_link_send_new").performScrollTo().assertIsDisplayed()
    }

    @Test fun `onboarding hides Not now while a link signs in`() {
        val status = mutableStateOf<EmailLinkSignInStatus>(EmailLinkSignInStatus.SigningIn)
        compose.setContent {
            EchoStillTheme(dynamicColor = false) {
                AccountSettingsPage(authRepository = null, onBack = {}, isOnboarding = true, emailLink = AccountEmailLinkUi(status = status.value))
            }
        }
        compose.onNodeWithText("Signing you in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("account_not_now").assertDoesNotExist()
        compose.runOnIdle { status.value = EmailLinkSignInStatus.Failed(expired = false, email = "listener@example.test") }
        compose.onNodeWithTag("account_not_now").assertExists()
    }

    private fun renderWithLink(status: androidx.compose.runtime.MutableState<EmailLinkSignInStatus>, onRetry: () -> Unit) {
        compose.setContent {
            EchoStillTheme(dynamicColor = false) {
                val layout = window.value
                CompositionLocalProvider(LocalBoxloreWindowLayout provides layout) {
                    Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                        AdaptiveSettingsLayout(ProfileSettingsDestination.Account, SettingsScreenConfig(onBack = { backs++ }, onResetAnalytics = {}), {}) {
                            AccountSettingsPage(
                                authRepository = null,
                                onBack = { backs++ },
                                emailLink = AccountEmailLinkUi(
                                    status = status.value,
                                    onRetry = onRetry,
                                    onDismiss = { status.value = EmailLinkSignInStatus.Idle },
                                ),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun render() {
        compose.setContent {
            EchoStillTheme(dynamicColor = false) {
                val layout = window.value
                CompositionLocalProvider(LocalBoxloreWindowLayout provides layout) {
                    Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                        AdaptiveSettingsLayout(ProfileSettingsDestination.Account, SettingsScreenConfig(onBack = { backs++ }, onResetAnalytics = {}), {}) {
                            AccountSettingsPage(authRepository = null, onBack = { backs++ })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}

/** The app theme with Echo held on its resting pose, so the screen can go idle under test. */
@androidx.compose.runtime.Composable
private fun EchoStillTheme(darkTheme: Boolean? = null, dynamicColor: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion provides false) {
        if (darkTheme == null) BoxLoreTheme(dynamicColor = dynamicColor, content = content) else BoxLoreTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
    }
}
