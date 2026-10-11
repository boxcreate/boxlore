package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.feature.settings.R

/** Keep both authentication choices readable at narrow widths and large text scales. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountAuthModeSelector(selected: AuthMode, onSelect: (AuthMode) -> Unit) {
    val options = listOf(AuthMode.SIGN_IN to R.string.account_sign_in, AuthMode.SIGN_UP to R.string.account_create)
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, (mode, label) ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { checked -> if (checked) onSelect(mode) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes(pressedShape = RoundedCornerShape(12.dp))
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes(pressedShape = RoundedCornerShape(12.dp))
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(stringResource(label), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountPageScaffold(onBack: () -> Unit, isOnboarding: Boolean, scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag(if (isOnboarding) "account_onboarding_page" else "account_settings_page"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isOnboarding) R.string.account_setup_title else R.string.account_page_title), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.account_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).navigationBarsPadding()
                    .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = if (isOnboarding) 24.dp else 104.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun AccountSignedOutIntro(isOnboarding: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoAvatar(false, Modifier.size(if (isOnboarding) 128.dp else 112.dp, if (isOnboarding) 139.dp else 121.dp))
        Text(stringResource(R.string.account_connect_title), style = MaterialTheme.typography.headlineMedium, fontWeight = GoogleSansWeight.semiBold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.account_connect_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (isOnboarding) Text(stringResource(R.string.account_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun AccountSignedInContent(user: BoxLoreUser, syncStatus: CloudSyncUiStatus, actions: SignedInActions, isOnboarding: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (isOnboarding) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EchoAvatar(syncStatus is CloudSyncUiStatus.Syncing, Modifier.size(128.dp, 139.dp))
                Text(stringResource(R.string.account_connected_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(stringResource(R.string.account_connected_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                AccountIdentity(user, centered = true)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EchoAvatar(syncStatus is CloudSyncUiStatus.Syncing, Modifier.size(96.dp, 104.dp))
                Box(Modifier.weight(1f)) { AccountIdentity(user, centered = false) }
            }
        }
        CloudSyncInfoGroup(syncStatus, actions.onSyncNow)
        if (isOnboarding) {
            Button(actions.onContinueToHome, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("account_continue"), shape = CircleShape) {
                Text(stringResource(R.string.account_continue), Modifier.weight(1f), textAlign = TextAlign.Center)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(22.dp))
            }
        } else {
            AccountManagementGroup(user, actions.onResetPassword, actions.onSignOut, actions.onDeleteAccountClick, actions.onOpenAccountDeletionInfo)
        }
    }
}

@Composable
private fun AccountIdentity(user: BoxLoreUser, centered: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(user.displayName?.takeIf { it.isNotBlank() } ?: user.email ?: stringResource(R.string.account_listener), style = MaterialTheme.typography.titleLarge, fontWeight = GoogleSansWeight.semiBold, textAlign = if (centered) TextAlign.Center else TextAlign.Start, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!user.displayName.isNullOrBlank() && !user.email.isNullOrBlank()) {
            Text(user.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.CloudDone, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(resolveProviderLabel(user.providerId), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
