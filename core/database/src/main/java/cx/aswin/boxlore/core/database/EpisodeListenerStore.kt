package cx.aswin.boxlore.core.database

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

/**
 * One listener state per episode across its stored IDs (docs/EPISODE_IDS.md §5).
 *
 * An episode can be stored under several IDs (a Podcast Index ID, a phone-minted ID, a recipe
 * ID). Every ID with a trusted alias of the same (show, catalog key) is one [EpisodeGroup]:
 * - **One owner** holds progress and position. It is the stored owner mark while that ID holds
 *   listener state; otherwise (or with no single mark) it is chosen by the upgrade's order
 *   ([EpisodeIdentityDao.chooseOwner]) and stored in the same transaction as the write that needs it.
 * - **Reads** are projected ([EpisodeListenerProjection]): progress from the owner, liked and
 *   completed as the latest decision across the group settings and every ID's row. Lists count
 *   the episode once.
 * - **Writes:** progress goes to the owner ([historyId]). Like and completion are absolute: the
 *   target is written to every trusted ID's row and to `episode_group_state`, so an older alias
 *   row can't bring a cleared like or completion back. Nothing changes ID.
 * - **Downloads** stay where they are: playback reads the bytes of the ID that holds a completed
 *   download ([downloadHolder]) while history goes to the owner.
 *
 * An ID without a trusted alias (no alias, ambiguous or unresolved, `rss:` and Brief IDs) is a
 * group of its own and reads and writes exactly as before aliases existed.
 *
 * Obtain it with [episodeListenerStore]; it holds no state of its own.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, projected listener state).
 * COMPAT(own-ids): one listener state per episode across its IDs. Permanent: never remove (MIGRATION.md §5).
 */
@Suppress("TooManyFunctions")
class EpisodeListenerStore(private val database: BoxLoreDatabase) {
    private val aliases get() = database.episodeIdentityDao()
    private val history get() = database.listeningHistoryDao()
    private val downloads get() = database.downloadedEpisodeDao()

    /** The episode [episodeId] belongs to. A read: the owner mark is not stored. */
    suspend fun group(episodeId: String): EpisodeGroup = resolve(episodeId, storeOwner = false)

    /**
     * The ID whose history row receives [episodeId]'s progress: the episode's owner, stored if it
     * changed. [episodeId] itself when it has no shared alias.
     */
    suspend fun historyId(episodeId: String): String {
        val alias = aliases.getAlias(episodeId)
        if (alias == null || !alias.isTrustedKeyed()) return episodeId
        return database.withTransaction { resolve(episodeId, storeOwner = true).ownerId }
    }

    /** Every trusted ID of [episodeId]'s episode, the owner first; just [episodeId] without a shared alias. */
    suspend fun memberIds(episodeId: String): List<String> = group(episodeId).memberIds

    /** The projected history row of [episodeId]'s episode ([EpisodeListenerProjection.project]). */
    suspend fun projectedHistory(episodeId: String): ListeningHistoryEntity? {
        val group = group(episodeId)
        if (!group.isShared) return history.getHistoryItem(episodeId)
        return project(group)
    }

    /**
     * [row] (the stored row of [episodeId], or null) projected for its episode. Equal to [row] when
     * the ID has no shared alias, so callers that already read the row pay one alias lookup.
     */
    suspend fun projectedHistory(episodeId: String, row: ListeningHistoryEntity?,): ListeningHistoryEntity? {
        val group = group(episodeId)
        if (!group.isShared) return row
        return project(group)
    }

    /**
     * Sets the like of [episodeId]'s episode to [liked] (an absolute target, not a toggle) on every
     * trusted ID's row and in the group settings. Returns the history rows written; 0 means no ID
     * of the episode has a row yet, and the caller creates the owner's ([historyId]).
     */
    suspend fun setLiked(episodeId: String, liked: Boolean, nowMs: Long,): Int = database.withTransaction {
        val group = resolve(episodeId, storeOwner = true)
        if (!group.isKeyed) return@withTransaction history.setLikeStatusForIds(group.memberIds, liked, nowMs)
        val podcastId = group.podcastId!!
        val catalogKey = group.catalogKey!!
        val state = aliases.getGroupState(podcastId, catalogKey)
        val at = EpisodeListenerProjection.likeTime(nowMs, rows(group), state)
        val written = history.setLikeStatusForIds(group.memberIds, liked, at)
        aliases.upsertGroupState(EpisodeListenerProjection.withLike(state, podcastId, catalogKey, liked, at))
        written
    }

    /**
     * Records a like decision in the group settings of [episodeId]'s episode after the caller wrote
     * the episode's one row itself, so an alias found later can't bring an older like state back.
     * Does nothing for an ID without a trusted alias. Use [setLiked] when the episode has several IDs.
     */
    suspend fun recordLike(episodeId: String, liked: Boolean, nowMs: Long,) {
        val alias = aliases.getAlias(episodeId)
        if (alias == null || !alias.isTrustedKeyed()) return
        database.withTransaction {
            val group = resolve(episodeId, storeOwner = true)
            val podcastId = group.podcastId ?: return@withTransaction
            val catalogKey = group.catalogKey ?: return@withTransaction
            val state = aliases.getGroupState(podcastId, catalogKey)
            val at = EpisodeListenerProjection.likeTime(nowMs, rows(group), state)
            aliases.upsertGroupState(EpisodeListenerProjection.withLike(state, podcastId, catalogKey, liked, at))
        }
    }

    /**
     * Records a completion decision for [episodeId]'s episode after the caller wrote [episodeId]'s
     * row itself, normally the owner's ([historyId]) after a playback completion, replay or mark as
     * played: every other trusted ID's row gets [completed] too, and the group settings record it.
     * Does nothing for an ID without a trusted alias, whose one row the caller already wrote.
     */
    suspend fun recordCompletion(episodeId: String, completed: Boolean, nowMs: Long,) {
        val alias = aliases.getAlias(episodeId)
        if (alias == null || !alias.isTrustedKeyed()) return
        database.withTransaction {
            val group = resolve(episodeId, storeOwner = true)
            val podcastId = group.podcastId ?: return@withTransaction
            val catalogKey = group.catalogKey ?: return@withTransaction
            val others = group.memberIds.filter { it != episodeId }
            if (others.isNotEmpty()) history.setCompletionForIds(others, completed)
            val state = aliases.getGroupState(podcastId, catalogKey)
            val at = EpisodeListenerProjection.completionTime(nowMs, rows(group), state)
            aliases.upsertGroupState(EpisodeListenerProjection.withCompletion(state, podcastId, catalogKey, completed, at))
        }
    }

    /**
     * The ID whose completed download plays [episodeId]: [episodeId] itself when it holds one, else
     * the owner, else another trusted ID of the episode. Null when none holds a completed download.
     */
    suspend fun downloadHolder(episodeId: String): String? {
        val group = group(episodeId)
        val completed = downloads.getCompletedDownloadIds(group.memberIds).toSet()
        return when {
            episodeId in completed -> episodeId
            group.ownerId in completed -> group.ownerId
            else -> group.memberIds.firstOrNull { it in completed }
        }
    }

    /**
     * For each of [episodeIds] that shares its episode with another trusted ID, the ID whose
     * completed download should play it ([downloadHolder]). IDs that play their own bytes are left out,
     * so the map is empty for every episode stored under one ID.
     */
    suspend fun downloadHolders(episodeIds: Collection<String>): Map<String, String> {
        val shared = sharedAliases(episodeIds).map { it.aliasId }
        if (shared.isEmpty()) return emptyMap()
        return buildMap {
            for (id in shared) {
                val holder = downloadHolder(id) ?: continue
                if (holder != id) put(id, holder)
            }
        }
    }

    /**
     * A token per ID that is equal for every ID of one episode ([EpisodeGroup.tokenOf]); an ID that
     * shares no episode with another ID maps to itself. Used to count an episode once.
     */
    suspend fun groupTokens(episodeIds: Collection<String>): Map<String, String> {
        val tokens = episodeIds.associateWith { it }.toMutableMap()
        for (alias in sharedAliases(episodeIds)) {
            EpisodeGroup.tokenOf(alias.podcastId, alias.catalogKey)?.let { tokens[alias.aliasId] = it }
        }
        return tokens
    }

    /**
     * The identity to record with a queue removal or restore of each of [episodeIds]: the show,
     * catalog key and recipe ID of a trusted alias, else the ID alone.
     */
    suspend fun queueIdentities(episodeIds: Collection<String>): List<QueueRemovalIdentity> {
        val ids = episodeIds.filter { it.isNotBlank() }.distinct()
        val trusted = ids.chunked(MAX_IDS_PER_QUERY)
            .flatMap { aliases.getAliases(it) }
            .filter { it.isTrustedKeyed() }
            .associateBy { it.aliasId }
        return ids.map { id ->
            val alias = trusted[id] ?: return@map QueueRemovalIdentity(id)
            QueueRemovalIdentity(id = id, recipeId = alias.recipeId, show = alias.podcastId, catalogKey = alias.catalogKey)
        }
    }

    /** [episodeIds] plus every other trusted ID of their episodes. */
    suspend fun withAllAliases(episodeIds: Collection<String>): Set<String> {
        val all = episodeIds.toMutableSet()
        val keys = sharedAliases(episodeIds).mapNotNull { alias -> alias.catalogKey?.let { alias.podcastId to it } }.toSet()
        for ((podcastId, catalogKey) in keys) {
            aliases.aliasesForKey(podcastId, catalogKey)
                .filter { it.state == EpisodeAliasState.TRUSTED }
                .mapTo(all) { it.aliasId }
        }
        return all
    }

    /**
     * [rows] with every episode stored under several IDs counted once: the first row of such an
     * episode is replaced by its projected row and the episode's other rows are dropped; then only
     * rows that pass [keep] (the source query's filter, applied to the projected values) remain.
     * Rows of episodes with one stored ID pass through untouched.
     */
    suspend fun projectRows(rows: List<ListeningHistoryEntity>, keep: (ListeningHistoryEntity) -> Boolean = { true },): List<ListeningHistoryEntity> {
        val shared = sharedAliases(rows.map { it.episodeId })
        return projectRows(rows, shared, keep)
    }

    /**
     * [source] projected as [projectRows] does. The alias table is watched separately, so episodes
     * stored under one ID cost nothing extra per emission.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun projectedRows(source: Flow<List<ListeningHistoryEntity>>, keep: (ListeningHistoryEntity) -> Boolean = { true },): Flow<List<ListeningHistoryEntity>> =
        combine(source, sharedAliasesFlow()) { rows, shared -> rows to shared }
            .mapLatest { (rows, shared) ->
                val relevant = rows.mapTo(HashSet()) { it.episodeId }
                projectRows(rows, shared.filter { it.aliasId in relevant }, keep)
            }

    /**
     * [completedIds] (IDs whose own row is completed) as projected completion: an episode stored
     * under several IDs is completed for all of them or for none. [shared] is the shared trusted
     * aliases to consider ([EpisodeIdentityDao.sharedTrustedAliasesFlow]); only episodes with a
     * completed row are re-evaluated.
     */
    suspend fun projectCompletedIds(completedIds: Collection<String>, shared: List<EpisodeAliasEntity>,): Set<String> {
        val completed = completedIds.toSet()
        if (shared.isEmpty()) return completed
        val result = completed.toMutableSet()
        val episodes = shared
            .filter { it.aliasId in completed && it.catalogKey != null }
            .distinctBy { EpisodeGroup.tokenOf(it.podcastId, it.catalogKey) }
        for (alias in episodes) {
            val group = resolve(alias.aliasId, storeOwner = false)
            if (project(group)?.isCompleted == true) {
                result.addAll(group.memberIds)
            } else {
                result.removeAll(group.memberIds.toSet())
            }
        }
        return result
    }

    /** [completedIds] projected as above, against the shared aliases stored now. */
    suspend fun projectCompletedIds(completedIds: Collection<String>): Set<String> =
        projectCompletedIds(completedIds, aliases.sharedTrustedAliasesFlow().first())

    /** [completedIds] projected as [projectCompletedIds], re-evaluated when the alias table changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun projectedCompletedIds(completedIds: Flow<List<String>>): Flow<Set<String>> =
        combine(completedIds, sharedAliasesFlow()) { ids, shared -> ids to shared }
            .mapLatest { (ids, shared) -> projectCompletedIds(ids, shared) }

    /**
     * The shared trusted aliases, re-emitted only when they change: alias writes that leave them as
     * they were (a catalog refresh adding aliases of single-ID episodes) don't re-emit lists.
     */
    private fun sharedAliasesFlow(): Flow<List<EpisodeAliasEntity>> = aliases.sharedTrustedAliasesFlow().distinctUntilChanged()

    private suspend fun projectRows(
        rows: List<ListeningHistoryEntity>,
        shared: List<EpisodeAliasEntity>,
        keep: (ListeningHistoryEntity) -> Boolean,
    ): List<ListeningHistoryEntity> {
        if (shared.isEmpty()) return rows
        val tokenById = shared.associate { it.aliasId to EpisodeGroup.tokenOf(it.podcastId, it.catalogKey)!! }
        val seenTokens = HashSet<String>()
        val result = ArrayList<ListeningHistoryEntity>(rows.size)
        for (row in rows) {
            val token = tokenById[row.episodeId]
            if (token == null) {
                result += row
            } else if (seenTokens.add(token)) {
                val projected = project(resolve(row.episodeId, storeOwner = false))
                if (projected != null && keep(projected)) result += projected
            }
        }
        return result
    }

    private suspend fun project(group: EpisodeGroup): ListeningHistoryEntity? {
        val state = if (group.isKeyed) aliases.getGroupState(group.podcastId!!, group.catalogKey!!) else null
        return EpisodeListenerProjection.project(group, rows(group), state)
    }

    private suspend fun rows(group: EpisodeGroup): List<ListeningHistoryEntity> = history.getHistoryItems(group.memberIds)

    private suspend fun sharedAliases(episodeIds: Collection<String>): List<EpisodeAliasEntity> =
        episodeIds.distinct().chunked(MAX_IDS_PER_QUERY).flatMap { aliases.sharedTrustedAliases(it) }

    private suspend fun resolve(episodeId: String, storeOwner: Boolean,): EpisodeGroup {
        val alias = aliases.getAlias(episodeId)
        if (alias == null || !alias.isTrustedKeyed()) return EpisodeGroup.single(episodeId)
        val podcastId = alias.podcastId
        val catalogKey = alias.catalogKey!!
        val members = aliases.aliasesForKey(podcastId, catalogKey).filter { it.state == EpisodeAliasState.TRUSTED }
        if (members.none { it.aliasId == episodeId }) return EpisodeGroup.single(episodeId)
        val ownerId = ownerOf(podcastId, catalogKey, members)
        if (storeOwner && members.any { it.owner != (it.aliasId == ownerId) }) {
            aliases.setOwner(podcastId, catalogKey, ownerId)
        }
        val memberIds = listOf(ownerId) + members.map { it.aliasId }.filter { it != ownerId }
        return EpisodeGroup(episodeId, podcastId, catalogKey, memberIds, ownerId)
    }

    /**
     * The stored owner while it holds listener state or no other ID does; otherwise the upgrade's
     * owner order. With no single stored mark, the upgrade's order decides.
     */
    private suspend fun ownerOf(podcastId: String, catalogKey: String, members: List<EpisodeAliasEntity>,): String {
        if (members.size == 1) return members.single().aliasId
        val marked = members.filter { it.owner }.singleOrNull()?.aliasId
        if (marked != null && aliases.holdsListenerState(marked)) return marked
        val chosen = aliases.chooseOwner(podcastId, catalogKey) ?: members.first().aliasId
        if (marked != null && chosen != marked && !aliases.holdsListenerState(chosen)) return marked
        return chosen
    }

    private fun EpisodeAliasEntity.isTrustedKeyed(): Boolean = state == EpisodeAliasState.TRUSTED && catalogKey != null

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
    }
}

/**
 * Listener state across an episode's stored IDs ([EpisodeListenerStore]). Reachable from every
 * database opener, including the playback service, without `AppContainer`.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, projected listener state).
 */
fun BoxLoreDatabase.episodeListenerStore(): EpisodeListenerStore = EpisodeListenerStore(this)
