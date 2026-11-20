package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PlaylistDao
import cx.aswin.boxlore.core.database.PlaylistEpisodeRow
import cx.aswin.boxlore.core.database.PlaylistShowRow
import cx.aswin.boxlore.core.database.isDismissed
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PLAYLIST_TAKE_TURNS_WINDOW_DAYS
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.isTrailer
import cx.aswin.boxlore.core.model.normalizePlaylistPerShowLimit

/**
 * Pure rules for what a playlist plays, unit-tested without Room: an episode plays when it has
 * audio, isn't a trailer, isn't finished or archived (under any of its IDs, which the history map
 * already projects) and isn't excluded; episodes midway through come first, most recently played first,
 * so Play resumes the one the listener was last in. Then each show's episodes that play, at most the
 * playlist's limit per show, are mixed by its [PlaylistMix] ([arrange]).
 */
internal object PlaylistEpisodeSelection {
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_DAY = 86_400L

    /** Release date, then episode ID: the order the episode queries page in. */
    private val OLDEST_FIRST = compareBy<Episode>({ it.publishedDate }, { it.id })
    private val NEWEST_FIRST = OLDEST_FIRST.reversed()

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

    /**
     * The earliest release date (epoch seconds, as [Episode.publishedDate]) [mix] plays at [nowMs]:
     * [PLAYLIST_TAKE_TURNS_WINDOW_DAYS] back when taking turns, null (no window) otherwise.
     */
    fun windowStart(mix: PlaylistMix, nowMs: Long): Long? =
        if (mix == PlaylistMix.TAKE_TURNS) nowMs / MS_PER_SECOND - PLAYLIST_TAKE_TURNS_WINDOW_DAYS * SECONDS_PER_DAY else null

    /** True when [mix] plays each show from its oldest episode; the other mixes start from the newest. */
    fun oldestFirst(mix: PlaylistMix): Boolean = mix == PlaylistMix.ONE_SHOW_AT_A_TIME

    /**
     * One show's [episodes] that play, released at or after [windowStart] when it is given, in the
     * show's order for [mix] ([oldestFirst] or newest first), each once, at most [cap] (null for all).
     */
    fun showPicks(
        mix: PlaylistMix,
        episodes: List<Episode>,
        history: Map<String, ListeningHistoryEntity>,
        excluded: Set<String>,
        windowStart: Long?,
        cap: Int?,
    ): List<Episode> {
        val picks = episodes
            .filter { plays(it, history, excluded) && (windowStart == null || it.publishedDate >= windowStart) }
            .distinctBy { it.id }
            .sortedWith(if (oldestFirst(mix)) OLDEST_FIRST else NEWEST_FIRST)
        return if (cap == null) picks else picks.take(cap)
    }

    /**
     * The shows' picks ([perShow], in playlist show order, each in its show's order) as one list:
     * taking turns ([takeTurns]), merged newest first, or one show after another.
     */
    fun combine(mix: PlaylistMix, perShow: List<List<Episode>>): List<Episode> = when (mix) {
        PlaylistMix.TAKE_TURNS -> takeTurns(perShow)
        PlaylistMix.NEWEST_FIRST -> perShow.flatten().sortedWith(NEWEST_FIRST)
        PlaylistMix.ONE_SHOW_AT_A_TIME -> perShow.flatten()
    }

    /** Every show's first episode in show order, then every show's second, and so on; a show that ran out is skipped. */
    fun takeTurns(perShow: List<List<Episode>>): List<Episode> {
        val rounds = perShow.maxOfOrNull { it.size } ?: 0
        return (0 until rounds).flatMap { round -> perShow.mapNotNull { it.getOrNull(round) } }
    }

    /** [first], then [rest] not among them, each once, at most [limit]. */
    fun join(first: List<Episode>, rest: List<Episode>, limit: Int): List<Episode> = (first + rest).distinctBy { it.id }.take(limit)

    /**
     * Everything a playlist plays, in order: [inProgress] that still play (as given), then each of
     * [shows]' episodes (playlist show order) picked by [showPicks] with [perShowLimit] (null or 0 for
     * no limit) and [windowStart], mixed by [combine]. [PlaylistEpisodeSource] reads the same rules
     * from Room a page at a time.
     */
    fun arrange(
        mix: PlaylistMix,
        perShowLimit: Int?,
        windowStart: Long?,
        inProgress: List<Episode>,
        shows: List<List<Episode>>,
        history: Map<String, ListeningHistoryEntity>,
        excluded: Set<String>,
    ): List<Episode> {
        val first = select(inProgress, emptyList(), history, excluded, Int.MAX_VALUE)
        val taken = excluded + first.map { it.id }
        val cap = normalizePlaylistPerShowLimit(perShowLimit)
        return join(first, combine(mix, shows.map { showPicks(mix, it, history, taken, windowStart, cap) }), Int.MAX_VALUE)
    }
}

/**
 * Reads what a playlist plays from the phone ([PlaylistRepository.nextEpisodes]): its shows'
 * in-progress history, then their stored episodes by the playlist's mix and limit per show
 * ([PlaylistEpisodeSelection]), until [limit] episodes play. Newest first with no limit pages
 * through every show at once ([MAX_PAGES] pages at most); the other settings read show by show, each
 * only as deep as it can still add (one show at a time stops once [limit] is filled).
 * [projectedHistory] and [withAliases] are `EpisodeListenerStore.projectedHistoryOf` and
 * `withAllAliases`, so an episode finished or queued under another of its IDs counts as such.
 */
internal class PlaylistEpisodeSource(
    private val dao: PlaylistDao,
    private val projectedHistory: suspend (Collection<String>) -> Map<String, ListeningHistoryEntity>,
    private val withAliases: suspend (Collection<String>) -> Set<String>,
) {
    suspend fun next(
        shows: List<PlaylistShowRow>,
        mix: PlaylistMix,
        perShowLimit: Int?,
        limit: Int,
        excludeIds: Collection<String>,
        nowMs: Long,
    ): List<Episode> {
        if (limit <= 0 || shows.isEmpty()) return emptyList()
        val byId = shows.associateBy { it.podcastId }
        // SQLite binds at most 999 arguments, so the shows are read in chunks and merged.
        val chunks = byId.keys.toList().chunked(SHOWS_PER_QUERY)
        val excluded = withAliases(excludeIds.filter(String::isNotBlank)).toMutableSet()

        val picked = inProgress(chunks, byId, excluded, limit)
        // The same episode under another ID must not come round again from the shows' episodes.
        if (picked.isNotEmpty()) excluded += withAliases(picked.map { it.id })
        val room = limit - picked.size
        if (room <= 0) return picked.take(limit)

        val cap = normalizePlaylistPerShowLimit(perShowLimit)
        val rest = if (mix == PlaylistMix.NEWEST_FIRST && cap == null) {
            newestAcross(chunks, byId, excluded, room)
        } else {
            byShow(byId.values.toList(), mix, cap, room, excluded, PlaylistEpisodeSelection.windowStart(mix, nowMs))
        }
        return PlaylistEpisodeSelection.join(picked, rest, limit)
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

    /** Up to [room] of every show's episodes that play, newest first, [PAGE_SIZE] at a time across the chunks of shows. */
    private suspend fun newestAcross(
        chunks: List<List<String>>,
        byId: Map<String, PlaylistShowRow>,
        excluded: MutableSet<String>,
        room: Int,
    ): List<Episode> {
        val picked = mutableListOf<Episode>()
        var before: PlaylistEpisodeRow? = null
        var pages = 0
        var more = true
        while (more && picked.size < room && pages < MAX_PAGES) {
            val page = newestPage(chunks, before)
            pages++
            // A short page is the last one.
            more = page.size == PAGE_SIZE
            before = page.lastOrNull()
            val episodes = page.map { it.toEpisode(byId[it.podcastId]) }
            val history = if (episodes.isEmpty()) emptyMap() else projectedHistory(episodes.map { it.id })
            val fresh = PlaylistEpisodeSelection.select(emptyList(), episodes, history, excluded, room - picked.size)
            picked += fresh
            excluded += fresh.map { it.id }
        }
        return picked
    }

    /** The next [PAGE_SIZE] released episodes before [before], newest first, across every chunk of shows. */
    private suspend fun newestPage(chunks: List<List<String>>, before: PlaylistEpisodeRow?): List<PlaylistEpisodeRow> {
        val rows = chunks.flatMap { ids -> dao.newestEpisodes(ids, before?.publishedDate ?: Long.MAX_VALUE, before?.episodeId.orEmpty(), PAGE_SIZE) }
        return rows.sortedWith(compareByDescending<PlaylistEpisodeRow> { it.publishedDate }.thenByDescending { it.episodeId }).take(PAGE_SIZE)
    }

    /**
     * Each show's picks in playlist order, at most [cap] (null for no limit) and never more than
     * [room], mixed by [mix]. One show at a time reads no further shows once [room] is filled.
     */
    private suspend fun byShow(
        shows: List<PlaylistShowRow>,
        mix: PlaylistMix,
        cap: Int?,
        room: Int,
        excluded: MutableSet<String>,
        windowStart: Long?,
    ): List<Episode> {
        val oneShowAtATime = mix == PlaylistMix.ONE_SHOW_AT_A_TIME
        val perShow = mutableListOf<List<Episode>>()
        var picked = 0
        for (show in shows) {
            val want = minOf(cap ?: room, if (oneShowAtATime) room - picked else room)
            if (want <= 0) break
            val picks = showPicks(show, mix, want, excluded, windowStart)
            // An episode stored under two of the shows plays once, from the first.
            excluded += picks.map { it.id }
            perShow += picks
            picked += picks.size
        }
        return PlaylistEpisodeSelection.combine(mix, perShow)
    }

    /**
     * Up to [want] of [show]'s episodes that play, in its order for [mix]. Pages start just over
     * [want] and double up to [PAGE_SIZE], so a show whose newest episodes are queued or finished
     * under other IDs is still read through; newest first stops at the first episode before [windowStart].
     */
    private suspend fun showPicks(show: PlaylistShowRow, mix: PlaylistMix, want: Int, excluded: Set<String>, windowStart: Long?): List<Episode> {
        val oldest = PlaylistEpisodeSelection.oldestFirst(mix)
        val ids = listOf(show.podcastId)
        val picks = mutableListOf<Episode>()
        var after: PlaylistEpisodeRow? = null
        var size = minOf(PAGE_SIZE, want + SHOW_PAGE_SLACK)
        var pages = 0
        var more = true
        while (more && picks.size < want && pages < MAX_PAGES) {
            val page = if (oldest) {
                dao.oldestEpisodes(ids, after?.publishedDate ?: Long.MIN_VALUE, after?.episodeId.orEmpty(), size)
            } else {
                dao.newestEpisodes(ids, after?.publishedDate ?: Long.MAX_VALUE, after?.episodeId.orEmpty(), size)
            }
            pages++
            val inWindow = if (windowStart == null || oldest) page else page.takeWhile { it.publishedDate >= windowStart }
            // A short page, or one reaching past the window, is the last one.
            more = page.size == size && inWindow.size == page.size
            after = page.lastOrNull()
            size = minOf(PAGE_SIZE, size * 2)
            val episodes = inWindow.map { it.toEpisode(show) }
            if (episodes.isNotEmpty()) {
                val history = projectedHistory(episodes.map { it.id })
                picks += PlaylistEpisodeSelection.showPicks(mix, episodes, history, excluded, windowStart, want - picks.size)
            }
        }
        return picks
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
        /** Episodes read per page, at most. */
        const val PAGE_SIZE = 200

        /**
         * Pages read at most per call across all shows (newest first with no limit), or per show.
         * Finished episodes are left out in the query, so this only stops a playlist whose stored
         * episodes are mostly finished under another of their IDs.
         */
        const val MAX_PAGES = 25

        /** A show's first page reads this many episodes more than it still needs, for ones that don't play. */
        const val SHOW_PAGE_SLACK = 20

        /** In-progress plays read at most. */
        const val IN_PROGRESS_LIMIT = 50

        /** Listened at least this long before an episode counts as midway through, so a few seconds' sample doesn't jump ahead. */
        const val IN_PROGRESS_MIN_MS = 30_000L

        /** Shows per query: the episode query binds each ID twice, within SQLite's 999 arguments. */
        const val SHOWS_PER_QUERY = 400
    }
}
