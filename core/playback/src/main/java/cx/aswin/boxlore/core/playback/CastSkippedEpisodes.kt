package cx.aswin.boxlore.core.playback

import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cx.aswin.boxlore.core.model.Episode

/**
 * The saved queue's episodes a Cast receiver can't fetch. The service keeps them out of the Cast
 * queue (`service.CastTransferPolicy`, `service.CastQueueGuardPlayer`); the saved queue keeps them
 * while casting, and they go back into the phone's player when playback returns to it.
 */
internal object CastSkippedEpisodes {
    /** True while [player] plays on a Cast receiver; a player that reports no device plays locally. */
    fun isCasting(player: Player): Boolean {
        val device: DeviceInfo? = player.deviceInfo
        return device?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
    }

    /** True when a Cast receiver can fetch [item]: its saved public address, else its own URI. */
    fun canCast(item: MediaItem): Boolean = CastMediaEligibility.isCastable(CastMediaMetadata.effectiveUri(item))

    /** False only while [player] casts and no receiver can fetch [startItem], the episode a new queue would start on. */
    fun canStart(player: Player, startItem: MediaItem?): Boolean = startItem == null || !isCasting(player) || canCast(startItem)

    /** The episodes of [local] missing from the Cast queue [remoteIds] because no receiver can fetch them. */
    fun skipped(local: List<Episode>, remoteIds: List<String>): List<Episode> {
        val onCast = remoteIds.toHashSet()
        return local.filter { it.id !in onCast && !CastMediaEligibility.isCastable(it.audioUrl) }
    }

    /**
     * [remote] with each of [skipped] back after the episode it followed in [local]. One that led
     * [local] goes right after the playing episode, the first of [remote].
     */
    fun withSkipped(remote: List<Episode>, local: List<Episode>, skipped: List<Episode>): List<Episode> {
        if (skipped.isEmpty()) return remote
        val skippedIds = skipped.mapTo(HashSet()) { it.id }
        val merged = remote.toMutableList()
        var anchor = if (merged.isEmpty()) -1 else 0
        for (episode in local) {
            if (episode.id in skippedIds) {
                anchor++
                merged.add(anchor, episode)
            } else {
                val index = merged.indexOfFirst { it.id == episode.id }
                if (index >= 0) anchor = index
            }
        }
        return merged
    }

    /**
     * Where each episode of [local] named in [missing] goes back into a player holding [playerIds],
     * playing the one at [currentIndex]: after the nearest earlier episode of [local] the player
     * holds from [currentIndex] on, else right after the playing one. Apply the indices in order.
     */
    fun insertions(local: List<Episode>, playerIds: List<String>, currentIndex: Int, missing: Set<String>): List<Pair<Int, Episode>> {
        val ids = playerIds.toMutableList()
        val from = currentIndex.coerceIn(0, ids.size)
        var anchor = currentIndex.coerceIn(-1, ids.lastIndex)
        val plan = mutableListOf<Pair<Int, Episode>>()
        for (episode in local) {
            val index = ids.subList(from, ids.size).indexOf(episode.id).let { if (it < 0) it else from + it }
            when {
                index >= 0 -> anchor = index
                episode.id in missing -> {
                    anchor++
                    ids.add(anchor, episode.id)
                    plan += anchor to episode
                }
            }
        }
        return plan
    }
}
