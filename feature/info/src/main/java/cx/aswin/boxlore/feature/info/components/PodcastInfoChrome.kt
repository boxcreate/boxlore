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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogConfirmButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogDismissButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIcon
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIconTone
import cx.aswin.boxlore.core.designsystem.components.BoxloreDropdownMenu
import cx.aswin.boxlore.core.designsystem.components.BoxloreMenuState
import cx.aswin.boxlore.core.designsystem.components.BoxloreTooltip
import cx.aswin.boxlore.core.designsystem.components.rememberBoxloreMenuState
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneOwnsStatusBar
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneStatusBarsPadding
import cx.aswin.boxlore.core.designsystem.links.startFirstAvailable
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedSeal
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
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
    /** Archived episodes, marked on their rows when Show archived or a search lists them. */
    val archivedEpisodeIds: Set<String> = emptySet(),
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
    /** Episode titles only: short [EpisodeTitleRow]s instead of full cards. Trailer cards are already short and stay. */
    val titlesOnly: Boolean = false,
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
                val onRowClick = {
                    if (selection.isActive) {
                        selection.onToggle(episode)
                    } else {
                        viewModel.recordEpisodeClick(episode.id)
                        onEpisodeClick(episode, if (ui.isSearchActive) "podcast_info_search_results" else "podcast_info_episodes_list", index)
                    }
                }
                if (ui.titlesOnly) {
                    NormalEpisodeTitleRow(episode, playState, ui, selection, onRowClick, viewModel, modifier.padding(horizontal = 16.dp))
                } else {
                    EpisodeListItem(
                        episode = episode,
                        isLiked = ui.indicators.likedEpisodeIds.contains(episode.id),
                        accentColor = ui.accentColor,
                        podcastImageUrl = ui.podcastImageUrl,
                        // Playback State
                        isPlaying = playState?.isPlaying == true,
                        isResume = playState?.isResume == true,
                        progress = playState?.progress ?: 0f,
                        timeLeft = playState?.timeLeft?.asString(),
                        // Download State
                        isDownloaded = ui.indicators.downloadedEpisodeIds.contains(episode.id),
                        isDownloading = ui.indicators.downloadingEpisodeIds.contains(episode.id),
                        isQueued = ui.indicators.queuedEpisodeIds.contains(episode.id),
                        isCompleted = ui.indicators.completedEpisodeIds.contains(episode.id),
                        isUpNext = episode.id == ui.autoScrolledEpisodeId,
                        isArchived = episode.id in ui.indicators.archivedEpisodeIds,
                        selectionActive = selection.isActive,
                        isSelected = episode.id in selection.selectedEpisodeIds,
                        onClick = onRowClick,
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
    val onPlaybackSettings: () -> Unit,
    val isSubscribed: Boolean = false,
    /** Opens Add to playlist; null hides it (the show isn't followed, or there are no playlists). */
    val onAddToPlaylist: (() -> Unit)? = null,
    val isPinnedToHome: Boolean = false,
    val onToggleHomePin: () -> Unit = {},
    val onEditGenre: () -> Unit = {},
    /** Mark all is reading the show: both Mark all items wait until it ends. */
    val markAllRunning: Boolean = false,
    /** The show is hidden everywhere: the menu offers Unhide show instead of Hide show everywhere. */
    val isHidden: Boolean = false,
    val onToggleHidden: () -> Unit = {},
)

/**
 * The list's archived-episodes choice: offered while the show has [archivedCount] archived episodes,
 * or while they show ([showArchived]) so they can be hidden again.
 */
internal data class PodcastInfoArchiveMenu(
    val archivedCount: Int = 0,
    val showArchived: Boolean = false,
    val onToggle: () -> Unit = {},
) {
    val offered: Boolean get() = showArchived || archivedCount > 0
}

internal data class MissingEpisodesChip(
    val visible: Boolean = false,
    val state: DirectFeedChipState = DirectFeedChipState.Hidden,
    val onClick: () -> Unit = {},
)

/** How a branded page dresses the top bar: the seal's fade, and its icons' colour over a dark header (null for the page's own). */
internal data class PodcastInfoTopBarLook(val sealAlpha: Float = 0f, val iconTint: Color? = null)

/**
 * The show page's top bar. Its colour spans the pane; Back, Share and More keep inside the pane's
 * safe area (clear of side system buttons and a camera cutout) and, on wide tablet panes, inside
 * the page's reading column ([sidePadding] each side). In a tabletop lower half it starts at the
 * top of that half, with no room for a status bar that isn't there.
 */
@Composable
internal fun PodcastInfoTopOverlay(
    podcast: Podcast,
    headerColor: Color,
    collapsedHeaderHeight: Dp,
    actions: PodcastInfoTopOverlayActions,
    missingEpisodesChip: MissingEpisodesChip = MissingEpisodesChip(),
    look: PodcastInfoTopBarLook = PodcastInfoTopBarLook(),
    sidePadding: Dp = 0.dp,
) {
    val sealAlpha = look.sealAlpha
    val iconTint = look.iconTint ?: MaterialTheme.colorScheme.onSurface
    val context = LocalContext.current
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(collapsedHeaderHeight)
            .background(headerColor),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .boxlorePaneStatusBarsPadding()
                .boxloreHorizontalSafePadding()
                .padding(horizontal = sidePadding)
                .testTag(PODCAST_INFO_TOP_BAR_TAG),
        ) {
            val brandedPage = LocalBrandedPage.current
            if (brandedPage != null && sealAlpha > 0f) {
                BrandedSeal(brandedPage.style.popup, Modifier.align(Alignment.Center).graphicsLayer { alpha = sealAlpha })
            }
            val backLabel = stringResource(R.string.episode_info_back)
            Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                BoxloreTooltip(backLabel) {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = backLabel,
                            tint = iconTint,
                        )
                    }
                }
            }

            // Share and More Options Dropdown Menu (Top Right)
            PodcastInfoHeaderOverflow(
                podcast = podcast,
                context = context,
                actions = actions,
                missingEpisodesChip = missingEpisodesChip,
                iconTint = iconTint,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            )
        }
    }
}

@Composable
private fun PodcastInfoHeaderOverflow(
    podcast: Podcast,
    context: android.content.Context,
    actions: PodcastInfoTopOverlayActions,
    missingEpisodesChip: MissingEpisodesChip,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    // Both survive a fold or unfold that recreates the page.
    val menu = rememberBoxloreMenuState(key = podcast.id)
    var showShareSheet by rememberSaveable(podcast.id) { mutableStateOf(false) }
    val shareLabel = stringResource(R.string.podcast_info_share)
    val moreLabel = stringResource(R.string.podcast_info_more_options)
    Box(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MissingEpisodesChipButton(chip = missingEpisodesChip)
            BoxloreTooltip(shareLabel) {
                IconButton(onClick = { showShareSheet = true }) {
                    Icon(imageVector = Icons.Rounded.Share, contentDescription = shareLabel, tint = iconTint)
                }
            }
            BoxloreTooltip(moreLabel) {
                IconButton(onClick = menu::open) {
                    Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = moreLabel, tint = iconTint)
                }
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
                details = cx.aswin.boxlore.core.designsystem.share.sharePodcastDetails(podcast.effectiveGenre, podcast.updateFrequency),
                onShare = { _, _, _, target ->
                    cx.aswin.boxlore.core.designsystem.share.ShareManager.sharePodcast(
                        context = context,
                        podcast = podcast,
                        target = target,
                    )
                },
            )
        }

        PodcastInfoOverflowMenu(menu, actions)
    }
}

/**
 * The show's More menu in three groups: marking every episode, the show's own settings, and Hide
 * show everywhere on its own (it has Undo, so it isn't drawn as destructive). How the list looks
 * lives in the sort button's Sort and view menu.
 */
@Composable
private fun PodcastInfoOverflowMenu(menu: BoxloreMenuState, actions: PodcastInfoTopOverlayActions) {
    val markPlayed = stringResource(R.string.podcast_info_mark_all_played)
    val markUnplayed = stringResource(R.string.podcast_info_mark_all_unplayed)
    val playback = stringResource(R.string.podcast_info_playback_for_show)
    val addToPlaylist = stringResource(R.string.podcast_info_add_to_playlist)
    val changeGenre = stringResource(R.string.podcast_info_change_genre)
    val pin = stringResource(if (actions.isPinnedToHome) R.string.podcast_info_unpin_home else R.string.podcast_info_pin_home)
    val hide = stringResource(if (actions.isHidden) R.string.podcast_info_unhide_show else R.string.podcast_info_hide_show)
    // Each item closes the menu, then acts.
    val close: (() -> Unit) -> () -> Unit = { action ->
        {
            menu.dismiss()
            action()
        }
    }
    BoxloreDropdownMenu(menu, offset = DpOffset((-12).dp, 4.dp)) {
        group {
            item(markPlayed, onClick = close(actions.onMarkAllPlayed), leadingIcon = Icons.Rounded.DoneAll, enabled = !actions.markAllRunning)
            item(markUnplayed, onClick = close(actions.onMarkAllUnplayed), leadingIcon = Icons.Rounded.RadioButtonUnchecked, enabled = !actions.markAllRunning)
        }
        group {
            item(playback, onClick = close(actions.onPlaybackSettings), leadingIcon = Icons.Rounded.Tune)
            actions.onAddToPlaylist?.let { open -> item(addToPlaylist, onClick = close(open), leadingIcon = Icons.AutoMirrored.Rounded.PlaylistAdd) }
            if (actions.isSubscribed) {
                item(changeGenre, onClick = close(actions.onEditGenre), leadingIcon = Icons.Rounded.LocalOffer)
                item(pin, onClick = close(actions.onToggleHomePin), leadingIcon = Icons.Rounded.PushPin)
            }
        }
        // Followed or not: hiding a followed show unsubscribes from it after a confirmation.
        group {
            item(hide, onClick = close(actions.onToggleHidden), leadingIcon = if (actions.isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff)
        }
    }
}

/** Test tag on the show page's top bar row (Back, Share and More). */
internal const val PODCAST_INFO_TOP_BAR_TAG = "podcast_info_top_bar"

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
        val idle = chip.state == DirectFeedChipState.Updated || chip.state == DirectFeedChipState.CheckFailed
        val container = if (idle) scheme.surfaceContainerHigh else scheme.primaryContainer
        val content = if (idle) scheme.onSurfaceVariant else scheme.onPrimaryContainer
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
                BoxLoreLoader.CircularWavy(size = 16.dp, color = content)
            } else {
                Icon(
                    when (chip.state) {
                        DirectFeedChipState.Updated -> Icons.Rounded.CheckCircle
                        DirectFeedChipState.CheckFailed -> Icons.Rounded.SyncProblem
                        else -> Icons.AutoMirrored.Rounded.PlaylistAdd
                    },
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
                            DirectFeedChipState.CheckFailed -> R.string.podcast_info_missing_check_failed
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
    TrackScreenView(if (markAsPlayed) "podcast_mark_all_played_confirmation" else "podcast_mark_all_unplayed_confirmation")
    BoxloreAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            BoxloreDialogIcon(
                if (markAsPlayed) Icons.Rounded.DoneAll else Icons.Rounded.RadioButtonUnchecked,
                if (markAsPlayed) BoxloreDialogIconTone.Neutral else BoxloreDialogIconTone.Destructive,
            )
        },
        title = {
            Text(stringResource(if (markAsPlayed) R.string.podcast_info_mark_all_played_title else R.string.podcast_info_mark_all_unplayed_title))
        },
        text = {
            Text(
                if (markAsPlayed) {
                    stringResource(R.string.podcast_info_mark_all_played_body, podcastTitle)
                } else {
                    stringResource(R.string.podcast_info_mark_all_unplayed_body, podcastTitle)
                },
            )
        },
        // Unplayed resets every episode's progress with no Undo, so it is the destructive kind.
        confirmButton = { BoxloreDialogConfirmButton(stringResource(R.string.podcast_info_confirm), onConfirm, destructive = !markAsPlayed) },
        dismissButton = { BoxloreDialogDismissButton(stringResource(R.string.podcast_info_hide_cancel), onDismiss) },
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
    // Some ROMs drop the per-app notification page: fall back to the app's details, else do nothing.
    val details = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    )
    context.startFirstAvailable(intent, details)
}

/**
 * Light status bar icons while a branded page's dark header is under the status bar in a light
 * theme ([lightIcons]); the theme's own icons once the bar fills, and again when the page leaves or
 * stops filling the top of the screen (beside another pane, below a tabletop hinge).
 */
@Composable
internal fun BrandedHeaderStatusBar(lightIcons: Boolean) {
    // Leaving composition runs the restore below.
    if (!boxlorePaneOwnsStatusBar()) return
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    val themeLight = !LocalEffectiveDarkTheme.current
    val restore by rememberUpdatedState(themeLight)
    DisposableEffect(view) {
        onDispose { controller.isAppearanceLightStatusBars = restore }
    }
    SideEffect { controller.isAppearanceLightStatusBars = themeLight && !lightIcons }
}
