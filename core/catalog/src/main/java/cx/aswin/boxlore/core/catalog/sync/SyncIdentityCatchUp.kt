package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.sync.SyncEpisodeIdentity.IncomingRow
import cx.aswin.boxlore.core.catalog.sync.SyncEpisodeIdentity.OutgoingRow
import cx.aswin.boxlore.core.catalog.sync.SyncEpisodeIdentity.WireIdentity
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncEpisodeIdentityFields
import cx.aswin.boxlore.core.network.model.SyncPullResponse

/**
 * What the one-time identity catch-up pushes back ([identityCatchUp]): pulled history rows and the
 * pulled queue, each exactly as the cloud sent it plus the identity fields it lacked.
 *
 * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, the one-time sync catch-up).
 * COMPAT(own-ids): cloud rows of older builds gain their episode identity. Permanent: never remove (MIGRATION.md §5).
 */
internal data class IdentityCatchUp(val history: List<ListeningHistorySyncDto>, val queue: QueueSyncDto?) {
    val isEmpty: Boolean get() = history.isEmpty() && queue == null
}

/**
 * The one-time identity catch-up's push for [pulled], a pull from the start that was just merged:
 * each pulled history row whose cloud copy lacks the identity this phone knows for its ID
 * ([SyncEpisodeIdentity.outgoing]), exactly as the cloud sent it plus the missing fields, and the
 * pulled queue the same way when one of its items gains an identity (else null). Every other field,
 * `updatedAt` included, is the cloud's own, so the server keeps its values (it applies an equal
 * `updatedAt` and keeps stored identity fields a push leaves out) and no other device's delta pull
 * sees a change. The queue goes without rich removals, so the server keeps its list and expands
 * none of it again. A copy that already names another key or recipe ID is left as it is.
 *
 * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, the one-time sync catch-up).
 * COMPAT(own-ids): cloud rows of older builds gain their episode identity. Permanent: never remove (MIGRATION.md §5).
 */
internal suspend fun SyncEpisodeIdentity.identityCatchUp(pulled: SyncPullResponse): IdentityCatchUp {
    val queue = pulled.queue
    val queueItems = queue?.items.orEmpty()
    val added = missingIdentities(pulled.history.map { incomingOf(it) } + queueItems.map { incomingOf(it) })
    if (added.isEmpty()) return IdentityCatchUp(emptyList(), null)
    val history = pulled.history.mapNotNull { dto ->
        added[incomingOf(dto)]?.let { dto.copy(recipeId = it.recipeId ?: dto.recipeId, catalogKey = it.catalogKey ?: dto.catalogKey) }
    }
    val queueBack = queue?.takeIf { queueItems.any { incomingOf(it) in added } }?.copy(
        items = queueItems.map { dto ->
            added[incomingOf(dto)]?.let { dto.copy(recipeId = it.recipeId ?: dto.recipeId, catalogKey = it.catalogKey ?: dto.catalogKey) } ?: dto
        },
        recentRemovedEpisodes = null,
    )
    return IdentityCatchUp(history, queueBack)
}

/** For each of [rows] whose cloud copy lacks some of the identity this phone would send, the fields to add. */
private suspend fun SyncEpisodeIdentity.missingIdentities(rows: Collection<IncomingRow>): Map<IncomingRow, WireIdentity> {
    val distinct = rows.distinct()
    if (distinct.isEmpty()) return emptyMap()
    val known = outgoing(distinct.map { OutgoingRow(it.episodeId, it.podcastId) })
    return distinct.mapNotNull { row ->
        known[OutgoingRow(row.episodeId, row.podcastId)]?.let { missingFrom(row, it) }?.let { row to it }
    }.toMap()
}

/**
 * The fields of [mine] that [row]'s cloud copy lacks; null when it lacks none, when this phone
 * knows no key for it, or when the copy names another key or recipe ID (another device's reading,
 * never overwritten).
 */
private fun missingFrom(row: IncomingRow, mine: WireIdentity): WireIdentity? {
    val key = mine.catalogKey ?: return null
    val storedKey = SyncEpisodeIdentityFields.catalogKeyOrNull(row.catalogKey)
    val storedRecipe = SyncEpisodeIdentityFields.recipeIdOrNull(row.recipeId)
    if (storedKey != null && storedKey != key) return null
    if (storedRecipe != null && storedRecipe != mine.recipeId) return null
    val added = WireIdentity(recipeId = mine.recipeId.takeIf { storedRecipe == null }, catalogKey = key.takeIf { storedKey == null })
    return added.takeUnless { it == WireIdentity.NONE }
}

private fun incomingOf(dto: ListeningHistorySyncDto) = IncomingRow(dto.episodeId, dto.podcastId, dto.recipeId, dto.catalogKey)

private fun incomingOf(dto: QueueItemSyncDto) = IncomingRow(dto.episodeId, dto.podcastId, dto.recipeId, dto.catalogKey)
