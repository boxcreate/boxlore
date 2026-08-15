package cx.aswin.boxlore.core.testing.fakes

import cx.aswin.boxlore.core.domain.RssSubscriptionResult
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import cx.aswin.boxlore.core.domain.ports.EpisodeOfflineLookupPort
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.OfflineEpisodeSnapshot
import cx.aswin.boxlore.core.domain.ports.PodcastCatalogPort
import cx.aswin.boxlore.core.domain.ports.RankingResetPort
import cx.aswin.boxlore.core.domain.ports.RssSubscriptionPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.testing.TestFixtures

/** Controllable [LocalCatalogPort] for hermetic ViewModel / worker tests. */
class FakeLocalCatalogPort(
    private val byId: MutableMap<String, Podcast> = mutableMapOf(),
    private val rssLinks: MutableMap<String, Podcast> = mutableMapOf(),
) : LocalCatalogPort {
    var upsertCalls = 0
    val upserted = mutableListOf<Podcast>()

    fun put(podcast: Podcast) {
        byId[podcast.id] = podcast
    }

    fun linkRss(
        podcastIndexId: String,
        rssPodcast: Podcast,
    ) {
        rssLinks[podcastIndexId] = rssPodcast
    }

    override suspend fun getLocalPodcast(id: String): Podcast? = byId[id]

    override suspend fun getSubscribedRssLinkedTo(id: String): Podcast? = rssLinks[id]

    override suspend fun upsertSubscribedPodcast(podcast: Podcast) {
        upsertCalls++
        upserted += podcast
        byId[podcast.id] = podcast
    }
}

/** Controllable [LocalEpisodeCatalogPort] for catalog / FCM tests. */
class FakeLocalEpisodeCatalogPort(
    var readyIds: Set<String> = emptySet(),
    var episodes: MutableMap<String, Episode> = mutableMapOf(),
) : LocalEpisodeCatalogPort {
    var refreshCalls: Int = 0
    var refreshError: Throwable? = null
    var sweepCalls: Int = 0
    var sweepError: Throwable? = null
    var lastSweepNowMs: Long? = null

    override suspend fun isReady(podcastId: String): Boolean = podcastId in readyIds

    override suspend fun getPage(
        podcastId: String,
        limit: Int,
        offset: Int,
        sort: String,
        meta: LocalEpisodeCatalogPort.PodcastMeta,
    ): List<Episode> {
        val filtered = episodes.values.filter { it.podcastId == podcastId }
        val sorted =
            if (sort == "oldest") {
                filtered.sortedWith(
                    compareBy<Episode> { it.publishedDate }.thenByDescending { it.id },
                )
            } else {
                filtered.sortedWith(
                    compareByDescending<Episode> { it.publishedDate }.thenBy { it.id },
                )
            }
        return sorted.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
    }

    override suspend fun getWindow(
        podcastId: String,
        sort: String,
        bound: Int,
        aroundEpisodeId: String?,
        meta: LocalEpisodeCatalogPort.PodcastMeta,
    ): List<Episode> = getPage(podcastId, bound, 0, sort, meta)

    override suspend fun getEpisode(
        episodeId: String,
        meta: LocalEpisodeCatalogPort.PodcastMeta,
    ): Episode? = episodes[episodeId]

    override suspend fun findByCatalogKey(
        podcastId: String,
        guid: String?,
        enclosureUrl: String?,
        meta: LocalEpisodeCatalogPort.PodcastMeta,
    ): Episode? {
        val wantedGuid = guid?.trim().orEmpty()
        val enclosure = enclosureUrl?.trim().orEmpty()
        if (wantedGuid.isEmpty() && enclosure.isEmpty()) return null
        return episodes.values.firstOrNull { episode ->
            episode.podcastId == podcastId &&
                (
                    (wantedGuid.isNotEmpty() && episode.id == wantedGuid) ||
                        (enclosure.isNotEmpty() && episode.audioUrl.trim() == enclosure)
                )
        }
    }

    override suspend fun search(
        podcastId: String,
        query: String,
        meta: LocalEpisodeCatalogPort.PodcastMeta,
    ): List<Episode> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return episodes.values.filter { episode ->
            episode.podcastId == podcastId &&
                (
                    episode.title.contains(trimmed, ignoreCase = true) ||
                        episode.description.contains(trimmed, ignoreCase = true)
                )
        }
    }

    override suspend fun newest(
        podcastId: String,
        meta: LocalEpisodeCatalogPort.PodcastMeta,
    ): Episode? = episodes.values.filter { it.podcastId == podcastId }.maxByOrNull { it.publishedDate }

    override suspend fun count(podcastId: String): Int = episodes.values.count { it.podcastId == podcastId }

    override suspend fun refresh(request: LocalEpisodeCatalogPort.RefreshRequest): LocalEpisodeCatalogPort.RefreshOutcome {
        refreshCalls += 1
        refreshError?.let { throw it }
        return LocalEpisodeCatalogPort.RefreshOutcome.Unchanged(newest(request.podcastIndexId))
    }

    override suspend fun isPublisherFeedUnchanged(
        podcastId: String,
        feedUrl: String,
    ): Boolean = true

    override suspend fun markFeedUrlLookup(
        podcastId: String,
        atMillis: Long,
    ) = Unit

    override suspend fun lastFeedUrlLookupAt(podcastId: String): Long = 0L

    override suspend fun setUnsubscribedTtl(
        podcastId: String,
        ttlExpiresAt: Long?,
    ) = Unit

    override suspend fun sweepExpired(nowMillis: Long) {
        lastSweepNowMs = nowMillis
        sweepCalls++
        sweepError?.let { throw it }
    }
}

/** Controllable [PodcastCatalogPort] for Info / catalog ViewModel tests. */
class FakePodcastCatalogPort(
    var details: Podcast? = TestFixtures.podcast(),
    var episode: Episode? = TestFixtures.episode(),
    var episodes: List<Episode> = listOf(TestFixtures.episode()),
    var detailsError: Exception? = null,
    var episodeError: Exception? = null,
    var episodesError: Exception? = null,
) : PodcastCatalogPort {
    var detailsCalls = 0
    var episodeCalls = 0
    var episodesCalls = 0
    var lastDetailsId: String? = null
    var lastEpisodeId: String? = null
    var lastEpisodesFeedId: String? = null

    override suspend fun getPodcastDetails(feedId: String): Podcast? {
        detailsCalls++
        lastDetailsId = feedId
        detailsError?.let { throw it }
        return details
    }

    override suspend fun getEpisode(episodeId: String): Episode? {
        episodeCalls++
        lastEpisodeId = episodeId
        episodeError?.let { throw it }
        return episode
    }

    override suspend fun getEpisodes(feedId: String): List<Episode> {
        episodesCalls++
        lastEpisodesFeedId = feedId
        episodesError?.let { throw it }
        return episodes
    }
}

/** Controllable [EpisodeOfflineLookupPort]. */
class FakeEpisodeOfflineLookup(
    var fromDownload: OfflineEpisodeSnapshot? = null,
    var fromHistory: OfflineEpisodeSnapshot? = null,
) : EpisodeOfflineLookupPort {
    var downloadCalls = 0
    var historyCalls = 0

    override suspend fun fromDownload(episodeId: String): OfflineEpisodeSnapshot? {
        downloadCalls++
        return fromDownload
    }

    override suspend fun fromHistory(episodeId: String): OfflineEpisodeSnapshot? {
        historyCalls++
        return fromHistory
    }
}

/** Controllable [RssSubscriptionPort] for Settings / onboarding hermetic tests. */
class FakeRssSubscriptionPort(
    var result: RssSubscriptionResult =
        RssSubscriptionResult(
            podcast = TestFixtures.rssPodcast(),
            episodeCount = 3,
            automaticUpdateChecksSupported = true,
        ),
    var addError: Exception? = null,
    var confirmError: Exception? = null,
) : RssSubscriptionPort {
    var addCalls = 0
    var confirmCalls = 0
    var lastAddUrl: String? = null
    var lastConfirmRssId: String? = null
    var lastConfirmIndexId: String? = null

    override suspend fun addSubscription(rawUrl: String): RssSubscriptionResult {
        addCalls++
        lastAddUrl = rawUrl
        addError?.let { throw it }
        return result
    }

    override suspend fun confirmPodcastIndexLink(
        rssPodcastId: String,
        podcastIndexId: String,
    ): Podcast {
        confirmCalls++
        lastConfirmRssId = rssPodcastId
        lastConfirmIndexId = podcastIndexId
        confirmError?.let { throw it }
        return result.podcast
    }
}

/** Controllable [RankingResetPort]. */
class FakeRankingResetPort(
    var result: Boolean = true,
) : RankingResetPort {
    var resetCalls = 0

    override suspend fun reset(): Boolean {
        resetCalls++
        return result
    }
}

/** Controllable [HistoryRecommendationSource]. */
class FakeHistoryRecommendationSource(
    var items: List<HistoryItem> = listOf(TestFixtures.historyItem()),
) : HistoryRecommendationSource {
    var calls = 0
    var lastLimit: Int? = null

    override suspend fun getHistoryForRecommendations(limit: Int): List<HistoryItem> {
        calls++
        lastLimit = limit
        return items.take(limit)
    }
}

/** Controllable [ConnectivityStatusPort]. */
class FakeConnectivityStatusPort(
    var online: Boolean = true,
) : ConnectivityStatusPort {
    var calls = 0

    override fun isOnline(): Boolean {
        calls++
        return online
    }
}
