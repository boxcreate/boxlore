package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import kotlin.math.roundToInt

/**
 * Importing and done on one screen: Echo shuffles its cards on the stage while the progress and the
 * checklist below update, then hops when the shows are in. Only the words change between the steps.
 */
@Composable
internal fun ProgressFlowScaffold(state: OpmlImportState, onDone: () -> Unit) {
    ImportPage(
        echo = importEchoMomentFor(state) ?: ImportEchoMoment.Working,
        content = {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                contentKey = { contentKeyFor(it) },
                label = "import_progress_copy",
            ) { current ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current is OpmlImportState.Success) SuccessHeader(current) else WorkingHeader(current)
                }
            }
        },
        actions = {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                contentKey = { it is OpmlImportState.Success },
                label = "import_progress_actions",
            ) { current ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (current is OpmlImportState.Success) SuccessActions(current, onDone) else ImportSteps(importStepsFor(current))
                }
            }
        },
    )
}

@Composable
private fun WorkingHeader(state: OpmlImportState) {
    ImportTitle(
        stringResource(
            when (state) {
                is OpmlImportState.Parsing -> R.string.import_reading_title
                is OpmlImportState.ImportingJson -> R.string.import_restoring_title
                is OpmlImportState.Completing -> R.string.import_marking_title
                else -> R.string.import_importing_title
            },
        ),
    )
    val progress = importProgressFor(state) ?: return
    val now = when (state) {
        is OpmlImportState.Importing -> state.currentFeedTitle.takeIf { it.isNotBlank() }?.let { stringResource(R.string.import_now_subscribing, it) }
        is OpmlImportState.Completing -> state.currentShowTitle.takeIf { it.isNotBlank() }?.let { stringResource(R.string.import_now_marking, it) }
        is OpmlImportState.ImportingJson -> state.currentTitle.takeIf { it.isNotBlank() }?.let { stringResource(R.string.import_now_refreshing, it) }
        else -> null
    }
    ImportProgressCard(progress, now)
}

@Composable
private fun ImportProgressCard(progress: ImportProgress, now: String?) {
    Surface(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("import_progress"), shape = ImportCorner, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    pluralStringResource(R.plurals.import_progress_count, progress.total, progress.current, progress.total),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                )
                Text(
                    stringResource(R.string.import_progress_percent, (progress.fraction * 100).roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            if (now != null) {
                Text(now, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The checklist: finished steps are ticked (with what they found), the current one is filled, later ones are outlined. */
@Composable
private fun ImportSteps(steps: List<ImportStep>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("import_steps"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        steps.forEach { step -> ImportStepRow(step) }
    }
}

@Composable
private fun ImportStepRow(step: ImportStep) {
    val status = stringResource(
        when (step.status) {
            ImportStepStatus.Done -> R.string.import_step_status_done
            ImportStepStatus.Active -> R.string.import_step_status_now
            ImportStepStatus.Upcoming -> R.string.import_step_status_next
        },
    )
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = status },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ImportStepMarker(step.status)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(importStepLabel(step.kind)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (step.status == ImportStepStatus.Active) GoogleSansWeight.semiBold else null,
                color = if (step.status == ImportStepStatus.Upcoming) colors.onSurfaceVariant else colors.onSurface,
            )
            importStepDetail(step)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        }
    }
}

/** Ticked when done, a filled ring for the step in progress, an outline for steps still to come. */
@Composable
private fun ImportStepMarker(status: ImportStepStatus) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        when (status) {
            ImportStepStatus.Done -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            ImportStepStatus.Active -> Box(Modifier.size(20.dp).border(BorderStroke(2.dp, colors.primary), CircleShape).padding(5.dp).background(colors.primary, CircleShape))
            ImportStepStatus.Upcoming -> Box(Modifier.size(20.dp).border(BorderStroke(2.dp, colors.outlineVariant), CircleShape))
        }
    }
}

/** What a finished step found or added, when it counted shows. */
@Composable
private fun importStepDetail(step: ImportStep): String? {
    val count = step.count?.takeIf { step.status == ImportStepStatus.Done } ?: return null
    return when (step.kind) {
        ImportStepKind.ReadFile -> pluralStringResource(R.plurals.import_step_read_done, count, count)
        ImportStepKind.Subscribe -> pluralStringResource(R.plurals.import_step_subscribe_done, count, count)
        else -> null
    }
}

private fun importStepLabel(kind: ImportStepKind): Int = when (kind) {
    ImportStepKind.ReadFile -> R.string.import_step_read
    ImportStepKind.Subscribe -> R.string.import_step_subscribe
    ImportStepKind.ChooseMarkPlayed -> R.string.import_step_choose
    ImportStepKind.MarkPlayed -> R.string.import_step_mark
    ImportStepKind.RestoreBackup -> R.string.import_step_restore
    ImportStepKind.LatestEpisodes -> R.string.import_step_latest
}

@Composable
private fun SuccessHeader(state: OpmlImportState.Success) {
    ImportTitle(stringResource(if (state.isJson) R.string.import_done_backup_title else R.string.import_done_title))
    ImportBody(stringResource(R.string.import_done_body))
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("import_stats"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ImportStatTile(state.importedCount, pluralStringResource(R.plurals.import_done_subscribed, state.importedCount), Modifier.weight(1f))
        if (!state.isJson && state.completedCount > 0) {
            ImportStatTile(state.completedCount, stringResource(R.string.import_done_marked), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ImportStatTile(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(modifier.semantics(mergeDescendants = true) {}, shape = ImportCorner, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.displaySmall, fontWeight = GoogleSansWeight.bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SuccessActions(state: OpmlImportState.Success, onDone: () -> Unit) {
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(areAppNotificationsEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = areAppNotificationsEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (showsImportNotificationPrompt(state, notificationsAllowed)) {
        ImportNotificationPermissionCard(
            onPermissionChanged = { notificationsAllowed = it },
            onOpenSettings = { openAppNotificationSettings(context) },
        )
    }
    ImportPrimaryButton(stringResource(R.string.import_continue), onDone, Modifier.testTag("import_continue"))
}
