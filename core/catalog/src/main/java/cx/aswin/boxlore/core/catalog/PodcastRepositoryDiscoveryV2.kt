package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.logic.SemanticSearchGroupedResult
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.BecauseYouLikeRequest
import cx.aswin.boxlore.core.network.model.BootstrapRequest
import cx.aswin.boxlore.core.network.model.CuratedCuriosityResponseDto
import cx.aswin.boxlore.core.network.model.SimilarEpisodesRequest
import cx.aswin.boxlore.core.network.model.V2BootstrapRequest
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import retrofit2.awaitResponse

/*
 * Discovery on the `/v2` routes (Home, Explore, Search, recommendations, Smart Queue and Android
 * Auto sources, briefings). The answers have the v1 shapes; episodes in them carry recipe IDs,
 * which are translated to the phone's own stored IDs ([DiscoveryEpisodeIds]) before callers rank,
 * dedupe or show them. A failed request keeps the last good answer of the same request in this
 * process, and recommendations keep the last good picks across restarts ([RecommendationsLastGood]);
 * with neither, the answer is what v1 gives on failure (empty). Discovery never falls back to the
 * v1 routes on a failure; only the app's `v2Discovery` switch ([RolloutSwitches.v2Discovery]) sends
 * it back to them.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 */

private const val V2_DISCOVERY_TAG = "V2Discovery"

/** Last good `/v2` discovery answers of this process, by request ([LastGoodCache]). */
internal class V2DiscoveryLastGood {
    val podcasts = LastGoodCache<List<Podcast>>(MAX_PODCAST_LISTS)
    val search = LastGoodCache<SearchResult>()
    val semantic = LastGoodCache<SemanticSearchGroupedResult>()
    val becauseYouLike = LastGoodCache<BecauseYouLikeData>()
    val similar = LastGoodCache<List<Episode>>()
    val briefings = LastGoodCache<Briefing>()
    val bootstrap = LastGoodCache<HomeBootstrapData>()
    val curiosity = LastGoodCache<CuratedCuriosityResponseDto>()

    private companion object {
        /** Trending categories, pages and vibes all keep their own list. */
        const val MAX_PODCAST_LISTS = 48
    }
}

/**
 * The body of a successful `/v2` answer, or null when the request failed: an HTTP error, no body,
 * or a network or parsing error. Cancellation propagates.
 */
private inline fun <T> v2Body(route: String, call: () -> Response<T>,): T? = try {
    val response = call()
    val body = response.body()
    if (response.isSuccessful && body != null) {
        body
    } else {
        android.util.Log.w(V2_DISCOVERY_TAG, "v2 $route failed: HTTP ${response.code()}")
        null
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    android.util.Log.w(V2_DISCOVERY_TAG, "v2 $route failed", error)
    null
}

internal suspend fun PodcastRepository.v2TrendingPodcasts(country: String, limit: Int, category: String?, offset: Int,): List<Podcast> {
    val key = trendingLastGoodKey(country, limit, category, offset)
    val body =
        v2Body("trending") { apiV2.getTrending(country, limit, category, offset).execute() }
            ?: return v2LastGood.podcasts[key].orEmpty()
    return discoveryEpisodeIds.latestToLocalOwners(mapFeedsToPodcasts(body.feeds)).also { v2LastGood.podcasts[key] = it }
}

internal fun trendingLastGoodKey(country: String, limit: Int, category: String?, offset: Int,): String = "trending|$country|$limit|${category.orEmpty()}|$offset"

/** One vibe's shows, or null on a failure with no last good list; [queryLanguages] is the comma-joined `languages` parameter. */
internal suspend fun PodcastRepository.v2CuratedPodcasts(vibeId: String, country: String?, queryLanguages: String,): List<Podcast>? {
    val key = "vibe|$vibeId|${country.orEmpty()}|$queryLanguages"
    val body =
        v2Body("curated/vibe") { apiV2.getCuratedVibe(vibeId, country, queryLanguages).execute() }
            ?: return v2LastGood.podcasts[key]
    return discoveryEpisodeIds.latestToLocalOwners(mapFeedsToPodcasts(body.feeds)).also { v2LastGood.podcasts[key] = it }
}

/** Show search; shows carry no episode IDs, so only the request moves. */
internal fun PodcastRepository.v2SearchPodcasts(query: String): SearchResult {
    val body = v2Body("search") { apiV2.search(query).execute() } ?: return v2LastGood.search[query] ?: SearchResult(emptyList())
    return SearchResult(body.feeds.mapNotNull(::mapSearchFeedToPodcast), null).also { v2LastGood.search[query] = it }
}

internal suspend fun PodcastRepository.v2SemanticGrouped(query: String, country: String,): SemanticSearchGroupedResult {
    val key = "$country|$query"
    val body =
        v2Body("search/semantic") { apiV2.searchSemantic(query, country).execute() }
            ?: return v2LastGood.semantic[key] ?: SemanticSearchGroupedResult(emptyList(), emptyList())
    return SemanticSearchGroupedResult(
        podcasts = body.feeds.mapNotNull(::mapSearchFeedToPodcast),
        episodes = discoveryEpisodeIds.toLocalOwners(body.items.mapNotNull(::mapToEpisode)),
    ).also { v2LastGood.semantic[key] = it }
}

internal suspend fun PodcastRepository.v2BecauseYouLike(request: BecauseYouLikeRequest, cacheKey: String,): BecauseYouLikeData {
    val body =
        v2Body("recommendations/because-you-like") { apiV2.getBecauseYouLike(request).execute() }
            ?: return v2LastGood.becauseYouLike[cacheKey] ?: BecauseYouLikeData()
    return BecauseYouLikeData(
        podcasts = body.podcasts.map(::mapToPodcast),
        episodes = discoveryEpisodeIds.toLocalOwners(body.episodes.mapNotNull(::mapToEpisode)),
    ).also {
        v2LastGood.becauseYouLike[cacheKey] = it
        PodcastRepository.becauseYouLikeCache[cacheKey] = it
    }
}

/**
 * Similar episodes. The seed episode is sent as its recipe ID ([DiscoveryEpisodeIds.recipeIds]);
 * one the phone can't place goes as `"0"`, as v1 sends a phone-minted ID, and the server matches
 * on the text alone.
 */
internal suspend fun PodcastRepository.v2SimilarEpisodes(query: SimilarEpisodesQuery, country: String, languages: List<String>,): List<Episode> {
    val seed = discoveryEpisodeIds.recipeIdsOrOwn(listOf(query.episodeId))[query.episodeId]
    val request =
        SimilarEpisodesRequest(
            id = seed?.toString() ?: "0",
            podcastId = query.podcastId.takeUnless { it.startsWith("rss:") } ?: "0",
            title = query.title,
            description = query.description,
            podcastTitle = query.podcastTitle,
            categories = query.categories,
            author = query.author,
            limit = query.limit,
            country = country,
            languages = languages,
        )
    val key = "${query.episodeId}|${query.podcastId}|${query.limit}|$country|${languages.joinToString(",")}"
    // Cancellable, so a switched-off Try these episodes or a closed page stops the request.
    val body = v2Body("episodes/similar") { apiV2.getSimilarEpisodes(request).awaitResponse() } ?: return v2LastGood.similar[key].orEmpty()
    return discoveryEpisodeIds.toLocalOwners(body.items.mapNotNull(::mapToEpisode)).also { v2LastGood.similar[key] = it }
}

/**
 * Personal picks from `/v2/recommendations` (Smart Queue, Smart Downloads, Android Auto and content
 * rows). Without a seed or an interest no request is made. A failure, or an answer without picks,
 * keeps the last good picks for [country] ([RecommendationsLastGood]), never the v1 recommendations.
 */
internal suspend fun PodcastRepository.v2PersonalizedRecommendations(
    history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
    interests: List<String>,
    country: String,
    subscribedPodcastIds: List<String>,
    languages: List<String>,
): List<Episode> {
    val request = v2RecommendationRequest(history, interests, country, subscribedPodcastIds, languages) ?: return emptyList()
    val cacheKey = request.v2CacheKey()
    PodcastRepository.recommendationsCache[cacheKey]?.let { return it }
    val items =
        v2Body("recommendations") { apiV2.getRecommendations(getOrCreateDeviceUuid(), request).execute() }
            ?.takeIf { it.isValidRecommendationV2Response(isHttpSuccessful = true) }
            ?.items
            ?.mapNotNull(::mapToEpisode)
            .orEmpty()
    if (items.isEmpty()) {
        val lastGood = recommendationsLastGood.load(RecommendationsLastGood.Surface.PERSONAL, country) ?: return emptyList()
        return discoveryEpisodeIds.toLocalOwners(lastGood.episodes)
    }
    recommendationsLastGood.save(RecommendationsLastGood.Surface.PERSONAL, country, RecommendationsLastGood.LastGoodPicks(items, isFallback = false))
    return discoveryEpisodeIds.toLocalOwners(items).also { PodcastRepository.recommendationsCache[cacheKey] = it }
}

/** A region's briefing; its chapters link is the signed `/v2` one, so related episodes come under recipe IDs. */
internal fun PodcastRepository.v2BriefingMetadata(mappedRegion: String): Briefing? {
    val body = v2Body("briefings/metadata") { apiV2.getBriefingMetadata(mappedRegion).execute() } ?: return v2LastGood.briefings[mappedRegion]
    return body.also { v2LastGood.briefings[mappedRegion] = it }
}

/** The launch bootstrap (Brief, its chapters and trending); its episodes are translated to local owners. */
internal suspend fun PodcastRepository.v2HomeBootstrapFast(country: String): HomeBootstrapData {
    val body =
        v2Body("home/bootstrap") { apiV2.getHomeBootstrapGet(country).execute() }
            ?: return v2LastGood.bootstrap[country] ?: HomeBootstrapData(null, emptyList(), emptyList(), emptyMap(), emptyList())
    return HomeBootstrapData(
        briefing = body.briefing,
        briefingChapters = discoveryEpisodeIds.relatedToLocalOwners(body.briefingChapters),
        trending = discoveryEpisodeIds.latestToLocalOwners(body.trending.map(::mapToPodcast)),
        curatedVibes = emptyMap(),
        recommendations = emptyList(),
        isRecommendationsFallback = body.isRecommendationsFallback ?: true,
    ).also { v2LastGood.bootstrap[country] = it }
}

/**
 * Home's Picked for you and Explore's For you through `/v2/home/bootstrap`. The request carries the
 * current recommendation body ([v2RecommendationRequest]); without a seed or an interest there is
 * nothing to ask for, so no request is made. No picks (a failure, or none found) keeps the last
 * good picks for [country], translated again ([RecommendationsLastGood]).
 */
internal suspend fun PodcastRepository.v2HomeRecommendations(
    country: String,
    history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
    interests: List<String>,
    subscribedPodcastIds: List<String>,
    languages: List<String>,
): HomeRecommendations {
    val recommendationsRequest = v2RecommendationRequest(history, interests, country, subscribedPodcastIds, languages)
    val body =
        recommendationsRequest?.let { recsRequest ->
            val deviceUuid = getOrCreateDeviceUuid()
            val request =
                V2BootstrapRequest(
                    country = country,
                    vibeIds = emptyList(),
                    deviceUuid = deviceUuid,
                    recommendationsRequest = recsRequest,
                    languages = languages,
                    include = listOf(BootstrapRequest.INCLUDE_RECOMMENDATIONS),
                )
            v2Body("home/bootstrap") { apiV2.getHomeBootstrap(deviceUuid, request).execute() }
        }
    val items = body?.recommendations?.mapNotNull(::mapToEpisode).orEmpty()
    if (items.isEmpty()) {
        val lastGood = recommendationsLastGood.load(RecommendationsLastGood.Surface.HOME, country) ?: return HomeRecommendations()
        return HomeRecommendations(discoveryEpisodeIds.toLocalOwners(lastGood.episodes), lastGood.isFallback)
    }
    val isFallback = body?.isRecommendationsFallback ?: true
    recommendationsLastGood.save(RecommendationsLastGood.Surface.HOME, country, RecommendationsLastGood.LastGoodPicks(items, isFallback))
    return HomeRecommendations(discoveryEpisodeIds.toLocalOwners(items), isFallback)
}

/** A page of the curiosity deck; each card's recipe ID is translated to its local owner. */
internal suspend fun PodcastRepository.v2CuratedCuriosity(page: Int, cacheBuster: String?,): CuratedCuriosityResponseDto? {
    val key = page.toString()
    val body = v2Body("curated/curiosity-v3") { apiV2.getCuratedCuriosity(page, cacheBuster).execute() } ?: return v2LastGood.curiosity[key]
    return discoveryEpisodeIds.curiosityToLocalOwners(body).also { v2LastGood.curiosity[key] = it }
}
