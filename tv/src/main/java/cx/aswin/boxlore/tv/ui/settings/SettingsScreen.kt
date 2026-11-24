package cx.aswin.boxlore.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.BuildConfig
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.cloud.TvAccount
import cx.aswin.boxlore.tv.cloud.TvSignIn
import cx.aswin.boxlore.tv.data.TvPlayback
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace

private enum class SettingsPage { Account, Playback, About }

/** Settings: Account first, as on the watch; each row shows its value, and its page opens beside it. */
@Composable
fun SettingsScreen(account: TvAccount) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Account) }
    val signIn by account.signIn.collectAsState()
    // Leaving Settings stops waiting for a code; signing in again shows a fresh one.
    DisposableEffect(account) { onDispose { account.cancelSignIn() } }
    val first = remember { FocusRequester() }
    Row(
        Modifier.fillMaxSize().padding(start = TvSpace.contentStart, end = TvSpace.safeSide, top = TvSpace.safeTop, bottom = TvSpace.safeTop),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.tv_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp))
            val accountValue =
                when (val current = signIn) {
                    is TvSignIn.SignedIn -> current.account ?: stringResource(R.string.tv_signed_in)
                    else -> stringResource(R.string.tv_not_signed_in)
                }
            MenuRow(stringResource(R.string.tv_account), accountValue, page == SettingsPage.Account, Modifier.focusRequester(first)) { page = SettingsPage.Account }
            MenuRow(
                stringResource(R.string.tv_playback),
                stringResource(R.string.tv_playback_summary, TvPlayback.SEEK_BACK_SECONDS, TvPlayback.SEEK_FORWARD_SECONDS),
                page == SettingsPage.Playback,
            ) { page = SettingsPage.Playback }
            MenuRow(stringResource(R.string.tv_about), stringResource(R.string.tv_version, BuildConfig.VERSION_NAME), page == SettingsPage.About) { page = SettingsPage.About }
        }
        Column(
            Modifier.weight(1f).padding(top = 52.dp).clip(RoundedCornerShape(28.dp)).background(TvColors.surface).padding(horizontal = 32.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (page) {
                SettingsPage.Account -> AccountPane(signIn, account)
                SettingsPage.Playback -> {
                    Text(stringResource(R.string.tv_playback), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.tv_playback_summary, TvPlayback.SEEK_BACK_SECONDS, TvPlayback.SEEK_FORWARD_SECONDS),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvColors.onSurfaceVariant,
                    )
                }
                SettingsPage.About -> {
                    Text(stringResource(R.string.tv_about), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.tv_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun MenuRow(title: String, value: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        colors =
        ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvColors.primaryContainer else Color.Transparent,
            contentColor = if (selected) TvColors.onPrimaryContainer else TvColors.onSurface,
            focusedContainerColor = TvColors.primary,
            focusedContentColor = TvColors.onPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The account page: sign in with a code a phone approves, the code itself, or signed in with Sign out. */
@Composable
private fun AccountPane(signIn: TvSignIn, account: TvAccount) {
    // The button pressed gives way to the next step's; the remote moves to that one, never to nothing.
    val action = remember { FocusRequester() }
    var acted by remember { mutableStateOf(false) }
    val act = { run: () -> Unit ->
        acted = true
        run()
    }
    LaunchedEffect(signIn) { if (acted && signIn != TvSignIn.Starting) runCatching { action.requestFocus() } }
    Text(stringResource(R.string.tv_account), style = MaterialTheme.typography.headlineSmall)
    when (signIn) {
        is TvSignIn.SignedIn -> {
            val name = signIn.account
            if (name != null) Text(stringResource(R.string.tv_signed_in_as, name), style = MaterialTheme.typography.titleMedium, color = TvColors.onSurface)
            Text(stringResource(R.string.tv_signed_in_body), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
            PillButton(stringResource(R.string.tv_sign_out), onClick = { act(account::signOut) }, Modifier.focusRequester(action), icon = Icons.AutoMirrored.Rounded.Logout)
        }
        TvSignIn.Starting -> Text(stringResource(R.string.tv_getting_code), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
        is TvSignIn.ShowingCode -> {
            Text(stringResource(R.string.tv_sign_in_steps), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
            Text(
                SignInCode.grouped(signIn.code),
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                color = TvColors.onSurface,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(stringResource(R.string.tv_code_refreshes), style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant)
            PillButton(stringResource(R.string.tv_cancel), onClick = { act(account::cancelSignIn) }, Modifier.focusRequester(action))
        }
        TvSignIn.SignedOut, TvSignIn.NoConnection -> {
            Text(stringResource(R.string.tv_account_body), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
            if (signIn == TvSignIn.NoConnection) Text(stringResource(R.string.tv_sign_in_failed), style = MaterialTheme.typography.bodyMedium, color = TvColors.error)
            val label = if (signIn == TvSignIn.NoConnection) R.string.tv_try_again else R.string.tv_sign_in_with_phone
            PillButton(stringResource(label), onClick = { act(account::startSignIn) }, Modifier.focusRequester(action), icon = Icons.Rounded.PhoneAndroid)
            Text(stringResource(R.string.tv_without_account), style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant)
        }
    }
}

/** The code as the phone asks for it: two groups of four, easy to read across a room. */
internal object SignInCode {
    private const val GROUP = 4

    fun grouped(code: String): String = code.chunked(GROUP).joinToString("-")
}
