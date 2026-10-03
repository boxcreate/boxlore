package cx.aswin.boxlore.core.playback.service

import android.util.Log
import androidx.media3.common.Player
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import cx.aswin.boxlore.core.playback.SleepTimerHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps terminal context playback alive while the service persists completion and refills. */
internal class ContextQueueContinuationCoordinator(
    private val scope: CoroutineScope,
    private val generation: () -> Long,
    private val awaitCompletion: suspend () -> Unit,
    private val refill: suspend (Player, () -> Boolean) -> Boolean,
    private val stop: (Player) -> Unit,
) {
    private var continuationJob: Job? = null

    fun invalidate() {
        continuationJob?.cancel()
        continuationJob = null
    }

    fun onExhausted(player: Player): Boolean {
        if (!PlaybackQueueContext.isContextItem(player.currentMediaItem) || player.hasNextMediaItem()) return false
        if (SleepTimerHolder.sleepAtEndOfEpisode || !player.playWhenReady) return false
        if (continuationJob?.isActive == true) return true
        val activation = generation()
        val item = player.currentMediaItem
        val isCurrent = {
            generation() == activation &&
                player.currentMediaItem == item &&
                player.playWhenReady &&
                !SleepTimerHolder.sleepAtEndOfEpisode
        }
        continuationJob = scope.launch { continueWhenReady(player, isCurrent) }
        return true
    }

    private suspend fun continueWhenReady(player: Player, isCurrent: () -> Boolean) {
        try {
            awaitCompletion()
            if (!isCurrent()) return
            val added = refill(player, isCurrent)
            if (added) awaitNextItem(player, isCurrent)
            if (!isCurrent()) return
            if (added && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                stop(player)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("AutoQueue", "Context continuation failed", error)
            if (isCurrent()) stop(player)
        }
    }

    private suspend fun awaitNextItem(player: Player, isCurrent: () -> Boolean) {
        // Cast insertion becomes visible only after the receiver acknowledges the batch.
        withTimeoutOrNull(5_000L) {
            while (isCurrent() && !player.hasNextMediaItem()) delay(50L)
        }
    }
}
