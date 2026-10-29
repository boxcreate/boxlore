package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Bundled params for [PodcastDao.updateRssState] — Room's `@Query` bind parameters can't
 * reference object fields (no `:state.field` syntax), so this is applied via a partial-entity
 * [Update] instead: field names below must match [PodcastEntity] column names exactly, and
 * [podcastId] doubles as the `@PrimaryKey` Room matches the update against.
 */
data class RssFeedStateUpdate(
    val podcastId: String,
    val feedEtag: String?,
    val feedLastModified: String?,
    val feedDeclaredUpdatedAt: Long?,
    val rssRefreshCapability: String,
    val lastRssSyncAt: Long,
    val rssCatalogStale: Boolean,
    val rssHasNewEpisodes: Boolean,
)

@Dao
interface PodcastDao {

    /**
     * Inserts [podcast], or updates the stored row while keeping its Room v39 columns
     * ([PodcastEntity.keepingFeedIdentityColumnsOf]). Subscribe, restore and sync build the row
     * from scratch, and the v39 upgrade seeds `epNs` only once, so a plain upsert would reset it.
     * Those columns change only through their own updates.
     *
     * Changed in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns).
     * COMPAT(own-ids): keeps the seeded namespace. Permanent: never remove (MIGRATION.md §5).
     */
    @Transaction
    suspend fun upsert(podcast: PodcastEntity) {
        val stored = getPodcast(podcast.podcastId)
        upsertRow(if (stored == null) podcast else podcast.keepingFeedIdentityColumnsOf(stored))
    }

    /**
     * Writes every column of [podcast] as given, the v39 columns included. Use [upsert], which keeps
     * those columns of a stored row.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns).
     */
    @Upsert
    suspend fun upsertRow(podcast: PodcastEntity)

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY title ASC")
    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>>

    // Suspend version for Android Auto browse tree (non-Flow, one-shot)
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY title ASC")
    suspend fun getSubscribedPodcastsList(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 AND sourceType = 'rss' ORDER BY title ASC")
    suspend fun getSubscribedRssPodcasts(): List<PodcastEntity>

    /** Includes disabled and unsubscribed rows for migration of device-owned notification registrations. */
    @Query("SELECT * FROM podcasts WHERE podcastId LIKE 'rss:%' ORDER BY podcastId ASC")
    suspend fun getRssNotificationRows(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 AND sourceType = 'podcast_index' ORDER BY title ASC")
    suspend fun getSubscribedPodcastIndexPodcasts(): List<PodcastEntity>

    /**
     * Followed RSS shows that replaced a Podcast Index show the listener no longer follows
     * (`linkedPodcastIndexId`), watched: listening read by show counts the old show's rows as the
     * RSS show's, because switching keeps every row under its stored show.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
     */
    @Query(
        """
        SELECT * FROM podcasts
        WHERE isSubscribed = 1 AND sourceType = 'rss' AND linkedPodcastIndexId IS NOT NULL
          AND linkedPodcastIndexId NOT IN (SELECT podcastId FROM podcasts WHERE isSubscribed = 1)
        """,
    )
    fun observeSwitchedRssShows(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE linkedPodcastIndexId = :podcastIndexId LIMIT 1")
    suspend fun getRssPodcastLinkedTo(podcastIndexId: String): PodcastEntity?

    /**
     * The followed `rss:` feed linked to [podcastIndexId], when several feed rows (one unfollowed)
     * are linked to it.
     *
     * Added for renumbered shows (docs/EPISODE_IDS.md §5, renumbered shows).
     */
    @Query(
        """
        SELECT * FROM podcasts
        WHERE linkedPodcastIndexId = :podcastIndexId AND sourceType = 'rss' AND isSubscribed = 1
        ORDER BY subscribedAt DESC LIMIT 1
        """,
    )
    suspend fun getSubscribedRssPodcastLinkedTo(podcastIndexId: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE podcastId = :id")
    suspend fun getPodcast(id: String): PodcastEntity?

    /**
     * Runs [move] in one transaction: it reads the old and new show rows of a follow moving from a
     * show Podcast Index renumbered, merges them and writes both through [upsert] (so each keeps its
     * stored namespace), with no other writer (a cloud pull, a toggle, the foreground pass) landing
     * between the read and the write. The caller writes both rows dirty, so the next sync push
     * carries the follow and the unfollow.
     *
     * Added for renumbered shows (`RenumberedShowFollows` in `:core:catalog`).
     */
    @Transaction
    suspend fun moveRenumberedFollow(move: suspend () -> Unit) {
        move()
    }

    @Query("SELECT * FROM podcasts WHERE podcastId IN (:ids)")
    suspend fun getPodcastsByIds(ids: List<String>): List<PodcastEntity>

    @Query(
        """
        UPDATE podcasts
        SET isSubscribed = :isSubscribed,
            subscribedAt = CASE WHEN :isSubscribed = 1 THEN :now ELSE 0 END,
            unsubscribedAt = CASE WHEN :isSubscribed = 0 THEN :now ELSE 0 END,
            isDirty = 1,
            customGenre = CASE WHEN :isSubscribed = 0 THEN NULL ELSE customGenre END,
            customGenreIcon = CASE WHEN :isSubscribed = 0 THEN NULL ELSE customGenreIcon END
        WHERE podcastId = :id
        """,
    )
    suspend fun setSubscribed(id: String, isSubscribed: Boolean, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE podcasts
        SET isSubscribed = 0,
            subscribedAt = 0,
            unsubscribedAt = :now,
            isDirty = 1,
            notificationsEnabled = 0,
            autoDownloadEnabled = 0,
            customGenre = NULL,
            customGenreIcon = NULL
        WHERE podcastId = :id
        """,
    )
    suspend fun retireLinkedPodcastIndexSubscription(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM rss_episodes WHERE podcastId = :podcastId")
    suspend fun deleteRssEpisodes(podcastId: String)

    @Query("UPDATE podcasts SET latestEpisode = :episode WHERE podcastId = :id")
    suspend fun updateLatestEpisode(id: String, episode: cx.aswin.boxlore.core.model.Episode?)

    @Query("UPDATE podcasts SET preferredSort = :sort, type = :type, isDirty = 1 WHERE podcastId = :id")
    suspend fun updatePreferredSortAndType(id: String, sort: String?, type: String)

    @Query("UPDATE podcasts SET notificationsEnabled = :enabled, isDirty = 1 WHERE podcastId = :id")
    suspend fun setNotificationsEnabled(id: String, enabled: Boolean)

    @Query("UPDATE podcasts SET autoDownloadEnabled = :enabled, isDirty = 1 WHERE podcastId = :id")
    suspend fun setAutoDownloadEnabled(id: String, enabled: Boolean)

    @Query(
        """
        UPDATE podcasts
        SET skipBeginningOverrideMs = :skipBeginningMs,
            skipEndingOverrideMs = :skipEndingMs,
            isDirty = 1
        WHERE podcastId = :id
        """,
    )
    suspend fun setPlaybackSkipOverrides(id: String, skipBeginningMs: Long?, skipEndingMs: Long?,)

    /** Partial-entity update — see [RssFeedStateUpdate] for why this isn't a plain `@Query`. */
    @Update(entity = PodcastEntity::class)
    suspend fun updateRssState(state: RssFeedStateUpdate)

    @Query("SELECT * FROM podcasts WHERE notificationsEnabled = 1")
    suspend fun getNotificationEnabledPodcasts(): List<PodcastEntity>

    /** Clears the "new episodes" badge once the user has opened/dismissed the podcast. */
    @Query("UPDATE podcasts SET rssHasNewEpisodes = 0 WHERE podcastId = :id")
    suspend fun clearRssNewEpisodesFlag(id: String)

    /**
     * Sets the shared "new episodes" badge (RSS freshness or PI direct-feed tip promote).
     * Cleared via [clearRssNewEpisodesFlag] when the tip is seen.
     */
    @Query("UPDATE podcasts SET rssHasNewEpisodes = 1 WHERE podcastId = :id")
    suspend fun markHasNewEpisodes(id: String)

    /**
     * Saves a publisher HTTPS feed URL found on this device (address recovery, auto-download,
     * library restore) and marks the row dirty so the next sync push sends it, like the other
     * single-field edits. Before, the corrected address stayed on this device only. An unchanged
     * address writes nothing, so repeated recovery passes don't re-push.
     */
    @Query(
        """
        UPDATE podcasts SET feedUrl = :feedUrl, isDirty = 1
        WHERE podcastId = :id AND (feedUrl IS NULL OR feedUrl != :feedUrl)
        """,
    )
    suspend fun setFeedUrl(id: String, feedUrl: String,)

    /**
     * Replaces a show's saved feed address in one transaction: the publisher and transport
     * addresses, where the address came from and when the registry last confirmed it (a write the
     * registry didn't confirm keeps the stored daily-check stamp), the old address kept for undo, every stored ETag and Last-Modified for the show (so the next read
     * fetches the new address in full), and the dirty flag when [FeedAddressWrite.markDirty].
     * Compare-and-set: nothing is written unless the row still holds [FeedAddressWrite.expectedFeedUrl].
     * Never for `rss:` shows, whose address is their identity.
     *
     * @return true when the address was replaced
     *
     * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
     * COMPAT(own-ids): the one address writer. Permanent: never remove (MIGRATION.md §5).
     */
    @Transaction
    suspend fun replaceFeedAddress(write: FeedAddressWrite): Boolean {
        val replaced =
            replaceFeedAddressRow(
                id = write.podcastId,
                expectedFeedUrl = write.expectedFeedUrl,
                feedUrl = write.feedUrl,
                transportUrl = write.transportUrl,
                previousFeedUrl = write.expectedFeedUrl,
                source = write.source,
                checkedAt = write.checkedAt,
                markDirty = write.markDirty,
            ) == 1
        if (replaced) {
            clearCatalogFeedValidators(write.podcastId)
            clearSupplementFeedValidators(write.podcastId)
        }
        return replaced
    }

    /** The row update of [replaceFeedAddress]; use that, which also clears the show's other validators. */
    @Query(
        """
        UPDATE podcasts
        SET feedUrl = :feedUrl,
            transportUrl = :transportUrl,
            previousFeedUrl = :previousFeedUrl,
            feedUrlSource = :source,
            feedUrlCheckedAt = CASE WHEN :checkedAt > 0 THEN :checkedAt ELSE feedUrlCheckedAt END,
            feedEtag = NULL,
            feedLastModified = NULL,
            isDirty = CASE WHEN :markDirty THEN 1 ELSE isDirty END
        WHERE podcastId = :id
          AND sourceType != 'rss'
          AND podcastId NOT LIKE 'rss:%'
          AND feedUrl IS :expectedFeedUrl
        """,
    )
    @Suppress("LongParameterList", "kotlin:S107") // Room binds each column as its own parameter.
    suspend fun replaceFeedAddressRow(
        id: String,
        expectedFeedUrl: String?,
        feedUrl: String,
        transportUrl: String?,
        previousFeedUrl: String?,
        source: String?,
        checkedAt: Long,
        markDirty: Boolean,
    ): Int

    /** Clears the catalog read's validators so the next read of a new address is a full fetch. */
    @Query("UPDATE local_episode_feeds SET feedEtag = NULL, feedLastModified = NULL WHERE podcastId = :podcastId")
    suspend fun clearCatalogFeedValidators(podcastId: String)

    /** Clears the feed-only extras' validators so the next read of a new address is a full fetch. */
    @Query("UPDATE episode_supplements SET feedEtag = NULL, feedLastModified = NULL WHERE podcastId = :podcastId")
    suspend fun clearSupplementFeedValidators(podcastId: String)

    /**
     * Records that the show's saved address was checked against the address registry at [atMillis]
     * (the daily check, or its `/podcast` fallback). Leaves `isDirty` alone: nothing user-visible changed.
     *
     * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: daily check).
     * COMPAT(own-ids): one daily stamp shared by the check and its fallback. Permanent: never remove (MIGRATION.md §5).
     */
    @Query("UPDATE podcasts SET feedUrlCheckedAt = :atMillis WHERE podcastId = :id")
    suspend fun markFeedUrlChecked(id: String, atMillis: Long,)

    /**
     * Marks a followed show for the next sync push, for a saved address this phone keeps over an
     * older one a pull brought, so the account gets the corrected address back.
     *
     * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
     */
    @Query("UPDATE podcasts SET isDirty = 1 WHERE podcastId = :id AND isSubscribed = 1")
    suspend fun markFollowedDirty(id: String)

    /**
     * [upsert] for a whole-row write built from a row read earlier (sync merges): when another writer
     * replaced the saved address since, that address, its bookkeeping and its pending push are kept
     * instead of being written back over.
     *
     * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
     * COMPAT(own-ids): the one address writer stays the only one. Permanent: never remove (MIGRATION.md §5).
     */
    @Transaction
    suspend fun upsertKeepingFeedAddress(podcast: PodcastEntity) {
        val stored = getPodcast(podcast.podcastId)
        if (stored == null) {
            upsertRow(podcast)
            return
        }
        val row =
            if (stored.feedUrl == podcast.feedUrl) {
                podcast
            } else {
                podcast.copy(feedUrl = stored.feedUrl, isDirty = podcast.isDirty || stored.isDirty)
            }
        upsertRow(row.keepingFeedIdentityColumnsOf(stored))
    }

    /**
     * The show's stored episode namespace (`epNs`), or null when the show has no row or none was set.
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, recipe IDs; decision D12).
     * COMPAT(own-ids): new recipe IDs are minted in the stored namespace. Permanent: never remove (MIGRATION.md §5).
     */
    @Query("SELECT epNs FROM podcasts WHERE podcastId = :id LIMIT 1")
    suspend fun getEpNs(id: String): Long?

    /**
     * Sets the episode namespace of a Podcast Index show that has none. A stored value is never
     * replaced: recipe IDs already issued depend on it. Leaves `isDirty` alone (the namespace is
     * not synced). `rss:` shows never get one.
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, recipe IDs; decision D12).
     * COMPAT(own-ids): an issued namespace never changes. Permanent: never remove (MIGRATION.md §5).
     */
    @Query("UPDATE podcasts SET epNs = :epNs WHERE podcastId = :id AND epNs IS NULL AND sourceType != 'rss'")
    suspend fun setEpNsIfUnset(id: String, epNs: Long,)

    @Query("UPDATE podcasts SET customGenre = :customGenre, customGenreIcon = :customGenreIcon, isDirty = 1 WHERE podcastId = :id")
    suspend fun updateCustomGenre(id: String, customGenre: String?, customGenreIcon: String?)

    @Query("UPDATE podcasts SET customGenre = NULL, customGenreIcon = NULL, isDirty = 1 WHERE podcastId = :id")
    suspend fun clearCustomGenre(id: String)

    @Query("SELECT * FROM podcasts WHERE isDirty = 1")
    suspend fun getDirtyPodcasts(): List<PodcastEntity>

    @Query("SELECT COUNT(*) FROM podcasts WHERE isDirty = 1")
    fun getDirtyCountFlow(): Flow<Int>

    @Query("UPDATE podcasts SET isDirty = 1 WHERE isSubscribed = 1")
    suspend fun markAllSubscribedPodcastsDirty(): Int

    @Query("UPDATE podcasts SET isDirty = 1 WHERE isSubscribed = 1 AND syncedAt = 0")
    suspend fun markAllUnsyncedSubscribedPodcastsDirty(): Int

    @Query("UPDATE podcasts SET isDirty = 0, syncedAt = :timestamp WHERE podcastId IN (:ids)")
    suspend fun markPodcastsSynced(ids: List<String>, timestamp: Long)

    @Query(
        """
        UPDATE podcasts 
        SET isDirty = 0, syncedAt = :syncedAt 
        WHERE podcastId = :id 
          AND isSubscribed = :snapshotIsSubscribed
          AND subscribedAt = :snapshotSubscribedAt 
          AND unsubscribedAt = :snapshotUnsubscribedAt
          AND autoDownloadEnabled = :snapshotAutoDownload
          AND notificationsEnabled = :snapshotNotifications
          AND (customGenre = :snapshotCustomGenre OR (customGenre IS NULL AND :snapshotCustomGenre IS NULL))
          AND (feedUrl = :snapshotFeedUrl OR (feedUrl IS NULL AND :snapshotFeedUrl IS NULL))
        """,
    )
    @Suppress("LongParameterList", "kotlin:S107")
    suspend fun markPodcastSyncedIfUnchanged(
        id: String,
        snapshotIsSubscribed: Boolean,
        snapshotSubscribedAt: Long,
        snapshotUnsubscribedAt: Long,
        snapshotAutoDownload: Boolean,
        snapshotNotifications: Boolean,
        snapshotCustomGenre: String?,
        snapshotFeedUrl: String?,
        syncedAt: Long,
    ): Int

    @Query(
        """
        UPDATE podcasts
        SET isSubscribed = 0,
            subscribedAt = 0,
            unsubscribedAt = 0,
            isDirty = 0,
            notificationsEnabled = 0,
            autoDownloadEnabled = 0,
            customGenre = NULL,
            customGenreIcon = NULL
        """,
    )
    suspend fun clearAllSubscriptionsForAccountSwitch(): Int
}
