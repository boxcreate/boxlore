package cx.aswin.boxlore.feature.info.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.logic.FeedItem
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import cx.aswin.boxlore.feature.info.logic.toolbarWarningActionText
import cx.aswin.boxlore.feature.info.logic.toolbarWarningMessage
import cx.aswin.boxlore.feature.info.logic.toolbarWarningTitle

internal fun handleNotificationsToggle(
    context: android.content.Context,
    podcastNotificationsEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onShowPermissionBlockedWarning: () -> Unit,
    onToggleNotifications: () -> Unit,
) {
    if (!podcastNotificationsEnabled) {
        // Turning notifications ON
        if (!areAppNotificationsEnabled(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                onRequestPermission()
            } else {
                onShowPermissionBlockedWarning()
            }
        } else {
            onToggleNotifications()
        }
    } else {
        // Turning notifications OFF
        onToggleNotifications()
    }
}

internal fun handleAutoDownloadToggle(
    podcastAutoDownloadEnabled: Boolean,
    podcastNotificationsEnabled: Boolean,
    onShowNotificationsRequiredWarning: () -> Unit,
    onToggleAutoDownload: () -> Unit,
) {
    if (!podcastAutoDownloadEnabled) {
        // Turning auto-download ON
        if (!podcastNotificationsEnabled) {
            onShowNotificationsRequiredWarning()
        } else {
            onToggleAutoDownload()
        }
    } else {
        // Turning auto-download OFF
        onToggleAutoDownload()
    }
}

internal fun handleToolbarWarningAction(
    warning: ToolbarWarning,
    context: android.content.Context,
    viewModel: PodcastInfoViewModel,
    onRequestNotificationPermission: () -> Unit,
    onShowPermissionBlockedWarning: () -> Unit,
) {
    when (warning) {
        ToolbarWarning.NOTIFICATIONS_REQUIRED -> {
            if (areAppNotificationsEnabled(context)) {
                viewModel.enableBothNotificationsAndAutoDownload()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                onRequestNotificationPermission()
            } else {
                onShowPermissionBlockedWarning()
            }
        }
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> openAppNotificationSettings(context)
        else -> {}
    }
}

@Composable
internal fun ToolbarWarningBanner(
    warning: ToolbarWarning,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    AnimatedVisibility(
        visible = warning != ToolbarWarning.NONE,
        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
    ) {
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(16.dp),
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = toolbarWarningTitle(warning),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = toolbarWarningMessage(warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                )

                val actionText = toolbarWarningActionText(warning)

                if (actionText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = onAction,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = actionText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Per-episode-list membership sets used to render like/queue/download/completed state on rows. */
internal data class EpisodeListIndicators(
    val likedEpisodeIds: Set<String> = emptySet(),
    val queuedEpisodeIds: Set<String> = emptySet(),
    val downloadedEpisodeIds: Set<String> = emptySet(),
    val downloadingEpisodeIds: Set<String> = emptySet(),
    val completedEpisodeIds: Set<String> = emptySet(),
)

@Composable
internal fun EpisodeFeedItemRow(
    feedItem: FeedItem,
    viewModel: PodcastInfoViewModel,
    accentColor: Color,
    indicators: EpisodeListIndicators,
    autoScrolledEpisodeId: String?,
    onEpisodeClick: (Episode, String, Int?) -> Unit,
) {
    when (feedItem) {
        is FeedItem.NormalEpisode -> {
            val index = feedItem.globalIndex
            val episode = feedItem.episode

            EpisodePlayStateWrapper(
                episodeId = episode.id,
                playbackStateFlow = viewModel.episodePlaybackState,
            ) { playState ->
                EpisodeListItem(
                    episode = episode,
                    isLiked = indicators.likedEpisodeIds.contains(episode.id),
                    accentColor = accentColor,
                    // Playback State
                    isPlaying = playState?.isPlaying == true,
                    isResume = playState?.isResume == true,
                    progress = playState?.progress ?: 0f,
                    timeLeft = playState?.timeLeft,
                    // Download State
                    isDownloaded = indicators.downloadedEpisodeIds.contains(episode.id),
                    isDownloading = indicators.downloadingEpisodeIds.contains(episode.id),
                    isQueued = indicators.queuedEpisodeIds.contains(episode.id),
                    isCompleted = indicators.completedEpisodeIds.contains(episode.id),
                    isUpNext = episode.id == autoScrolledEpisodeId,
                    onClick = {
                        viewModel.recordEpisodeClick(episode.id)
                        onEpisodeClick(episode, "podcast_info_episodes_list", index)
                    },
                    onPlayClick = { viewModel.onPlayClick(episode) },
                    onToggleLike = { viewModel.onToggleLike(episode) },
                    onQueueClick = { viewModel.toggleQueue(episode) },
                    onDownloadClick = { viewModel.toggleDownload(episode) },
                    onMarkPlayedClick = { viewModel.onToggleCompletion(episode) },
                    showMarkPlayedButton = false, // Hide in list view
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        is FeedItem.SingleTrailer -> {
            SingleTrailerCard(
                episode = feedItem.episode,
                globalIndex = feedItem.globalIndex,
                playbackStateFlow = viewModel.episodePlaybackState,
                onEpisodeClick = { ep, globalIndex ->
                    viewModel.recordEpisodeClick(ep.id)
                    onEpisodeClick(ep, "podcast_info_episodes_list", globalIndex)
                },
                onPlayClick = { ep -> viewModel.onPlayClick(ep) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        is FeedItem.TrailerGroup -> {
            TrailerStackCard(
                group = feedItem,
                playbackStateFlow = viewModel.episodePlaybackState,
                onEpisodeClick = { ep, globalIndex ->
                    viewModel.recordEpisodeClick(ep.id)
                    onEpisodeClick(ep, "podcast_info_episodes_list", globalIndex)
                },
                onPlayClick = { ep -> viewModel.onPlayClick(ep) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** Groups [PodcastInfoTopOverlay]'s menu actions so the composable stays under the Sonar param limit. */
internal data class PodcastInfoTopOverlayActions(
    val onBack: () -> Unit,
    val onMarkAllPlayed: () -> Unit,
    val onMarkAllUnplayed: () -> Unit,
    val onToggleHideCompleted: () -> Unit,
    val onPlaybackSettings: () -> Unit,
)

@Composable
internal fun PodcastInfoTopOverlay(
    podcast: Podcast,
    headerColor: Color,
    collapsedHeaderHeight: Dp,
    hideCompleted: Boolean,
    context: android.content.Context,
    actions: PodcastInfoTopOverlayActions,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(collapsedHeaderHeight)
                .background(headerColor)
                .statusBarsPadding(),
    ) {
        IconButton(
            onClick = actions.onBack,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Share and More Options Dropdown Menu (Top Right)
        var showMenu by remember { mutableStateOf(false) }
        var showShareSheet by remember { mutableStateOf(false) }
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showShareSheet = true },
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Share,
                        contentDescription = "Share Podcast",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (showShareSheet) {
                cx.aswin.boxlore.core.designsystem.components.ShareBottomSheet(
                    id = podcast.id,
                    type = "podcast",
                    title = podcast.title,
                    subtitle = podcast.artist,
                    imageUrl = podcast.imageUrl,
                    onDismissRequest = { showShareSheet = false },
                    onShare = { _, _, _, target ->
                        cx.aswin.boxlore.core.designsystem.share.ShareManager.sharePodcast(
                            context = context,
                            podcast = podcast,
                            target = target,
                        )
                    },
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(20.dp),
                offset = DpOffset(x = (-12).dp, y = 4.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("Mark all as played") },
                    onClick = {
                        showMenu = false
                        actions.onMarkAllPlayed()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Mark all as unplayed") },
                    onClick = {
                        showMenu = false
                        actions.onMarkAllUnplayed()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (hideCompleted) "Show completed episodes" else "Hide completed episodes") },
                    onClick = {
                        showMenu = false
                        actions.onToggleHideCompleted()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (hideCompleted) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text("Playback for this show") },
                    onClick = {
                        showMenu = false
                        actions.onPlaybackSettings()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Tune, contentDescription = null)
                    },
                )
            }
        }
    }
}

@Composable
internal fun MarkAllEpisodesDialog(
    podcastTitle: String,
    markAsPlayed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (markAsPlayed) Icons.Rounded.DoneAll else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (markAsPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = if (markAsPlayed) "Mark all as played?" else "Mark all as unplayed?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                text =
                    if (markAsPlayed) {
                        "This will mark all episodes of \"$podcastTitle\" as played."
                    } else {
                        "This will reset all episodes of \"$podcastTitle\" to unplayed."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (markAsPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    ),
                shape = ExpressiveShapes.Pill,
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

internal fun areAppNotificationsEnabled(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (androidx.core.content.ContextCompat
                .checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }
    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        notificationManager.areNotificationsEnabled()
    } else {
        androidx.core.app.NotificationManagerCompat
            .from(context)
            .areNotificationsEnabled()
    }
}

internal fun openAppNotificationSettings(context: android.content.Context) {
    val intent =
        android.content.Intent().apply {
            when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> {
                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                else -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
            }
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
    context.startActivity(intent)
}
