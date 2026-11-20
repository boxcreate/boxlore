package cx.aswin.boxlore.feature.settings.pages

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.prefs.AutoBackupFrequency
import cx.aswin.boxlore.core.prefs.AutoBackupProblem
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import cx.aswin.boxlore.core.prefs.AutoBackupStore
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** What Cloud Sync & Backups shows for the automatic backup: the stored settings and whether a backup is running. */
data class AutoBackupUiState(
    val settings: AutoBackupSettings = AutoBackupSettings(),
    val isBackingUp: Boolean = false,
)

/**
 * The automatic backup as the app provides it. The app owns the folder grant and the schedule, so
 * [onFolderPicked] receives the picked document tree and [onTurnOff] releases it.
 */
data class AutoBackupControl(
    val state: Flow<AutoBackupUiState>,
    val onFolderPicked: (Uri) -> Unit,
    val onTurnOff: () -> Unit,
    val onSetFrequency: (AutoBackupFrequency) -> Unit,
    val onSetKeepCount: (Int) -> Unit,
    val onBackUpNow: () -> Unit,
)

/** The page's automatic backup callbacks, with the folder picker already attached. */
data class AutoBackupActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onChooseFolder: () -> Unit,
    val onSetFrequency: (AutoBackupFrequency) -> Unit,
    val onSetKeepCount: (Int) -> Unit,
    val onBackUpNow: () -> Unit,
)

/** State and actions for the "Automatic backup" group on [SyncAndBackupsPage]. */
data class AutoBackupPage(
    val state: AutoBackupUiState,
    val actions: AutoBackupActions,
)

/**
 * Registers the folder picker where it is called and collects [control]'s state. [SettingsScreen][cx.aswin.boxlore.feature.settings.SettingsScreen]
 * calls it above the adaptive layout, so an open picker still delivers its folder after a resize, a
 * rotation or the split view appearing. Null until the stored settings load, or without [control].
 */
@Composable
internal fun rememberAutoBackupPage(control: AutoBackupControl?): AutoBackupPage? {
    val noState = remember { kotlinx.coroutines.flow.flowOf<AutoBackupUiState?>(null) }
    val state by (control?.state ?: noState).collectAsStateWithLifecycle(initialValue = null)
    val currentControl by rememberUpdatedState(control)
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) currentControl?.onFolderPicked?.invoke(uri)
        }
    val context = LocalContext.current
    val loaded = state ?: return null
    val active = control ?: return null
    val openPicker = { initial: Uri? ->
        launchFolderPicker(picker, initial) {
            Toast.makeText(context, context.getString(R.string.settings_auto_backup_no_picker), Toast.LENGTH_LONG).show()
        }
    }
    val chooseFolder = {
        AnalyticsHelper.trackSettingsInteraction("auto_backup_folder")
        openPicker(loaded.settings.folderUri?.let(Uri::parse))
    }
    return AutoBackupPage(
        state = loaded,
        actions = AutoBackupActions(
            onSetEnabled = { on ->
                AnalyticsHelper.trackSettingsInteraction("auto_backup_toggle", if (on) "on" else "off")
                when {
                    !on -> active.onTurnOff()
                    // Turning it on starts with the folder; it switches on once one is picked.
                    loaded.settings.folderUri == null -> openPicker(null)
                }
            },
            onChooseFolder = chooseFolder,
            onSetFrequency = { frequency ->
                if (frequency != loaded.settings.frequency) {
                    AnalyticsHelper.trackSettingsInteraction("auto_backup_frequency", frequency.storedValue)
                    active.onSetFrequency(frequency)
                }
            },
            onSetKeepCount = { count ->
                if (count != loaded.settings.keepCount) {
                    AnalyticsHelper.trackSettingsInteraction("auto_backup_keep", count.toString())
                    active.onSetKeepCount(count)
                }
            },
            onBackUpNow = {
                if (!loaded.isBackingUp) {
                    AnalyticsHelper.trackSettingsInteraction("auto_backup_now")
                    active.onBackUpNow()
                }
            },
        ),
    )
}

/**
 * Opens the system folder picker. Some stripped-down Android builds have none, so [onUnavailable] runs
 * instead of the tap crashing the app.
 */
internal fun launchFolderPicker(picker: ActivityResultLauncher<Uri?>, initial: Uri?, onUnavailable: () -> Unit) {
    try {
        picker.launch(initial)
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
    }
}

internal object AutoBackupTestTags {
    const val STATUS = "auto_backup_status"
    const val FIX = "auto_backup_fix"
}

/** Says what the folder needs, or null when nothing is wrong. */
@StringRes
internal fun autoBackupProblemText(settings: AutoBackupSettings): Int? = when (settings.lastError) {
    null -> null
    AutoBackupProblem.PERMISSION_LOST -> R.string.settings_auto_backup_problem_permission
    AutoBackupProblem.FOLDER_MISSING -> R.string.settings_auto_backup_problem_missing
    else -> R.string.settings_auto_backup_problem_other
}

/** The fix shown beside [autoBackupProblemText]. */
@StringRes
internal fun autoBackupFixLabel(settings: AutoBackupSettings): Int? = when {
    settings.lastError == null -> null
    AutoBackupProblem.needsNewFolder(settings.lastError) -> R.string.settings_auto_backup_fix_folder
    else -> R.string.settings_auto_backup_fix_retry
}

/** The line under Back up now. */
internal fun autoBackupStatusText(
    state: AutoBackupUiState,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): UiText {
    val last = state.settings.lastSuccessAt
    return when {
        state.isBackingUp -> UiText.res(R.string.settings_auto_backup_status_running)
        last == null -> UiText.res(R.string.settings_auto_backup_status_none)
        else -> UiText.res(
            R.string.settings_auto_backup_status_last,
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zone)
                .format(Instant.ofEpochMilli(last)),
        )
    }
}

@StringRes
internal fun autoBackupFrequencyLabel(frequency: AutoBackupFrequency): Int = when (frequency) {
    AutoBackupFrequency.DAILY -> R.string.settings_auto_backup_daily
    AutoBackupFrequency.WEEKLY -> R.string.settings_auto_backup_weekly
}

@Composable
internal fun AutoBackupGroup(page: AutoBackupPage) {
    val settings = page.state.settings
    val actions = page.actions
    SettingsGroup(
        title = stringResource(R.string.settings_auto_backup_title),
        footer = if (settings.enabled) stringResource(R.string.settings_auto_backup_footer) else null,
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_auto_backup_toggle),
            supportingText = stringResource(R.string.settings_auto_backup_toggle_summary),
            icon = Icons.Rounded.Backup,
            checked = settings.enabled,
            onCheckedChange = actions.onSetEnabled,
        )
        AnimatedVisibility(
            visible = settings.enabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                SettingsDivider()
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_auto_backup_folder),
                    supportingText = settings.folderName ?: stringResource(
                        if (settings.folderUri == null) R.string.settings_auto_backup_folder_choose else R.string.settings_auto_backup_folder_chosen,
                    ),
                    icon = Icons.Rounded.Folder,
                    onClick = actions.onChooseFolder,
                )
                AutoBackupProblemRow(settings, actions)
                SettingsDivider()
                AutoBackupChoices(
                    title = stringResource(R.string.settings_auto_backup_how_often),
                    options = AutoBackupFrequency.entries.map { it to stringResource(autoBackupFrequencyLabel(it)) },
                    selected = settings.frequency,
                    onSelect = actions.onSetFrequency,
                )
                AutoBackupChoices(
                    title = stringResource(R.string.settings_auto_backup_keep),
                    options = AutoBackupStore.KEEP_CHOICES.map { it to LocaleFormats.number(it.toLong(), currentLocale()) },
                    selected = settings.keepCount,
                    onSelect = actions.onSetKeepCount,
                )
                SettingsDivider()
                SettingsActionRow(
                    title = stringResource(R.string.settings_auto_backup_now),
                    supportingText = autoBackupStatusText(page.state, locale = currentLocale()).asString(),
                    icon = Icons.Rounded.Backup,
                    onClick = actions.onBackUpNow,
                    modifier = Modifier.testTag(AutoBackupTestTags.STATUS),
                )
            }
        }
    }
}

@Composable
private fun AutoBackupProblemRow(settings: AutoBackupSettings, actions: AutoBackupActions) {
    val problem = autoBackupProblemText(settings)?.let { stringResource(it) } ?: return
    val fixLabel = autoBackupFixLabel(settings)?.let { stringResource(it) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = problem,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (fixLabel != null) {
            TextButton(
                onClick = if (AutoBackupProblem.needsNewFolder(settings.lastError)) actions.onChooseFolder else actions.onBackUpNow,
                modifier = Modifier.testTag(AutoBackupTestTags.FIX),
            ) { Text(fixLabel) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> AutoBackupChoices(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = GoogleSansWeight.medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
