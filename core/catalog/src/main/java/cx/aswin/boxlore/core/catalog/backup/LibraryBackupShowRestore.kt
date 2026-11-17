package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast

/** Builds the followed show a JSON backup row restores, from what the backup file itself holds. */
internal object LibraryBackupShowRestore {
    /** The show as the backup saved it; Gson can write JSON null into its non-null fields. */
    fun podcastFromBackup(entity: PodcastEntity): Podcast = Podcast(
        id = (entity.podcastId as String?) ?: "",
        title = (entity.title as String?) ?: "Unknown",
        artist = (entity.author as String?) ?: "Unknown",
        imageUrl = (entity.imageUrl as String?) ?: "",
        description = entity.description,
        genre = entity.genre ?: "Podcast",
        type = (entity.type as String?) ?: "episodic",
        latestEpisode = entity.latestEpisode,
        subscribedAt = entity.subscribedAt,
        podcastGuid = entity.podcastGuid,
        fundingUrl = entity.fundingUrl,
        fundingMessage = entity.fundingMessage,
        medium = entity.medium,
        hasValue = entity.hasValue,
        updateFrequency = entity.updateFrequency,
        location = entity.location,
        license = entity.license,
        isLocked = entity.isLocked,
        preferredSort = entity.preferredSort,
        skipBeginningOverrideMs = entity.skipBeginningOverrideMs,
        skipEndingOverrideMs = entity.skipEndingOverrideMs,
        sourceType = (entity.sourceType as String?) ?: PodcastEntity.SOURCE_PODCAST_INDEX,
        feedUrl = entity.feedUrl,
        rssRefreshCapability = (entity.rssRefreshCapability as String?) ?: PodcastEntity.RSS_REFRESH_MANUAL,
        rssCatalogStale = entity.rssCatalogStale,
        rssHasNewEpisodes = entity.rssHasNewEpisodes,
        linkedPodcastIndexId = entity.linkedPodcastIndexId,
        customGenre = entity.customGenre,
        customGenreIcon = entity.customGenreIcon,
    )

    /**
     * An `rss:` show whose feed could not be read during the restore, followed from the backup's
     * saved title, address and artwork. Its catalog is marked stale, so the normal RSS refresh reads
     * the feed later; its history and listening restore with it. Null when the row has no address
     * or no `rss:` ID to keep.
     */
    fun unreadRssPodcast(entity: PodcastEntity): Podcast? {
        val id = (entity.podcastId as String?) ?: return null
        if (!id.startsWith(RSS_ID_PREFIX) || entity.feedUrl.isNullOrBlank()) return null
        return podcastFromBackup(entity).copy(sourceType = PodcastEntity.SOURCE_RSS, rssCatalogStale = true)
    }

    private const val RSS_ID_PREFIX = "rss:"
}
