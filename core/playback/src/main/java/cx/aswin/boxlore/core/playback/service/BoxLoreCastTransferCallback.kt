package cx.aswin.boxlore.core.playback.service

import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import cx.aswin.boxlore.core.playback.BoxLoreCastMediaItemConverter
import cx.aswin.boxlore.core.playback.CastMediaEligibility
import cx.aswin.boxlore.core.playback.CastMediaMetadata

/**
 * Backports Media3's null-URI filtering for Cast transfers.
 *
 * Cast can briefly expose placeholder queue rows that cannot be loaded by ExoPlayer. Passing
 * those rows through the default transfer callback crashes during remote-to-local teardown.
 * In the other direction, the phone's queue can hold episodes a Cast receiver cannot fetch
 * (a download with no public address, a feed on a private host); those stay out of the Cast queue.
 */
internal class BoxLoreCastTransferCallback : CastPlayer.TransferCallback {
    override fun transferState(sourcePlayer: Player, targetPlayer: Player,) {
        var transferState = PlayerTransferState.fromPlayer(sourcePlayer)
        val sanitized =
            CastTransferPolicy.sanitize(
                mediaItems = transferState.mediaItems,
                currentIndex = transferState.currentMediaItemIndex,
                targetIsRemote = CastQueueGuard.isRemote(targetPlayer),
            )

        if (sanitized.mediaItems.size < transferState.mediaItems.size) {
            val builder =
                transferState
                    .buildUpon()
                    .setMediaItems(sanitized.mediaItems)
                    .setCurrentMediaItemIndex(sanitized.currentIndex)
            if (sanitized.resetPosition) {
                builder.setCurrentPosition(0L)
            }
            transferState = builder.build()
        }
        transferState.setToPlayer(targetPlayer)
    }
}

internal data class SanitizedCastTransfer(val mediaItems: List<MediaItem>, val currentIndex: Int, val resetPosition: Boolean,)

internal object CastTransferPolicy {
    /**
     * Drops the rows the target cannot play and keeps the current row selected.
     *
     * [targetIsRemote] is true when the queue is moving to a Cast receiver, which needs a public
     * HTTP(S) address for every row; the phone only needs a URI.
     */
    fun sanitize(mediaItems: List<MediaItem>, currentIndex: Int, targetIsRemote: Boolean = false,): SanitizedCastTransfer {
        val canPlayOnTarget: (MediaItem) -> Boolean = if (targetIsRemote) ::isCastable else ::isPlayable
        val playableItems = ArrayList<MediaItem>(mediaItems.size)
        var adjustedIndex = currentIndex
        var currentItemWasFiltered = false

        mediaItems.forEachIndexed { index, mediaItem ->
            when {
                canPlayOnTarget(mediaItem) -> playableItems += mediaItem
                index < currentIndex -> adjustedIndex--
                index == currentIndex -> currentItemWasFiltered = true
            }
        }

        adjustedIndex =
            when {
                playableItems.isEmpty() -> C.INDEX_UNSET
                else -> adjustedIndex.coerceIn(0, playableItems.lastIndex)
            }
        return SanitizedCastTransfer(
            mediaItems = playableItems,
            currentIndex = adjustedIndex,
            resetPosition = currentItemWasFiltered,
        )
    }

    fun isCastable(mediaItem: MediaItem): Boolean = !isPlaceholder(mediaItem) && CastMediaEligibility.isCastable(CastMediaMetadata.effectiveUri(mediaItem))

    private fun isPlayable(mediaItem: MediaItem): Boolean {
        val uri = mediaItem.localConfiguration?.uri
        return uri != null && uri != Uri.EMPTY && !isPlaceholder(mediaItem)
    }

    private fun isPlaceholder(mediaItem: MediaItem): Boolean = mediaItem.mediaId.startsWith(BoxLoreCastMediaItemConverter.PLACEHOLDER_ID_PREFIX)
}

/**
 * Keeps episodes a Cast receiver cannot fetch out of the Cast queue while casting. The Cast
 * converter rejects them, which would otherwise crash the playback service.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object CastQueueGuard {
    fun isRemote(player: Player): Boolean = player is RemoteCastPlayer || player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

    /** [mediaItems] unchanged for local playback; only the castable ones while casting. */
    fun castable(player: Player, mediaItems: List<MediaItem>,): List<MediaItem> = if (isRemote(player)) mediaItems.filter(CastTransferPolicy::isCastable) else mediaItems

    /**
     * The list and start row to load, or null when the request holds nothing Cast can stream and
     * should leave the current Cast queue alone. Local playback gets the request back unchanged.
     */
    fun startRequest(player: Player, mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long,): StartRequest? {
        if (!isRemote(player) || mediaItems.all(CastTransferPolicy::isCastable)) return StartRequest(mediaItems, startIndex, startPositionMs)
        val sanitized = CastTransferPolicy.sanitize(mediaItems, startIndex, targetIsRemote = true)
        if (sanitized.mediaItems.isEmpty()) return null
        return StartRequest(
            mediaItems = sanitized.mediaItems,
            startIndex = if (startIndex == C.INDEX_UNSET) C.INDEX_UNSET else sanitized.currentIndex,
            startPositionMs = if (sanitized.resetPosition) 0L else startPositionMs,
        )
    }

    data class StartRequest(val mediaItems: List<MediaItem>, val startIndex: Int, val startPositionMs: Long,)
}

/**
 * A session player that applies [CastQueueGuard] to every call that loads or queues episodes, so
 * playing or queueing something Cast cannot stream during a Cast session is skipped, not fatal.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal open class CastQueueGuardPlayer(private val guarded: Player) : ForwardingPlayer(guarded) {
    override fun setMediaItem(mediaItem: MediaItem) = setMediaItems(listOf(mediaItem))

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long,) = setMediaItems(listOf(mediaItem), 0, startPositionMs)

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean,) = setMediaItems(listOf(mediaItem), resetPosition)

    override fun setMediaItems(mediaItems: List<MediaItem>) = setMediaItems(mediaItems, true)

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean,) {
        val castable = CastQueueGuard.castable(guarded, mediaItems)
        if (mediaItems.isNotEmpty() && castable.isEmpty()) return
        super.setMediaItems(castable, resetPosition)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long,) {
        val request = CastQueueGuard.startRequest(guarded, mediaItems, startIndex, startPositionMs) ?: return
        super.setMediaItems(request.mediaItems, request.startIndex, request.startPositionMs)
    }

    override fun addMediaItem(mediaItem: MediaItem) = addMediaItems(listOf(mediaItem))

    override fun addMediaItem(index: Int, mediaItem: MediaItem,) = addMediaItems(index, listOf(mediaItem))

    override fun addMediaItems(mediaItems: List<MediaItem>) {
        val castable = CastQueueGuard.castable(guarded, mediaItems)
        if (castable.isNotEmpty()) super.addMediaItems(castable)
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>,) {
        val castable = CastQueueGuard.castable(guarded, mediaItems)
        if (castable.isNotEmpty()) super.addMediaItems(index, castable)
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem,) = replaceMediaItems(index, index + 1, listOf(mediaItem))

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>,) {
        val castable = CastQueueGuard.castable(guarded, mediaItems)
        if (mediaItems.isNotEmpty() && castable.isEmpty()) return
        super.replaceMediaItems(fromIndex, toIndex, castable)
    }
}
