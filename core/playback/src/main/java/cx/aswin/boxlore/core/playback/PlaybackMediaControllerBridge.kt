package cx.aswin.boxlore.core.playback

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import cx.aswin.boxlore.core.catalog.BriefingLinks
import cx.aswin.boxlore.core.catalog.mapRegionForBriefing
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackLifecycleSignals
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.playback.QueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNKNOWN_PODCAST = "Unknown Podcast"

/**
 * Media3 [Player.Listener] implementation extracted from PlaybackRepository.initializeMediaController.
 */
@Suppress("LongParameterList")
internal class PlaybackMediaControllerBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val mediaHandle: PlaybackMediaControllerHandle,
    private val queueRepository: QueueRepository,
    private val currentSkipBehavior: () -> String,
    private val onPlaybackStarted: () -> Unit,
    private val startProgressTicker: () -> Unit,
    private val stopProgressTicker: () -> Unit,
    private val cancelSleepTimer: () -> Unit,
    private val syncQueueToDb: suspend () -> Unit,
    private val reconcileQueueWithController: () -> Unit,
    private val markEpisodeAsCompleted: suspend (Episode, Podcast?) -> Unit,
    private val findPodcastIdForEpisode: suspend (String) -> String?,
    private val hasActiveCastSession: () -> Boolean?,
    /** The show stored under an ID in Room, if any. */
    private val findStoredPodcast: suspend (String) -> Podcast? = { podcastId ->
        BoxLoreDatabase.getDatabase(context).podcastDao().getPodcast(podcastId)?.toTransitionPodcast()
    },
) : Player.Listener {
    private var lastReportedRemote: Boolean? = null
    private var pendingCastTransition: PendingCastTransition? = null
    private val errorSkipGuard = PlaybackErrorSkipGuard()

    /** Called when the listener starts a new queue, so earlier failures do not count against it. */
    fun resetErrorSkips() {
        errorSkipGuard.reset()
    }

    fun syncPlaybackRoute() {
        val controller = mediaHandle.controller ?: return
        updatePlaybackRoute(
            deviceInfo = controller.deviceInfo,
            volume = controller.deviceVolume,
            isMuted = controller.isDeviceMuted,
        )
    }

    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
        val controller = mediaHandle.controller ?: return
        updatePlaybackRoute(
            deviceInfo = deviceInfo,
            volume = controller.deviceVolume,
            isMuted = controller.isDeviceMuted,
        )
    }

    override fun onDeviceVolumeChanged(
        volume: Int,
        muted: Boolean,
    ) {
        val controller = mediaHandle.controller ?: return
        updatePlaybackRoute(
            deviceInfo = controller.deviceInfo,
            volume = volume,
            isMuted = muted,
        )
    }

    private fun updatePlaybackRoute(
        deviceInfo: DeviceInfo,
        volume: Int,
        isMuted: Boolean,
    ) {
        val resolvedRoute =
            PlaybackRouteResolver.resolve(
                context = context,
                deviceInfo = deviceInfo,
                volume = volume,
                isMuted = isMuted,
            )
        val route =
            if (resolvedRoute.isRemote &&
                !CastSessionSyncPolicy.shouldAcceptRemoteRoute(hasActiveCastSession())
            ) {
                PlaybackRouteState()
            } else {
                resolvedRoute
            }
        playerStateFlow.value = playerStateFlow.value.copy(playbackRoute = route)
        if (lastReportedRemote != route.isRemote) {
            if (lastReportedRemote != null || route.isRemote) {
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPlaybackRouteChanged(
                    isRemote = route.isRemote,
                    volumeControlAvailable = route.canControlVolume,
                )
            }
            lastReportedRemote = route.isRemote
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Log.d("PlaybackRepo", "onIsPlayingChanged: isPlaying=$isPlaying, currentPos=${mediaHandle.controller?.currentPosition}")
        val oldIsPlaying = playerStateFlow.value.isPlaying
        playerStateFlow.value = playerStateFlow.value.copy(isPlaying = isPlaying)
        if (isPlaying) {
            errorSkipGuard.onPlaybackSucceeded()
            if (!oldIsPlaying) {
                onPlaybackStarted()
            }
            startProgressTicker()
        } else {
            stopProgressTicker()
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
        playerStateFlow.value =
            PlaybackControlSync.applyPlaybackParametersSpeed(
                playerStateFlow.value,
                playbackParameters.speed,
            )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val isLoading = playbackState == androidx.media3.common.Player.STATE_BUFFERING
        playerStateFlow.value = playerStateFlow.value.copy(isLoading = isLoading)

        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
            Log.d("PlaybackRepo", "Playback ENDED. Completion persistence remains service-owned.")

            val controller = mediaHandle.controller
            val hasMedia = controller != null && controller.mediaItemCount > 0
            val reachedEnd =
                controller != null &&
                    controller.duration > 0 &&
                    (
                        controller.currentPosition >= controller.duration - 10000 ||
                            controller.currentPosition >= controller.duration * 0.95
                    )

            if (hasMedia && reachedEnd) {
                // Sleep Timer: End of Episode — stop everything if EOE is active
                if (playerStateFlow.value.sleepAtEndOfEpisode) {
                    Log.d("PlaybackRepo", "Sleep Timer (EOE): Episode ended, stopping playback.")
                    mediaHandle.controller?.stop()
                    mediaHandle.controller?.clearMediaItems()
                    cancelSleepTimer()
                    stopProgressTicker()
                    playerStateFlow.value =
                        playerStateFlow.value.copy(
                            isPlaying = false,
                            position = 0,
                            sleepTimerEnd = null,
                            sleepAtEndOfEpisode = false,
                        )
                    return
                }

                playerStateFlow.value = playerStateFlow.value.copy(isPlaying = false, position = 0)
                stopProgressTicker()
                // Natural completion persistence is service-owned. The history
                // observer mirrors the resulting DB state back into PlayerState.
            } else {
                Log.d(
                    "PlaybackRepo",
                    "Playback ended but not naturally completed (hasMedia=$hasMedia, reachedEnd=$reachedEnd). Skipping completion marking.",
                )
                playerStateFlow.value = playerStateFlow.value.copy(isPlaying = false)
                stopProgressTicker()
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        Log.e("PlaybackRepo", "Player Error: ${error.message}", error)
        val controller = mediaHandle.controller ?: return
        val queue = playerStateFlow.value.queue
        val failedEpisode = playerStateFlow.value.currentEpisode

        // Try to skip the bad item instead of nuking the entire queue
        val currentIndex = controller.currentMediaItemIndex
        val hasNext = currentIndex < controller.mediaItemCount - 1
        val action = errorSkipGuard.onError(hasNext = hasNext, playWhenReady = controller.playWhenReady)

        when (action) {
            PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY, PlaybackErrorSkipGuard.Action.SKIP_PAUSED -> {
                Log.d("PlaybackRepo", "onPlayerError: Skipping failed item '${failedEpisode?.title}', advancing to next ($action)")
                controller.removeMediaItem(currentIndex)
                val newQueue = queue.filterNot { it.id == failedEpisode?.id }
                val autoPlay = action == PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY
                playerStateFlow.value =
                    playerStateFlow.value.copy(
                        queue = newQueue,
                        isLoading = autoPlay,
                        isPlaying = autoPlay && playerStateFlow.value.isPlaying,
                    )
                controller.prepare()
                // Never resume after the listener paused; play() here used to override pause.
                if (autoPlay) controller.play()
            }
            PlaybackErrorSkipGuard.Action.STOP -> {
                Log.w("PlaybackRepo", "onPlayerError: ${errorSkipGuard.consecutiveFailures} failures in a row, stopping auto-skip")
                controller.pause()
                stopProgressTicker()
                playerStateFlow.value = playerStateFlow.value.copy(isPlaying = false, isLoading = false)
            }
            PlaybackErrorSkipGuard.Action.CLEAR_QUEUE -> {
                // No more items — clear everything
                Log.d("PlaybackRepo", "onPlayerError: No more items in queue, clearing.")
                controller.stop()
                controller.clearMediaItems()
                playerStateFlow.value =
                    playerStateFlow.value.copy(
                        isPlaying = false,
                        isLoading = false,
                        currentEpisode = null,
                        queue = emptyList(),
                    )
            }
        }

        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val message = PlaybackErrorMessages.forAction(action, isRemote = playerStateFlow.value.playbackRoute.isRemote)
                android.widget.Toast
                    .makeText(
                        context,
                        message,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (mediaItem != null && pendingCastTransition?.mediaId != mediaItem.mediaId) {
            pendingCastTransition = null
        }
        // Use mediaId to find episode — more reliable than index
        val episodeId = mediaItem?.mediaId?.let(PlaybackMediaIdPolicy::stripMediaIdPrefixes) ?: return
        val queue = playerStateFlow.value.queue
        val oldState = playerStateFlow.value

        val slotIndex = queue.indexOfFirst { it.id == episodeId }
        android.util.Log.d(
            "PlaybackRepo",
            "onMediaItemTransition: mediaId=${mediaItem.mediaId}, stripped=$episodeId, slotIndex=$slotIndex, queueSize=${queue.size}, reason=$reason",
        )

        // Sleep Timer: End of Episode — intercept auto-advance
        if (oldState.sleepAtEndOfEpisode && reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            Log.d("PlaybackRepo", "Sleep Timer (EOE): Auto-advance intercepted. Pausing playback.")
            mediaHandle.controller?.pause()
            cancelSleepTimer()
            stopProgressTicker()
            playerStateFlow.value =
                playerStateFlow.value.copy(
                    isPlaying = false,
                    sleepTimerEnd = null,
                    sleepAtEndOfEpisode = false,
                )
            // Completion persistence and timer-holder clearing are service-owned.
            // The history observer mirrors completion into this repository.
            return
        }

        // Launch a single coroutine to handle the transition logic sequentially
        // This ensures DB updates finish BEFORE we trigger SmartQueue refill
        scope.launch {
            val finalQueue: List<Episode>
            val finalSlotIndex: Int

            if (slotIndex == -1) {
                android.util.Log.w(
                    "PlaybackRepo",
                    "onMediaItemTransition: Episode $episodeId NOT found in local queue. Attempting recovery from DB...",
                )
                val dbQueue = queueRepository.getQueueEpisodeSnapshot()
                val dbSlotIndex = dbQueue.indexOfFirst { it.id == episodeId }
                android.util.Log.d(
                    "PlaybackRepo",
                    "onMediaItemTransition (Recovery): dbSlotIndex=$dbSlotIndex, dbQueueSize=${dbQueue.size}",
                )

                val resolvedEpisode =
                    if (dbSlotIndex != -1) {
                        dbQueue[dbSlotIndex]
                    } else {
                        val metadata = mediaItem.mediaMetadata
                        val title = CastMediaMetadata.queueTitle(metadata.title)
                        if (title == null) {
                            android.util.Log.w(
                                "PlaybackRepo",
                                "Deferring transition for $episodeId until Cast metadata is available",
                            )
                            if (mediaHandle.controller?.currentMediaItem?.mediaId == mediaItem.mediaId) {
                                pendingCastTransition =
                                    PendingCastTransition(
                                        mediaId = mediaItem.mediaId,
                                        reason = reason,
                                    )
                            }
                            return@launch
                        }
                        pendingCastTransition = null
                        val resolvedPodcastId = QueueShowPolicy.showId(metadata) ?: findPodcastIdForEpisode(episodeId) ?: ""
                        val resolvedPodcastTitle = QueueShowPolicy.showTitle(metadata) ?: UNKNOWN_PODCAST
                        Episode(
                            id = episodeId,
                            title = title,
                            description = "",
                            audioUrl = mediaItem.localConfiguration?.uri?.toString() ?: "",
                            imageUrl = PlaybackArtworkMetadata.savedArtworkUrl(metadata),
                            podcastImageUrl = PlaybackArtworkMetadata.savedArtworkUrl(metadata),
                            podcastTitle = resolvedPodcastTitle,
                            podcastId = resolvedPodcastId,
                            podcastGenre = metadata.genre?.toString() ?: "Podcast",
                            podcastArtist = metadata.artist?.toString()?.takeIf(String::isNotBlank) ?: resolvedPodcastTitle,
                            duration = 0,
                            publishedDate = 0L,
                        )
                    }

                if (dbSlotIndex != -1) {
                    finalQueue = dbQueue
                    finalSlotIndex = dbSlotIndex
                } else {
                    val mutable = queue.toMutableList()
                    if (mutable.none { it.id == episodeId }) {
                        mutable.add(resolvedEpisode)
                    }
                    finalQueue = mutable.toList()
                    finalSlotIndex = finalQueue.indexOfFirst { it.id == episodeId }.coerceAtLeast(0)
                }
            } else {
                finalQueue = queue
                finalSlotIndex = slotIndex
            }

            pendingCastTransition = null
            var newEpisode = finalQueue[finalSlotIndex]
            // Enrich only when URLs are missing; present ones may carry server signatures
            if (newEpisode.id.startsWith("briefing_") &&
                (newEpisode.chaptersUrl == null || newEpisode.transcriptUrl == null)
            ) {
                try {
                    val parts = newEpisode.id.split("_")
                    if (parts.size >= 3) {
                        val region = parts[1]
                        val date = parts[2]
                        val audioUri = android.net.Uri.parse(newEpisode.audioUrl)
                        val version = audioUri.getQueryParameter("v")
                        val mappedRegion = mapRegionForBriefing(region)
                        newEpisode =
                            newEpisode.copy(
                                chaptersUrl = BriefingLinks.chaptersUrl(mappedRegion, date, version),
                                transcriptUrl = BriefingLinks.transcriptUrl(mappedRegion, date, version),
                            )
                        android.util.Log.d(
                            "PlaybackRepo",
                            "onMediaItemTransition: Enriched briefing episode ${newEpisode.id} with chaptersUrl=${newEpisode.chaptersUrl}",
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackRepo", "Failed to enrich transition briefing episode ${newEpisode.id}", e)
                }
            }
            android.util.Log.d("PlaybackRepo", "Media transition to: ${newEpisode.title}")

            // 1. Mark PREVIOUS episode as completed (if distinct and transition reason permits)
            val previousEpisode = oldState.currentEpisode
            val previousPodcast = oldState.currentPodcast
            val isServiceOwnedNaturalAdvance =
                previousEpisode?.id ==
                    PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId
            if (isServiceOwnedNaturalAdvance) {
                PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId = null
            }

            val shouldMarkCompleted =
                reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
                    currentSkipBehavior() == "mark_completed_skip" &&
                    !isServiceOwnedNaturalAdvance

            if (previousEpisode != null && previousEpisode.id != newEpisode.id && shouldMarkCompleted) {
                android.util.Log.d(
                    "PlaybackRepo",
                    "Transition (ShouldMarkCompleted): Marking previous episode as COMPLETED: ${previousEpisode.title} (ID: ${previousEpisode.id}), reason: $reason, skipBehavior: $currentSkipBehavior()",
                )
                markEpisodeAsCompleted(previousEpisode, previousPodcast)
            }

            // 2. The episode's own show (its ID, else the one its media item carries), never a
            // different previous show; Room supplies the full show when it has it.
            val showId = newEpisode.podcastId?.takeIf(String::isNotBlank) ?: QueueShowPolicy.showId(mediaItem.mediaMetadata)
            val newPodcast =
                QueueShowPolicy.currentShow(
                    episode = newEpisode.copy(podcastId = showId),
                    previous = oldState.currentPodcast,
                    stored = showId?.let { findStoredPodcast(it) },
                    queueShow = oldState.queueShow,
                )
            // An entry whose show is known keeps it, in the saved queue too, for a restart.
            val withOwnShow: (Episode) -> Episode = { episode ->
                if (showId != null && newPodcast != null) QueueShowPolicy.withShow(episode.copy(podcastId = showId), newPodcast) else episode
            }
            newEpisode = withOwnShow(newEpisode)

            // A later transition owns the state once the player moved on while the lookups above ran.
            val liveMediaId = mediaHandle.controller?.currentMediaItem?.mediaId
            if (liveMediaId != null && liveMediaId != mediaItem.mediaId) {
                Log.d("PlaybackRepo", "onMediaItemTransition: ${mediaItem.mediaId} is no longer current ($liveMediaId); skipping")
                return@launch
            }

            // 3. QUEUE CONSUMPTION: Drop items before current
            val consumedQueue = finalQueue.drop(finalSlotIndex)
            val newQueue = listOf(withOwnShow(consumedQueue.first())) + consumedQueue.drop(1)
            android.util.Log.d("PlaybackRepo", "Consuming queue: Dropped $finalSlotIndex items. New size: ${newQueue.size}")
            playerStateFlow.value =
                playerStateFlow.value.copy(
                    currentEpisode = newEpisode,
                    currentPodcast = newPodcast,
                    queue = newQueue,
                )

            // 4. Sync queue to DB for restart recovery
            syncQueueToDb()

            // Resume/intro seeking is owned exclusively by BoxLorePlaybackService.
            // A controller transition must never apply a second seek.

            // NOTE: auto-refill is owned exclusively by BoxLorePlaybackService's
            // transition listener (single guarded path, works with UI closed).
            // Items it appends are picked up by onTimelineChanged below.
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        val pending = pendingCastTransition ?: return
        val currentItem = mediaHandle.controller?.currentMediaItem ?: return
        if (
            !DeferredCastTransitionPolicy.shouldRetry(
                pendingMediaId = pending.mediaId,
                currentMediaId = currentItem.mediaId,
                title = mediaMetadata.title,
            )
        ) {
            return
        }
        pendingCastTransition = null
        onMediaItemTransition(
            mediaItem = currentItem.buildUpon().setMediaMetadata(mediaMetadata).build(),
            reason = pending.reason,
        )
    }

    override fun onTimelineChanged(
        timeline: androidx.media3.common.Timeline,
        reason: Int,
    ) {
        // The playback service (auto-refill, Android Auto) can append items
        // directly to the player. Reconcile the in-memory queue whenever the
        // playlist changes so the UI stays in sync.
        if (reason == androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            reconcileQueueWithController()
        }
    }
}

private fun PodcastEntity.toTransitionPodcast(): Podcast = Podcast(
    id = podcastId,
    title = title,
    artist = author,
    imageUrl = imageUrl,
    fallbackImageUrl = latestEpisode?.imageUrl ?: "",
    description = description,
    genre = genre ?: "Podcast",
    type = type,
    latestEpisode = latestEpisode,
    subscribedAt = subscribedAt,
    podcastGuid = podcastGuid,
    fundingUrl = fundingUrl,
    fundingMessage = fundingMessage,
    medium = medium,
    hasValue = hasValue,
    updateFrequency = updateFrequency,
    location = location,
    license = license,
    isLocked = isLocked,
    preferredSort = preferredSort,
)

private data class PendingCastTransition(
    val mediaId: String,
    val reason: Int,
)

internal object DeferredCastTransitionPolicy {
    fun shouldRetry(
        pendingMediaId: String,
        currentMediaId: String,
        title: CharSequence?,
    ): Boolean = pendingMediaId == currentMediaId && CastMediaMetadata.queueTitle(title) != null
}
