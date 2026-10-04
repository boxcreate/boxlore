package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.getRecentHistoryList
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.home.logic.BecauseYouLikeRotationLogic
import cx.aswin.boxlore.feature.home.logic.HomeBecauseYouLikeLogic
import cx.aswin.boxlore.feature.home.logic.PodcastAffinityLogic
import cx.aswin.boxlore.feature.home.logic.toRecommendationPodcast
import java.time.Clock
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// from private suspend fun resolveFavoritePodcast
internal suspend fun HomeViewModel.resolveFavoritePodcast(
    overriddenId: String?,
    subscriptions: List<Podcast>,
    historyList: List<HomeListeningHistoryItem>,
    clock: Clock = Clock.systemDefaultZone(),
): Podcast? {
    val historySignals = historyList.map { HomeBecauseYouLikeLogic.toAffinitySignal(it) }
    if (overriddenId != null) {
        val sub = subscriptions.find { it.id == overriddenId }
        if (sub != null) return sub

        localCatalog.getLocalPodcast(overriddenId)?.let { return it }

        val hist = historySignals.find { it.podcastId == overriddenId }
        if (hist != null) {
            return PodcastAffinityLogic.podcastFromHistorySignal(hist)
        }

        return null
    }

    if (subscriptions.isEmpty() && historyList.isEmpty()) return null

    val lastPlayedMap = mutableMapOf<String, Long>()
    val podcastNameMap = mutableMapOf<String, String>()
    val podcastImageMap = mutableMapOf<String, String>()

    val scores =
        PodcastAffinityLogic.calculatePodcastAffinityScores(
            subscriptions = subscriptions,
            historyList = historySignals,
            lastPlayedMap = lastPlayedMap,
            podcastNameMap = podcastNameMap,
            podcastImageMap = podcastImageMap,
        )

    val candidateIds =
        BecauseYouLikeRotationLogic.filterEligibleCandidates(
            scores = scores,
            lastPlayedMap = lastPlayedMap,
        )
    if (candidateIds.isEmpty()) return null

    val candidatePodcasts = candidateIds.map { podId ->
        subscriptions.find { it.id == podId }
            ?: localCatalog.getLocalPodcast(podId)
            ?: Podcast(
                id = podId,
                title = podcastNameMap[podId] ?: "Podcast",
                artist = "",
                imageUrl = podcastImageMap[podId] ?: "",
                fallbackImageUrl = "",
                description = "",
            )
    }

    val now = ZonedDateTime.now(clock)
    val dayPart = BecauseYouLikeRotationLogic.resolveDayPart(now.hour)
    val epochDay = now.toLocalDate().toEpochDay()

    return BecauseYouLikeRotationLogic.selectRotatedAnchor(
        candidates = candidatePodcasts,
        dayPart = dayPart,
        epochDay = epochDay,
    ) ?: candidatePodcasts.firstOrNull()
}

// from private fun fetchBecauseYouLikeRecommendations
internal fun HomeViewModel.fetchBecauseYouLikeRecommendations(
    podcast: Podcast,
    region: String,
    forceRefresh: Boolean = false,
    clock: Clock = Clock.systemDefaultZone(),
) {
    viewModelScope.launch(Dispatchers.Default) {
        val currentSlotKey = BecauseYouLikeRotationLogic.currentSlotKey(clock)

        if (!forceRefresh &&
            boxcastPrefs.getCachedBylPodcastId() == podcast.id &&
            boxcastPrefs.getCachedBylSlot() == currentSlotKey
        ) {
            if (_becauseYouLikeRecommendations.value.isNotEmpty() && _becauseYouLikePodcasts.value.isNotEmpty()) {
                android.util.Log.d(
                    "HomeViewModel",
                    "BYL cache hit in memory for ${podcast.id} in slot $currentSlotKey; skipping network fetch.",
                )
                return@launch
            }
            if (restoreCachedBecauseYouLike(podcast.id, currentSlotKey)) return@launch
        }

        _isBecauseYouLikeLoading.value = true
        try {
            val title = podcast.title
            val desc = podcast.description ?: ""
            val id = podcast.id

            android.util.Log.d(
                "HomeViewModel",
                "Fetching because-you-like recommendations for: $title (ID: $id), region: $region, slot: $currentSlotKey",
            )
            val data =
                podcastRepository.getBecauseYouLikeRecommendations(
                    podcastTitle = title,
                    podcastDescription = desc,
                    excludePodcastId = id,
                    country = region,
                )

            val distinctPodcasts =
                HomeBecauseYouLikeLogic.distinctByIdAndTitle(
                    data.podcasts,
                    id = { it.id },
                    title = { it.title },
                )
            val distinctEpisodes =
                HomeBecauseYouLikeLogic.distinctByIdAndTitle(
                    data.episodes,
                    id = { it.id },
                    title = { it.title },
                )
            val ranked = rankBecauseYouLike(distinctPodcasts, distinctEpisodes)

            android.util.Log.d(
                "HomeViewModel",
                "Fetched because-you-like: podcasts count = ${distinctPodcasts.size}, episodes count = ${distinctEpisodes.size}",
            )

            _becauseYouLikePodcasts.value = ranked.first
            _becauseYouLikeRecommendations.value = ranked.second

            try {
                val json = Json { ignoreUnknownKeys = true }
                val serializedEpisodes = json.encodeToString(ranked.second)
                val serializedPodcasts = json.encodeToString(ranked.first)
                boxcastPrefs.saveBylCache(
                    episodesJson = serializedEpisodes,
                    podcastsJson = serializedPodcasts,
                    podcastId = id,
                    slotKey = currentSlotKey,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (ce: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to cache because-you-like recommendations", ce)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Failed to fetch because-you-like recommendations", e)
        } finally {
            _isBecauseYouLikeLoading.value = false
        }
    }
}

// from private suspend fun rankBecauseYouLike
internal suspend fun HomeViewModel.rankBecauseYouLike(
    podcasts: List<Podcast>,
    episodes: List<Episode>,
): Pair<List<Podcast>, List<Episode>> {
    val history = playbackRepository.getRecentHistoryList(300)
    val subscribedIds = subscriptionRepository.subscribedPodcastIds.first()
    val podcastById = podcasts.associateBy(Podcast::id)
    val podcastInputs =
        podcasts.mapIndexedNotNull { index, candidate ->
            candidate.latestEpisode?.let { episode ->
                EpisodeRankingInput(
                    episode = episode,
                    podcast = candidate,
                    priorScore = (podcasts.size - index).toDouble(),
                    source = CandidateSource.SERVER_RECOMMENDATION,
                    isNovel = candidate.id !in subscribedIds,
                )
            }
        }
    val episodeInputs =
        episodes.mapIndexed { index, episode ->
            EpisodeRankingInput(
                episode = episode,
                podcast = podcastById[episode.podcastId] ?: episode.toRecommendationPodcast(),
                priorScore = (episodes.size - index).toDouble(),
                source = CandidateSource.SERVER_RECOMMENDATION,
                isNovel = episode.podcastId !in subscribedIds,
            )
        }
    val podcastScores =
        adaptiveScorer.scoreEpisodes(
            podcastInputs,
            history,
            RankingObjective.DISCOVERY,
            RankingSurface.HOME,
        )
    val episodeScores =
        adaptiveScorer.scoreEpisodes(
            episodeInputs,
            history,
            RankingObjective.DISCOVERY,
            RankingSurface.HOME,
        )
    return HomeBecauseYouLikeLogic.sortPodcastsByEpisodeScores(podcasts, podcastScores) to
        HomeBecauseYouLikeLogic.sortEpisodesByScores(episodes, episodeScores)
}

private fun HomeViewModel.restoreCachedBecauseYouLike(podcastId: String, slotKey: String): Boolean {
    val cachedRecs = boxcastPrefs.getCachedBylRecommendationsJson() ?: return false
    val cachedPods = boxcastPrefs.getCachedBylPodcastsJson() ?: return false
    return try {
        val json = Json { ignoreUnknownKeys = true }
        val episodes = json.decodeFromString<List<Episode>>(cachedRecs)
        val podcasts = json.decodeFromString<List<Podcast>>(cachedPods)
        _becauseYouLikeRecommendations.value = episodes
        _becauseYouLikePodcasts.value = podcasts
        android.util.Log.d("HomeViewModel", "BYL disk cache restored for $podcastId in slot $slotKey; skipping network fetch.")
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("HomeViewModel", "Failed to parse cached BYL recommendations", e)
        false
    }
}
