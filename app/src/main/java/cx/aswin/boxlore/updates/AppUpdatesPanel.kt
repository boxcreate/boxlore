package cx.aswin.boxlore.updates

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialog as Dialog
import cx.aswin.boxlore.core.designsystem.links.openLink
import cx.aswin.boxlore.ui.NativeDialogSystemBars

@Composable
fun AppUpdatesPanel(updates: AppUpdates, onInstall: () -> Unit, onPlayUpdate: () -> Unit, onOpenPlay: () -> Unit = {}) {
    val visible by updates.visible.collectAsStateWithLifecycle()
    if (!visible) return
    TrackScreenView("app_updates")
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
                // A first check shows its spinner in the Check button; a re-check over an offer shows this bar.
                if (check.offer != null && check.status == UpdateCheckStatus.CHECKING) BoxLoreLoader.LinearWavy(Modifier.fillMaxWidth().testTag(UPDATES_TOP_PROGRESS_TAG))
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
                        UpdateStatusContent(
                            check.status,
                            onCheck = { updates.open(checkNow = true) },
                            onOpenPlay = onOpenPlay.takeIf { updates.usesPlay },
                        )
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
    val iconTurn by animateFloatAsState(if (expanded) 180f else 0f, ExpressiveMotion.FormalSpring, label = "upcoming_icon")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            // A calm row that presses with the panel's own shape; TalkBack hears expanded or collapsed.
            Surface(
                onClick = { expanded = !expanded },
                shape = MaterialTheme.shapes.large,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().testTag(UPDATES_UPCOMING_ROW_TAG).semantics {
                    if (expanded) {
                        collapse {
                            expanded = false
                            true
                        }
                    } else {
                        expand {
                            expanded = true
                            true
                        }
                    }
                },
            ) {
                Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.updates_upcoming), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.updates_unreleased), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(iconTurn))
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(UpcomingRevealSpring) + fadeIn(),
                exit = shrinkVertically(UpcomingRevealSpring) + fadeOut(),
            ) {
                Column(Modifier.padding(16.dp)) { ReleaseNotesMarkdown(notes) }
            }
        }
    }
}

/**
 * The check's result with its next step. A Play build whose check failed (Play won't answer for an
 * install it didn't make, or Play itself is unavailable) leads with opening boxlore's Play page,
 * and keeps Try again under it.
 */
@Composable
private fun UpdateStatusContent(status: UpdateCheckStatus, onCheck: () -> Unit, onOpenPlay: (() -> Unit)? = null) {
    val checking = status == UpdateCheckStatus.CHECKING || status == UpdateCheckStatus.IDLE
    val openPlay = onOpenPlay?.takeIf { status == UpdateCheckStatus.FAILED }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(painterResource(R.drawable.ic_boxlore_wordmark_mic), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(32.dp).aspectRatio(6213f / 820f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (openPlay != null) R.string.updates_play_check_failed else checkStatusText(status)),
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
        if (openPlay != null) {
            PlayFallbackActions(onOpenPlay = openPlay, onCheck = onCheck)
            return@Column
        }
        FilledTonalButton(onClick = onCheck, enabled = !checking, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (checking) {
                    BoxLoreLoader.CircularWavy(size = 20.dp, color = LocalContentColor.current, modifier = Modifier.testTag(UPDATES_CHECK_LOADER_TAG))
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(if (checking) R.string.updates_checking else R.string.updates_check))
            }
        }
    }
}

@Composable
private fun PlayFallbackActions(onOpenPlay: () -> Unit, onCheck: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onOpenPlay, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.updates_open_play))
            }
        }
        TextButton(onClick = onCheck, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.updates_try_again)) }
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
            IconButton(onClick = { context.openLink(Uri.parse(manifest.notesUrl)) }) {
                Icon(Icons.Rounded.OpenInNew, stringResource(R.string.updates_read_notes), Modifier.size(20.dp))
            }
        }
        ReleaseNotesMarkdown(manifest.notes)
    }
}

@Composable
private fun UpdateInstallContent(updates: AppUpdates, state: ApkInstallState, onInstall: () -> Unit, onPlayUpdate: () -> Unit, enabled: Boolean) {
    if (updates.usesPlay) {
        UpdatePrimaryButton(stringResource(R.string.updates_play), onPlayUpdate, enabled)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (state.stage) {
            ApkInstallStage.DOWNLOADING -> {
                BoxLoreLoader.LinearWavy(modifier = Modifier.fillMaxWidth(), progress = state.progress)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.updates_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = updates::pauseDownload) { Text(stringResource(R.string.updates_pause)) }
                }
            }
            ApkInstallStage.VERIFYING -> {
                BoxLoreLoader.LinearWavy(Modifier.fillMaxWidth())
                Text(stringResource(R.string.updates_verifying))
            }
            ApkInstallStage.READY, ApkInstallStage.PERMISSION -> {
                Text(stringResource(if (state.stage == ApkInstallStage.PERMISSION) R.string.updates_permission else R.string.updates_ready))
                UpdatePrimaryButton(stringResource(R.string.updates_install), onInstall, enabled)
            }
            else -> {
                if (state.stage == ApkInstallStage.FAILED) Text(stringResource(R.string.updates_transfer_failed), color = MaterialTheme.colorScheme.error)
                UpdatePrimaryButton(stringResource(R.string.updates_download), updates::download, enabled)
            }
        }
    }
}

/** The page's main action: a full-width 56dp pill, like Account's primary buttons. */
@Composable
private fun UpdatePrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Button(onClick = onClick, enabled = enabled, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/** The size counterpart of [ExpressiveMotion.FormalSpring], for the upcoming notes opening. */
private val UpcomingRevealSpring = spring(dampingRatio = 0.8f, stiffness = 400f, visibilityThreshold = IntSize(1, 1))

internal const val UPDATES_TOP_PROGRESS_TAG = "updates_top_progress"
internal const val UPDATES_CHECK_LOADER_TAG = "updates_check_loader"
internal const val UPDATES_UPCOMING_ROW_TAG = "updates_upcoming_row"
