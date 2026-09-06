package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SubscriptionRepository(
    private val podcastDao: PodcastDao,
    private val localEpisodeCatalog: LocalEpisodeCatalogPort? = null,
    private val lookupHttpsFeedUrl: (suspend (String) -> String?)? = null,
) {
    val subscribedPodcastIds: Flow<Set<String>> =
        podcastDao
            .getSubscribedPodcasts()
            .map { list -> list.map { it.podcastId }.toSet() }

    fun getAllSubscribedPodcasts(): Flow<List<PodcastEntity>> = podcastDao.getSubscribedPodcasts()

    /** Room row for tip / feed-url lookups (Home direct-feed sync). */
    suspend fun getPodcastEntity(podcastId: String): PodcastEntity? = podcastDao.getPodcast(podcastId)

    val subscribedPodcasts: Flow<List<Podcast>> =
        podcastDao
            .getSubscribedPodcasts()
            .map { list -> list.map { it.toPodcast() } }

    suspend fun toggleSubscription(podcast: Podcast) {
        val existing = podcastDao.getPodcast(podcast.id)
        val linkedRss =
            if (!podcast.isRss) {
                podcastDao.getRssPodcastLinkedTo(podcast.id)
            } else {
                null
            }
        val activeEntity = linkedRss?.takeIf { it.isSubscribed } ?: existing
        val isCurrentlySubscribed = activeEntity?.isSubscribed == true

        if (isCurrentlySubscribed) {
            unsubscribeInternal(podcast, checkNotNull(activeEntity), existing)
        } else {
            // Subscribe (Upsert to ensure we have data for offline/Jump Back In)
            val entity =
                PodcastEntity(
                    podcastId = podcast.id,
                    title = podcast.title,
                    author = podcast.artist,
                    imageUrl = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: existing?.imageUrl ?: "",
                    description = podcast.description,
                    isSubscribed = true,
                    subscribedAt = System.currentTimeMillis(),
                    genre = podcast.genre, // Persist genre for Smart Queue matching
                    type = podcast.type,
                    lastRefreshed = System.currentTimeMillis(),
                    latestEpisode =
                    podcast.latestEpisode?.let { ep ->
                        ep.copy(
                            podcastId = ep.podcastId.takeIf { !it.isNullOrBlank() } ?: podcast.id,
                            podcastTitle = ep.podcastTitle.takeIf { !it.isNullOrBlank() } ?: podcast.title,
                        )
                    },
                    podcastGuid = podcast.podcastGuid,
                    fundingUrl = podcast.fundingUrl,
                    fundingMessage = podcast.fundingMessage,
                    medium = podcast.medium,
                    hasValue = podcast.hasValue,
                    updateFrequency = podcast.updateFrequency,
                    location = podcast.location,
                    license = podcast.license,
                    isLocked = podcast.isLocked,
                    preferredSort = existing?.preferredSort, // Preserve existing sort preference
                    notificationsEnabled = false, // Off by default
                    autoDownloadEnabled = false,
                    skipBeginningOverrideMs =
                    existing?.skipBeginningOverrideMs
                        ?: podcast.skipBeginningOverrideMs,
                    skipEndingOverrideMs =
                    existing?.skipEndingOverrideMs
                        ?: podcast.skipEndingOverrideMs,
                    sourceType = podcast.sourceType,
                    feedUrl = podcast.feedUrl,
                    rssRefreshCapability = podcast.rssRefreshCapability,
                    rssCatalogStale = podcast.rssCatalogStale,
                    rssHasNewEpisodes = podcast.rssHasNewEpisodes,
                    linkedPodcastIndexId = podcast.linkedPodcastIndexId,
                )
            podcastDao.upsert(entity)
            localEpisodeCatalog?.setUnsubscribedTtl(podcast.id, null)
            recoverFeedUrlIfMissing(podcast)
            RankingFeedbackRepository.getIfInitialized()?.recordAction(
                target =
                FeedbackTarget(
                    episodeId = podcast.latestEpisode?.id ?: "podcast:${podcast.id}",
                    podcastId = podcast.id,
                    genre = podcast.recommendationGenre,
                ),
                action = RankingAction.SUBSCRIBE,
            )
        }
    }

    private suspend fun unsubscribeInternal(podcast: Podcast, target: PodcastEntity, existing: PodcastEntity?) {
        val updated =
            target.copy(
                isSubscribed = false,
                subscribedAt = 0L,
                notificationsEnabled = false,
                autoDownloadEnabled = false,
                customGenre = null,
                customGenreIcon = null,
            )
        podcastDao.upsert(updated)
        if (existing != null && existing.podcastId != target.podcastId) {
            podcastDao.clearCustomGenre(existing.podcastId)
        }
        if (target.isRss) podcastDao.deleteRssEpisodes(target.podcastId)
        if (!podcast.isRss) {
            updateFirebaseSubscription(podcast.id, podcast.title, podcast.imageUrl, false)
            localEpisodeCatalog?.setUnsubscribedTtl(
                podcast.id,
                System.currentTimeMillis() + LocalEpisodeCatalogRepository.UNSUBSCRIBE_TTL_MS,
            )
        }
        RankingFeedbackRepository.getIfInitialized()?.recordAction(
            target =
            FeedbackTarget(
                episodeId = podcast.latestEpisode?.id ?: "podcast:${podcast.id}",
                podcastId = podcast.id,
                genre = podcast.recommendationGenre,
            ),
            action = RankingAction.UNSUBSCRIBE,
        )
    }

    suspend fun isSubscribed(podcastId: String): Boolean {
        if (podcastDao.getPodcast(podcastId)?.isSubscribed == true) return true
        return podcastDao.getRssPodcastLinkedTo(podcastId)?.isSubscribed == true
    }

    suspend fun subscribe(podcast: Podcast) {
        subscribeInternal(podcast, restoredSubscribedAt = null, recordFeedback = true)
    }

    /**
     * Restores a subscription without making the import look like a new listener action.
     *
     * Valid historical timestamps are preserved. Missing or future timestamps fall back
     * to the current time for compatibility with older or malformed backups.
     */
    internal suspend fun restoreSubscription(podcast: Podcast, subscribedAt: Long,) {
        subscribeInternal(
            podcast = podcast,
            restoredSubscribedAt = subscribedAt,
            recordFeedback = false,
        )
    }

    private suspend fun subscribeInternal(podcast: Podcast, restoredSubscribedAt: Long?, recordFeedback: Boolean,) {
        if (!podcast.isRss && podcastDao.getRssPodcastLinkedTo(podcast.id)?.isSubscribed == true) {
            return
        }
        val now = System.currentTimeMillis()
        val existing = podcastDao.getPodcast(podcast.id)
        val isNewSubscription = existing?.isSubscribed != true
        val validRestoredSubscribedAt = restoredSubscribedAt?.takeIf { it in 1..now }
        val preferredSortVal = existing?.preferredSort ?: if (podcast.type == "serial") "oldest" else "newest"
        val typeVal = if (preferredSortVal == "oldest" || podcast.type == "serial") "serial" else "episodic"
        val entity =
            PodcastEntity(
                podcastId = podcast.id,
                title = podcast.title,
                author = podcast.artist,
                imageUrl = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: existing?.imageUrl ?: "",
                description = podcast.description,
                isSubscribed = true,
                subscribedAt =
                validRestoredSubscribedAt
                    ?: existing?.takeIf { it.isSubscribed }?.subscribedAt
                    ?: now,
                genre = podcast.genre,
                type = typeVal,
                lastRefreshed = existing?.lastRefreshed ?: now,
                latestEpisode =
                (podcast.latestEpisode ?: existing?.latestEpisode)?.let { ep ->
                    ep.copy(
                        podcastId = ep.podcastId.takeIf { !it.isNullOrBlank() } ?: podcast.id,
                        podcastTitle = ep.podcastTitle.takeIf { !it.isNullOrBlank() } ?: podcast.title,
                    )
                },
                podcastGuid = existing?.podcastGuid ?: podcast.podcastGuid,
                fundingUrl = existing?.fundingUrl ?: podcast.fundingUrl,
                fundingMessage = existing?.fundingMessage ?: podcast.fundingMessage,
                medium = existing?.medium ?: podcast.medium,
                hasValue = existing?.hasValue ?: podcast.hasValue,
                updateFrequency = existing?.updateFrequency ?: podcast.updateFrequency,
                location = existing?.location ?: podcast.location,
                license = existing?.license ?: podcast.license,
                isLocked = existing?.isLocked ?: podcast.isLocked,
                preferredSort = preferredSortVal,
                notificationsEnabled = false, // Off by default
                autoDownloadEnabled = false,
                skipBeginningOverrideMs =
                existing?.skipBeginningOverrideMs
                    ?: podcast.skipBeginningOverrideMs,
                skipEndingOverrideMs =
                existing?.skipEndingOverrideMs
                    ?: podcast.skipEndingOverrideMs,
                sourceType = existing?.sourceType ?: podcast.sourceType,
                feedUrl = existing?.feedUrl ?: podcast.feedUrl,
                feedEtag = existing?.feedEtag,
                feedLastModified = existing?.feedLastModified,
                feedDeclaredUpdatedAt = existing?.feedDeclaredUpdatedAt,
                rssRefreshCapability =
                existing?.rssRefreshCapability
                    ?: podcast.rssRefreshCapability,
                lastRssSyncAt = existing?.lastRssSyncAt ?: 0L,
                rssCatalogStale = existing?.rssCatalogStale ?: podcast.rssCatalogStale,
                rssHasNewEpisodes = existing?.rssHasNewEpisodes ?: podcast.rssHasNewEpisodes,
                linkedPodcastIndexId =
                existing?.linkedPodcastIndexId
                    ?: podcast.linkedPodcastIndexId,
            )
        podcastDao.upsert(entity)
        localEpisodeCatalog?.setUnsubscribedTtl(podcast.id, null)
        recoverFeedUrlIfMissing(podcast)
        if (isNewSubscription && recordFeedback) {
            RankingFeedbackRepository.getIfInitialized()?.recordAction(
                target =
                FeedbackTarget(
                    episodeId = podcast.latestEpisode?.id ?: "podcast:${podcast.id}",
                    podcastId = podcast.id,
                    genre = podcast.recommendationGenre,
                ),
                action = RankingAction.SUBSCRIBE,
            )
        }
    }

    suspend fun setNotificationsEnabled(podcast: Podcast, enabled: Boolean,) {
        if (podcast.isRss) {
            podcastDao.setNotificationsEnabled(podcast.id, false)
            podcastDao.setAutoDownloadEnabled(podcast.id, false)
            return
        }
        podcastDao.setNotificationsEnabled(podcast.id, enabled)
        val row = podcastDao.getPodcast(podcast.id)
        val feedUrl =
            if (enabled) {
                TrackedPodcastRtdbLogic.attachableFeedUrl(
                    feedUrl = podcast.feedUrl ?: row?.feedUrl,
                    latestEpisodeId = (podcast.latestEpisode ?: row?.latestEpisode)?.id,
                )
            } else {
                null
            }
        updateFirebaseSubscription(podcast.id, podcast.title, podcast.imageUrl, enabled, feedUrl)
    }

    /**
     * When notifications are already on and the show just opted into Missing episodes?,
     * patch RTDB `tracked_podcasts/{id}` with the publisher `feedUrl` so the checker
     * polls RSS. No-ops unless the Room row has notifications enabled and a supplement.
     */
    suspend fun syncTrackedPodcastFeedUrl(podcast: Podcast) {
        if (podcast.isRss) return
        val entity = podcastDao.getPodcast(podcast.id) ?: return
        if (!entity.notificationsEnabled) return
        val feedUrl =
            TrackedPodcastRtdbLogic.attachableFeedUrl(
                feedUrl = podcast.feedUrl ?: entity.feedUrl,
                latestEpisodeId = (podcast.latestEpisode ?: entity.latestEpisode)?.id,
            )
        updateFirebaseSubscription(
            podcastId = podcast.id,
            title = entity.title,
            imageUrl = entity.imageUrl,
            isSubscribed = true,
            feedUrl = feedUrl,
        )
    }

    private fun updateFirebaseSubscription(
        podcastId: String,
        title: String,
        imageUrl: String,
        isSubscribed: Boolean,
        feedUrl: String? = null,
    ) {
        try {
            if (isSubscribed) {
                val dbRef =
                    com.google.firebase.database.FirebaseDatabase
                        .getInstance()
                        .getReference("tracked_podcasts")
                        .child(podcastId)

                val data = TrackedPodcastRtdbLogic.payload(title, imageUrl, feedUrl)
                dbRef.setValue(data)

                com.google.firebase.messaging.FirebaseMessaging
                    .getInstance()
                    .subscribeToTopic("new_ep_$podcastId")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            android.util.Log.d("FCM_Topic", "Successfully subscribed to topic: new_ep_$podcastId")
                        } else {
                            android.util.Log.e("FCM_Topic", "Failed to subscribe to topic: new_ep_$podcastId", task.exception)
                        }
                    }
            } else {
                val dbRef =
                    com.google.firebase.database.FirebaseDatabase
                        .getInstance()
                        .getReference("tracked_podcasts")
                        .child(podcastId)
                // Keep the tracked node (unsubscribe does not delete it) but drop
                // feedUrl so Check New Episodes stops polling the publisher feed.
                dbRef.child("feedUrl").removeValue()
                com.google.firebase.messaging.FirebaseMessaging
                    .getInstance()
                    .unsubscribeFromTopic("new_ep_$podcastId")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            android.util.Log.d("FCM_Topic", "Successfully unsubscribed from topic: new_ep_$podcastId")
                        } else {
                            android.util.Log.e("FCM_Topic", "Failed to unsubscribe from topic: new_ep_$podcastId", task.exception)
                        }
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionRepository", "Firebase update failed for $podcastId", e)
        }
    }

    suspend fun updateLatestEpisode(
        podcastId: String,
        episode: cx.aswin.boxlore.core.model.Episode?,
        /**
         * When true (direct-feed tip promote), sets [PodcastEntity.rssHasNewEpisodes] so Home
         * NEW badges / hero grid "NEW" use the same freshness as RSS until the tip is seen.
         */
        markAsNew: Boolean = false,
        /** A successful full publisher-feed ingest owns the tip, even when PI cached a newer cross-promo. */
        publisherFeedAuthoritative: Boolean = false,
    ) {
        val enrichedEpisode =
            episode?.let { ep ->
                val resolvedTitle =
                    if (ep.podcastTitle.isNullOrBlank()) {
                        val podcast = podcastDao.getPodcast(podcastId)
                        podcast?.title
                    } else {
                        ep.podcastTitle
                    }
                ep.copy(
                    podcastId = ep.podcastId.takeIf { !it.isNullOrBlank() } ?: podcastId,
                    podcastTitle = resolvedTitle,
                )
            }
        val existing = podcastDao.getPodcast(podcastId)?.latestEpisode
        if (enrichedEpisode != null &&
            !publisherFeedAuthoritative &&
            !LatestEpisodeTipLogic.shouldReplace(existing, enrichedEpisode)
        ) {
            return
        }
        podcastDao.updateLatestEpisode(podcastId, enrichedEpisode)
        if (markAsNew &&
            enrichedEpisode != null &&
            LatestEpisodeTipLogic.isNewerPublish(existing, enrichedEpisode)
        ) {
            podcastDao.markHasNewEpisodes(podcastId)
        }
    }

    /**
     * Clears the shared "new episodes" badge ([PodcastEntity.rssHasNewEpisodes]) used by
     * true-RSS freshness and PI direct-feed tip promotes. Safe for either source.
     */
    suspend fun clearRssNewEpisodesFlag(podcastId: String) {
        podcastDao.clearRssNewEpisodesFlag(podcastId)
    }

    suspend fun updatePreferredSort(podcastId: String, sort: String?,) {
        val type = if (sort == "oldest") "serial" else "episodic"
        podcastDao.updatePreferredSortAndType(podcastId, sort, type)
    }

    suspend fun setAutoDownloadEnabled(podcastId: String, enabled: Boolean,) {
        if (podcastDao.getPodcast(podcastId)?.isRss == true) {
            podcastDao.setAutoDownloadEnabled(podcastId, false)
            return
        }
        podcastDao.setAutoDownloadEnabled(podcastId, enabled)
    }

    suspend fun setPlaybackSkipOverrides(podcastId: String, skipBeginningMs: Long?, skipEndingMs: Long?,) {
        podcastDao.setPlaybackSkipOverrides(
            podcastId,
            skipBeginningMs,
            skipEndingMs,
        )
    }

    suspend fun updateCustomGenre(podcastId: String, customGenre: String?, customGenreIcon: String?) {
        val existing = podcastDao.getPodcast(podcastId)
        val linkedRss = podcastDao.getRssPodcastLinkedTo(podcastId)
        val target = linkedRss?.takeIf { it.isSubscribed }
            ?: existing?.takeIf { it.isSubscribed }
            ?: return
        val trimmedGenre = customGenre?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedIcon = customGenreIcon?.trim()?.takeIf { it.isNotEmpty() }
        podcastDao.updateCustomGenre(target.podcastId, trimmedGenre, trimmedIcon)
    }

    /**
     * Re-subscribes all notification-enabled podcasts to their FCM topics.
     *
     * After an uninstall/reinstall or device migration, the Room database may be
     * restored from backup while the FCM token is new and has no topic
     * subscriptions. This method reconciles the two by iterating every podcast
     * where [PodcastEntity.notificationsEnabled] is true and calling
     * [updateFirebaseSubscription] to re-register with Firebase.
     */
    suspend fun reconcileFcmTopicSubscriptions() {
        try {
            val podcasts = podcastDao.getNotificationEnabledPodcasts()
            if (podcasts.isEmpty()) return
            android.util.Log.i(
                "FCM_Topic",
                "Reconciling ${podcasts.size} FCM topic subscriptions after restore",
            )
            for (entity in podcasts) {
                val feedUrl =
                    TrackedPodcastRtdbLogic.attachableFeedUrl(
                        feedUrl = entity.feedUrl,
                        latestEpisodeId = entity.latestEpisode?.id,
                    )
                updateFirebaseSubscription(
                    podcastId = entity.podcastId,
                    title = entity.title,
                    imageUrl = entity.imageUrl,
                    isSubscribed = true,
                    feedUrl = feedUrl,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("FCM_Topic", "FCM topic reconciliation failed", e)
        }
    }

    /**
     * Writes an HTTPS publisher [feedUrl] onto the Room row so launch sync and
     * notification RTDB can poll the same feed after library restore.
     */
    suspend fun ensureHttpsFeedUrl(podcastId: String, feedUrl: String,) {
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(feedUrl) ?: return
        if (podcastDao.getPodcast(podcastId) == null) return
        podcastDao.setFeedUrl(podcastId, https)
    }

    private suspend fun recoverFeedUrlIfMissing(podcast: Podcast) {
        if (podcast.isRss) return
        val stored = podcastDao.getPodcast(podcast.id)?.feedUrl
        if (TrackedPodcastRtdbLogic.httpsFeedUrl(stored) != null) return
        val supplied = TrackedPodcastRtdbLogic.httpsFeedUrl(podcast.feedUrl)
        if (supplied != null) {
            ensureHttpsFeedUrl(podcast.id, supplied)
            return
        }
        val recovered = lookupHttpsFeedUrl?.invoke(podcast.id) ?: return
        ensureHttpsFeedUrl(podcast.id, recovered)
    }
}
