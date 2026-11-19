package cx.aswin.boxlore.widgets

import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ScorablePodcast
import cx.aswin.boxlore.core.database.sortedByListening
import cx.aswin.boxlore.core.database.toScorable
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.completedEpisodeIds
import cx.aswin.boxlore.core.playback.dismissedEpisodeIds
import cx.aswin.boxlore.core.playback.inProgressForEpisodeLookup
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.RankingHistorySignature
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingScoreCache
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.widgets.WidgetEpisodeRow
import cx.aswin.boxlore.feature.widgets.WidgetLibraryCounts
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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Projects Library → Subscriptions (Shows + Latest) into widget rows.
 * Lives in `:app` so scoring/history stay out of `:feature:widgets`.
 *
 * The widget ranks from [RankingHistorySignature], not raw history: playback saves progress every few
 * seconds, and those saves change the signature only when an episode is first started or the
 * last-played hour moves; a new, finished or liked episode always re-ranks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetLibrarySourceAdapter
@Suppress("LongParameterList")
internal constructor(
    podcasts: Flow<List<Podcast>>,
    history: Flow<List<ListeningHistoryEntity>>,
    sortName: Flow<String>,
    completedEpisodeIds: Flow<Set<String>> = flowOf(emptySet()),
    inProgress: Flow<List<ListeningHistoryEntity>> = flowOf(emptyList()),
    dismissedEpisodeIds: Flow<Set<String>> = flowOf(emptySet()),
    hideCompleted: Flow<Boolean>,
    useSmart: Flow<Boolean>,
    lastSeen: Flow<Map<String, String>>,
    private val scoreShows: suspend (List<ScorablePodcast>, List<ListeningHistoryEntity>) -> Map<String, Double>,
    private val scoreLatest: suspend (List<EpisodeRankingInput>, List<ListeningHistoryEntity>) -> Map<String, Double>,
    scope: CoroutineScope,
) : WidgetLibrarySource {
    constructor(
        subscriptionRepository: SubscriptionRepository,
        playbackRepository: PlaybackRepository,
        userPreferencesRepository: UserPreferencesRepository,
        adaptiveScorer: AdaptiveCandidateScorer,
        scope: CoroutineScope,
    ) : this(
        podcasts = subscriptionRepository.subscribedPodcasts,
        history = playbackRepository.getRecentHistory(),
        sortName = userPreferencesRepository.subscriptionSortStream,
        completedEpisodeIds = playbackRepository.completedEpisodeIds,
        inProgress = playbackRepository.inProgressForEpisodeLookup,
        dismissedEpisodeIds = playbackRepository.dismissedEpisodeIds,
        hideCompleted = userPreferencesRepository.hideCompletedInSubsStream,
        useSmart = userPreferencesRepository.latestEpisodesSortUseSmartStream,
        lastSeen = userPreferencesRepository.lastSeenEpisodesStream,
        scoreShows = { shows, rows ->
            adaptiveScorer.scorePodcasts(
                podcasts = shows,
                history = rows,
                objective = RankingObjective.YOUR_SHOWS,
                surface = RankingSurface.LIBRARY,
            )
        },
        scoreLatest = { inputs, rows ->
            adaptiveScorer.scoreEpisodes(
                inputs = inputs,
                history = rows,
                objective = RankingObjective.YOUR_SHOWS,
                surface = RankingSurface.LIBRARY,
            )
        },
        scope = scope,
    )

    private data class Projection(
        val subscriptions: List<WidgetShowRow>,
        val newEpisodes: List<WidgetEpisodeRow>,
        val counts: WidgetLibraryCounts = WidgetLibraryCounts(),
    )

    private val showScores = RankingScoreCache<Pair<List<ScorablePodcast>, RankingHistorySignature>>()
    private val latestScores = RankingScoreCache<Pair<List<EpisodeRankingInput>, RankingHistorySignature>>()

    private val projection: Flow<Projection> =
        combine(
            combine(podcasts, rankingHistory(history), sortName) { podcastList, signedHistory, sort ->
                Triple(podcastList, signedHistory, sort)
            },
            combine(
                hideCompleted,
                useSmart,
                lastSeen,
                combine(completedEpisodeIds, dismissedEpisodeIds) { completed, dismissed -> completed to dismissed },
                startedEpisodes(inProgress),
            ) { hide, smart, seen, (completed, dismissed), started ->
                Filters(hide, smart, seen, completed, started, dismissed)
            },
        ) { podcastsHistorySort, filters ->
            val (podcastList, signedHistory, sort) = podcastsHistorySort
            Inputs(podcastList, signedHistory, sort, filters)
        }.mapLatest { inputs ->
            val enriched = enrichWithHistory(inputs.podcasts, inputs.history.rows, inputs.filters.completedIds, inputs.filters.inProgress)
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
                            fallbackArtworkUrls = podcast.artworkUrls(),
                            deepLinkUri = podcastDeepLink(podcast.id),
                            isNew = podcast.isLatestEpisodeNew(inputs.filters.lastSeen[podcast.id]),
                        )
                    },
                )

            val pool = latestEpisodePool(enriched, inputs.filters.hideCompleted, inputs.filters.dismissedIds)
            val sortedLatest = sortLatest(pool, inputs.history, inputs.filters.useSmart)
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
                            fallbackArtworkUrls = episode.artworkUrls(podcast),
                            deepLinkUri = episodeDeepLink(episode.id, podcast.id, podcast.title),
                        )
                    },
                )
            Projection(
                subscriptions = showRows,
                newEpisodes = episodeRows,
                counts = libraryCounts(sortedShows, sortedLatest),
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = Projection(emptyList(), emptyList()),
        )

    override val subscriptions: Flow<List<WidgetShowRow>> = projection.map { it.subscriptions }

    override val newEpisodes: Flow<List<WidgetEpisodeRow>> = projection.map { it.newEpisodes }
    override val counts: Flow<WidgetLibraryCounts> = projection.map { it.counts }

    private data class Filters(
        val hideCompleted: Boolean,
        val useSmart: Boolean,
        val lastSeen: Map<String, String>,
        val completedIds: Set<String>,
        val inProgress: List<ListeningHistoryEntity>,
        val dismissedIds: Set<String> = emptySet(),
    )

    private data class Inputs(
        val podcasts: List<Podcast>,
        val history: SignedHistory,
        val sortName: String,
        val filters: Filters,
    )

    /** History with the signature it ranks by; two values with one signature rank the same. */
    internal class SignedHistory(val rows: List<ListeningHistoryEntity>, val signature: RankingHistorySignature)

    private suspend fun sortShows(podcasts: List<Podcast>, history: SignedHistory, sort: ShowSort,): List<Podcast> = when (sort) {
        ShowSort.SmartRank -> {
            val scores =
                try {
                    val scorable = podcasts.map { it.toScorable() }
                    showScores.get(scorable to history.signature) { scoreShows(scorable, history.rows) }
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
        ShowSort.MostListened -> podcasts.sortedByListening(history.rows)
    }

    private suspend fun sortLatest(podcasts: List<Podcast>, history: SignedHistory, useSmart: Boolean,): List<Podcast> {
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
                latestScores.get(inputs to history.signature) { scoreLatest(inputs, history.rows) }
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
        /**
         * The shows whose latest episode New Episodes lists: all of them, or only the unfinished ones.
         * An archived latest episode ([dismissedIds]) is never listed, whatever the setting.
         */
        fun latestEpisodePool(enriched: List<Podcast>, hideCompleted: Boolean, dismissedIds: Set<String> = emptySet()): List<Podcast> {
            val withLatest = enriched.filter { it.latestEpisode != null && it.latestEpisode!!.id !in dismissedIds }
            return if (hideCompleted) withLatest.filter { it.episodeStatus != EpisodeStatus.COMPLETED } else withLatest
        }

        /** The whole library's sizes, taken from the full lists before the widget's rows are capped. */
        fun libraryCounts(shows: List<Podcast>, latest: List<Podcast>): WidgetLibraryCounts = WidgetLibraryCounts(subscriptions = shows.size, newEpisodes = latest.size)

        /**
         * [history] as the widget ranks it: a new value only when its [RankingHistorySignature] changes,
         * so a progress save re-ranks only when it starts an episode or reaches a new hour.
         */
        internal fun rankingHistory(history: Flow<List<ListeningHistoryEntity>>): Flow<SignedHistory> = history
            .map { rows -> SignedHistory(rows, RankingHistorySignature.of(rows)) }
            .distinctUntilChangedBy { it.signature }

        /**
         * Every started episode, again only when one starts or stops being in progress: like
         * [rankingHistory], a progress save alone doesn't redraw the widget.
         */
        internal fun startedEpisodes(inProgress: Flow<List<ListeningHistoryEntity>>): Flow<List<ListeningHistoryEntity>> =
            inProgress.distinctUntilChangedBy { rows -> rows.mapTo(HashSet()) { it.episodeId } }

        /**
         * [podcasts] with their latest episode's status. [completedIds] holds every completed
         * episode and [inProgress] every started one, so an episode finished or started long ago,
         * or behind many marked played at once, keeps its status even when the recent [allHistory]
         * no longer holds its row.
         */
        fun enrichWithHistory(
            podcasts: List<Podcast>,
            allHistory: List<ListeningHistoryEntity>,
            completedIds: Set<String> = emptySet(),
            inProgress: List<ListeningHistoryEntity> = emptyList(),
        ): List<Podcast> {
            val historyById = (inProgress + allHistory).associateBy { it.episodeId }
            return podcasts.map { podcast -> enrich(podcast, historyById, completedIds) }
        }

        private fun enrich(podcast: Podcast, historyById: Map<String, ListeningHistoryEntity>, completedIds: Set<String>): Podcast {
            val episode = podcast.latestEpisode ?: return podcast
            val history = historyById[episode.id]
            return when {
                episode.id in completedIds || history?.isCompleted == true ->
                    podcast.copy(
                        resumeProgress = 1f,
                        episodeStatus = EpisodeStatus.COMPLETED,
                    )
                history == null || history.progressMs == 0L ->
                    podcast.copy(episodeStatus = EpisodeStatus.UNPLAYED)
                history.progressMs > 0L -> {
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
