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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
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
    val first = remember { FocusRequester() }
    Row(
        Modifier.fillMaxSize().padding(start = TvSpace.contentStart, end = TvSpace.safeSide, top = TvSpace.safeTop, bottom = TvSpace.safeTop),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.tv_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp))
            // Settings opens only while signed in: signing out leads to the sign-in screen.
            val accountValue = (signIn as? TvSignIn.SignedIn)?.account ?: stringResource(R.string.tv_signed_in)
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
                SettingsPage.Account -> AccountPane((signIn as? TvSignIn.SignedIn)?.account, account)
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

/** The account page: who the TV is signed in to, and Sign out, which leads back to the sign-in screen. */
@Composable
private fun AccountPane(name: String?, account: TvAccount) {
    Text(stringResource(R.string.tv_account), style = MaterialTheme.typography.headlineSmall)
    if (name != null) Text(stringResource(R.string.tv_signed_in_as, name), style = MaterialTheme.typography.titleMedium, color = TvColors.onSurface)
    Text(stringResource(R.string.tv_signed_in_body), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
    PillButton(stringResource(R.string.tv_sign_out), onClick = account::signOut, icon = Icons.AutoMirrored.Rounded.Logout)
}
