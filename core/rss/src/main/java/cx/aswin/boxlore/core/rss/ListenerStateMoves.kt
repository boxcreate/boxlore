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
     * [orphan]'s episode: trusted, recorded as a non-owner, provenance `catalog_key_match`. Null when
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
     * The alias recording that [oldId]'s state moved onto [target]'s episode: trusted, recorded as a
     * non-owner (the catalog row now holds the state). Null without a target alias.
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
     * - **Trusted by key** when a row's ID has a trusted, keyed alias of [podcastIndexId] and exactly
     *   one of [rssEpisodes] has that catalog key. When [feedProven] is false (the listener confirmed
     *   a show that only looks the same: title and author), one of the old ID's rows must also have
     *   the RSS episode's audio address or title, so a feed with generic GUIDs ("1", "2") never
     *   links another show's episode. A weaker match from another old ID doesn't undo a trusted key
     *   match: the key is the episode's identity.
     * - **Trusted by audio address** when an old ID has no alias, or an unresolved alias of
     *   [podcastIndexId] (played before the show was followed, or a v39 listener-only or
     *   address-keyed ID), its rows name exactly one RSS episode by audio address, that episode has
     *   a GUID, and no other old ID points at it. The old ID's alias is settled first
     *   ([SwitchedShowLinkPlan.settled]: trusted under [podcastIndexId] with that episode's key,
     *   provenance `rss_source_audio_match`), then the RSS ID joins it, as the orphan rematch and
     *   a catalog read treat an exact audio address.
     * - **Ambiguous** otherwise: several RSS episodes share the key, the match is only by title or
     *   date ([RssSourceMatcher.findMatchingEpisode]), or the evidence above is incomplete. An
     *   ambiguous alias never resolves and never moves state; a later link with exact evidence makes
     *   it trusted ([linkWrite]).
     * An old ID whose own alias is ambiguous links nothing: it never resolves. Aliases are recorded
     * under [podcastIndexId] with provenance `rss_source_link`, and only for episodes of the
     * switched-to show, so unrelated shows are never linked. An RSS ID joins as a non-owner;
     * ownership then follows the §5 owner rule like any trusted ID. An RSS episode whose ID the
     * Podcast Index show's rows already hold is that episode and gets no alias.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
     * COMPAT(own-ids): a show switched to its feed reads its old IDs' state. Permanent: never remove (MIGRATION.md §5).
     *
     * @param aliasesById the aliases of the IDs [references] hold
     * @param epNs the Podcast Index show's episode namespace, for the aliases' recipe IDs; null when it has none
     * @param feedProven the RSS feed is the Podcast Index show's own feed (same address or podcast GUID)
     */
    @Suppress("LongParameterList")
    fun switchedShowLinks(
        podcastIndexId: String,
        references: List<LocalCatalogListenerReference>,
        aliasesById: Map<String, EpisodeAliasEntity>,
        rssEpisodes: List<RssEpisodeEntity>,
        epNs: Long?,
        feedProven: Boolean,
        nowMs: Long,
    ): SwitchedShowLinkPlan = SwitchedShowLinks(podcastIndexId, aliasesById, rssEpisodes, epNs, feedProven, nowMs).plan(references)

    /**
     * What to store for [link] given the RSS ID's stored alias [existing]: [link] when the ID has no
     * alias; the stored alias made trusted (with [link]'s key) when it is an ambiguous
     * `rss_source_link` alias of the same Podcast Index show and exact evidence now exists; null
     * otherwise. A trusted alias is never downgraded, and an alias of another provenance or show is
     * never touched, so linking again changes nothing else.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
     */
    fun linkWrite(existing: EpisodeAliasEntity?, link: EpisodeAliasEntity,): EpisodeAliasEntity? = when {
        existing == null -> link
        existing.provenance == EpisodeAliasProvenance.RSS_SOURCE_LINK &&
            existing.podcastId == link.podcastId &&
            existing.state == EpisodeAliasState.AMBIGUOUS &&
            link.state == EpisodeAliasState.TRUSTED -> existing.copy(state = EpisodeAliasState.TRUSTED, catalogKey = link.catalogKey, recipeId = link.recipeId)
        else -> null
    }

    /** True when [a] and [b] are the same title once case, spaces and punctuation are ignored. */
    fun sameTitle(a: String?, b: String?,): Boolean {
        val left = a?.let(::normalizedTitle).orEmpty()
        return left.isNotEmpty() && left == b?.let(::normalizedTitle)
    }

    private fun normalizedTitle(value: String): String = value.lowercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit)

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
 * What linking an RSS show to the Podcast Index show it replaced writes
 * ([ListenerStateMoves.switchedShowLinks]).
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 *
 * @property links one alias per matched RSS episode, stored through [ListenerStateMoves.linkWrite]
 * @property settled old IDs (no alias, or an unresolved one) whose alias the link makes trusted; the
 *   caller marks one owner when the episode has none
 */
internal data class SwitchedShowLinkPlan(
    val links: List<EpisodeAliasEntity>,
    val settled: List<EpisodeAliasEntity>,
)

/**
 * Works out [ListenerStateMoves.switchedShowLinks]: one claim per (old ID, RSS episode) match,
 * then one alias per RSS episode.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 */
private class SwitchedShowLinks(
    private val podcastIndexId: String,
    private val aliasesById: Map<String, EpisodeAliasEntity>,
    private val rssEpisodes: List<RssEpisodeEntity>,
    private val epNs: Long?,
    private val feedProven: Boolean,
    private val nowMs: Long,
) {
    /**
     * An RSS episode that one old ID points at, under [key], and whether the match is exact. An
     * [byAudio] claim is an exact audio address match of an old ID with no usable alias; it stays
     * exact only while no other old ID points at the same RSS episode.
     */
    private data class Claim(val oldId: String, val rssId: String, val key: String?, val recipeId: Long?, val exact: Boolean, val byAudio: Boolean = false,)

    private val rssKeys: Map<String, String?> = rssEpisodes.associate { it.episodeId to EpisodeRecipeId.catalogKey(it.guid, it.audioUrl) }
    private val rssByKey: Map<String, List<RssEpisodeEntity>> =
        rssEpisodes.mapNotNull { episode -> rssKeys[episode.episodeId]?.let { it to episode } }.groupBy({ it.first }, { it.second })
    private val rssByAudio: Map<String, List<RssEpisodeEntity>> =
        rssEpisodes.filter { it.audioUrl.isNotBlank() }.groupBy { it.audioUrl.trim() }

    fun plan(references: List<LocalCatalogListenerReference>): SwitchedShowLinkPlan {
        val heldIds = references.mapTo(HashSet()) { it.episodeId }
        val claims = references
            .groupBy { it.episodeId }
            .flatMap { (oldId, rows) -> claims(oldId, rows) }
            .filter { it.rssId !in heldIds }
        val claimants = claims.groupBy { it.rssId }.mapValues { (_, byRss) -> byRss.mapTo(HashSet()) { it.oldId } }
        val resolved = claims.map { claim -> if (claim.byAudio && claimants.getValue(claim.rssId).size > 1) claim.copy(exact = false) else claim }
        return SwitchedShowLinkPlan(
            links = resolved.groupBy { it.rssId }.map { (rssId, byRss) -> aliasOf(rssId, byRss) },
            settled = resolved.filter { it.byAudio && it.exact }.map(::settledAlias),
        )
    }

    private fun claims(oldId: String, rows: List<LocalCatalogListenerReference>,): List<Claim> {
        val alias = aliasesById[oldId]
        if (alias?.state == EpisodeAliasState.AMBIGUOUS) return emptyList()
        val key = alias?.takeIf { it.state == EpisodeAliasState.TRUSTED && it.podcastId == podcastIndexId }?.catalogKey
        val byKey = key?.let { rssByKey[it] }.orEmpty()
        if (byKey.isNotEmpty()) {
            return byKey.map { episode ->
                Claim(oldId, episode.episodeId, key, alias?.recipeId, exact = byKey.size == 1 && (feedProven || corroborates(rows, episode)))
            }
        }
        val settleable = alias == null || (alias.state == EpisodeAliasState.UNRESOLVED && alias.podcastId == podcastIndexId)
        if (settleable) audioClaim(oldId, rows)?.let { return listOf(it) }
        return rows
            .mapNotNull { row -> RssSourceMatcher.findMatchingEpisode(rssEpisodes, row.title.orEmpty(), row.enclosureUrl, row.publishedDate) }
            .distinctBy { it.episodeId }
            .map { episode ->
                val rssKey = rssKeys[episode.episodeId]
                Claim(oldId, episode.episodeId, rssKey, recipeIdOf(rssKey), exact = false)
            }
    }

    /** The one RSS episode with a GUID that [rows] name by exact audio address, as an exact claim; null otherwise. */
    private fun audioClaim(oldId: String, rows: List<LocalCatalogListenerReference>,): Claim? {
        val matched = rows
            .mapNotNull { row -> row.enclosureUrl?.trim()?.takeIf(String::isNotEmpty)?.let { rssByAudio[it]?.singleOrNull() } }
            .distinctBy { it.episodeId }
            .singleOrNull() ?: return null
        val key = EpisodeRecipeId.trimKey(matched.guid) ?: return null
        return Claim(oldId, matched.episodeId, key, recipeIdOf(key), exact = true, byAudio = true)
    }

    /** True when one of [rows] has [episode]'s audio address or title: a second signal next to the key. */
    private fun corroborates(rows: List<LocalCatalogListenerReference>, episode: RssEpisodeEntity,): Boolean = rows.any { row ->
        val audio = row.enclosureUrl?.trim()
        (!audio.isNullOrEmpty() && audio == episode.audioUrl.trim()) || ListenerStateMoves.sameTitle(row.title, episode.title)
    }

    /**
     * Trusted when any claim is exact: the RSS episode's own key is an old ID's trusted key and no
     * other RSS episode has it, or an old ID's exact audio address names it alone. Ambiguous otherwise.
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

    /** The old ID's alias an exact audio claim settles: trusted under the Podcast Index show with the RSS episode's key. */
    private fun settledAlias(claim: Claim): EpisodeAliasEntity = EpisodeAliasEntity(
        aliasId = claim.oldId,
        podcastId = podcastIndexId,
        catalogKey = claim.key,
        recipeId = claim.recipeId,
        state = EpisodeAliasState.TRUSTED,
        owner = false,
        provenance = EpisodeAliasProvenance.RSS_SOURCE_AUDIO_MATCH,
        createdAt = aliasesById[claim.oldId]?.createdAt ?: nowMs,
    )

    private fun recipeIdOf(key: String?): Long? {
        val namespace = epNs ?: return null
        return key?.let { runCatching { EpisodeRecipeId.forNamespace(namespace, it) }.getOrNull() }
    }
}
