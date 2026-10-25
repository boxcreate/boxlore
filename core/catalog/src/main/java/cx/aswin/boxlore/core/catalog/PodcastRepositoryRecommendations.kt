package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.model.ContentRegions
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.network.model.RecommendationsV2Request

/** Interests per recommendation request; the API caps legacy requests at 24 and v2 at 12. */
internal const val MAX_RECOMMENDATION_INTERESTS = 12

/** Seed shows and excluded episodes per current-algorithm request. */
private const val MAX_RECOMMENDATION_IDS = 250

/** Trimmed, non-empty, distinct interests, at most [MAX_RECOMMENDATION_INTERESTS]. */
internal fun boundedRecommendationInterests(interests: List<String>): List<String> =
    interests.map(String::trim).filter(String::isNotEmpty).distinct().take(MAX_RECOMMENDATION_INTERESTS)

/**
 * The recommendations/bootstrap v1 endpoints only understand Podcast Index-sourced history
 * and subscriptions, so both v1 callers strip RSS entries the same way before building a request.
 */
internal fun filterToPodcastIndexScope(history: List<HistoryItem>, subscribedPodcastIds: List<String>,): PodcastIndexScopedInputs {
    val podcastIndexHistory =
        history.filter { item ->
            item.podcastId?.startsWith("rss:") != true &&
                item.episodeId?.toLongOrNull()?.let { it > 0L } != false
        }
    val podcastIndexSubscriptionIds = subscribedPodcastIds.filterNot { it.startsWith("rss:") }
    return PodcastIndexScopedInputs(podcastIndexHistory, podcastIndexSubscriptionIds)
}

internal fun PodcastRepository.fetchRecommendationV2(
    history: List<HistoryItem>,
    interests: List<String>,
    country: String?,
    subscribedPodcastIds: List<String>,
    languages: List<String>,
): List<Episode>? {
    val seeds = buildRecommendationSeeds(history, subscribedPodcastIds)
    val boundedInterests = boundedRecommendationInterests(interests)
    if (seeds.isEmpty() && boundedInterests.isEmpty()) return null
    val resolvedCountry = ContentRegions.canonicalize(country ?: "us")
    val request = RecommendationsV2Request(
        contractVersion = RecommendationsV2Request.CONTRACT_VERSION,
        country = resolvedCountry,
        languages = languages,
        seeds = seeds,
        interests = boundedInterests,
        subscribedPodcastIds = subscribedPodcastIds.mapNotNull(String::toLongOrNull)
            .filter { it > 0L }
            .distinct()
            .take(MAX_RECOMMENDATION_IDS),
        excludedEpisodeIds = history.mapNotNull { it.episodeId?.toLongOrNull() }
            .filter { it > 0L }
            .distinct()
            .take(MAX_RECOMMENDATION_IDS),
    )
    val response = api.getPersonalizedRecommendationsV2(
        publicKey,
        getOrCreateDeviceUuid(),
        request,
    ).execute()
    val body = response.body()
    val payload = body ?: return null
    if (!payload.isValidRecommendationV2Response(response.isSuccessful)) {
        return null
    }
    return payload.items.mapNotNull { mapToEpisode(it) }.takeIf { it.isNotEmpty() }
}

internal fun cx.aswin.boxlore.core.network.model.RecommendationsV2Response.isValidRecommendationV2Response(
    isHttpSuccessful: Boolean,
): Boolean {
    if (!isHttpSuccessful) return false
    return status == "true" &&
        contractVersion == RecommendationsV2Request.CONTRACT_VERSION &&
        !algorithmVersion.isNullOrBlank()
}

/**
 * Seeds for the current recommendation algorithm: episodes from [history] weighted by how they
 * were listened to, then followed shows up to [maximumSeeds].
 *
 * An episode seed is the ID in [seedIds] for its stored ID when there is one (on `/v2`, its recipe
 * ID), else the stored ID when positive (a Podcast Index ID, which `/v2` matches through the seed's
 * text fallback). Other episodes are left out.
 */
internal fun buildRecommendationSeeds(
    history: List<HistoryItem>,
    subscribedPodcastIds: List<String>,
    maximumSeeds: Int = 12,
    seedIds: Map<String, Long> = emptyMap(),
): List<cx.aswin.boxlore.core.network.model.RecommendationSeedV2> {
    require(maximumSeeds > 0)
    val episodeSeeds = history.mapNotNull { item ->
        val storedId = item.episodeId ?: return@mapNotNull null
        val id = seedIds[storedId] ?: storedId.toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
        val durationMs = item.durationMs ?: 0L
        val progressRatio = if (durationMs > 0L) {
            (item.progressMs ?: 0L).toDouble() / durationMs
        } else {
            0.0
        }
        val weight = when {
            item.isLiked == true -> 1.0
            item.isCompleted == true -> 0.9
            progressRatio >= 0.5 -> 0.75
            progressRatio >= 0.2 -> 0.55
            else -> 0.35
        }
        cx.aswin.boxlore.core.network.model.RecommendationSeedV2(
            kind = "episode",
            id = id,
            weight = weight,
            fallback = cx.aswin.boxlore.core.network.model.RecommendationSemanticFallback(
                episodeTitle = item.episodeTitle.take(180),
                podcastTitle = item.podcastTitle.take(180),
                genre = item.genre?.take(120),
                description = item.episodeDescription.toSemanticFallback(),
            ),
        )
    }.distinctBy { it.id }.sortedByDescending { it.weight }
    if (episodeSeeds.size >= maximumSeeds) return episodeSeeds.take(maximumSeeds)
    val podcastSeeds = subscribedPodcastIds.asSequence()
        .mapNotNull(String::toLongOrNull)
        .filter { it > 0L }
        .distinct()
        .map { id ->
            cx.aswin.boxlore.core.network.model.RecommendationSeedV2(
                kind = "podcast",
                id = id,
                weight = 0.35,
            )
        }
        .take(maximumSeeds - episodeSeeds.size)
        .toList()
    return episodeSeeds.take(maximumSeeds) + podcastSeeds
}

/**
 * History entries `/v2` recommendations can use: every entry whose stored ID has a recipe ID in
 * [recipeIds] (including phone-minted IDs of Podcast Index shows and RSS episodes linked to one),
 * and Podcast Index-show entries with a positive ID. Entries without an episode ID are left out.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 */
internal fun v2RecommendationHistory(history: List<HistoryItem>, recipeIds: Map<String, Long>,): List<HistoryItem> = history.filter { item ->
    val id = item.episodeId ?: return@filter false
    id in recipeIds || (item.podcastId?.startsWith("rss:") != true && (id.toLongOrNull() ?: 0L) > 0L)
}

/**
 * The current-algorithm request `/v2` takes for [history], [interests] and the followed shows, or
 * null when it has neither a seed nor an interest (the API refuses such a request).
 *
 * Seeds and exclusions use recipe IDs ([DiscoveryEpisodeIds.recipeIds]): a Podcast Index ID from an
 * older build is sent as its episode's recipe ID when the phone knows it, and phone-minted IDs with
 * a recipe ID are no longer left out. Exclusions carry recipe IDs only, the IDs `/v2` excludes by.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 */
internal suspend fun PodcastRepository.v2RecommendationRequest(
    history: List<HistoryItem>,
    interests: List<String>,
    country: String,
    subscribedPodcastIds: List<String>,
    languages: List<String>,
): RecommendationsV2Request? {
    val recipeIds = discoveryEpisodeIds.recipeIdsOrOwn(history.mapNotNull { it.episodeId })
    val usable = v2RecommendationHistory(history, recipeIds)
    val follows = subscribedPodcastIds.filterNot { it.startsWith("rss:") }
    val seeds = buildRecommendationSeeds(usable, follows, seedIds = recipeIds)
    val boundedInterests = boundedRecommendationInterests(interests)
    if (seeds.isEmpty() && boundedInterests.isEmpty()) return null
    return RecommendationsV2Request(
        contractVersion = RecommendationsV2Request.CONTRACT_VERSION,
        country = country,
        languages = languages,
        seeds = seeds,
        interests = boundedInterests,
        subscribedPodcastIds = follows.mapNotNull(String::toLongOrNull).filter { it > 0L }.distinct().take(MAX_RECOMMENDATION_IDS),
        excludedEpisodeIds = usable.mapNotNull { item -> item.episodeId?.let(recipeIds::get) }.distinct().take(MAX_RECOMMENDATION_IDS),
    )
}

/** In-memory cache key of a `/v2` recommendation request; never equal to a v1 key, which starts with the country. */
internal fun RecommendationsV2Request.v2CacheKey(): String = buildString {
    append("v2|")
    append(country)
    append('|')
    append(languages.joinToString(","))
    append('|')
    append(interests.sorted().joinToString(","))
    append('|')
    append(subscribedPodcastIds.sorted().joinToString(","))
    append('|')
    append(seeds.joinToString(",") { "${it.kind}:${it.id}:${it.weight}" })
    append('|')
    append(excludedEpisodeIds.joinToString(","))
}

internal fun PodcastRepository.fetchLegacyRecommendations(
    history: List<HistoryItem>,
    interests: List<String>,
    country: String?,
    subscribedPodcastIds: List<String>,
    subscribedGenres: List<String>,
): List<Episode> {
    val request = cx.aswin.boxlore.core.network.model.RecommendationsRequest(
        history = history,
        interests = boundedRecommendationInterests(interests),
        country = country,
        subscribedPodcastIds = subscribedPodcastIds,
        subscribedGenres = subscribedGenres,
    )
    val response = api.getPersonalizedRecommendations(
        publicKey,
        getOrCreateDeviceUuid(),
        request,
    ).execute()
    return if (response.isSuccessful) {
        response.body()?.items.orEmpty().mapNotNull { mapToEpisode(it) }
    } else {
        emptyList()
    }
}

/**
 * Personal picks on the v1 routes: `/recommendations/v2`, then the older `/recommendations` when
 * that fails. Used only while the app's v2 discovery switch is off.
 *
 * LEGACY(own-ids): the v1 discovery fallback. Remove at sunset step S7 (MIGRATION.md §6) once the
 * v1 API client methods are removed.
 */
@Suppress("LongParameterList", "kotlin:S107") // The public getPersonalizedRecommendations inputs, passed through.
internal fun PodcastRepository.v1PersonalizedRecommendations(
    history: List<HistoryItem>,
    interests: List<String>,
    country: String?,
    subscribedPodcastIds: List<String>,
    subscribedGenres: List<String>,
    languages: List<String>?,
): List<Episode> {
    val (podcastIndexHistory, podcastIndexSubscriptionIds) =
        filterToPodcastIndexScope(history, subscribedPodcastIds)
    // Normalise once, so blank-only interests count as none and both request paths agree.
    val boundedInterests = boundedRecommendationInterests(interests)
    if (podcastIndexHistory.isEmpty() && boundedInterests.isEmpty() && podcastIndexSubscriptionIds.isEmpty()) {
        // Nothing Podcast Index-scoped to base recommendations on — skip the network call.
        return emptyList()
    }
    val resolvedCountry =
        country?.let {
            cx.aswin.boxlore.core.model.ContentRegions
                .canonicalize(it)
        } ?: "us"
    val queryLanguages = resolveContentLanguagesForQuery(languages, resolvedCountry)
    val cacheKey =
        buildString {
            append(resolvedCountry)
            append("|")
            append(queryLanguages.joinToString(","))
            append("|")
            append(boundedInterests.sorted().joinToString(","))
            append("|")
            append(podcastIndexSubscriptionIds.sorted().joinToString(","))
            append("|")
            append(subscribedGenres.sorted().joinToString(","))
            append("|")
            append(podcastIndexHistory.joinToString(",") { "${it.episodeId}:${it.progressMs}" })
        }

    val cached = PodcastRepository.recommendationsCache[cacheKey]
    if (cached != null) {
        android.util.Log.d("PodcastRepository", "Cache HIT for getPersonalizedRecommendations: key=$cacheKey")
        return cached
    }
    android.util.Log.d("PodcastRepository", "Recommendation cache miss; fetching candidates")

    return try {
        val v2Results =
            try {
                fetchRecommendationV2(
                    history = podcastIndexHistory,
                    interests = boundedInterests,
                    country = resolvedCountry,
                    subscribedPodcastIds = podcastIndexSubscriptionIds,
                    languages = queryLanguages,
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("BoxCastRepo", "Recommendation v2 failed; using legacy", error)
                null
            }
        val results =
            v2Results ?: fetchLegacyRecommendations(
                history = podcastIndexHistory,
                interests = boundedInterests,
                country = resolvedCountry,
                subscribedPodcastIds = podcastIndexSubscriptionIds,
                subscribedGenres = subscribedGenres,
            )
        PodcastRepository.recommendationsCache[cacheKey] = results
        results
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("BoxCastRepo", "Personalized Recommendations Error", e)
        emptyList()
    }
}
