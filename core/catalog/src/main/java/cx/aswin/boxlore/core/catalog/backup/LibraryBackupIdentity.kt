package cx.aswin.boxlore.core.catalog.backup

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.LocalEpisodeOwner
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.rss.RenumberedShowIds
import cx.aswin.boxlore.core.rss.RenumberedShows

/**
 * The identity block of a JSON library backup (version 7+): which episode (show, catalog key)
 * every stored episode ID points at, each episode's shared like and completion settings, and the
 * shows Podcast Index renumbered. Older backups have none of it, and every field is optional.
 *
 * Added in P4 PR 17 of the own-episode-IDs plan (plan section 8, JSON backup v7).
 * COMPAT(own-ids): restore-compatible field names. Permanent: never rename (MIGRATION.md §5).
 */
data class EpisodeIdentityBackup(
    /** Every row of `episode_aliases`. */
    val episodeAliases: List<EpisodeAliasBackup>? = null,
    /** Every row of `episode_group_state`. */
    val episodeGroupStates: List<EpisodeGroupStateBackup>? = null,
    /** `boxlore_renumbered_shows`: old show ID → the show ID it answers as. */
    val renumberedShows: Map<String, String>? = null,
)

/** One `episode_aliases` row in a backup ([EpisodeAliasEntity]). Gson can leave any field null. */
data class EpisodeAliasBackup(
    val aliasId: String? = null,
    val podcastId: String? = null,
    val catalogKey: String? = null,
    val recipeId: Long? = null,
    val state: String? = null,
    val owner: Boolean = false,
    val provenance: String? = null,
    val createdAt: Long = 0L,
)

/** One `episode_group_state` row in a backup ([EpisodeGroupStateEntity]). */
data class EpisodeGroupStateBackup(
    val podcastId: String? = null,
    val catalogKey: String? = null,
    val likedAt: Long = 0L,
    val likeClearedAt: Long = 0L,
    val completedAt: Long = 0L,
    val completeClearedAt: Long = 0L,
    val version: Long = 0L,
)

/**
 * What a restore did with one backup alias: added it, left the phone's row (or an unreadable
 * backup row) alone, or refused it because the phone holds its ID for another episode.
 */
internal enum class AliasRestoreDecision { INSERT, KEEP_PHONE, REFUSE }

/**
 * Pure rules for the identity block (docs/EPISODE_IDS.md §4–§5).
 *
 * - An alias the phone already has is never replaced. When the phone's row names another show, or
 *   both name a key and the keys differ, the backup's alias is refused.
 * - An ID only a catalog row holds is refused when the row is another show's or another key's
 *   (a legacy row's synthetic `id:<its own ID>` key is unknown, so it never differs).
 * - Unresolved and ambiguous aliases keep their state; only a trusted alias with a key may own.
 * - Group settings merge with the phone's by the latest like and completion decision.
 */
internal object LibraryBackupIdentityLogic {
    private val STATES = setOf(EpisodeAliasState.TRUSTED, EpisodeAliasState.AMBIGUOUS, EpisodeAliasState.UNRESOLVED)

    fun toBackup(alias: EpisodeAliasEntity) = EpisodeAliasBackup(
        aliasId = alias.aliasId,
        podcastId = alias.podcastId,
        catalogKey = alias.catalogKey,
        recipeId = alias.recipeId,
        state = alias.state,
        owner = alias.owner,
        provenance = alias.provenance,
        createdAt = alias.createdAt,
    )

    fun toBackup(state: EpisodeGroupStateEntity) = EpisodeGroupStateBackup(
        podcastId = state.podcastId,
        catalogKey = state.catalogKey,
        likedAt = state.likedAt,
        likeClearedAt = state.likeClearedAt,
        completedAt = state.completedAt,
        completeClearedAt = state.completeClearedAt,
        version = state.version,
    )

    /**
     * [backup] as a row to store, or null when it can't be read (no ID, show, provenance or a
     * known state). A trusted or ambiguous alias without a key is stored as unresolved, a recipe ID
     * outside the recipe range is dropped, and only a trusted alias with a key keeps its owner mark.
     */
    fun toEntity(backup: EpisodeAliasBackup): EpisodeAliasEntity? {
        val aliasId = backup.aliasId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val podcastId = backup.podcastId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val provenance = backup.provenance?.takeIf(String::isNotBlank) ?: return null
        val storedState = backup.state?.takeIf { it in STATES } ?: return null
        val key = EpisodeRecipeId.trimKey(backup.catalogKey)
        val state = if (key == null) EpisodeAliasState.UNRESOLVED else storedState
        return EpisodeAliasEntity(
            aliasId = aliasId,
            podcastId = podcastId,
            catalogKey = key,
            recipeId = backup.recipeId?.takeIf { key != null && EpisodeRecipeId.isRecipeId(it) },
            state = state,
            owner = backup.owner && state == EpisodeAliasState.TRUSTED,
            provenance = provenance,
            createdAt = backup.createdAt.coerceAtLeast(0L),
        )
    }

    /** [backup] as a row to store, or null without a show or key. */
    fun toEntity(backup: EpisodeGroupStateBackup): EpisodeGroupStateEntity? {
        val podcastId = backup.podcastId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val key = EpisodeRecipeId.trimKey(backup.catalogKey) ?: return null
        return EpisodeGroupStateEntity(
            podcastId = podcastId,
            catalogKey = key,
            likedAt = backup.likedAt.coerceAtLeast(0L),
            likeClearedAt = backup.likeClearedAt.coerceAtLeast(0L),
            completedAt = backup.completedAt.coerceAtLeast(0L),
            completeClearedAt = backup.completeClearedAt.coerceAtLeast(0L),
            version = backup.version.coerceAtLeast(0L),
        )
    }

    /**
     * What to do with [restored]: the phone's alias of the same ID ([phoneAlias]) is kept, and
     * refuses it when it names another episode; else a catalog row holding the ID ([catalogRow])
     * refuses it when it is another episode's.
     */
    fun decide(restored: EpisodeAliasEntity, phoneAlias: EpisodeAliasEntity?, catalogRow: LocalEpisodeOwner?,): AliasRestoreDecision {
        if (phoneAlias != null) {
            return if (namesAnotherEpisode(restored, phoneAlias.podcastId, phoneAlias.catalogKey)) AliasRestoreDecision.REFUSE else AliasRestoreDecision.KEEP_PHONE
        }
        if (catalogRow != null && namesAnotherEpisode(restored, catalogRow.podcastId, catalogKeyOf(catalogRow))) {
            return AliasRestoreDecision.REFUSE
        }
        return AliasRestoreDecision.INSERT
    }

    /** The phone's group settings ([phone]) and the backup's ([restored]) merged: each decision keeps its latest time. */
    fun mergedGroupState(phone: EpisodeGroupStateEntity?, restored: EpisodeGroupStateEntity): EpisodeGroupStateEntity {
        phone ?: return restored
        return phone.copy(
            likedAt = maxOf(phone.likedAt, restored.likedAt),
            likeClearedAt = maxOf(phone.likeClearedAt, restored.likeClearedAt),
            completedAt = maxOf(phone.completedAt, restored.completedAt),
            completeClearedAt = maxOf(phone.completeClearedAt, restored.completeClearedAt),
            version = maxOf(phone.version, restored.version) + 1L,
        )
    }

    /**
     * The backup's renumbered shows the phone can take: valid renumberings for old IDs the phone
     * records nothing for, and never one that reverses an entry the phone has.
     */
    fun renumberingsToRecord(phone: Map<String, String>, restored: Map<String, String>?): List<Pair<String, String>> =
        restored.orEmpty().entries
            .filter { (oldId, newId) -> RenumberedShowIds.isRenumbering(oldId, newId) && oldId !in phone && phone[newId] != oldId }
            .map { it.key to it.value }

    private fun namesAnotherEpisode(restored: EpisodeAliasEntity, podcastId: String, catalogKey: String?): Boolean =
        podcastId != restored.podcastId || (catalogKey != null && restored.catalogKey != null && catalogKey != restored.catalogKey)

    /** A catalog row's recipe-trimmed key; null for the synthetic `id:<its own ID>` key, which names no item. */
    private fun catalogKeyOf(row: LocalEpisodeOwner): String? =
        if (row.guid == "id:${row.episodeId}") null else EpisodeRecipeId.trimKey(row.guid)
}

/**
 * Exports and restores the identity block ([EpisodeIdentityBackup]) on Room.
 *
 * Restore runs in one transaction, before the backup's history: aliases are only added
 * ([LibraryBackupIdentityLogic.decide]), so nothing the phone stores changes identity, and unresolved
 * records stay unresolved until the show's feed is read. Every restored alias is added as a
 * non-owner; the backup's owner mark is kept only for an episode that has no owner on the phone, so
 * no episode gets a second owner, and otherwise the owner rule decides when the episode is next
 * read or written. The IDs it refuses are returned, so the history restore leaves their rows alone
 * instead of writing one episode's listening onto another's.
 *
 * Added in P4 PR 17 of the own-episode-IDs plan (plan section 8, JSON backup v7).
 */
internal class LibraryBackupIdentity(private val database: BoxLoreDatabase, private val renumberedShows: RenumberedShows,) {
    private val aliases get() = database.episodeIdentityDao()

    suspend fun export(): EpisodeIdentityBackup = EpisodeIdentityBackup(
        episodeAliases = aliases.allAliases().map(LibraryBackupIdentityLogic::toBackup),
        episodeGroupStates = aliases.allGroupStates().map(LibraryBackupIdentityLogic::toBackup),
        renumberedShows = renumberedShows.all().toSortedMap(),
    )

    /** Records the backup's renumbered shows the phone doesn't know yet; an entry the phone has is kept. */
    fun restoreRenumberedShows(backup: EpisodeIdentityBackup?) {
        for ((oldId, newId) in LibraryBackupIdentityLogic.renumberingsToRecord(renumberedShows.all(), backup?.renumberedShows)) {
            renumberedShows.record(oldId, newId)
        }
    }

    /** Restores the backup's aliases and group settings; returns the alias IDs it refused. */
    suspend fun restore(backup: EpisodeIdentityBackup?): Set<String> {
        val restored = backup?.episodeAliases.orEmpty().mapNotNull(LibraryBackupIdentityLogic::toEntity).distinctBy { it.aliasId }
        val groupStates = backup?.episodeGroupStates.orEmpty().mapNotNull(LibraryBackupIdentityLogic::toEntity)
            .distinctBy { it.podcastId to it.catalogKey }
        if (restored.isEmpty() && groupStates.isEmpty()) return emptySet()
        return database.withTransaction {
            val refused = restoreAliases(restored)
            for (state in groupStates) {
                aliases.upsertGroupState(LibraryBackupIdentityLogic.mergedGroupState(aliases.getGroupState(state.podcastId, state.catalogKey), state))
            }
            refused
        }
    }

    private suspend fun restoreAliases(restored: List<EpisodeAliasEntity>): Set<String> {
        if (restored.isEmpty()) return emptySet()
        val ids = restored.map { it.aliasId }
        val phoneAliases = ids.chunked(MAX_IDS_PER_QUERY).flatMap { aliases.getAliases(it) }.associateBy { it.aliasId }
        val unaliased = ids.filter { it !in phoneAliases }
        val catalogRows = unaliased.chunked(MAX_IDS_PER_QUERY).flatMap { database.localEpisodeCatalogDao().findOwners(it) }.associateBy { it.episodeId }
        val decisions = restored.associateWith { LibraryBackupIdentityLogic.decide(it, phoneAliases[it.aliasId], catalogRows[it.aliasId]) }
        val inserted = decisions.filterValues { it == AliasRestoreDecision.INSERT }.keys.toList()
        inserted.chunked(MAX_IDS_PER_QUERY).forEach { chunk -> aliases.insertAliasesIfAbsent(chunk.map { it.copy(owner = false) }) }
        restoreOwners(inserted)
        return decisions.filterValues { it == AliasRestoreDecision.REFUSE }.keys.mapTo(HashSet()) { it.aliasId }
    }

    /** The backup's owner of each restored episode that has no owner on the phone. */
    private suspend fun restoreOwners(inserted: List<EpisodeAliasEntity>) {
        val owners =
            inserted
                .filter { it.owner }
                .groupBy { it.podcastId to it.catalogKey!! }
                .mapNotNull { (episode, marked) -> marked.singleOrNull()?.let { episode to it.aliasId } }
        for ((episode, ownerId) in owners) {
            val (podcastId, catalogKey) = episode
            val phoneHasOwner = aliases.aliasesForKey(podcastId, catalogKey).any { it.owner && it.state == EpisodeAliasState.TRUSTED }
            if (!phoneHasOwner) aliases.setOwner(podcastId, catalogKey, ownerId)
        }
    }

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
    }
}
