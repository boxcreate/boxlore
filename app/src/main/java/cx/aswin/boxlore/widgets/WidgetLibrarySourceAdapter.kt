package cx.aswin.boxlore.widgets

import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.toScorable
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.widgets.WidgetEpisodeRow
import cx.aswin.boxlore.feature.widgets.WidgetLibrarySource
import cx.aswin.boxlore.feature.widgets.WidgetShowRow
import cx.aswin.boxlore.feature.widgets.logic.LibraryWidgetLogic
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Projects Library → Subscriptions (Shows + Latest) into widget rows.
 * Lives in `:app` so scoring/history stay out of `:feature:widgets`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetLibrarySourceAdapter(
    private val subscriptionRepository: SubscriptionRepository,
    private val playbackRepository: PlaybackRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val adaptiveScorer: AdaptiveCandidateScorer,
    scope: CoroutineScope,
) : WidgetLibrarySource {
    private data class Projection(val subscriptions: List<WidgetShowRow>, val newEpisodes: List<WidgetEpisodeRow>,)

    private val projection: Flow<Projection> =
        combine(
            combine(
                subscriptionRepository.subscribedPodcasts,
                playbackRepository.getAllHistory(),
                userPreferencesRepository.subscriptionSortStream,
            ) { podcasts, history, sortName ->
                Triple(podcasts, history, sortName)
            },
            combine(
                userPreferencesRepository.hideCompletedInSubsStream,
                userPreferencesRepository.latestEpisodesSortUseSmartStream,
                userPreferencesRepository.lastSeenEpisodesStream,
            ) { hideCompleted, useSmart, lastSeen ->
                Triple(hideCompleted, useSmart, lastSeen)
            },
        ) { podcastsHistorySort, filters ->
            val (podcasts, history, sortName) = podcastsHistorySort
            val (hideCompleted, useSmart, lastSeen) = filters
            Inputs(podcasts, history, sortName, hideCompleted, useSmart, lastSeen)
        }.mapLatest { inputs ->
            val enriched = enrichWithHistory(inputs.podcasts, inputs.history)
            val sort = parseSort(inputs.sortName)
            val sortedShows = sortShows(enriched, inputs.history, sort)
            val showRows =
                LibraryWidgetLogic.truncateShows(
                    sortedShows.map { podcast ->
                        WidgetShowRow(
                            podcastId = podcast.id,
                            title = podcast.title,
                            subtitle = podcast.artist,
                            artworkUrl = artworkUrlFor(podcast),
                            deepLinkUri = podcastDeepLink(podcast.id),
                            isNew = podcast.isLatestEpisodeNew(inputs.lastSeen[podcast.id]),
                        )
                    },
                )

            val withLatest = enriched.filter { it.latestEpisode != null }
            val latestPool =
                if (inputs.hideCompleted) {
                    withLatest.filter { it.episodeStatus != EpisodeStatus.COMPLETED }
                } else {
                    withLatest
                }
            val sortedLatest = sortLatest(latestPool, inputs.history, inputs.useSmart)
            val episodeRows =
                LibraryWidgetLogic.truncateEpisodes(
                    sortedLatest.mapNotNull { podcast ->
                        val episode = podcast.latestEpisode ?: return@mapNotNull null
                        WidgetEpisodeRow(
                            episodeId = episode.id,
                            episodeTitle = episode.title,
                            podcastId = podcast.id,
                            podcastTitle = podcast.title,
                            artworkUrl = episodeArtUrl(episode.imageUrl, podcast),
                            deepLinkUri = episodeDeepLink(episode.id, podcast.id, podcast.title),
                        )
                    },
                )
            Projection(showRows, episodeRows)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = Projection(emptyList(), emptyList()),
        )

    override val subscriptions: Flow<List<WidgetShowRow>> = projection.map { it.subscriptions }

    override val newEpisodes: Flow<List<WidgetEpisodeRow>> = projection.map { it.newEpisodes }

    private data class Inputs(
        val podcasts: List<Podcast>,
        val history: List<ListeningHistoryEntity>,
        val sortName: String,
        val hideCompleted: Boolean,
        val useSmart: Boolean,
        val lastSeen: Map<String, String>,
    )

    private suspend fun sortShows(podcasts: List<Podcast>, history: List<ListeningHistoryEntity>, sort: ShowSort,): List<Podcast> = when (sort) {
        ShowSort.SmartRank -> {
            val scores =
                try {
                    adaptiveScorer.scorePodcasts(
                        podcasts = podcasts.map { it.toScorable() },
                        history = history,
                        objective = RankingObjective.YOUR_SHOWS,
                        surface = RankingSurface.LIBRARY,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emptyMap()
                }
            podcasts
                .map { pod ->
                    val fallback = pod.latestEpisode?.publishedDate?.toDouble() ?: 0.0
                    pod to (scores[pod.id] ?: fallback)
                }.sortedWith(
                    compareByDescending<Pair<Podcast, Double>> { it.second }
                        .thenBy { it.first.title },
                ).map { it.first }
        }
        ShowSort.RecentlyUpdated ->
            podcasts.sortedByDescending { it.latestEpisode?.publishedDate ?: 0L }
        ShowSort.Alphabetical ->
            podcasts.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        ShowSort.MostListened -> {
            val counts = history.groupBy { it.podcastId }.mapValues { it.value.size }
            podcasts.sortedByDescending { counts[it.id] ?: 0 }
        }
    }

    private suspend fun sortLatest(podcasts: List<Podcast>, history: List<ListeningHistoryEntity>, useSmart: Boolean,): List<Podcast> {
        if (!useSmart) {
            return podcasts.sortedByDescending { it.latestEpisode!!.publishedDate }
        }
        val inputs =
            podcasts.mapNotNull { podcast ->
                podcast.latestEpisode?.let { episode ->
                    EpisodeRankingInput(
                        episode = episode,
                        podcast = podcast,
                        priorScore = episode.publishedDate.toDouble().coerceAtLeast(0.0),
                        source = CandidateSource.SUBSCRIPTION,
                    )
                }
            }
        val scores =
            try {
                adaptiveScorer.scoreEpisodes(
                    inputs = inputs,
                    history = history,
                    objective = RankingObjective.YOUR_SHOWS,
                    surface = RankingSurface.LIBRARY,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                inputs.associate { it.episode.id to it.priorScore }
            }
        return podcasts.sortedByDescending { scores[it.latestEpisode?.id] ?: 0.0 }
    }

    private enum class ShowSort {
        SmartRank,
        RecentlyUpdated,
        Alphabetical,
        MostListened,
    }

    private fun parseSort(name: String): ShowSort = runCatching { ShowSort.valueOf(name) }.getOrDefault(ShowSort.SmartRank)

    companion object {
        fun enrichWithHistory(podcasts: List<Podcast>, allHistory: List<ListeningHistoryEntity>,): List<Podcast> = podcasts.map { podcast ->
            val episode = podcast.latestEpisode ?: return@map podcast
            val history = allHistory.find { it.episodeId == episode.id }
            when {
                history == null || (history.progressMs == 0L && !history.isCompleted) ->
                    podcast.copy(episodeStatus = EpisodeStatus.UNPLAYED)
                !history.isCompleted && history.progressMs > 0L -> {
                    val progress =
                        if (history.durationMs > 0) {
                            (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    podcast.copy(
                        resumeProgress = progress,
                        episodeStatus = EpisodeStatus.IN_PROGRESS,
                    )
                }
                history.isCompleted ->
                    podcast.copy(
                        resumeProgress = 1f,
                        episodeStatus = EpisodeStatus.COMPLETED,
                    )
                else -> podcast
            }
        }

        fun podcastDeepLink(podcastId: String): String = "boxlore://podcast/${encode(podcastId)}"

        fun episodeDeepLink(episodeId: String, podcastId: String, podcastTitle: String,): String = "boxlore://episode/${encode(episodeId)}" +
            "?autoplay=false" +
            "&podcastId=${encode(podcastId)}" +
            "&podcastTitle=${encode(podcastTitle)}"

        /** JVM-safe path segment encoding (spaces as %20). */
        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

        fun subscriptionsDeepLink(tab: Int): String = "boxlore://library/subscriptions?tab=$tab"

        fun artworkUrlFor(podcast: Podcast): String? = podcast.imageUrl
            .takeIf { it.isNotBlank() }
            ?: podcast.fallbackImageUrl?.takeIf { it.isNotBlank() }

        fun episodeArtUrl(episodeImageUrl: String?, podcast: Podcast,): String? = episodeImageUrl?.takeIf { it.isNotBlank() }
            ?: artworkUrlFor(podcast)
    }
}
