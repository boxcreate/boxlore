package cx.aswin.boxlore.wear.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.cloud.WatchAccountState
import cx.aswin.boxlore.wear.data.StoragePolicy
import cx.aswin.boxlore.wear.model.ShowsLayout
import cx.aswin.boxlore.wear.ui.WatchFormat

/** Settings' pages; the first screen lists them, each with what is chosen now. */
internal enum class SettingsPage { Account, ShowsLayout, StorageLimit }

/** The first Settings screen: one row per page, the account first, each saying what is set now. */
@Composable
internal fun SettingsScreen(
    account: WatchAccountState,
    showsLayout: ShowsLayout,
    storageLimitMb: Int,
    onOpen: (SettingsPage) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val summary = AccountCopy.summary(account)
    SettingsList(R.string.wear_settings) { spec ->
        item(key = SettingsPage.Account.name) {
            SettingsRow(
                icon = Icons.Rounded.AccountCircle,
                label = stringResource(R.string.wear_account),
                value = summary.name ?: stringResource(summary.label),
                // Signing in is the one setting that changes what the watch can do, so its row stands out until done.
                prominent = account !is WatchAccountState.SignedIn,
                spec = spec,
                onClick = { onOpen(SettingsPage.Account) },
            )
        }
        item(key = SettingsPage.ShowsLayout.name) {
            SettingsRow(
                icon = Icons.Rounded.GridView,
                label = stringResource(R.string.wear_shows_layout),
                value = stringResource(showsLayout.label()),
                spec = spec,
                onClick = { onOpen(SettingsPage.ShowsLayout) },
            )
        }
        item(key = SettingsPage.StorageLimit.name) {
            SettingsRow(
                icon = Icons.Rounded.SdStorage,
                label = stringResource(R.string.wear_storage_limit),
                value = WatchFormat.megabytes(StoragePolicy.limitBytes(storageLimitMb), locale),
                spec = spec,
                onClick = { onOpen(SettingsPage.StorageLimit) },
            )
        }
    }
}

/** The account page: Sign in with the phone, or who is signed in and Sign out. */
@Composable
internal fun AccountSettingsScreen(account: AccountSettings) {
    SettingsList(R.string.wear_account) { spec -> accountItems(account, spec) }
}

@Composable
internal fun ShowsLayoutSettingsScreen(showsLayout: ShowsLayout, onShowsLayout: (ShowsLayout) -> Unit) {
    SettingsList(R.string.wear_shows_layout) { spec ->
        ShowsLayout.entries.forEach { layout ->
            item(key = layout.name) {
                RadioButton(
                    selected = layout == showsLayout,
                    onSelect = { onShowsLayout(layout) },
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(layout.label())) },
                )
            }
        }
    }
}

@Composable
internal fun StorageLimitSettingsScreen(storageLimitMb: Int, onStorageLimitMb: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    SettingsList(R.string.wear_storage_limit) { spec ->
        StoragePolicy.LIMIT_CHOICES_MB.forEach { choice ->
            item(key = "limit-$choice") {
                RadioButton(
                    selected = choice == storageLimitMb,
                    onSelect = { onStorageLimitMb(choice) },
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(WatchFormat.megabytes(StoragePolicy.limitBytes(choice), locale)) },
                )
            }
        }
    }
}

private fun ShowsLayout.label(): Int =
    when (this) {
        ShowsLayout.Honeycomb -> R.string.wear_shows_layout_honeycomb
        ShowsLayout.List -> R.string.wear_shows_layout_list
    }

/** A Settings list under its [title]. */
@Composable
private fun SettingsList(title: Int, rows: TransformingLazyColumnScope.(TransformationSpec) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "title") {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(title))
                }
            }
            rows(spec)
        }
    }
}

/** One row of the first Settings screen: what the page is, and under it what is set there now. */
@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    spec: TransformationSpec,
    onClick: () -> Unit,
    prominent: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        colors = if (prominent) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
