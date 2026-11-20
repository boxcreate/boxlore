package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PlaylistDao
import cx.aswin.boxlore.core.database.PlaylistEpisodeRow
import cx.aswin.boxlore.core.database.PlaylistShowRow
import cx.aswin.boxlore.core.database.isDismissed
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaylistOrder
import cx.aswin.boxlore.core.model.isTrailer

/**
 * Pure rules for what a playlist plays, unit-tested without Room: an episode plays when it has
 * audio, isn't a trailer, isn't finished or archived (under any of its IDs, which the history map
 * already projects) and isn't excluded; episodes midway through come first, most recently played first,
 * so Play resumes the one the listener was last in.
 */
internal object PlaylistEpisodeSelection {
    fun plays(episode: Episode, history: Map<String, ListeningHistoryEntity>, excluded: Set<String>): Boolean =
        episode.audioUrl.isNotBlank() &&
            !episode.isTrailer &&
            episode.id !in excluded &&
            history[episode.id]?.isCompleted != true &&
            history[episode.id]?.isDismissed != true

    /**
     * The episodes to play: [inProgress] that still play (most recently played first, as given),
     * then [released] (already in the playlist's order) that play and aren't among them, at most
     * [limit], each once.
     */
    fun select(
        inProgress: List<Episode>,
        released: List<Episode>,
        history: Map<String, ListeningHistoryEntity>,
        excluded: Set<String>,
        limit: Int,
    ): List<Episode> {
        val first = inProgress.filter { plays(it, history, excluded) }
        val rest = released.filter { plays(it, history, excluded) }
        return (first + rest).distinctBy { it.id }.take(limit)
    }
}

/**
 * Reads what a playlist plays from the phone ([PlaylistRepository.nextEpisodes]): its shows'
 * in-progress history and stored episodes, page by page in release order, until [limit] episodes
 * play or [MAX_PAGES] pages were read. [projectedHistory] and [withAliases] are
 * `EpisodeListenerStore.projectedHistoryOf` and `withAllAliases`, so an episode finished or queued
 * under another of its IDs counts as such.
 */
internal class PlaylistEpisodeSource(
    private val dao: PlaylistDao,
    private val projectedHistory: suspend (Collection<String>) -> Map<String, ListeningHistoryEntity>,
    private val withAliases: suspend (Collection<String>) -> Set<String>,
) {
    suspend fun next(shows: List<PlaylistShowRow>, order: PlaylistOrder, limit: Int, excludeIds: Collection<String>): List<Episode> {
        if (limit <= 0 || shows.isEmpty()) return emptyList()
        val byId = shows.associateBy { it.podcastId }
        // SQLite binds at most 999 arguments, so the shows are read in chunks and merged.
        val chunks = byId.keys.toList().chunked(SHOWS_PER_QUERY)
        val excluded = withAliases(excludeIds.filter(String::isNotBlank)).toMutableSet()

        val picked = inProgress(chunks, byId, excluded, limit).toMutableList()
        // The same episode under another ID must not come round again from the release list.
        if (picked.isNotEmpty()) excluded += withAliases(picked.map { it.id })

        var after: PlaylistEpisodeRow? = null
        var pages = 0
        var more = true
        while (more && picked.size < limit && pages < MAX_PAGES) {
            val page = releasedPage(chunks, order, after)
            pages++
            // A short page is the last one.
            more = page.size == PAGE_SIZE
            after = page.lastOrNull()
            val episodes = page.map { it.toEpisode(byId[it.podcastId]) }
            val history = if (episodes.isEmpty()) emptyMap() else projectedHistory(episodes.map { it.id })
            picked += PlaylistEpisodeSelection.select(emptyList(), episodes, history, excluded, limit - picked.size)
            excluded += picked.map { it.id }
        }
        return picked.distinctBy { it.id }.take(limit)
    }

    /** The shows' episodes the listener is midway through that still play, most recently played first. */
    private suspend fun inProgress(chunks: List<List<String>>, byId: Map<String, PlaylistShowRow>, excluded: Set<String>, limit: Int): List<Episode> {
        val rows = chunks.flatMap { dao.inProgressHistory(it, IN_PROGRESS_MIN_MS, IN_PROGRESS_LIMIT) }
            .sortedByDescending { it.lastPlayedAt }
            .take(IN_PROGRESS_LIMIT)
        if (rows.isEmpty()) return emptyList()
        // Both episode tables can hold an ID; only the row of the history row's own show is this episode.
        val stored = dao.storedEpisodes(rows.map { it.episodeId }).groupBy { it.episodeId }
        val episodes = rows.mapNotNull { row ->
            val show = byId[row.podcastId]
            stored[row.episodeId]?.firstOrNull { it.podcastId == row.podcastId }?.toEpisode(show) ?: row.toEpisode(show)
        }
        return PlaylistEpisodeSelection.select(episodes, emptyList(), projectedHistory(episodes.map { it.id }), excluded, limit)
    }

    /** The next [PAGE_SIZE] released episodes after [after] in [order], across every chunk of shows. */
    private suspend fun releasedPage(chunks: List<List<String>>, order: PlaylistOrder, after: PlaylistEpisodeRow?): List<PlaylistEpisodeRow> {
        val oldest = order == PlaylistOrder.OLDEST
        val rows = chunks.flatMap { ids ->
            if (oldest) {
                dao.oldestEpisodes(ids, after?.publishedDate ?: Long.MIN_VALUE, after?.episodeId.orEmpty(), PAGE_SIZE)
            } else {
                dao.newestEpisodes(ids, after?.publishedDate ?: Long.MAX_VALUE, after?.episodeId.orEmpty(), PAGE_SIZE)
            }
        }
        val ascending = compareBy<PlaylistEpisodeRow>({ it.publishedDate }, { it.episodeId })
        return rows.sortedWith(if (oldest) ascending else ascending.reversed()).take(PAGE_SIZE)
    }

    private fun PlaylistEpisodeRow.toEpisode(show: PlaylistShowRow?): Episode = Episode(
        id = episodeId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        imageUrl = imageUrl?.takeIf(String::isNotBlank) ?: show?.imageUrl?.takeIf(String::isNotBlank),
        podcastImageUrl = show?.imageUrl?.takeIf(String::isNotBlank),
        podcastTitle = show?.title,
        podcastId = podcastId,
        podcastGenre = show?.genre,
        podcastArtist = show?.author,
        duration = duration,
        publishedDate = publishedDate,
        chaptersUrl = chaptersUrl,
        transcriptUrl = transcriptUrl,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeType = episodeType,
        enclosureType = enclosureType,
    )

    /** An in-progress episode the episode tables no longer hold, from its history row; null without audio. */
    private fun ListeningHistoryEntity.toEpisode(show: PlaylistShowRow?): Episode? {
        val audio = episodeAudioUrl?.takeIf(String::isNotBlank) ?: return null
        return Episode(
            id = episodeId,
            title = episodeTitle,
            description = episodeDescription.orEmpty(),
            audioUrl = audio,
            imageUrl = episodeImageUrl?.takeIf(String::isNotBlank) ?: show?.imageUrl,
            podcastImageUrl = podcastImageUrl?.takeIf(String::isNotBlank) ?: show?.imageUrl,
            podcastTitle = show?.title ?: podcastName,
            podcastId = podcastId,
            podcastGenre = show?.genre,
            podcastArtist = show?.author,
            duration = (durationMs / 1_000L).toInt(),
            enclosureType = enclosureType,
        )
    }

    companion object {
        /** Episodes read per page. */
        const val PAGE_SIZE = 200

        /**
         * Pages read at most per call. Finished episodes are left out in the query, so this only
         * stops a playlist whose stored episodes are mostly finished under another of their IDs.
         */
        const val MAX_PAGES = 25

        /** In-progress plays read at most. */
        const val IN_PROGRESS_LIMIT = 50

        /** Listened at least this long before an episode counts as midway through, so a few seconds' sample doesn't jump ahead. */
        const val IN_PROGRESS_MIN_MS = 30_000L

        /** Shows per query: the episode query binds each ID twice, within SQLite's 999 arguments. */
        const val SHOWS_PER_QUERY = 400
    }
}
