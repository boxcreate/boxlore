package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.auth.EmailLinkSignInStatus
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Send a new link moves on to Check your email only after a link actually goes out from this screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AccountLinkFailedContentTest {
    @get:Rule val compose = createComposeRule()

    private class SendingAuthRepository : AuthRepository {
        val sentTo = mutableListOf<String>()
        override val currentUser: StateFlow<BoxLoreUser?> = MutableStateFlow(null)
        override val currentUserId: String? = null
        override suspend fun sendMagicLink(email: String): Result<Unit> {
            sentTo += email
            return Result.success(Unit)
        }
        override suspend fun signInWithGoogle(idToken: String) = unsupported<BoxLoreUser>()
        override suspend fun signInWithEmailPassword(email: String, password: String) = unsupported<BoxLoreUser>()
        override suspend fun signUpWithEmailPassword(email: String, password: String) = unsupported<BoxLoreUser>()
        override suspend fun signInWithEmailLink(email: String, emailLink: String) = unsupported<BoxLoreUser>()
        override fun isSignInWithEmailLink(link: String): Boolean = false
        override suspend fun sendPasswordReset(email: String) = unsupported<Unit>()
        override suspend fun sendEmailVerification() = unsupported<Unit>()
        override suspend fun reloadUser() = unsupported<BoxLoreUser?>()
        override fun signOut() = Unit
        override suspend fun deleteAccount() = unsupported<Unit>()
        override suspend fun getIdToken(forceRefresh: Boolean): String? = null

        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }

    @Test fun `an earlier link-sent flag doesn't skip the failure, and a new link moves on once`() {
        val repository = SendingAuthRepository()
        var dismissed = 0
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalEchoMotion provides false) {
                BoxLoreTheme(dynamicColor = false) {
                    val state = rememberAccountAuthState(authRepository = repository)
                    // A link was sent earlier in this session, before the opened one failed.
                    remember { state.magicLinkSent = true }
                    Column {
                        AccountLinkFailedContent(
                            failure = EmailLinkSignInStatus.Failed(expired = true, email = "listener@example.test"),
                            state = state,
                            onRetry = {},
                            onDismiss = { dismissed++ },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(0, dismissed)
        compose.onNodeWithText("This link has expired").assertExists()

        compose.onNodeWithTag("account_link_send_new").performClick()
        compose.waitForIdle()

        assertEquals(listOf("listener@example.test"), repository.sentTo)
        assertEquals(1, dismissed)
    }
}
