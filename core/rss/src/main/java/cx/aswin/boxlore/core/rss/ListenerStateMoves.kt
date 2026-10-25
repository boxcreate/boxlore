package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeListenerProjection
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity

/**
 * Rules for the two paths that still move listener state from one stored episode ID to another:
 * the orphan rematch after a feed read ([LocalCatalogListenerStateReconciler]) and the switch of a
 * Podcast Index show to its RSS feed (`RssPodcastRepository.confirmPodcastIndexLink`).
 * docs/EPISODE_IDS.md §5: nothing changes ID except these, they consult aliases first, and a
 * merged like is the latest like decision rather than either row's like.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed).
 * COMPAT(own-ids): keeps the remaining state moves alias-aware. Permanent: never remove (MIGRATION.md §5).
 */
internal object ListenerStateMoves {
    /** IDs whose alias is trusted (they already resolve) or ambiguous (never moved): the orphan rematch leaves them alone. */
    fun settledIds(aliases: Collection<EpisodeAliasEntity>): Set<String> =
        aliases.filter { it.state != EpisodeAliasState.UNRESOLVED }.mapTo(HashSet()) { it.aliasId }

    /** IDs whose alias is ambiguous: their state never moves to another ID. */
    fun ambiguousIds(aliases: Collection<EpisodeAliasEntity>): Set<String> =
        aliases.filter { it.state == EpisodeAliasState.AMBIGUOUS }.mapTo(HashSet()) { it.aliasId }

    /** [alias] when it is a trusted, keyed alias of [podcastId]'s episode; null otherwise. */
    fun trustedTarget(alias: EpisodeAliasEntity?, podcastId: String): EpisodeAliasEntity? =
        alias?.takeIf { it.state == EpisodeAliasState.TRUSTED && it.catalogKey != null && it.podcastId == podcastId }

    /**
     * The alias recording that [oldId]'s state moved onto [target]'s episode: trusted, never the
     * owner (the catalog row now holds the state). Null without a target alias.
     */
    fun movedAlias(oldId: String, target: EpisodeAliasEntity?, nowMs: Long,): EpisodeAliasEntity? {
        target ?: return null
        return EpisodeAliasEntity(
            aliasId = oldId,
            podcastId = target.podcastId,
            catalogKey = target.catalogKey,
            recipeId = target.recipeId,
            state = EpisodeAliasState.TRUSTED,
            owner = false,
            provenance = EpisodeAliasProvenance.ORPHAN_REMATCH,
            createdAt = nowMs,
        )
    }

    /**
     * [old] (a Podcast Index show's history row) moved onto [rssEpisode] of the RSS show the
     * listener switched to, merged with that episode's [existing] row. Progress, length and play time
     * keep the larger value and completion is kept from either row; the like is the latest like decision.
     */
    fun linkedHistoryRow(
        old: ListeningHistoryEntity,
        existing: ListeningHistoryEntity?,
        rssEpisode: RssEpisodeEntity,
        rssPodcast: PodcastEntity,
    ): ListeningHistoryEntity {
        val like = EpisodeListenerProjection.likeDecision(listOfNotNull(old, existing), null)
        return old.copy(
            episodeId = rssEpisode.episodeId,
            podcastId = rssPodcast.podcastId,
            episodeTitle = rssEpisode.title,
            episodeImageUrl = rssEpisode.imageUrl ?: old.episodeImageUrl,
            podcastImageUrl = rssPodcast.imageUrl,
            episodeAudioUrl = rssEpisode.audioUrl,
            podcastName = rssPodcast.title,
            progressMs = maxOf(old.progressMs, existing?.progressMs ?: 0L),
            durationMs = maxOf(old.durationMs, existing?.durationMs ?: 0L),
            isCompleted = old.isCompleted || existing?.isCompleted == true,
            isLiked = like.value,
            likedAt = maxOf(old.likedAt, existing?.likedAt ?: 0L, like.at),
            lastPlayedAt = maxOf(old.lastPlayedAt, existing?.lastPlayedAt ?: 0L),
            enclosureType = rssEpisode.enclosureType ?: old.enclosureType,
        )
    }
}
