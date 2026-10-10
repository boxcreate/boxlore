package cx.aswin.boxlore.ui.announcement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository.Announcement
import cx.aswin.boxlore.ui.NativeDialogSystemBars
import cx.aswin.boxlore.updates.ReleaseNotesMarkdown
import cx.aswin.boxlore.util.isInstalledFromPlayStore

/** Native Material 3 presentation; the admin's browser preview follows the same visual specification. */
@Composable
fun InAppAnnouncementDialog(announcement: Announcement, onDismiss: () -> Unit, onAction: (route: String) -> Unit) {
    val actions = remember(announcement) { announcementActions(announcement) }
    val fullscreen = announcement.presentation == "fullscreen"
    val colors = announcementColors(MaterialTheme.colorScheme, announcement.tone)
    val dismiss = {
        AnalyticsHelper.trackInAppAnnouncementDismissed(announcement.category, !announcement.imageUrl.isNullOrBlank(), announcement.showActionInApp)
        onDismiss()
    }
    LaunchedEffect(announcement.timestamp) {
        AnalyticsHelper.trackInAppAnnouncementViewed(announcement.category, !announcement.imageUrl.isNullOrBlank(), announcement.showActionInApp && !announcement.route.isNullOrBlank())
    }
    MaterialTheme(colorScheme = colors) {
        Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            NativeDialogSystemBars(colors.surface, dimmed = !fullscreen)
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .background(if (fullscreen) colors.surface else colors.scrim.copy(alpha = 0.38f))
                    .safeDrawingPadding()
                    .padding(horizontal = if (fullscreen) 0.dp else 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val panel = if (fullscreen) Modifier.fillMaxSize() else Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight * 0.9f)
                Surface(
                    modifier = panel,
                    shape = RoundedCornerShape(if (fullscreen) 0.dp else 28.dp),
                    color = if (fullscreen) colors.surface else colors.surfaceContainer,
                    shadowElevation = if (fullscreen) 0.dp else 3.dp,
                ) {
                    Column {
                        AnnouncementHeader(announcement.category, dismiss)
                        Column(
                            Modifier.weight(1f, fill = fullscreen).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AnnouncementImage(announcement)
                            Text(announcement.title, style = if (fullscreen) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall)
                            ReleaseNotesMarkdown(announcement.body, stripPrLinks = false)
                        }
                        AnnouncementFooter(actions, dismiss, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementHeader(category: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Text(category, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.announcement_close)) }
    }
}

@Composable
private fun AnnouncementImage(announcement: Announcement) {
    val url = announcementImageUrl(announcement.imageUrl) ?: return
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    val cover = announcement.imageStyle == "cover"
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = if (cover) ContentScale.Fit else ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().heightIn(max = if (cover) 260.dp else 200.dp).aspectRatio(if (cover) 1f else 16f / 9f).clip(RoundedCornerShape(20.dp)),
        onError = { failed = true },
    )
}

@Composable
private fun AnnouncementFooter(actions: List<AnnouncementAction>, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    if (actions.firstOrNull()?.kind == AnnouncementActionKind.UPDATE) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { item -> AnnouncementActionButton(item, Modifier.fillMaxWidth(), onAction) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.announcement_dismiss)) }
        }
    } else {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.announcement_dismiss)) }
            actions.forEach { item -> AnnouncementActionButton(item, Modifier, onAction) }
        }
    }
}

@Composable
private fun AnnouncementActionButton(item: AnnouncementAction, modifier: Modifier, onAction: (String) -> Unit) {
    val play = !BuildConfig.BOXLORE_DIRECT_UPDATES || LocalContext.current.isInstalledFromPlayStore()
    val label = when (item.kind) {
        AnnouncementActionKind.UPDATE -> stringResource(if (play) R.string.announcement_play else R.string.announcement_download)
        AnnouncementActionKind.GITHUB -> stringResource(R.string.announcement_github)
        AnnouncementActionKind.CUSTOM -> item.label ?: stringResource(R.string.announcement_open)
    }
    if (item.kind == AnnouncementActionKind.GITHUB) {
        FilledTonalButton(onClick = { onAction(item.route) }, modifier = modifier.heightIn(min = 48.dp)) { Text(label) }
    } else {
        Button(onClick = { onAction(item.route) }, modifier = modifier.heightIn(min = 48.dp)) { Text(label) }
    }
}

internal fun announcementColors(colors: ColorScheme, tone: String): ColorScheme = when (tone) {
    "secondary" -> colors.copy(primary = colors.secondary, onPrimary = colors.onSecondary, primaryContainer = colors.secondaryContainer, onPrimaryContainer = colors.onSecondaryContainer)
    "tertiary" -> colors.copy(primary = colors.tertiary, onPrimary = colors.onTertiary, primaryContainer = colors.tertiaryContainer, onPrimaryContainer = colors.onTertiaryContainer)
    "error" -> colors.copy(primary = colors.error, onPrimary = colors.onError, primaryContainer = colors.errorContainer, onPrimaryContainer = colors.onErrorContainer)
    else -> colors
}
