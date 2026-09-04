package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.database.ListeningHistoryEntity

internal object LibraryBackupHistoryRestore {
    /**
     * Gson can populate null into historical non-null Kotlin string fields.
     * Rebuild the row with safe display defaults while preserving every
     * listener-state and completion-provenance field.
     */
    fun sanitize(entity: ListeningHistoryEntity): ListeningHistoryEntity = ListeningHistoryEntity(
        episodeId = (entity.episodeId as String?) ?: "",
        podcastId = (entity.podcastId as String?) ?: "",
        episodeTitle = (entity.episodeTitle as String?) ?: "Unknown",
        episodeImageUrl = entity.episodeImageUrl,
        podcastImageUrl = entity.podcastImageUrl,
        episodeAudioUrl = entity.episodeAudioUrl,
        podcastName = (entity.podcastName as String?) ?: "Unknown",
        progressMs = entity.progressMs,
        durationMs = entity.durationMs,
        isCompleted = entity.isCompleted,
        isLiked = entity.isLiked,
        lastPlayedAt = entity.lastPlayedAt,
        isDirty = entity.isDirty,
        syncedAt = entity.syncedAt,
        enclosureType = entity.enclosureType,
        isManualCompletion = entity.isManualCompletion,
        isBulkCompletion = entity.isBulkCompletion,
        episodeDescription = entity.episodeDescription,
    )
}
