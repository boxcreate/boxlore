package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.listenerShowIds
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

internal fun PodcastEntity.toDownloadManagerPodcast(): Podcast = Podcast(
    id = this.podcastId,
    title = this.title,
    artist = this.author,
    imageUrl = this.imageUrl,
    fallbackImageUrl = this.latestEpisode?.imageUrl ?: "",
    description = this.description,
    genre = this.genre ?: "Podcast",
    type = this.type,
    latestEpisode = this.latestEpisode,
    subscribedAt = this.subscribedAt,
    podcastGuid = this.podcastGuid,
    fundingUrl = this.fundingUrl,
    fundingMessage = this.fundingMessage,
    medium = this.medium,
    hasValue = this.hasValue,
    updateFrequency = this.updateFrequency,
    location = this.location,
    license = this.license,
    isLocked = this.isLocked,
    preferredSort = this.preferredSort,
    notificationsEnabled = this.notificationsEnabled,
    autoDownloadEnabled = this.autoDownloadEnabled,
    sourceType = this.sourceType,
    feedUrl = this.feedUrl,
    rssRefreshCapability = this.rssRefreshCapability,
    rssCatalogStale = this.rssCatalogStale,
    rssHasNewEpisodes = this.rssHasNewEpisodes,
)

internal object SmartDownloadCandidateLogic {
    private const val ESTIMATED_BYTES_PER_SECOND = 12_000L
    private const val DEFAULT_EPISODE_SIZE_BYTES = 50L * 1024 * 1024

    internal data class MixtapeCandidate(
        val episodeId: String,
        val score: Double,
        val isProgress: Boolean,
        val podcast: Podcast,
        val episode: Episode,
        val progressMs: Long = 0L,
        val durationMs: Long = 0L
    )

    internal data class DownloadQuotas(val subscriptionQuota: Int, val recommendationQuota: Int, val trendingQuota: Int,)

    internal fun scoreInProgressCandidate(lastPlayedAt: Long, nowMs: Long): Double {
        val hoursSinceLastPlay = (nowMs - lastPlayedAt).toDouble() / (1000.0 * 3600.0)
        return 1000.0 + 500.0 / (1.0 + hoursSinceLastPlay.coerceAtLeast(0.0) / 24.0)
    }

    internal fun buildInProgressMixtapeCandidates(
        subsMap: Map<String, PodcastEntity>,
        allHistory: List<ListeningHistoryEntity>,
        subIds: Set<String>,
        nowMs: Long,
    ): List<MixtapeCandidate> {
        // One candidate per followed show, also for rows a show switched to its feed reads from its old show.
        val inProgressCandidates = allHistory.filter { history ->
            history.podcastId in subIds && !history.isCompleted && history.progressMs > 0L
        }.groupBy { subsMap[it.podcastId]?.podcastId ?: it.podcastId }
            .mapValues { (_, eps) -> eps.maxByOrNull { it.lastPlayedAt } }
            .values.filterNotNull()

        return inProgressCandidates.mapNotNull { history ->
            val parentPod = subsMap[history.podcastId] ?: return@mapNotNull null
            if (parentPod.autoDownloadEnabled) return@mapNotNull null
            val score = scoreInProgressCandidate(history.lastPlayedAt, nowMs)

            val inProgressEpisode = Episode(
                id = history.episodeId,
                title = history.episodeTitle,
                description = "",
                audioUrl = history.episodeAudioUrl ?: "",
                imageUrl = history.episodeImageUrl ?: "",
                podcastImageUrl = history.podcastImageUrl ?: parentPod.imageUrl,
                podcastTitle = history.podcastName.takeIf { it.isNotBlank() && it != "Unknown Podcast" } ?: parentPod.title,
                podcastId = history.podcastId,
                duration = (history.durationMs / 1000).toInt(),
                publishedDate = 0L
            )

            MixtapeCandidate(
                episodeId = history.episodeId,
                score = score,
                isProgress = true,
                podcast = parentPod.toDownloadManagerPodcast(),
                episode = inProgressEpisode,
                progressMs = history.progressMs,
                durationMs = history.durationMs
            )
        }
    }

    /**
     * The unplayed episode [pod] offers: its latest episode, or for a show listened to oldest first
     * only its next episode from [resolvedSerial]. An oldest-first show missing from [resolvedSerial]
     * (a failed scan, or one that never found where the listener is) offers nothing, never its
     * latest episode, which would jump ahead.
     */
    internal fun resolveUnplayedDropCandidate(
        pod: PodcastEntity,
        resolvedSerial: Map<String, Episode>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        completedEpisodeIds: Set<String>,
    ): Pair<PodcastEntity, Episode>? {
        val sort = pod.preferredSort ?: "newest"
        val resolvedEpisode = if (sort == "oldest") {
            resolvedSerial[pod.podcastId]
        } else {
            pod.latestEpisode
        }
        return buildUnplayedDropCandidate(pod, resolvedEpisode, historyByEpisode, completedEpisodeIds)
    }

    /**
     * [pod] and [episode] when the episode is unplayed. [historyByEpisode] holds only the latest
     * history rows, so an episode missing from it can still be finished: [completedEpisodeIds]
     * (every finished episode under each of its IDs) rules it out.
     */
    internal fun buildUnplayedDropCandidate(
        pod: PodcastEntity,
        episode: Episode?,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        completedEpisodeIds: Set<String>,
    ): Pair<PodcastEntity, Episode>? {
        val ep = episode ?: return null
        if (ep.id in completedEpisodeIds) return null
        val history = historyByEpisode[ep.id]
        val isUnplayed = history == null || (history.progressMs == 0L && !history.isCompleted)
        return if (isUnplayed) {
            pod to ep.copy(podcastTitle = pod.title, podcastId = pod.podcastId)
        } else {
            null
        }
    }

    internal fun scoreUnplayedDropCandidate(pod: PodcastEntity, episode: Episode, podScoresMap: Map<String, Double>, nowMs: Long,): Double {
        val nowSeconds = nowMs / 1000.0
        val isRecent = (nowSeconds - episode.publishedDate) / 3600.0 <= 168.0
        val releasedAfterSub = episode.publishedDate > (pod.subscribedAt / 1000L) || isRecent
        val freshnessBoost = if (releasedAfterSub) {
            val hoursSinceRelease = (nowSeconds - episode.publishedDate) / 3600.0
            300.0 / (1.0 + hoursSinceRelease.coerceAtLeast(0.0) / 24.0)
        } else {
            0.0
        }
        val newTagBoost = if (releasedAfterSub) 200.0 else 0.0
        val sort = pod.preferredSort ?: "newest"
        val serialBoost = if (sort == "oldest") 150.0 else 0.0
        val parentPodScore = podScoresMap[pod.podcastId] ?: 0.0

        return 500.0 + freshnessBoost + newTagBoost + serialBoost + 0.8 * parentPodScore
    }

    internal fun buildUnplayedDropsMixtapeCandidates(
        subs: List<PodcastEntity>,
        resolvedSerial: Map<String, Episode>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        completedEpisodeIds: Set<String>,
        podScoresMap: Map<String, Double>,
        nowMs: Long,
    ): List<MixtapeCandidate> {
        val eligibleSubs = subs.filter { !it.autoDownloadEnabled }
        val unplayedDropsCandidates = eligibleSubs.mapNotNull { pod ->
            resolveUnplayedDropCandidate(pod, resolvedSerial, historyByEpisode, completedEpisodeIds)
        }

        return unplayedDropsCandidates.map { (pod, ep) ->
            MixtapeCandidate(
                episodeId = ep.id,
                score = scoreUnplayedDropCandidate(pod, ep, podScoresMap, nowMs),
                isProgress = false,
                podcast = pod.toDownloadManagerPodcast(),
                episode = ep
            )
        }
    }

    internal fun shouldIncludeCandidate(cand: MixtapeCandidate, slots: Set<Boolean>): Boolean {
        if (cand.isProgress !in slots) {
            return true
        }
        if (slots.size < 2) {
            val isNewTagged = !cand.isProgress && cand.episode.publishedDate > (cand.podcast.subscribedAt / 1000L)
            if (isNewTagged && slots.contains(true)) {
                return true
            }
            if (cand.isProgress && slots.contains(false)) {
                return true
            }
        }
        return false
    }

    internal fun deduplicateAndOrderMixtapeCandidates(
        inProgressMixtapeCandidates: List<MixtapeCandidate>,
        unplayedDropsMixtapeCandidates: List<MixtapeCandidate>
    ): List<MixtapeCandidate> {
        val allMixtapeCandidates = (inProgressMixtapeCandidates + unplayedDropsMixtapeCandidates)
            .sortedByDescending { it.score }

        val deduplicatedCandidates = mutableListOf<MixtapeCandidate>()
        val seenEpisodeIds = mutableSetOf<String>()
        val podcastSlots = mutableMapOf<String, MutableSet<Boolean>>()

        for (cand in allMixtapeCandidates) {
            if (cand.episodeId in seenEpisodeIds) continue
            val podId = cand.podcast.id
            val slots = podcastSlots.getOrPut(podId) { mutableSetOf() }
            if (shouldIncludeCandidate(cand, slots)) {
                slots.add(cand.isProgress)
                seenEpisodeIds.add(cand.episodeId)
                deduplicatedCandidates.add(cand)
            }
        }

        val orderedCandidates = mutableListOf<MixtapeCandidate>()
        val inProgressList = deduplicatedCandidates.filter { it.isProgress }
        val unplayedList = deduplicatedCandidates.filter { !it.isProgress }.toMutableList()

        for (ipCand in inProgressList) {
            orderedCandidates.add(ipCand)
            val nextEpCand = unplayedList.find { it.podcast.id == ipCand.podcast.id }
            if (nextEpCand != null) {
                orderedCandidates.add(nextEpCand)
                unplayedList.remove(nextEpCand)
            }
        }
        orderedCandidates.addAll(unplayedList)
        return orderedCandidates
    }

    internal fun generateMixtapeCandidates(
        subs: List<PodcastEntity>,
        allHistory: List<ListeningHistoryEntity>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        resolvedSerial: Map<String, Episode>,
        completedEpisodeIds: Set<String>,
        podScoresMap: Map<String, Double>,
        nowMs: Long,
    ): List<MixtapeCandidate> {
        val eligibleSubs = subs.filter { !it.autoDownloadEnabled }
        // A show switched to its feed owns the rows its old Podcast Index show keeps (`listenerShowIds`),
        // unless the listener follows that show too.
        val subsMap = eligibleSubs.associateByTo(LinkedHashMap()) { it.podcastId }
        eligibleSubs.forEach { sub -> sub.listenerShowIds.forEach { subsMap.putIfAbsent(it, sub) } }
        val subIds = subsMap.keys

        val inProgress = buildInProgressMixtapeCandidates(subsMap, allHistory, subIds, nowMs)
        val unplayed = buildUnplayedDropsMixtapeCandidates(eligibleSubs, resolvedSerial, historyByEpisode, completedEpisodeIds, podScoresMap, nowMs)

        return deduplicateAndOrderMixtapeCandidates(inProgress, unplayed)
    }

    /** One page of a show's oldest-first episode list; [sourceCount] advances the offset. */
    internal data class SerialPage(val episodes: List<Episode>, val sourceCount: Int, val hasMore: Boolean,)

    /**
     * The oldest-first shows of [subs] whose next episode Smart Downloads can offer: never an
     * auto-download show, whose pick the candidates drop. Highest [podScores] first, so the network
     * pages the sync shares go to the shows the listener plays most rather than by title.
     */
    internal fun serialShowsToResolve(subs: List<PodcastEntity>, podScores: Map<String, Double>,): List<PodcastEntity> = subs
        .filter { !it.autoDownloadEnabled && (it.preferredSort ?: "newest") == "oldest" }
        .sortedByDescending { podScores[it.podcastId] ?: 0.0 }

    /**
     * The first unfinished episode ([isUnfinished]) after [anchorId]'s episode in a show's
     * oldest-first list, read page by page from [loadPage] until it is found or [maxEpisodes] have
     * been read. With no [anchorId], the first unfinished episode. When the whole list was read and
     * nothing unfinished follows the anchor, or the anchor is not in it, the first unfinished
     * episode read. A later page that comes back empty is a failed read and gives no pick rather
     * than an early episode the listener skipped.
     *
     * A scan that stops at [maxEpisodes] or [maxPages] looks for the anchor among the newest episodes
     * ([loadNewestPage], newest first; null when it may not be read): the first unfinished episode
     * after it there, else the first unfinished episode read, as for a whole list. An anchor in
     * neither range gives no pick, so a listener partway through is never jumped ahead. The anchor
     * is matched under any of its stored IDs ([groupTokens]).
     */
    internal suspend fun findSerialNextEpisode(
        anchorId: String?,
        isUnfinished: (Episode) -> Boolean,
        groupTokens: suspend (Collection<String>) -> Map<String, String>,
        loadPage: suspend (offset: Int) -> SerialPage,
        maxEpisodes: Int,
        loadNewestPage: suspend () -> List<Episode>? = { null },
        /** Pages [loadPage] may read, whatever each holds: a page short of rows can't stretch a show's request budget. */
        maxPages: Int = Int.MAX_VALUE,
    ): Episode? {
        val scan = SerialScan(anchorToken = anchorId?.let { id -> groupTokens(listOf(id))[id] ?: id }, isUnfinished = isUnfinished)
        var offset = 0
        var pages = 0
        while (offset < maxEpisodes && pages < maxPages) {
            val page = loadPage(offset)
            pages++
            // The page before said there was more, so an empty page here is a failed read, not the end of the list.
            if (offset > 0 && page.sourceCount <= 0) return null
            val tokens = if (scan.anchorFound) emptyMap() else groupTokens(page.episodes.map { it.id })
            scan.read(page.episodes, tokens)?.let { return it }
            if (!page.hasMore || page.sourceCount <= 0) return scan.firstUnfinished
            offset += page.sourceCount
        }
        return scan.nextFromNewest(loadNewestPage() ?: return null, groupTokens)
    }

    /**
     * The pages one Smart Downloads sync may read from the network for its serial shows. Each show
     * read from the network gets its first page, as before, and up to [perShowPages] pages while
     * the [extraPages] shared by every show last; a show whose scan stops at its cap reads one
     * newest-first page while the [newestPages] shared by every show last. So paging past the first
     * page adds at most [extraPages] plus [newestPages] requests to a sync; first pages are not
     * capped here. A show read from the phone (`rss:` or a ready catalog) costs nothing here.
     */
    internal class SerialNetworkPages(private var extraPages: Int, private val perShowPages: Int, private var newestPages: Int,) {
        /** Pages the next show read from the network may load. */
        fun pagesForNextShow(): Int = 1 + minOf(extraPages, perShowPages - 1).coerceAtLeast(0)

        /** Records that a show loaded a page past its first from the network. */
        fun spendExtraPage() {
            if (extraPages > 0) extraPages--
        }

        /** Takes one newest-first page for a show whose scan stopped at its cap; false once none are left. */
        fun takeNewestPage(): Boolean {
            if (newestPages <= 0) return false
            newestPages--
            return true
        }
    }

    internal fun estimateDownloadSize(download: DownloadedEpisodeEntity): Long = if (download.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
        if (download.sizeBytes > 0L) {
            download.sizeBytes
        } else {
            val durSec = download.durationMs / 1000L
            if (durSec > 0) durSec * ESTIMATED_BYTES_PER_SECOND else DEFAULT_EPISODE_SIZE_BYTES
        }
    } else if (download.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) {
        val durSec = download.durationMs / 1000L
        if (durSec > 0) durSec * ESTIMATED_BYTES_PER_SECOND else DEFAULT_EPISODE_SIZE_BYTES
    } else {
        0L
    }

    internal fun estimateEpisodeSize(episode: Episode): Long = if (episode.duration > 0) {
        episode.duration.toLong() * ESTIMATED_BYTES_PER_SECOND
    } else {
        DEFAULT_EPISODE_SIZE_BYTES
    }

    internal fun computeDownloadQuotas(maxCount: Int): DownloadQuotas {
        val subQuota = (maxCount * 0.7).toInt().coerceAtLeast(1)
        val recQuota = (maxCount * 0.2).toInt().coerceAtLeast(1)
        val trendQuota = (maxCount - subQuota - recQuota).coerceAtLeast(1)
        return DownloadQuotas(
            subscriptionQuota = subQuota,
            recommendationQuota = recQuota,
            trendingQuota = trendQuota,
        )
    }
}

/** [SmartDownloadCandidateLogic.findSerialNextEpisode]'s progress through a show's oldest-first list, one page at a time. */
private class SerialScan(val anchorToken: String?, private val isUnfinished: (Episode) -> Boolean,) {
    /** True once the anchor episode has been read, or when there is none. */
    var anchorFound = anchorToken == null
        private set

    /** The first unfinished episode read before the anchor. */
    var firstUnfinished: Episode? = null
        private set

    /** The first unfinished episode after the anchor in [episodes], whose IDs map to group tokens in [tokens]; null when none yet. */
    fun read(episodes: List<Episode>, tokens: Map<String, String>): Episode? {
        for (episode in episodes) {
            when {
                anchorFound -> if (isUnfinished(episode)) return episode
                (tokens[episode.id] ?: episode.id) == anchorToken -> anchorFound = true
                firstUnfinished == null && isUnfinished(episode) -> firstUnfinished = episode
            }
        }
        return null
    }

    /**
     * After a scan cut off at its cap: the first unfinished episode after the anchor in [newest]
     * (the show's newest episodes, newest first), else [firstUnfinished], since nothing after the
     * anchor is unfinished up to the end of the list. Null when the anchor is not in [newest].
     */
    suspend fun nextFromNewest(newest: List<Episode>, groupTokens: suspend (Collection<String>) -> Map<String, String>,): Episode? {
        val anchor = anchorToken ?: return null
        val tokens = groupTokens(newest.map { it.id })
        val anchorIndex = newest.indexOfFirst { (tokens[it.id] ?: it.id) == anchor }
        if (anchorIndex == -1) return null
        // The episodes newer than the anchor, in publish order.
        return newest.subList(0, anchorIndex).asReversed().firstOrNull(isUnfinished) ?: firstUnfinished
    }
}

/** These candidates without archived episodes ([dismissedEpisodeIds]): never downloaded, even one the listener had started. */
internal fun List<SmartDownloadCandidateLogic.MixtapeCandidate>.withoutArchived(dismissedEpisodeIds: Set<String>): List<SmartDownloadCandidateLogic.MixtapeCandidate> =
    if (dismissedEpisodeIds.isEmpty()) this else filter { it.episode.id !in dismissedEpisodeIds }
