package cx.aswin.boxlore.feature.library.downloads

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.model.text.formatTimeOfDay
import cx.aswin.boxlore.feature.library.R

@Composable
fun SmartDownloadsDashboardCard(
    userPrefs: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    downloads: List<cx.aswin.boxlore.core.database.DownloadedEpisodeEntity>,
    onSettingsClick: () -> Unit,
    onSyncNow: () -> Unit,
    isSyncing: Boolean = false
) {
    val isEnabled by userPrefs.smartDownloadsEnabledStream.collectAsState(initial = false)
    val maxEpisodes by userPrefs.smartDownloadsMaxEpisodesStream.collectAsState(initial = 10)
    val storageBudget by userPrefs.smartDownloadsStorageBudgetStream.collectAsState(initial = 250L)
    val lastSyncTime by userPrefs.smartDownloadsLastSyncTimeStream.collectAsState(initial = 0L)

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "rotation"
    )

    // Count how many completed smart downloaded episodes we currently have
    val smartDownloadedCount = remember(downloads) {
        downloads.count { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
    }

    // Count how many smart downloads are currently in-progress (downloading)
    val smartDownloadingCount = remember(downloads) {
        downloads.count { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING }
    }

    // Calculate total storage used by completed smart downloads in MB
    val smartDownloadedSizeMb = remember(downloads) {
        downloads.filter { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }.sumOf { it.sizeBytes } / (1024 * 1024)
    }

    if (!isEnabled) {
        Surface(
            onClick = onSettingsClick,
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.library_smart_downloads_enable),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        return
    }

    val statusText = stringResource(
        if (smartDownloadingCount > 0) {
            R.string.library_smart_downloads_status_syncing
        } else if (isSyncing) {
            R.string.library_smart_downloads_status_checking
        } else {
            R.string.library_smart_downloads_status_ready
        },
    )

    // A solid panel, like the module's other panels (no outline).
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row (Clickable to collapse/expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveClickable(
                        shape = RoundedCornerShape(12.dp),
                        onClick = { isExpanded = !isExpanded }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.library_smart_downloads),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.bold
                        )
                        Text(
                            text = if (isExpanded) {
                                stringResource(R.string.library_smart_downloads_subtitle)
                            } else {
                                pluralStringResource(R.plurals.library_smart_downloads_collapsed_summary, maxEpisodes, statusText, smartDownloadedCount, maxEpisodes, smartDownloadedSizeMb)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!isExpanded && (smartDownloadingCount > 0 || isSyncing)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (!isExpanded && (smartDownloadingCount > 0 || isSyncing)) GoogleSansWeight.bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isExpanded) {
                        FilledTonalIconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.library_smart_downloads_settings),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (isSyncing || smartDownloadingCount > 0) {
                        BoxLoreLoader.CircularWavy(size = 20.dp)
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(if (isExpanded) R.string.library_collapse else R.string.library_expand),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(rotationZ = rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded body content
            if (isExpanded) {
                // Sync Status & Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = GoogleSansWeight.bold,
                            color = if (smartDownloadingCount > 0 || isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lastSyncLabel(lastSyncTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(if (isSyncing) R.string.library_smart_downloads_syncing else R.string.library_smart_downloads_sync_now),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = GoogleSansWeight.bold
                        )
                    }
                }

                // Squiggly Wave Progress Loader (Displays during active network requests or sync tasks)
                if (isSyncing || smartDownloadingCount > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BoxLoreLoader.LinearWavy(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = if (smartDownloadingCount > 0) {
                                pluralStringResource(R.plurals.library_smart_downloads_downloading, smartDownloadingCount, smartDownloadingCount)
                            } else {
                                stringResource(R.string.library_smart_downloads_syncing_cloud)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = GoogleSansWeight.medium
                        )
                    }
                }

                // Linear Progress Metrics matching limits & storage budget
                val countProgress = ((smartDownloadedCount + smartDownloadingCount).toFloat() / maxEpisodes.toFloat()).coerceIn(0f, 1f)
                val storageProgress = if (storageBudget <= 0L) {
                    0f
                } else {
                    (smartDownloadedSizeMb.toFloat() / storageBudget.toFloat()).coerceIn(0f, 1f)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Count Progress
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.library_smart_downloads_episodes_progress, smartDownloadedCount, maxEpisodes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = LocaleFormats.percent(countProgress.toDouble(), currentLocale()),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = GoogleSansWeight.bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { countProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(100.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        )
                    }

                    // Storage Limits Progress
                    if (storageBudget > 0L) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.library_smart_downloads_storage_progress, smartDownloadedSizeMb, storageBudget),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = LocaleFormats.percent(storageProgress.toDouble(), currentLocale()),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = GoogleSansWeight.bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { storageProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(100.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.library_smart_downloads_storage_used),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.library_smart_downloads_storage_unlimited, smartDownloadedSizeMb),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = GoogleSansWeight.bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Last sync: 5m ago", or the date and time once it is a day old. */
@Composable
private fun lastSyncLabel(lastSyncTime: Long): String {
    if (lastSyncTime <= 0L) return stringResource(R.string.library_smart_downloads_last_sync_never)
    val nowMs = System.currentTimeMillis()
    val diff = nowMs - lastSyncTime
    val locale = currentLocale()
    val whenText = when {
        diff < 60_000 -> stringResource(R.string.library_relative_just_now)
        diff < 86_400_000 -> LocaleFormats.compactAgo(lastSyncTime, nowMs, locale)
        else -> stringResource(
            R.string.library_date_and_time,
            LocaleFormats.date(lastSyncTime, LocaleFormats.DateStyle.DAY_MONTH, locale),
            LocalContext.current.formatTimeOfDay(lastSyncTime),
        )
    }
    return stringResource(R.string.library_smart_downloads_last_sync, whenText)
}
