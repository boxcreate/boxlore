package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.EpisodeListenerStore

/**
 * Whether a newer listening session pulled from the cloud may replace the idle mini-player's
 * episode (`PlaybackRepository.updateIdlePlaybackSession` asks [shouldHandOver]). It never replaces
 * the same episode: not under the same ID, and not under another stored ID of that episode
 * (docs/EPISODE_IDS.md §5), which cloud sync hands over as the episode's owner ID while the player
 * may hold another one. Another episode replaces it only when the pulled session is newer than the
 * player's episode's own last play.
 *
 * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, cloud sync identity).
 * COMPAT(own-ids): the mini-player handoff treats an episode's IDs as one. Permanent: never remove (MIGRATION.md §5).
 */
internal object IdleSessionHandoff {
    /** True when [currentId], the idle player's episode, is [pulledId]'s episode under any of its IDs. */
    suspend fun isSameEpisode(currentId: String?, pulledId: String, listenerStore: EpisodeListenerStore,): Boolean =
        currentId != null && (currentId == pulledId || currentId in listenerStore.memberIds(pulledId))

    /**
     * True when the idle player, holding [currentId] (null when it holds nothing), takes the pulled
     * session of [pulledId] last played at [pulledLastPlayedAt]: never for the same episode
     * ([isSameEpisode]), else only when the pulled session is newer than the projected last play of
     * the player's episode.
     */
    suspend fun shouldHandOver(currentId: String?, pulledId: String, pulledLastPlayedAt: Long, listenerStore: EpisodeListenerStore,): Boolean {
        if (isSameEpisode(currentId, pulledId, listenerStore)) return false
        val currentLastPlayed = currentId?.let { listenerStore.projectedHistory(it)?.lastPlayedAt } ?: 0L
        return pulledLastPlayedAt > currentLastPlayed
    }
}
