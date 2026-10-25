package cx.aswin.boxlore.core.database

/**
 * Every stored ID of one episode that may carry listener state (docs/EPISODE_IDS.md §5).
 *
 * An ID with a trusted alias belongs to the group of its (show, catalog key): every trusted ID of
 * that key, the owner first. Any other ID (no alias, or an ambiguous or unresolved one, `rss:` and
 * Brief IDs) is a group of its own with no key, which behaves exactly as listener state did before
 * aliases existed.
 *
 * @property requestedId the ID the group was looked up by
 * @property podcastId the show of the key, or null for an unkeyed group
 * @property catalogKey the episode's catalog key, or null for an unkeyed group
 * @property memberIds every trusted ID of the episode, the owner first; just [requestedId] when unkeyed
 * @property ownerId the one ID that holds progress and position
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, projected listener state).
 * COMPAT(own-ids): one listener state per episode across its IDs. Permanent: never remove (MIGRATION.md §5).
 */
data class EpisodeGroup(
    val requestedId: String,
    val podcastId: String?,
    val catalogKey: String?,
    val memberIds: List<String>,
    val ownerId: String,
) {
    /** True when the episode has an alias key, so its like and completion settings are recorded in `episode_group_state`. */
    val isKeyed: Boolean get() = podcastId != null && catalogKey != null

    /** True when more than one stored ID carries this episode's state, so reads must be projected. */
    val isShared: Boolean get() = memberIds.size > 1

    companion object {
        /** The group of an ID that has no trusted alias: the ID alone, which owns its own state. */
        fun single(episodeId: String): EpisodeGroup = EpisodeGroup(episodeId, null, null, listOf(episodeId), episodeId)

        /**
         * A key equal for every ID of one (show, catalog key) and different between episodes, used to
         * count an episode once; null without a key. A NUL never appears in an episode ID, so a token
         * can't collide with an ID used as its own token.
         */
        fun tokenOf(podcastId: String?, catalogKey: String?): String? =
            if (podcastId == null || catalogKey == null) null else "$podcastId\u0000$catalogKey"
    }
}
