package cx.aswin.boxlore.core.playback

/**
 * Queue rules for an episode stored under several IDs (docs/EPISODE_IDS.md §5): the queue holds
 * the episode once, and removing it removes every ID it is queued under. Pure, so the rules are
 * unit-tested without a player.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, queue removals across aliases).
 * COMPAT(own-ids): one queue entry per episode across its IDs. Permanent: never remove (MIGRATION.md §5).
 */
internal object QueueAliasPolicy {
    /** The first media index holding any of [aliases] (media ID prefixes ignored), or -1. */
    fun indexOfAny(mediaIds: List<String>, aliases: Collection<String>): Int =
        mediaIds.indexOfFirst { QueueMath.stripMediaIdPrefixes(it) in aliases }

    /**
     * Media indices that still hold the episode under an ID other than [removedId], highest first
     * so they can be removed one by one. The playing item ([currentIndex]) is never included.
     */
    fun otherAliasIndices(mediaIds: List<String>, aliases: Collection<String>, removedId: String, currentIndex: Int,): List<Int> =
        mediaIds.indices
            .filter { index ->
                val id = QueueMath.stripMediaIdPrefixes(mediaIds[index])
                index != currentIndex && id != removedId && id in aliases
            }.sortedDescending()

    /** [queue] without [removedId] and without the episode's other IDs, keeping the playing one. */
    fun <T> withoutEpisode(queue: List<T>, removedId: String, aliases: Collection<String>, playingId: String?, idOf: (T) -> String,): List<T> =
        queue.filter { item ->
            val id = idOf(item)
            id != removedId && (id !in aliases || id == playingId)
        }
}
