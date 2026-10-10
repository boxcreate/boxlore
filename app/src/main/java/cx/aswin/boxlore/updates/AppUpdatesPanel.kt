package cx.aswin.boxlore.updates

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.R
import cx.aswin.boxlore.ui.NativeDialogSystemBars

@Composable
fun AppUpdatesPanel(updates: AppUpdates, onInstall: () -> Unit, onPlayUpdate: () -> Unit) {
    val visible by updates.visible.collectAsStateWithLifecycle()
    if (!visible) return
    val check by updates.checker.state.collectAsStateWithLifecycle()
    val install by updates.install.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = updates::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        NativeDialogSystemBars(MaterialTheme.colorScheme.surface)
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = updates::dismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.updates_close))
                    }
                    Text(stringResource(R.string.updates_title), style = MaterialTheme.typography.titleLarge)
                }
                if (check.status == UpdateCheckStatus.CHECKING) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).then(if (check.offer == null) Modifier.navigationBarsPadding() else Modifier).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = if (check.offer == null) Arrangement.Center else Arrangement.spacedBy(20.dp),
                ) {
                    if (cx.aswin.boxlore.BuildConfig.BOXLORE_ISOLATED_TESTS) Text(stringResource(R.string.updates_test_build), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val offer = check.offer
                    if (offer != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(if (updates.usesPlay) R.string.updates_play_available else R.string.updates_available), style = MaterialTheme.typography.headlineLarge)
                            if (!updates.usesPlay) Text(stringResource(R.string.updates_version, offer.versionName), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        offer.manifest?.let { ReleaseNotes(it) }
                    } else {
                        UpdateStatusContent(check.status, onCheck = { updates.open(checkNow = true) })
                        UpcomingChanges(check.upcoming)
                    }
                    CachedUpdateCheckFailure(check, updates)
                }
                if (check.offer != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            UpdateInstallContent(updates, install, onInstall, onPlayUpdate, check.status != UpdateCheckStatus.CHECKING)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CachedUpdateCheckFailure(check: UpdateCheckState, updates: AppUpdates) {
    if (check.offer == null || check.status != UpdateCheckStatus.FAILED) return
    Text(stringResource(R.string.updates_check_failed), color = MaterialTheme.colorScheme.error)
    TextButton(onClick = { updates.open(checkNow = true) }) { Text(stringResource(R.string.updates_check)) }
}

@Composable
private fun UpcomingChanges(notes: String) {
    if (notes.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.updates_upcoming), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.updates_unreleased), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            if (expanded) Column(Modifier.padding(16.dp)) { ReleaseNotesMarkdown(notes) }
        }
    }
}

@Composable
private fun UpdateStatusContent(status: UpdateCheckStatus, onCheck: () -> Unit) {
    val checking = status == UpdateCheckStatus.CHECKING || status == UpdateCheckStatus.IDLE
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(painterResource(R.drawable.ic_boxlore_wordmark_mic), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(32.dp).aspectRatio(6213f / 820f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(checkStatusText(status)),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                stringResource(R.string.updates_version, cx.aswin.boxlore.BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onCheck, enabled = !checking, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(if (checking) R.string.updates_checking else R.string.updates_check))
            }
        }
    }
}

private fun checkStatusText(status: UpdateCheckStatus): Int = when (status) {
    UpdateCheckStatus.CURRENT -> R.string.updates_current
    UpdateCheckStatus.FAILED -> R.string.updates_check_failed
    UpdateCheckStatus.INCOMPATIBLE -> R.string.updates_incompatible
    else -> R.string.updates_checking
}

@Composable
private fun ReleaseNotes(manifest: UpdateManifest) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.updates_notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(manifest.notesUrl))) }) {
                Icon(Icons.Rounded.OpenInNew, stringResource(R.string.updates_read_notes), Modifier.size(20.dp))
            }
        }
        ReleaseNotesMarkdown(manifest.notes)
    }
}

@Composable
private fun UpdateInstallContent(updates: AppUpdates, state: ApkInstallState, onInstall: () -> Unit, onPlayUpdate: () -> Unit, enabled: Boolean) {
    if (updates.usesPlay) {
        Button(onClick = onPlayUpdate, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.updates_play)) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (state.stage) {
            ApkInstallStage.DOWNLOADING -> {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.updates_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = updates::pauseDownload) { Text(stringResource(R.string.updates_pause)) }
                }
            }
            ApkInstallStage.VERIFYING -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.updates_verifying))
            }
            ApkInstallStage.READY, ApkInstallStage.PERMISSION -> {
                Text(stringResource(if (state.stage == ApkInstallStage.PERMISSION) R.string.updates_permission else R.string.updates_ready))
                Button(onClick = onInstall, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.updates_install)) }
            }
            else -> {
                if (state.stage == ApkInstallStage.FAILED) Text(stringResource(R.string.updates_transfer_failed), color = MaterialTheme.colorScheme.error)
                Button(onClick = updates::download, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.updates_download)) }
            }
        }
    }
}
