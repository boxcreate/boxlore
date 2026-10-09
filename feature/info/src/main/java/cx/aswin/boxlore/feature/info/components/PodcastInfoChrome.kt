package cx.aswin.boxlore.feature.info.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.logic.FeedItem
import cx.aswin.boxlore.feature.info.logic.NotificationToggleAction
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import cx.aswin.boxlore.feature.info.logic.canPromptNotificationPermission
import cx.aswin.boxlore.feature.info.logic.resolveAutoDownloadToggleWarning
import cx.aswin.boxlore.feature.info.logic.resolveNotificationToggleAction

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun canPromptRuntimeNotificationPermission(context: Context): Boolean {
    val activity = context.findActivity()
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        false
    }
    val prefs = BoxcastPrefs(context)
    val hasPromptedBefore = prefs.hasRequestedNotificationPermission()
    val shouldShowRationale = activity != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

    return canPromptNotificationPermission(
        sdkInt = Build.VERSION.SDK_INT,
        isPostNotificationsGranted = hasPermission,
        hasPromptedBefore = hasPromptedBefore,
        shouldShowRationale = shouldShowRationale,
    )
}

internal fun handleNotificationsToggle(
    context: Context,
    podcastNotificationsEnabled: Boolean,
    isWarningVisible: Boolean = false,
    onRequestPermission: () -> Unit,
    onShowPermissionBlockedWarning: () -> Unit,
    onToggleNotifications: () -> Unit,
) {
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    when (
        resolveNotificationToggleAction(
            podcastNotificationsEnabled = podcastNotificationsEnabled,
            areAppNotificationsEnabled = areAppNotificationsEnabled(context),
            hasPostNotificationPermission = hasPermission,
            isWarningVisible = isWarningVisible,
        )
    ) {
        NotificationToggleAction.REQUEST_PERMISSION -> {
            if (canPromptRuntimeNotificationPermission(context)) {
                onRequestPermission()
            } else {
                onShowPermissionBlockedWarning()
            }
        }
        NotificationToggleAction.SHOW_PERMISSION_BLOCKED_WARNING -> onShowPermissionBlockedWarning()
        NotificationToggleAction.TOGGLE_NOTIFICATIONS -> onToggleNotifications()
    }
}

internal fun handleAutoDownloadToggle(
    podcastAutoDownloadEnabled: Boolean,
    podcastNotificationsEnabled: Boolean,
    backgroundChecksEnabled: Boolean,
    currentWarning: ToolbarWarning,
    onWarningChange: (ToolbarWarning) -> Unit,
    onToggleAutoDownload: () -> Unit,
) {
    onWarningChange(
        resolveAutoDownloadToggleWarning(
            currentWarning = currentWarning,
            autoDownloadEnabled = podcastAutoDownloadEnabled,
            notificationsEnabled = podcastNotificationsEnabled,
            backgroundChecksEnabled = backgroundChecksEnabled,
        ),
    )
    onToggleAutoDownload()
}

internal fun handleToolbarWarningAction(
    warning: ToolbarWarning,
    context: Context,
    onEnableNotifications: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onShowPermissionBlockedWarning: () -> Unit,
) {
    when (warning) {
        ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY -> {
            if (areAppNotificationsEnabled(context)) {
                onEnableNotifications()
            } else if (canPromptRuntimeNotificationPermission(context)) {
                onRequestNotificationPermission()
            } else {
                onShowPermissionBlockedWarning()
            }
        }
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> {
            if (canPromptRuntimeNotificationPermission(context)) {
                onRequestNotificationPermission()
            } else {
                openAppNotificationSettings(context)
            }
        }
        else -> {}
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

internal data class EpisodeSelectionUi(
    val selectedEpisodeIds: Set<String> = emptySet(),
    val isActive: Boolean = false,
    val onToggle: (Episode) -> Unit = {},
    val onLongPress: (Episode) -> Unit = {},
)

internal data class EpisodeFeedRowUi(
    val accentColor: Color,
    val indicators: EpisodeListIndicators,
    val autoScrolledEpisodeId: String?,
    val podcastImageUrl: String?,
    val isSearchActive: Boolean = false,
)

@Composable
internal fun EpisodeFeedItemRow(
    feedItem: FeedItem,
    viewModel: PodcastInfoViewModel,
    ui: EpisodeFeedRowUi,
    onEpisodeClick: (Episode, String, Int?) -> Unit,
    selection: EpisodeSelectionUi = EpisodeSelectionUi(),
    modifier: Modifier = Modifier,
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
                    isLiked = ui.indicators.likedEpisodeIds.contains(episode.id),
                    accentColor = ui.accentColor,
                    podcastImageUrl = ui.podcastImageUrl,
                    // Playback State
                    isPlaying = playState?.isPlaying == true,
                    isResume = playState?.isResume == true,
                    progress = playState?.progress ?: 0f,
                    timeLeft = playState?.timeLeft,
                    // Download State
                    isDownloaded = ui.indicators.downloadedEpisodeIds.contains(episode.id),
                    isDownloading = ui.indicators.downloadingEpisodeIds.contains(episode.id),
                    isQueued = ui.indicators.queuedEpisodeIds.contains(episode.id),
                    isCompleted = ui.indicators.completedEpisodeIds.contains(episode.id),
                    isUpNext = episode.id == ui.autoScrolledEpisodeId,
                    selectionActive = selection.isActive,
                    isSelected = episode.id in selection.selectedEpisodeIds,
                    onClick = {
                        if (selection.isActive) {
                            selection.onToggle(episode)
                        } else {
                            viewModel.recordEpisodeClick(episode.id)
                            onEpisodeClick(episode, if (ui.isSearchActive) "podcast_info_search_results" else "podcast_info_episodes_list", index)
                        }
                    },
                    onLongClick = { selection.onLongPress(episode) },
                    onPlayClick = { viewModel.onPlayClick(episode) },
                    onToggleLike = { viewModel.onToggleLike(episode) },
                    onQueueClick = { viewModel.toggleQueue(episode) },
                    onDownloadClick = { viewModel.toggleDownload(episode) },
                    onMarkPlayedClick = { viewModel.onToggleCompletion(episode) },
                    showMarkPlayedButton = ui.isSearchActive,
                    modifier = modifier.padding(horizontal = 16.dp),
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
                selection = selection,
                modifier = modifier.padding(horizontal = 16.dp),
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
                selection = selection,
                modifier = modifier.padding(horizontal = 16.dp),
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
    val isSubscribed: Boolean = false,
    val isPinnedToHome: Boolean = false,
    val onToggleHomePin: () -> Unit = {},
    val onEditGenre: () -> Unit = {},
)

internal data class MissingEpisodesChip(
    val visible: Boolean = false,
    val state: DirectFeedChipState = DirectFeedChipState.Hidden,
    val onClick: () -> Unit = {},
)

@Composable
internal fun PodcastInfoTopOverlay(
    podcast: Podcast,
    headerColor: Color,
    collapsedHeaderHeight: Dp,
    hideCompleted: Boolean,
    context: android.content.Context,
    actions: PodcastInfoTopOverlayActions,
    missingEpisodesChip: MissingEpisodesChip = MissingEpisodesChip(),
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
        PodcastInfoHeaderOverflow(
            podcast = podcast,
            hideCompleted = hideCompleted,
            context = context,
            actions = actions,
            missingEpisodesChip = missingEpisodesChip,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
        )
    }
}

@Composable
private fun PodcastInfoHeaderOverflow(
    podcast: Podcast,
    hideCompleted: Boolean,
    context: android.content.Context,
    actions: PodcastInfoTopOverlayActions,
    missingEpisodesChip: MissingEpisodesChip,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MissingEpisodesChipButton(chip = missingEpisodesChip)
            IconButton(
                onClick = { showShareSheet = true },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
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
            PodcastInfoOverflowMenuItems(
                hideCompleted = hideCompleted,
                onDismiss = { showMenu = false },
                actions = actions,
            )
        }
    }
}

@Composable
private fun PodcastInfoOverflowMenuItems(
    hideCompleted: Boolean,
    onDismiss: () -> Unit,
    actions: PodcastInfoTopOverlayActions,
) {
    DropdownMenuItem(
        text = { Text("Mark all as played") },
        onClick = {
            onDismiss()
            actions.onMarkAllPlayed()
        },
        leadingIcon = {
            Icon(Icons.Rounded.DoneAll, contentDescription = null)
        },
    )
    DropdownMenuItem(
        text = { Text("Mark all as unplayed") },
        onClick = {
            onDismiss()
            actions.onMarkAllUnplayed()
        },
        leadingIcon = {
            Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null)
        },
    )
    DropdownMenuItem(
        text = { Text(if (hideCompleted) "Show completed episodes" else "Hide completed episodes") },
        onClick = {
            onDismiss()
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
            onDismiss()
            actions.onPlaybackSettings()
        },
        leadingIcon = {
            Icon(Icons.Rounded.Tune, contentDescription = null)
        },
    )
    if (actions.isSubscribed) {
        DropdownMenuItem(
            text = { Text("Change tag / genre") },
            onClick = {
                onDismiss()
                actions.onEditGenre()
            },
            leadingIcon = {
                Icon(Icons.Rounded.LocalOffer, contentDescription = null)
            },
        )
        DropdownMenuItem(
            text = { Text(if (actions.isPinnedToHome) "Unpin from Home screen" else "Pin to Home screen") },
            onClick = {
                onDismiss()
                actions.onToggleHomePin()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = 45f },
                )
            },
        )
    }
}

@Composable
private fun MissingEpisodesChipButton(chip: MissingEpisodesChip) {
    val chipShown = chip.visible && chip.state != DirectFeedChipState.Hidden
    AnimatedVisibility(
        visible = chipShown,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val clickable = chip.state == DirectFeedChipState.Offer
        val scheme = MaterialTheme.colorScheme
        val container = if (chip.state == DirectFeedChipState.Updated) scheme.surfaceContainerHigh else scheme.primaryContainer
        val content = if (chip.state == DirectFeedChipState.Updated) scheme.onSurfaceVariant else scheme.onPrimaryContainer
        FilledTonalButton(
            onClick = chip.onClick,
            enabled = clickable,
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container,
                disabledContentColor = content,
            ),
            modifier =
            Modifier
                .padding(end = 4.dp)
                .widthIn(max = 176.dp).heightIn(min = 40.dp),
        ) {
            if (chip.state == DirectFeedChipState.Fetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = content,
                    )
            } else {
                Icon(
                    if (chip.state == DirectFeedChipState.Updated) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringResource(
                            when (chip.state) {
                            DirectFeedChipState.Fetching -> R.string.podcast_info_missing_fetching
                            DirectFeedChipState.Updated -> R.string.podcast_info_missing_updated
                            else -> R.string.podcast_info_missing_offer
                        }
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = GoogleSansWeight.bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                fontWeight = GoogleSansWeight.bold,
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
