package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.EpisodeListenerProjection
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId

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
    /** IDs whose alias is trusted (never moved; joined by key instead) or ambiguous (never moved): the orphan rematch leaves them alone. */
    fun settledIds(aliases: Collection<EpisodeAliasEntity>): Set<String> =
        aliases.filter { it.state != EpisodeAliasState.UNRESOLVED }.mapTo(HashSet()) { it.aliasId }

    /**
     * The one catalog row of [orphan]'s show that holds [orphan]'s catalog key under another ID,
     * or null. [orphan] must be a trusted, keyed alias. A legacy row's synthetic key never matches.
     */
    fun catalogRowOfKey(orphan: EpisodeAliasEntity, catalogRows: List<LocalEpisodeEntity>): LocalEpisodeEntity? {
        if (orphan.state != EpisodeAliasState.TRUSTED) return null
        val key = orphan.catalogKey ?: return null
        return catalogRows
            .filter { it.podcastId == orphan.podcastId && it.episodeId != orphan.aliasId && catalogKeyOf(it) == key }
            .singleOrNull()
    }

    /**
     * The alias that joins [row] (a catalog row holding [orphan]'s key under another ID) to
     * [orphan]'s episode: trusted, never the owner, provenance `catalog_key_match`. Null when
     * [row]'s ID already belongs to that episode, or holds a trusted alias of another key or an
     * ambiguous one, which are left as they are. An unresolved alias of the row's ID is resolved by it.
     */
    fun joinedCatalogAlias(orphan: EpisodeAliasEntity, row: LocalEpisodeEntity, existing: EpisodeAliasEntity?, nowMs: Long,): EpisodeAliasEntity? {
        if (existing != null && (existing.state != EpisodeAliasState.UNRESOLVED || existing.podcastId != orphan.podcastId)) return null
        return EpisodeAliasEntity(
            aliasId = row.episodeId,
            podcastId = orphan.podcastId,
            catalogKey = orphan.catalogKey,
            recipeId = orphan.recipeId,
            state = EpisodeAliasState.TRUSTED,
            owner = false,
            provenance = EpisodeAliasProvenance.CATALOG_KEY_MATCH,
            createdAt = nowMs,
        )
    }

    /** [row]'s recipe-trimmed catalog key; null for a legacy row's synthetic `id:<its own ID>` key. */
    private fun catalogKeyOf(row: LocalEpisodeEntity): String? {
        if (row.legacy && row.guid == "id:${row.episodeId}") return null
        return EpisodeRecipeId.trimKey(row.guid)
    }

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
     * keep the larger value. The like is the latest like decision and completion is kept from either
     * row, both weighed against [groupState] (the settings of [old]'s episode), so a like or
     * completion cleared later doesn't come back with the switch.
     */
    fun linkedHistoryRow(
        old: ListeningHistoryEntity,
        existing: ListeningHistoryEntity?,
        rssEpisode: RssEpisodeEntity,
        rssPodcast: PodcastEntity,
        groupState: EpisodeGroupStateEntity? = null,
    ): ListeningHistoryEntity {
        val rows = listOfNotNull(old, existing)
        val like = EpisodeListenerProjection.likeDecision(rows, groupState)
        val completed = mergedCompletion(rows, groupState)
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
            isCompleted = completed,
            isLiked = like.value,
            likedAt = maxOf(old.likedAt, existing?.likedAt ?: 0L, like.at),
            lastPlayedAt = maxOf(old.lastPlayedAt, existing?.lastPlayedAt ?: 0L),
            enclosureType = rssEpisode.enclosureType ?: old.enclosureType,
            isManualCompletion = completed && rows.any { it.isManualCompletion },
            isBulkCompletion = completed && rows.any { it.isBulkCompletion },
        )
    }

    /** Completed when either row is, unless the group settings hold a decision at least as late as every completed row's. */
    fun mergedCompletion(rows: List<ListeningHistoryEntity>, groupState: EpisodeGroupStateEntity?,): Boolean {
        val group = listOfNotNull(
            groupState?.completedAt?.takeIf { it > 0L }?.let { EpisodeListenerProjection.Decision(it, true) },
            groupState?.completeClearedAt?.takeIf { it > 0L }?.let { EpisodeListenerProjection.Decision(it, false) },
        ).maxByOrNull { it.at }
        val latestCompletedRow = rows.filter { it.isCompleted }.maxOfOrNull { it.lastPlayedAt }
        if (group != null && (latestCompletedRow == null || group.at >= latestCompletedRow)) return group.value
        return latestCompletedRow != null
    }
}
