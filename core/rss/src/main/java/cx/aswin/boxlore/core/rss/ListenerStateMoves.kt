package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.EpisodeListenerProjection
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Rules for the one path that still moves listener state from one stored episode ID to another,
 * the orphan rematch after a feed read ([LocalCatalogListenerStateReconciler]), and for the aliases
 * that replace the other former move, the switch of a Podcast Index show to its RSS feed
 * (`RssPodcastRepository.confirmPodcastIndexLink`). docs/EPISODE_IDS.md §5: nothing changes ID
 * except the orphan rematch of unresolved legacy negative IDs, it consults aliases first, and a
 * merged like is the latest like decision rather than either row's like.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 * COMPAT(own-ids): keeps the remaining state move alias-aware. Permanent: never remove (MIGRATION.md §5).
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
     * The aliases that let the RSS show the listener switched to read the listener state its Podcast
     * Index show holds, instead of moving that state onto the RSS show's IDs (decision D8;
     * docs/EPISODE_IDS.md §5). [references] are the Podcast Index show's history, queue and download
     * rows ([listenerReferences]); none of them changes ID or show.
     * - **Trusted** when a row's ID has a trusted, keyed alias of [podcastIndexId] and exactly one of
     *   [rssEpisodes] has that catalog key: the RSS episode's ID joins that episode, never as its
     *   owner. Reads then project the old IDs' state under the RSS ID, progress goes to the episode's
     *   owner and playback uses the ID that holds the completed download.
     *   A weaker match from another old ID doesn't undo it: the key is the episode's identity.
     * - **Ambiguous** otherwise: several RSS episodes share the key, or the match is only by audio
     *   address, title or date ([RssSourceMatcher.findMatchingEpisode]) because the old ID has no
     *   trusted key of this show that an RSS episode has. An ambiguous alias never resolves and never
     *   moves state; it records the candidate for later evidence.
     * An old ID whose own alias is ambiguous links nothing: it never resolves. Aliases are recorded
     * under [podcastIndexId] with provenance `rss_source_link`, and only for episodes of the
     * switched-to show, so unrelated shows are never linked. An RSS episode whose ID the Podcast
     * Index show's rows already hold is that episode and gets no alias. The caller inserts the
     * aliases only where an ID has none yet, so switching again changes nothing.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
     * COMPAT(own-ids): a show switched to its feed reads its old IDs' state. Permanent: never remove (MIGRATION.md §5).
     *
     * @param aliasesById the aliases of the IDs [references] hold
     * @param epNs the Podcast Index show's episode namespace, for an ambiguous alias's recipe ID; null when it has none
     */
    fun switchedShowAliases(
        podcastIndexId: String,
        references: List<LocalCatalogListenerReference>,
        aliasesById: Map<String, EpisodeAliasEntity>,
        rssEpisodes: List<RssEpisodeEntity>,
        epNs: Long?,
        nowMs: Long,
    ): List<EpisodeAliasEntity> = SwitchedShowLinks(podcastIndexId, aliasesById, rssEpisodes, epNs, nowMs).aliases(references)

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

/**
 * Works out [ListenerStateMoves.switchedShowAliases]: one claim per (old ID, RSS episode) match,
 * then one alias per RSS episode.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 */
private class SwitchedShowLinks(
    private val podcastIndexId: String,
    private val aliasesById: Map<String, EpisodeAliasEntity>,
    private val rssEpisodes: List<RssEpisodeEntity>,
    private val epNs: Long?,
    private val nowMs: Long,
) {
    /** An RSS episode that one old ID points at, under [key], and whether the key match was exact and unique. */
    private data class Claim(val rssId: String, val key: String?, val recipeId: Long?, val exact: Boolean,)

    private val rssKeys: Map<String, String?> = rssEpisodes.associate { it.episodeId to EpisodeRecipeId.catalogKey(it.guid, it.audioUrl) }
    private val rssByKey: Map<String, List<RssEpisodeEntity>> =
        rssEpisodes.mapNotNull { episode -> rssKeys[episode.episodeId]?.let { it to episode } }.groupBy({ it.first }, { it.second })

    fun aliases(references: List<LocalCatalogListenerReference>): List<EpisodeAliasEntity> {
        val heldIds = references.mapTo(HashSet()) { it.episodeId }
        return references
            .groupBy { it.episodeId }
            .flatMap { (oldId, rows) -> claims(oldId, rows) }
            .filter { it.rssId !in heldIds }
            .groupBy { it.rssId }
            .map { (rssId, claims) -> aliasOf(rssId, claims) }
    }

    private fun claims(oldId: String, rows: List<LocalCatalogListenerReference>,): List<Claim> {
        val alias = aliasesById[oldId]
        if (alias?.state == EpisodeAliasState.AMBIGUOUS) return emptyList()
        val key = alias?.takeIf { it.state == EpisodeAliasState.TRUSTED && it.podcastId == podcastIndexId }?.catalogKey
        val byKey = key?.let { rssByKey[it] }.orEmpty()
        if (byKey.isNotEmpty()) return byKey.map { Claim(it.episodeId, key, alias?.recipeId, exact = byKey.size == 1) }
        return rows
            .mapNotNull { row -> RssSourceMatcher.findMatchingEpisode(rssEpisodes, row.title.orEmpty(), row.enclosureUrl, row.publishedDate) }
            .distinctBy { it.episodeId }
            .map { episode ->
                val rssKey = rssKeys[episode.episodeId]
                Claim(episode.episodeId, rssKey, recipeIdOf(rssKey), exact = false)
            }
    }

    /**
     * Trusted when any claim is exact: the RSS episode's own key is an old ID's trusted key and no
     * other RSS episode has it, so it is that episode whatever weaker matches say. Ambiguous otherwise.
     */
    private fun aliasOf(rssId: String, claims: List<Claim>,): EpisodeAliasEntity {
        val exact = claims.firstOrNull { it.exact }
        val key = exact?.key ?: rssKeys[rssId]
        return EpisodeAliasEntity(
            aliasId = rssId,
            podcastId = podcastIndexId,
            catalogKey = key,
            recipeId = exact?.recipeId ?: recipeIdOf(key),
            state = if (exact != null) EpisodeAliasState.TRUSTED else EpisodeAliasState.AMBIGUOUS,
            owner = false,
            provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK,
            createdAt = nowMs,
        )
    }

    private fun recipeIdOf(key: String?): Long? {
        val namespace = epNs ?: return null
        return key?.let { runCatching { EpisodeRecipeId.forNamespace(namespace, it) }.getOrNull() }
    }
}
