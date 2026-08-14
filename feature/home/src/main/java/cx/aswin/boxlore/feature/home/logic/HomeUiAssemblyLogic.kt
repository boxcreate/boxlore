package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.core.playback.PlaybackSkipPolicy
import cx.aswin.boxlore.feature.home.HomeEditorialRow
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import cx.aswin.boxlore.feature.home.SmartHeroItem

internal data class HomeMixtapeCache(
    val podcasts: List<Podcast>,
    val unplayedCount: Int,
    val episodes: List<Episode>,
    val subSignature: Set<String>,
    val staleRestartEnabled: Boolean = true,
)

internal fun homeMixtapeCacheOrNull(
    podcasts: List<Podcast>?,
    unplayedCount: Int?,
    episodes: List<Episode>?,
    subSignature: Set<String>?,
    staleRestartEnabled: Boolean?,
): HomeMixtapeCache? =
    HomeMixtapeCache(
        podcasts = podcasts ?: return null,
        unplayedCount = unplayedCount ?: return null,
        episodes = episodes ?: return null,
        subSignature = subSignature ?: return null,
        staleRestartEnabled = staleRestartEnabled ?: return null,
    )

internal data class HomeUiAssemblyResult(
    val heroItems: List<SmartHeroItem>,
    val latestEpisodes: List<Podcast>,
    val unplayedEpisodeCount: Int,
    val completedEpisodeCount: Int,
    val subscribedPodcasts: List<Podcast>,
    val discoverPodcasts: List<Podcast>,
    val recommendations: List<Episode>,
    val episodePlaybackState: Map<String, Pair<EpisodeStatus, Float>>,
    val softExpireProgressEpisodeIds: Set<String>,
    val showImportBanner: Boolean,
    val briefing: Briefing?,
    val briefingChapters: List<cx.aswin.boxlore.core.model.Chapter>,
    val isLoading: Boolean,
    val isFilterLoading: Boolean,
    val stablePodcastOrder: List<String>,
    val mixtapeCache: HomeMixtapeCache?,
    val shouldUpdateForYouCache: Boolean,
)

/**
 * Pure-ish Home screen UI assembly extracted from [cx.aswin.boxlore.feature.home.HomeViewModel.loadData].
 * Ranking/mixtape builders are injected so this stays free of Android ViewModel deps.
 */
internal object HomeUiAssemblyLogic {
    @Suppress("LongParameterList", "LongMethod")
    suspend fun assemble(
        trendingList: List<Podcast>,
        rankedRecommendations: List<Episode>,
        resumeList: List<PlaybackSession>,
        subs: List<Podcast>,
        allHistory: List<HomeListeningHistoryItem>,
        resolvedSerial: Map<String, Episode>,
        completedEpisodeIds: Set<String>,
        region: String,
        editorialRows: List<HomeEditorialRow>,
        previousStableOrder: List<String>?,
        podcastScores: Map<String, Double>,
        previousMixtape: HomeMixtapeCache?,
        buildMixtape: suspend (scores: Map<String, Double>, recommendations: List<Episode>) -> MixtapeEngine.Result,
        isTrendingLoaded: Boolean,
        hasDismissedImportBanner: Boolean,
        rawBriefing: Briefing?,
        rawBriefingChapters: List<cx.aswin.boxlore.core.model.Chapter>,
        briefingDismissedDate: String,
        briefingDismissedForever: Boolean,
        staleRestartEnabled: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
        pinnedPodcastIds: List<String> = emptyList(),
    ): HomeUiAssemblyResult {
        val completedCount = allHistory.count { it.isCompleted }
        val catchUp =
            HomeCatchUpLogic.buildCatchUpBuckets(
                subs = subs,
                allHistory = allHistory,
                resolvedSerial = resolvedSerial,
            )

        val orderToUse =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = previousStableOrder,
                subs = subs,
                scores = podcastScores,
                pinnedIds = pinnedPodcastIds,
            )
        val sortedSubs = HomeShowsOrderLogic.orderedSubs(orderToUse, subs)

        val currentSubIds = subs.map { it.id }.toSet()
        val mixtapeCache =
            if (previousMixtape != null &&
                previousMixtape.staleRestartEnabled == staleRestartEnabled &&
                !HomeShowsOrderLogic.shouldInvalidateMixtapeCache(
                    previousMixtape.subSignature,
                    currentSubIds,
                )
            ) {
                previousMixtape
            } else {
                null
            }

        val mixtapePodcasts: List<Podcast>
        val mixtapeCount: Int
        val mixtapeEpisodes: List<Episode>
        val nextMixtape: HomeMixtapeCache?

        if (mixtapeCache != null) {
            mixtapePodcasts = mixtapeCache.podcasts
            mixtapeCount = mixtapeCache.unplayedCount
            mixtapeEpisodes = mixtapeCache.episodes
            nextMixtape = mixtapeCache
        } else {
            val result = buildMixtape(podcastScores, rankedRecommendations)
            mixtapePodcasts = result.podcasts
            mixtapeCount = result.unplayedCount
            mixtapeEpisodes = result.episodes
            nextMixtape =
                HomeMixtapeCache(
                    podcasts = mixtapePodcasts,
                    unplayedCount = mixtapeCount,
                    episodes = mixtapeEpisodes,
                    subSignature = currentSubIds,
                    staleRestartEnabled = staleRestartEnabled,
                )
        }

        val heroList =
            HomeHeroLogic.buildHeroItems(
                resumeList = resumeList,
                unplayedBucket = catchUp.unplayed,
                trendingList = trendingList,
                subs = subs,
                region = region,
            )

        val discover =
            discoverPodcastsExcluding(
                trending = trendingList,
                heroItems = heroList,
                editorialRows = editorialRows,
            ).orEmpty()

        val episodePlaybackState =
            HomePlaybackStateLogic.buildEpisodePlaybackState(
                allHistory = allHistory,
                completedEpisodeIds = completedEpisodeIds,
            )

        val softExpireProgressEpisodeIds =
            if (staleRestartEnabled) {
                allHistory
                    .asSequence()
                    .filter { !it.isCompleted && it.progressMs > 0L }
                    .filter { PlaybackSkipPolicy.isStaleLastPlayed(it.lastPlayedAt, nowMs) }
                    .map { it.episodeId }
                    .toSet()
            } else {
                emptySet()
            }

        val showBriefing =
            HomePlaybackStateLogic.shouldShowBriefing(
                rawBriefing = rawBriefing,
                completedEpisodeIds = completedEpisodeIds,
                briefingDismissedDate = briefingDismissedDate,
                briefingDismissedForever = briefingDismissedForever,
                heroList = heroList,
            )

        return HomeUiAssemblyResult(
            heroItems = heroList,
            latestEpisodes = mixtapePodcasts,
            unplayedEpisodeCount = mixtapeCount,
            completedEpisodeCount = completedCount,
            subscribedPodcasts = sortedSubs,
            discoverPodcasts = discover,
            recommendations = rankedRecommendations,
            episodePlaybackState = episodePlaybackState,
            softExpireProgressEpisodeIds = softExpireProgressEpisodeIds,
            showImportBanner = sortedSubs.isEmpty() && !hasDismissedImportBanner,
            briefing = if (showBriefing) rawBriefing else null,
            briefingChapters = if (showBriefing) rawBriefingChapters else emptyList(),
            isLoading = !isTrendingLoaded,
            isFilterLoading = trendingList.isEmpty(),
            stablePodcastOrder = orderToUse,
            mixtapeCache = nextMixtape,
            shouldUpdateForYouCache = trendingList.isNotEmpty(),
        )
    }
}
