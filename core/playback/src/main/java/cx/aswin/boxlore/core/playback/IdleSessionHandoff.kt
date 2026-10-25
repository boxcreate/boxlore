package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.EpisodeListenerStore

/**
 * Whether a newer listening session pulled from the cloud may replace the idle mini-player's
 * episode (`PlaybackRepository.updateIdlePlaybackSession`). It never replaces the same episode:
 * not under the same ID, and not under another stored ID of that episode (docs/EPISODE_IDS.md §5),
 * which cloud sync hands over as the episode's owner ID while the player may hold another one.
 *
 * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, cloud sync identity).
 * COMPAT(own-ids): the mini-player handoff treats an episode's IDs as one. Permanent: never remove (MIGRATION.md §5).
 */
internal object IdleSessionHandoff {
    /** True when [currentId], the idle player's episode, is [pulledId]'s episode under any of its IDs. */
    suspend fun isSameEpisode(currentId: String?, pulledId: String, listenerStore: EpisodeListenerStore,): Boolean =
        currentId != null && (currentId == pulledId || currentId in listenerStore.memberIds(pulledId))
}
