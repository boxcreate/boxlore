package cx.aswin.boxlore.core.playback.service.auto

import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import kotlinx.coroutines.guava.future

/**
 * Coordinates playback resumption requests from Android Auto or media button events.
 * Bridges [ListeningHistoryDao], [QueueRepository], SharedPreferences (`boxlore_player`),
 * and [AutoMediaResolver].
 */
@OptIn(UnstableApi::class)
internal class AutoPlaybackResumptionHandler(
    private val host: AutoBrowseLibraryHost,
    private val mediaResolver: AutoMediaResolver,
    customPrefs: SharedPreferences? = null,
    customListeningHistoryDao: ListeningHistoryDao? = null,
    customQueueRepository: QueueRepository? = null,
) {
    private val prefs by lazy {
        customPrefs ?: PrefsFileMigrator.open(
            host.asContext(),
            newName = PrefsFileMigrator.Files.PLAYER,
            oldName = PrefsFileMigrator.LegacyFiles.PLAYER,
        )
    }
    private val listeningHistoryDao by lazy {
        customListeningHistoryDao ?: host.database.listeningHistoryDao()
    }
    private val queueRepository by lazy {
        customQueueRepository ?: host.queueRepository
    }
    companion object {
        const val KEY_PLAYER_DISMISSED = "player_dismissed"
        private const val TAG = "AutoResumption"
    }

    fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isStartedFromMediaNotification: Boolean = false,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        android.util.Log.d(
            TAG,
            "onPlaybackResumption called from ${controller.packageName}, notification=$isStartedFromMediaNotification",
        )
        return host.serviceScope.future {
            resolveResumption(mediaSession)
        }
    }

    @VisibleForTesting
    internal suspend fun resolveResumption(
        mediaSession: MediaSession,
    ): MediaSession.MediaItemsWithStartPosition {
        val player = mediaSession.player
        val liveItemCount = player.mediaItemCount
        if (liveItemCount > 0) {
            prefs.edit().putBoolean(KEY_PLAYER_DISMISSED, false).apply()
            return resolveLivePlayerResumption(player, liveItemCount)
        }

        val isPlayerDismissed = prefs.getBoolean(KEY_PLAYER_DISMISSED, false)
        val candidate = resolveCandidate(isPlayerDismissed)

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = false,
            isPlayerDismissed = isPlayerDismissed,
            candidate = candidate,
        )

        if (!decision.shouldResume || decision.targetEpisodeId.isNullOrBlank()) {
            android.util.Log.d(TAG, "No resumption possible: case=${decision.case}")
            throw UnsupportedOperationException("No playback resumption items available")
        }

        val targetEpisodeId = decision.targetEpisodeId
        val (resolvedMediaItems, startIndex) = resolvePlaylistForTarget(targetEpisodeId)

        // Reset dismissed flag since user explicitly resumed playback
        prefs.edit().putBoolean(KEY_PLAYER_DISMISSED, false).apply()

        val startPositionMs = decision.startPositionMs.coerceAtLeast(0L)
        android.util.Log.d(
            TAG,
            "Resumption prepared: case=${decision.case}, target=$targetEpisodeId, index=$startIndex, pos=$startPositionMs, count=${resolvedMediaItems.size}",
        )

        return MediaSession.MediaItemsWithStartPosition(
            resolvedMediaItems,
            startIndex,
            startPositionMs,
        )
    }

    private fun resolveLivePlayerResumption(
        player: androidx.media3.common.Player,
        liveItemCount: Int,
    ): MediaSession.MediaItemsWithStartPosition {
        val liveIndex = player.currentMediaItemIndex.coerceIn(0, liveItemCount - 1)
        val livePositionMs = player.currentPosition.coerceAtLeast(0L)
        val currentItems = (0 until liveItemCount).map { player.getMediaItemAt(it) }
        val liveEpisodeId = player.currentMediaItem?.mediaId?.stripEpisodePrefix()

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = true,
            isPlayerDismissed = false,
            candidate = null,
            liveEpisodeId = liveEpisodeId,
            livePositionMs = livePositionMs,
        )
        android.util.Log.d(
            TAG,
            "Resuming live player: case=${decision.case}, index=$liveIndex, pos=$livePositionMs",
        )
        return MediaSession.MediaItemsWithStartPosition(
            currentItems,
            liveIndex,
            livePositionMs,
        )
    }

    private suspend fun resolveCandidate(isPlayerDismissed: Boolean): AutoResumptionCandidate? {
        val candidateEntity = if (!isPlayerDismissed) {
            listeningHistoryDao.getLastPlayedSessionAny()
        } else {
            listeningHistoryDao.getLastPlayedSession()
        }

        if (candidateEntity != null) {
            return AutoResumptionCandidate.from(candidateEntity)
        }

        val firstQueue = queueRepository.getQueueSnapshot().firstOrNull()
        return firstQueue?.let {
            AutoResumptionCandidate(
                episodeId = it.id,
                progressMs = 0L,
                durationMs = it.duration.toLong() * 1_000L,
                isCompleted = false,
            )
        }
    }

    private suspend fun resolvePlaylistForTarget(targetEpisodeId: String): Pair<List<MediaItem>, Int> {
        val cleanTargetId = targetEpisodeId.stripEpisodePrefix()
        val savedQueue = queueRepository.getQueueSnapshot()
        val targetEpisode = mediaResolver.resolveDomainEpisode(cleanTargetId)

        val aligned = if (targetEpisode != null) {
            AutoPlaybackResumptionPolicy.alignQueue(
                targetItem = targetEpisode,
                queue = savedQueue,
                idSelector = { it.id },
            )
        } else {
            val existingIndex = savedQueue.indexOfFirst { it.id.stripEpisodePrefix() == cleanTargetId }
            if (existingIndex >= 0) {
                AlignedQueue(items = savedQueue, startIndex = existingIndex)
            } else {
                AlignedQueue(items = emptyList(), startIndex = 0)
            }
        }

        val resolvedMediaItems = if (aligned.items.isNotEmpty()) {
            aligned.items.map { episode ->
                val ungrounded = AutoMediaItemFactory.fromEpisode(
                    episode = episode,
                    source = AutoBrowseContract.SOURCE_QUEUE,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        host.asContext(),
                        episode.imageUrl ?: episode.podcastImageUrl,
                    ),
                    mediaIdPrefix = AutoBrowseContract.QUEUE_PREFIX,
                )
                mediaResolver.resolveMediaItem(ungrounded)
            }
        } else {
            val singleItem = mediaResolver.resolveMediaItem(
                MediaItem.Builder().setMediaId(cleanTargetId).build(),
            )
            if (singleItem.localConfiguration?.uri != null) {
                listOf(singleItem)
            } else {
                emptyList()
            }
        }

        val startIndex = aligned.startIndex.coerceIn(0, (resolvedMediaItems.size - 1).coerceAtLeast(0))
        if (resolvedMediaItems.isEmpty() || resolvedMediaItems.getOrNull(startIndex)?.localConfiguration?.uri == null) {
            android.util.Log.w(TAG, "Failed to resolve media items with playable URI for target $cleanTargetId")
            throw UnsupportedOperationException("No playable items resolved for resumption")
        }

        if (aligned.items.isNotEmpty() && savedQueue.none { it.id.stripEpisodePrefix() == cleanTargetId }) {
            queueRepository.replaceQueue(aligned.items)
        }

        return Pair(resolvedMediaItems, startIndex)
    }
}
