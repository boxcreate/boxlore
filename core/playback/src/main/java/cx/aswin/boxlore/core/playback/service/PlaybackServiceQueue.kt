package cx.aswin.boxlore.core.playback.service

import androidx.media3.cast.Cast
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaStatus
import cx.aswin.boxlore.core.playback.PlaybackActivationRequest
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun BoxLorePlaybackService.maybeRefillQueueAfterTransition(player: Player, reason: Int,) {
    tryStartSmartQueueRefill(player, logReason = "transition:$reason")
}

internal fun BoxLorePlaybackService.refillQueueAfterSmartQueueEnabled() {
    val player =
        playbackPlayer ?: run {
            android.util.Log.w("AutoQueue", "Smart queue turned on but player is missing")
            return
        }
    tryStartSmartQueueRefill(player, logReason = "smart_queue_enabled")
}

private fun BoxLorePlaybackService.tryStartSmartQueueRefill(player: Player, logReason: String,) {
    val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
    android.util.Log.d("AutoQueue", "tryStartRefill: remaining=$remaining, reason=$logReason")
    val currentItem = player.currentMediaItem
    val isLearn = currentItem?.mediaId?.startsWith(AutoBrowseContract.LEARN_PREFIX) == true
    val sleepingAtEndOfEpisode =
        cx.aswin.boxlore.core.playback.SleepTimerHolder.sleepAtEndOfEpisode

    if (
        !cx.aswin.boxlore.core.playback.SmartQueueRefillPolicy.shouldRefill(
            remainingUpcoming = remaining,
            isRefilling = isRefilling,
            mediaItemCount = player.mediaItemCount,
            isLearnEpisode = isLearn,
            sleepingAtEndOfEpisode = sleepingAtEndOfEpisode,
            isContextQueue = PlaybackQueueContext.isContextItem(currentItem),
        )
    ) {
        return
    }
    isRefilling = true
    serviceScope.launch {
        try {
            refillQueue(player)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("AutoQueue", "Refill failed ($logReason)", e)
        } finally {
            isRefilling = false
        }
    }
}

internal suspend fun BoxLorePlaybackService.refillQueueForSession(
    player: Player,
    contextExhausted: Boolean = false,
    isCurrent: () -> Boolean = { true },
): Boolean {
    val activation = queueActivationGeneration
    val transportGeneration = PlaybackActivationRequest.generation
    return smartQueueRefillCoordinator.refillQueue(player, contextExhausted) {
        activation == queueActivationGeneration && transportGeneration == PlaybackActivationRequest.generation && isCurrent()
    }
}

internal fun BoxLorePlaybackService.clearEndOfEpisodeSleep() {
    cx.aswin.boxlore.core.playback.SleepTimerHolder.activeSleepTimerEndMs = null
    cx.aswin.boxlore.core.playback.SleepTimerHolder.sleepAtEndOfEpisode = false
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun BoxLorePlaybackService.isRemoteContextNaturalEnd(player: Player): Boolean {
    if (player.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) return false
    val receiverFinished = runCatching {
        Cast.getSingletonInstance(this).currentCastSession?.remoteMediaClient?.mediaStatus?.idleReason == MediaStatus.IDLE_REASON_FINISHED
    }.getOrDefault(false)
    return shouldCompleteRemoteContext(player.playbackState, PlaybackQueueContext.isContextItem(player.currentMediaItem), player.hasNextMediaItem(), receiverFinished)
}

internal fun shouldCompleteRemoteContext(playbackState: Int, isContext: Boolean, hasNext: Boolean, receiverFinished: Boolean): Boolean =
    playbackState == Player.STATE_IDLE && isContext && !hasNext && receiverFinished
