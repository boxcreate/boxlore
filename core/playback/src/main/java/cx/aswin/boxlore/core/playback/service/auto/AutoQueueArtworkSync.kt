package cx.aswin.boxlore.core.playback.service.auto

import android.util.Log
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.launch

/**
 * While a car is connected, keeps every episode in the player's queue showing its cover there.
 *
 * Episodes queued from the phone, and the ones Smart Queue adds, carry a web address for their
 * cover. The car's Queue screen can't load those, so only the playing episode had a picture. This
 * swaps each such address for a link the car can open, when the car connects and whenever the
 * queue changes after that.
 */
internal class AutoQueueArtworkSync(private val host: AutoBrowseLibraryHost) : Player.Listener {
    private var player: Player? = null
    private var scheduled = false

    /** Starts looking after [player]'s queue (main thread). */
    fun start(player: Player) {
        if (this.player === player) return
        stop()
        this.player = player
        player.addListener(this)
        schedule()
    }

    fun stop() {
        player?.removeListener(this)
        player = null
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) schedule()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = schedule()

    /** Runs after the player has finished the change it is reporting; a burst of changes is one pass. */
    private fun schedule() {
        if (scheduled) return
        scheduled = true
        host.serviceScope.launch {
            scheduled = false
            player?.let(::refresh)
        }
    }

    /**
     * Swaps the cover link of each queued episode the car can't load, a stretch of neighbours at a
     * time. Nothing happens once every episode has one, so the change this makes to the queue
     * doesn't set it off again.
     *
     * The playing episode is left alone: replacing it could interrupt it, and the car already shows
     * its cover. It is swapped once playback moves on.
     */
    internal fun refresh(player: Player) {
        try {
            // A cast receiver loads covers from their web address.
            if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return
            val playing = player.currentMediaItemIndex
            val count = player.mediaItemCount
            val stretch = mutableListOf<MediaItem>()
            for (index in 0..count) {
                val item = if (index < count && index != playing) player.getMediaItemAt(index) else null
                val swapped = item?.let { AutoItemArtwork.forCar(host.asContext(), it) }
                if (swapped != null && swapped !== item) {
                    stretch += swapped
                } else if (stretch.isNotEmpty()) {
                    player.replaceMediaItems(index - stretch.size, index, stretch.toList())
                    stretch.clear()
                }
            }
        } catch (error: Exception) {
            // The queue still plays; its covers are only missing in the car.
            Log.w("AutoBrowse", "Unable to give the car's queue its covers", error)
        }
    }
}
