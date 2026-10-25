package cx.aswin.boxlore.core.database

/**
 * Rows the way older builds stored them. Each map lists the columns of every shipped schema;
 * [RoomSchemaFixture.insert] keeps only those the table at hand has.
 */
internal object LegacyRows {
    fun podcast(id: String, subscribed: Boolean, sourceType: String = PodcastEntity.SOURCE_PODCAST_INDEX,): Map<String, Any?> =
        mapOf(
            "podcastId" to id, "title" to "Show $id", "author" to "Author", "imageUrl" to "https://img/$id.jpg",
            "description" to null, "isSubscribed" to subscribed.int(), "subscribedAt" to 10L, "unsubscribedAt" to 0L,
            "isDirty" to 0, "syncedAt" to 0L, "genre" to null, "type" to "episodic", "lastRefreshed" to 0L,
            "hasValue" to 0, "isLocked" to 0, "notificationsEnabled" to 0, "autoDownloadEnabled" to 0,
            "sourceType" to sourceType, "feedUrl" to "https://feeds.example/$id.xml", "rssRefreshCapability" to "manual",
            "lastRssSyncAt" to 0L, "rssCatalogStale" to 0, "rssHasNewEpisodes" to 0,
        )

    fun localEpisode(id: String, podcastId: String, key: String, audioUrl: String = "https://cdn.example/$id.mp3",): Map<String, Any?> =
        mapOf(
            "episodeId" to id,
            "podcastId" to podcastId,
            "guid" to key,
            "title" to "Episode $id",
            "description" to "",
            "audioUrl" to audioUrl,
            "duration" to 60,
            "publishedDate" to 100L,
        )

    fun feed(podcastId: String, ttlExpiresAt: Long? = null,): Map<String, Any?> =
        mapOf(
            "podcastId" to podcastId, "feedUrl" to "https://feeds.example/$podcastId.xml", "feedEtag" to "etag",
            "fetchedAt" to 5L, "itemCount" to 3, "feedOrder" to "newest_first", "ttlExpiresAt" to ttlExpiresAt,
            "needsFullBackfill" to 0, "copiedExtrasCount" to 0, "ready" to 1, "feedUrlLookupAt" to 0L,
        )

    fun history(id: String, podcastId: String, lastPlayedAt: Long = 1_000L, likedAt: Long = 0L,): Map<String, Any?> =
        mapOf(
            "episodeId" to id, "podcastId" to podcastId, "episodeTitle" to "Episode $id",
            "episodeImageUrl" to "https://img/e.jpg", "podcastImageUrl" to "https://img/p.jpg",
            "episodeAudioUrl" to "https://cdn.example/$id.mp3", "podcastName" to "Show", "progressMs" to 1_234L,
            "durationMs" to 60_000L, "isCompleted" to 0, "isLiked" to (likedAt > 0).int(), "likedAt" to likedAt,
            "lastPlayedAt" to lastPlayedAt, "isDirty" to 1, "syncedAt" to 0L, "isManualCompletion" to 0,
            "isBulkCompletion" to 0,
        )

    fun queueItem(id: String, podcastId: String, position: Int,): Map<String, Any?> =
        mapOf(
            "episodeId" to id, "title" to "Episode $id", "podcastId" to podcastId, "podcastTitle" to "Show",
            "podcastGenre" to "", "podcastArtist" to "", "imageUrl" to null, "audioUrl" to "https://cdn.example/$id.mp3",
            "duration" to 60, "pubDate" to 100L, "description" to null, "position" to position, "addedAt" to 7L,
            "contextType" to "MANUAL",
        )

    fun download(id: String, podcastId: String, status: Int = DownloadedEpisodeEntity.STATUS_COMPLETED,): Map<String, Any?> =
        mapOf(
            "episodeId" to id, "podcastId" to podcastId, "episodeTitle" to "Episode $id", "podcastName" to "Show",
            "durationMs" to 60_000L, "publishedDate" to 100L, "localFilePath" to "CACHED", "downloadId" to 0L,
            "downloadedAt" to 9L, "sizeBytes" to 456L, "status" to status, "isSmartDownloaded" to 0,
            "downloadOrigin" to DownloadedEpisodeEntity.ORIGIN_MANUAL,
        )

    fun rssEpisode(id: String, podcastId: String,): Map<String, Any?> =
        mapOf(
            "episodeId" to id,
            "podcastId" to podcastId,
            "guid" to "rss-guid-$id",
            "title" to "RSS $id",
            "description" to "",
            "audioUrl" to "https://cdn.example/rss$id.mp3",
            "duration" to 60,
            "publishedDate" to 100L,
        )

    fun supplement(podcastId: String): Map<String, Any?> =
        mapOf(
            "podcastId" to podcastId,
            "feedUrl" to "https://feeds.example/$podcastId.xml",
            "rssNamespaceId" to "rss:namespace",
            "fetchedAt" to 4L,
        )

    fun supplementItem(id: String, podcastId: String, guid: String?, audioUrl: String,): Map<String, Any?> =
        mapOf(
            "episodeId" to id,
            "podcastId" to podcastId,
            "guid" to guid,
            "title" to "Extra $id",
            "description" to "",
            "audioUrl" to audioUrl,
            "duration" to 60,
            "publishedDate" to 100L,
        )

    fun queueMetadata(removed: String?): Map<String, Any?> =
        mapOf(
            "id" to 1,
            "queueUpdatedAt" to 3L,
            "queueSequence" to 2L,
            "lastModifiedDeviceId" to "device",
            "isDirty" to 1,
            "syncedAt" to 0L,
            "recentRemovedEpisodeIds" to removed,
        )

    private fun Boolean.int(): Int = if (this) 1 else 0
}
