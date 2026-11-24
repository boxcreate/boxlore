package cx.aswin.boxlore.feature.settings.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogConfirmButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogDismissButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIcon
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIconTone
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow

private val SmartMaxEpisodes = listOf(5, 10, 15, 25)
private val SmartStorageBudgets = listOf(250L, 500L, 1000L, 2000L, 5000L, 0L)

/** Test tag on the question asked before Smart Downloads turns off. */
internal const val SMART_DOWNLOADS_OFF_DIALOG_TAG = "smart_downloads_off_dialog"

@Composable
fun SmartDownloadsSettingsScreen(
    userPrefs: UserPreferencesRepository,
    onBack: () -> Unit,
) {
    TrackScreenView("smart_downloads_settings")
    val context = LocalContext.current
    val scheduler = remember(context) { WorkManagerSmartDownloadScheduler(context.applicationContext) }
    SmartDownloadsSettingsContent(userPrefs, scheduler, onBack)
}

/** The page with its [scheduler] and [writes] passed in, so tests can watch what each control starts. */
@Composable
internal fun SmartDownloadsSettingsContent(
    userPrefs: UserPreferencesRepository,
    scheduler: SmartDownloadScheduler,
    onBack: () -> Unit,
    writes: SmartDownloadsPrefs = remember(userPrefs) { userPrefs.smartDownloadsPrefs() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isEnabled by userPrefs.smartDownloadsEnabledStream.collectAsStateWithLifecycle(initialValue = false)
    val maxEpisodes by userPrefs.smartDownloadsMaxEpisodesStream.collectAsStateWithLifecycle(initialValue = 10)
    val storageBudget by userPrefs.smartDownloadsStorageBudgetStream.collectAsStateWithLifecycle(initialValue = 1000L)
    val wifiOnly by userPrefs.smartDownloadsWifiOnlyStream.collectAsStateWithLifecycle(initialValue = true)
    val chargingOnly by userPrefs.smartDownloadsChargingOnlyStream.collectAsStateWithLifecycle(initialValue = false)
    val cleanupRule by userPrefs.smartDownloadsCleanupRuleStream.collectAsStateWithLifecycle(initialValue = "after_24h")
    var askBeforeOff by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val currentState by rememberUpdatedState(SmartDownloadsState(isEnabled, wifiOnly, chargingOnly, maxEpisodes, storageBudget, cleanupRule))
    val actions = remember(scope, writes, scheduler) {
        SmartDownloadsActions(scope, writes, scheduler, { currentState }, onConfirmOffChange = { askBeforeOff = it })
    }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    SettingsScaffold(
        title = stringResource(R.string.settings_smart_downloads_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showHelp = true }) {
                Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.settings_smart_downloads_about))
            }
        },
    ) {
        SmartDownloadsSummary(isEnabled)
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_smart_downloads_enable),
                supportingText = stringResource(if (isEnabled) R.string.settings_smart_downloads_active else R.string.settings_smart_downloads_inactive),
                icon = Icons.Rounded.DownloadForOffline,
                checked = isEnabled,
                onCheckedChange = actions::setEnabled,
            )
        }
        if (isEnabled) {
            SmartDownloadsLimits(maxEpisodes, storageBudget, actions)
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_download_wifi_only),
                    supportingText = stringResource(if (wifiOnly) R.string.settings_smart_downloads_enabled else R.string.settings_smart_downloads_mobile_data),
                    icon = Icons.Rounded.Wifi,
                    checked = wifiOnly,
                    onCheckedChange = actions::setWifiOnly,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_smart_downloads_charging_only),
                    supportingText = stringResource(if (chargingOnly) R.string.settings_smart_downloads_enabled else R.string.settings_smart_downloads_on_battery),
                    icon = Icons.Rounded.BatteryChargingFull,
                    checked = chargingOnly,
                    onCheckedChange = actions::setChargingOnly,
                )
            }
            SettingsGroup(title = stringResource(R.string.settings_smart_downloads_auto_delete)) {
                SettingsContent {
                    ConnectedOptionSelector(
                        options = listOf(
                            "immediately" to stringResource(R.string.settings_smart_downloads_delete_immediately),
                            "after_24h" to stringResource(R.string.settings_smart_downloads_delete_24h),
                            "after_7d" to stringResource(R.string.settings_smart_downloads_delete_7d),
                            "never" to stringResource(R.string.settings_smart_downloads_delete_never),
                        ),
                        selected = cleanupRule,
                        onSelect = actions::setCleanupRule,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
            SmartDownloadsBackgroundGroup(isIgnoringBatteryOptimizations) { openBatterySettings(context) }
        }
    }

    if (askBeforeOff) SmartDownloadsOffDialog(onConfirm = actions::confirmDisable, onDismiss = actions::dismissDisable)
    if (showHelp) SmartDownloadsHelp(onDismiss = { showHelp = false })

    // Refresh the battery optimization state when boxlore comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun SmartDownloadsSummary(enabled: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(if (enabled) R.string.settings_smart_downloads_active else R.string.settings_smart_downloads_inactive),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                )
                Text(stringResource(R.string.settings_smart_downloads_description), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** How many episodes and how much room Smart Downloads may use, with a warning when the two disagree. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartDownloadsLimits(maxEpisodes: Int, storageBudget: Long, actions: SmartDownloadsActions) {
    SettingsGroup {
        SettingsContent {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_smart_downloads_max_episodes), style = MaterialTheme.typography.labelLarge)
                ConnectedOptionSelector(
                    options = SmartMaxEpisodes.map { it to it.toString() },
                    selected = maxEpisodes,
                    onSelect = actions::setMaxEpisodes,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
        SettingsDivider()
        SettingsContent {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_smart_downloads_storage_limit), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SmartStorageBudgets.forEach { megabytes ->
                        val selected = storageBudget == megabytes
                        FilterChip(
                            selected = selected,
                            onClick = { actions.setStorageBudget(megabytes) },
                            label = { Text(if (megabytes == 0L) stringResource(R.string.settings_smart_downloads_unlimited) else smartDownloadsBudgetLabel(megabytes)) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).semantics { role = Role.RadioButton },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                val conflict = storageBudget > 0L && maxEpisodes * 50L > storageBudget
                if (conflict) SmartDownloadsConflict(maxEpisodes, storageBudget)
            }
        }
    }
}

@Composable
private fun SmartDownloadsConflict(maxEpisodes: Int, storageBudget: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = stringResource(R.string.settings_smart_downloads_conflict_description),
                modifier = Modifier.size(20.dp),
            )
            Text(
                pluralStringResource(R.plurals.settings_smart_downloads_conflict, maxEpisodes, smartDownloadsBudgetLabel(storageBudget), maxEpisodes),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SmartDownloadsBackgroundGroup(allowed: Boolean, onAllow: () -> Unit) {
    SettingsGroup(title = stringResource(R.string.settings_smart_downloads_background)) {
        SettingsContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (allowed) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryAlert,
                        contentDescription = null,
                        tint = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(if (allowed) R.string.settings_smart_downloads_background_allowed else R.string.settings_smart_downloads_background_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (allowed) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            stringResource(R.string.settings_smart_downloads_allowed),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = GoogleSansWeight.bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.settings_smart_downloads_allow), style = MaterialTheme.typography.labelLarge, fontWeight = GoogleSansWeight.bold)
                    }
                }
            }
        }
    }
}

/** Asked before turning off, because turning off also removes the episodes Smart Downloads saved. */
@Composable
private fun SmartDownloadsOffDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TrackScreenView("smart_downloads_off_confirmation")
    BoxloreAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SMART_DOWNLOADS_OFF_DIALOG_TAG),
        icon = { BoxloreDialogIcon(Icons.Rounded.DownloadForOffline, tone = BoxloreDialogIconTone.Destructive) },
        title = { Text(stringResource(R.string.settings_smart_downloads_off_title)) },
        text = {
            Text(
                stringResource(R.string.settings_smart_downloads_off_body),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { BoxloreDialogConfirmButton(stringResource(R.string.settings_smart_downloads_off_confirm), onConfirm, destructive = true) },
        dismissButton = { BoxloreDialogDismissButton(stringResource(R.string.settings_cancel), onDismiss) },
    )
}

@Composable
private fun SmartDownloadsHelp(onDismiss: () -> Unit) {
    BoxloreAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_smart_downloads_about)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.settings_smart_downloads_description), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.settings_smart_downloads_background_blocked), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_got_it)) } },
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

@Suppress("TooGenericExceptionCaught") // Some phones refuse this screen in their own ways; the page stays put.
private fun openBatterySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") })
    } catch (e: Exception) {
        Log.e("SmartDownloadsSettings", "Failed to launch battery settings", e)
    }
}

/** A storage budget in megabytes as "250 MB" or "1.0 GB", with the number written for the app's language. */
@Composable
private fun smartDownloadsBudgetLabel(megabytes: Long): String {
    val locale = currentLocale()
    return if (megabytes >= 1000L) {
        stringResource(R.string.settings_smart_downloads_size_gb, String.format(locale, "%.1f", megabytes / 1000.0))
    } else {
        stringResource(R.string.settings_smart_downloads_size_mb, LocaleFormats.number(megabytes, locale))
    }
}
