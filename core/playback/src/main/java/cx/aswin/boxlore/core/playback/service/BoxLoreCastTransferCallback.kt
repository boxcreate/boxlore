package cx.aswin.boxlore.core.playback.service

import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState

/**
 * Backports Media3's null-URI filtering for Cast transfers.
 *
 * Cast can briefly expose placeholder queue rows that cannot be loaded by ExoPlayer. Passing
 * those rows through the default transfer callback crashes during remote-to-local teardown.
 */
internal class BoxLoreCastTransferCallback : CastPlayer.TransferCallback {
    override fun transferState(
        sourcePlayer: Player,
        targetPlayer: Player,
    ) {
        var transferState = PlayerTransferState.fromPlayer(sourcePlayer)
        val sanitized =
            CastTransferPolicy.sanitize(
                mediaItems = transferState.mediaItems,
                currentIndex = transferState.currentMediaItemIndex,
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

internal data class SanitizedCastTransfer(
    val mediaItems: List<MediaItem>,
    val currentIndex: Int,
    val resetPosition: Boolean,
)

internal object CastTransferPolicy {
    fun sanitize(
        mediaItems: List<MediaItem>,
        currentIndex: Int,
    ): SanitizedCastTransfer {
        val playableItems = ArrayList<MediaItem>(mediaItems.size)
        var adjustedIndex = currentIndex
        var currentItemWasFiltered = false

        mediaItems.forEachIndexed { index, mediaItem ->
            when {
                isPlayable(mediaItem) -> playableItems += mediaItem
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

    private fun isPlayable(mediaItem: MediaItem): Boolean {
        val uri = mediaItem.localConfiguration?.uri
        return uri != null && uri != Uri.EMPTY
    }
}
