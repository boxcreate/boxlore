package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeListenerStore
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * [DiscoveryEpisodeIds] over the phone's alias table (`episode_aliases`, docs/EPISODE_IDS.md §5).
 *
 * - **Out:** a stored ID with a trusted alias is sent as that alias's recipe ID; a stored ID in the
 *   recipe range without one is its own recipe ID. Anything else (an unresolved or ambiguous alias,
 *   a Podcast Index ID whose show's feed was never read) has none.
 * - **In:** a (show, recipe ID) maps to the owner of its episode when the trusted aliases that hold
 *   that recipe ID under the show, or the stored ID equal to it, all name one catalog key. Two keys
 *   with one recipe ID (a collision) leave the recipe ID as it is. With no trusted alias at all, a
 *   stored listening-history or queue row of the same show with the answer's audio address, when
 *   exactly one episode has it and no trusted alias places that row elsewhere, is the owner: most
 *   episodes older builds played from discovery are stored under Podcast Index IDs that stay
 *   unresolved until their show's feed is read.
 * - **Owner's show:** the show of the owner's history row, else its catalog or RSS row, else its
 *   alias, as [EpisodeListenerStore.ownerRow] reads it, so an episode of a show the listener switched
 *   to its feed comes back under that feed's show.
 *
 * [database] is read on first use, so building this opens nothing.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 * COMPAT(own-ids): discovery reads listener state under older IDs. Permanent: never remove (MIGRATION.md §5).
 */
class RoomDiscoveryEpisodeIds(private val database: () -> BoxLoreDatabase,) : DiscoveryEpisodeIds {
    override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> {
        val ids = episodeIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = DiscoveryEpisodeIds.ownRecipeIds(ids).toMutableMap()
        val dao = database().episodeIdentityDao()
        for (chunk in ids.chunked(MAX_IDS_PER_QUERY)) {
            for (alias in dao.getAliases(chunk)) {
                val recipeId = alias.recipeId?.takeIf { alias.isTrustedKeyed() && EpisodeRecipeId.isRecipeId(it) } ?: continue
                result[alias.aliasId] = recipeId
            }
        }
        return result
    }

    override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, DiscoveryEpisodeOwner> {
        val wanted = refs.distinct()
        if (wanted.isEmpty()) return emptyMap()
        val db = database()
        val dao = db.episodeIdentityDao()
        val store = db.episodeListenerStore()
        val storedUnderOwnId =
            wanted
                .map { it.recipeId.toString() }
                .distinct()
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { dao.getAliases(it) }
                .associateBy(EpisodeAliasEntity::aliasId)
        val evidence = ListenerRowEvidence(db)
        val result = LinkedHashMap<DiscoveryEpisodeRef, DiscoveryEpisodeOwner>()
        for (ref in wanted) {
            val sameId = storedUnderOwnId[ref.recipeId.toString()]?.takeIf { it.podcastId == ref.podcastId }
            val aliases = dao.aliasesForRecipeId(ref.podcastId, ref.recipeId) + listOfNotNull(sameId)
            val trusted = aliases.filter { it.isTrustedKeyed() }
            val ownerId =
                when {
                    trusted.isEmpty() -> evidence.storedIdFor(ref)?.let { store.group(it).ownerId }
                    trusted.distinctBy(EpisodeAliasEntity::catalogKey).size == 1 -> store.group(trusted.first().aliasId).ownerId
                    else -> null
                } ?: continue
            result[ref] = owner(db, ownerId, ref.podcastId)
        }
        return result
    }

    /** [ownerId] with the show its listener state is stored under (see the class docs). */
    private suspend fun owner(db: BoxLoreDatabase, ownerId: String, answerShow: String,): DiscoveryEpisodeOwner {
        val show =
            db.listeningHistoryDao().getHistoryItem(ownerId)?.podcastId
                ?: db.localEpisodeCatalogDao().getEpisode(ownerId)?.podcastId
                ?: db.rssEpisodeDao().getEpisode(ownerId)?.podcastId
                ?: db.episodeIdentityDao().getAlias(ownerId)?.podcastId
                ?: answerShow
        if (show == answerShow) return DiscoveryEpisodeOwner(ownerId, show)
        return DiscoveryEpisodeOwner(ownerId, show, db.podcastDao().getPodcast(show)?.title?.takeIf(String::isNotBlank))
    }

    /**
     * The stored listening-history and queue rows a ref with no trusted alias can be matched to by
     * its show and audio address. Each show's history and the queue are read once per lookup.
     */
    private class ListenerRowEvidence(private val db: BoxLoreDatabase,) {
        private val historyByShow = HashMap<String, Map<String, Set<String>>>()
        private var queueByShowAndAudio: Map<Pair<String, String>, Set<String>>? = null

        /** The one stored ID of [ref]'s show with [ref]'s audio address, or null when none or several have it. */
        suspend fun storedIdFor(ref: DiscoveryEpisodeRef): String? {
            val audio = ref.audioUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val fromHistory = historyIds(ref.podcastId)[audio].orEmpty()
            val fromQueue = queueIds()[ref.podcastId to audio].orEmpty()
            val candidates = (fromHistory + fromQueue).filterNot { it == ref.recipeId.toString() }.distinct()
            if (candidates.isEmpty()) return null
            // A row a trusted alias already places is another episode, whatever its address says.
            val placed = db.episodeIdentityDao().getAliases(candidates).filter { it.isTrustedKeyed() }.mapTo(HashSet()) { it.aliasId }
            return candidates.filterNot { it in placed }.singleOrNull()
        }

        private suspend fun historyIds(show: String): Map<String, Set<String>> = historyByShow.getOrPut(show) {
            db
                .listeningHistoryDao()
                .getHistoryForPodcast(show)
                .mapNotNull { row -> row.episodeAudioUrl?.trim()?.takeIf(String::isNotEmpty)?.let { it to row.episodeId } }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }
        }

        private suspend fun queueIds(): Map<Pair<String, String>, Set<String>> = queueByShowAndAudio
            ?: db
                .queueDao()
                .getAllQueueItemsSync()
                .filter { it.audioUrl.isNotBlank() }
                .groupBy({ it.podcastId to it.audioUrl.trim() }, { it.episodeId })
                .mapValues { it.value.toSet() }
                .also { queueByShowAndAudio = it }
    }

    private companion object {
        const val MAX_IDS_PER_QUERY = 500

        fun EpisodeAliasEntity.isTrustedKeyed(): Boolean = state == EpisodeAliasState.TRUSTED && catalogKey != null
    }
}
