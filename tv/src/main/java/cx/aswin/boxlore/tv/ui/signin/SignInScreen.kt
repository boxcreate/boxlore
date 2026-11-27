package cx.aswin.boxlore.tv.ui.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.cloud.TvAccount
import cx.aswin.boxlore.tv.cloud.TvSignIn
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace

/** What the code panel on the sign-in screen holds. */
internal enum class CodePanel { Loading, Code, RanOut, NoConnection }

internal object SignInRules {
    /** The panel for [signIn]; null once signed in, when the app itself takes the screen. */
    fun panel(signIn: TvSignIn): CodePanel? = when (signIn) {
        is TvSignIn.SignedIn -> null
        is TvSignIn.ShowingCode -> CodePanel.Code
        TvSignIn.CodeRanOut -> CodePanel.RanOut
        TvSignIn.NoConnection -> CodePanel.NoConnection
        TvSignIn.SignedOut, TvSignIn.Starting -> CodePanel.Loading
    }

    /** The code as the phone asks for it: two groups of four, easy to read across a room. */
    fun grouped(code: String): String = code.chunked(GROUP).joinToString("-")

    /** Read out one character at a time, so a screen reader doesn't try to say it as a word. */
    fun spoken(code: String): String = code.toList().joinToString(" ")

    private const val GROUP = 4
}

/**
 * The TV's first screen until it is signed in, and where signing out leads: how to approve the
 * TV on a phone, and the code to enter there. A code is asked for only while this screen is in
 * front (and polled only while it shows one); after a few run out unused, it waits for Show a new code.
 */
@Composable
fun SignInScreen(signIn: TvSignIn, account: TvAccount) {
    LifecycleStartEffect(account) {
        account.startSignIn()
        onStopOrDispose { account.cancelSignIn() }
    }
    Row(
        Modifier.fillMaxSize().padding(horizontal = TvSpace.safeSide + 24.dp, vertical = TvSpace.safeTop + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Steps(Modifier.weight(1f))
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(TvColors.surface).padding(horizontal = 36.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Panel(signIn, account)
        }
    }
}

@Composable
private fun Steps(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Image(painterResource(R.drawable.ic_boxlore_mark), contentDescription = null, modifier = Modifier.size(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tv_sign_in_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.tv_account_body), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Step(1, stringResource(R.string.tv_sign_in_step_open))
            Step(2, stringResource(R.string.tv_sign_in_step_choose))
            Step(3, stringResource(R.string.tv_sign_in_step_enter))
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(TvColors.primaryContainer), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = TvColors.onPrimaryContainer)
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = TvColors.onSurface)
    }
}

@Composable
private fun Panel(signIn: TvSignIn, account: TvAccount) {
    val action = remember { FocusRequester() }
    val panel = SignInRules.panel(signIn) ?: return
    // A panel with a button puts the remote on it; the code itself has nothing to press.
    LaunchedEffect(panel) { if (panel == CodePanel.RanOut || panel == CodePanel.NoConnection) runCatching { action.requestFocus() } }
    when (panel) {
        CodePanel.Loading -> Text(stringResource(R.string.tv_getting_code), style = MaterialTheme.typography.titleMedium, color = TvColors.onSurfaceVariant)
        CodePanel.Code -> {
            val code = (signIn as TvSignIn.ShowingCode).code
            Text(stringResource(R.string.tv_your_code), style = MaterialTheme.typography.titleSmall, color = TvColors.onSurfaceVariant)
            Text(
                SignInRules.grouped(code),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                color = TvColors.onSurface,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = SignInRules.spoken(code) },
            )
            Text(stringResource(R.string.tv_code_refreshes), style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        CodePanel.RanOut -> {
            Text(stringResource(R.string.tv_code_ran_out), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.tv_code_ran_out_body), style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant, textAlign = TextAlign.Center)
            PillButton(stringResource(R.string.tv_new_code), onClick = account::startSignIn, Modifier.focusRequester(action), icon = Icons.Rounded.Refresh)
        }
        CodePanel.NoConnection -> {
            Text(stringResource(R.string.tv_sign_in_failed), style = MaterialTheme.typography.titleMedium, color = TvColors.error, textAlign = TextAlign.Center)
            PillButton(stringResource(R.string.tv_try_again), onClick = account::startSignIn, Modifier.focusRequester(action), icon = Icons.Rounded.Refresh)
        }
    }
}
