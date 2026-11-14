package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.catalog.EpisodeLinkResult
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.resolveEpisodeLink
import cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator
import cx.aswin.boxlore.core.catalog.toPodcast
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.playback.PlaybackProgressNow
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.addToQueue
import cx.aswin.boxlore.core.playback.pause
import cx.aswin.boxlore.core.playback.playEpisode
import cx.aswin.boxlore.core.playback.progressNow
import cx.aswin.boxlore.core.playback.setPlaybackSpeed
import cx.aswin.boxlore.core.playback.skipBackward
import cx.aswin.boxlore.core.playback.skipForward
import cx.aswin.boxlore.core.playback.togglePlayPause
import cx.aswin.boxlore.core.wearlink.PhoneLibraryPort
import cx.aswin.boxlore.core.wearlink.PhoneLibraryRows
import cx.aswin.boxlore.core.wearlink.PhonePlayerPort
import cx.aswin.boxlore.core.wearlink.PhonePlayerState
import cx.aswin.boxlore.core.wearlink.WearLinkEpisode
import cx.aswin.boxlore.core.wearlink.WearLinkShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val MILLIS_PER_SECOND = 1_000L

/** The episode as the watch link carries it. A shared ID travels as the identity both sides know. */
internal fun Episode.toLink(showId: String?, showName: String? = null): WearLinkEpisode =
    WearLinkEpisode(
        episodeId = id,
        podcastId = podcastId ?: showId.orEmpty(),
        title = title,
        podcastName = podcastTitle ?: showName.orEmpty(),
        imageUrl = imageUrl ?: podcastImageUrl,
        audioUrl = audioUrl.takeIf { it.isNotBlank() },
        durationMs = duration * MILLIS_PER_SECOND,
        publishedAt = publishedDate * MILLIS_PER_SECOND,
        recipeId = id.toLongOrNull()?.takeIf(EpisodeRecipeId::isRecipeId),
    )

/** The two lists the phone works out for the watch's Home tiles. */
internal object PhoneListRules {
    const val MAX_NEW = 20

    /** A Daily Mix is ranking work; it is rebuilt at most this often, however often the library is sent. */
    const val MIX_TTL_MS = 30L * 60L * 1_000L

    /** The newest unplayed episode of each followed show, newest first: what the phone's own New episodes lists. */
    fun newEpisodes(latest: List<Episode>, completedIds: Set<String>): List<Episode> =
        latest.filter { it.id !in completedIds }.sortedByDescending { it.publishedDate }.take(MAX_NEW)

    fun mixIsFresh(builtAt: Long, now: Long): Boolean = builtAt > 0L && now - builtAt < MIX_TTL_MS
}

/** The phone's library for the watch link, over the same sync rows the cloud gets. */
internal class PhoneLibraryAdapter(
    private val podcasts: PodcastDao,
    private val sync: () -> UserSyncCoordinator,
    private val savedEpisodes: () -> LocalEpisodeCatalogPort,
    private val history: ListeningHistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : PhoneLibraryPort {
    private var mix: List<WearLinkEpisode> = emptyList()
    private var mixBuiltAt = 0L

    override val showsChanged: Flow<Unit> =
        podcasts.getSubscribedPodcasts()
            .map { shows -> shows.map { Triple(it.podcastId, it.title, it.imageUrl) } }
            .distinctUntilChanged()
            .map { }

    override suspend fun library(historyLimit: Int): PhoneLibraryRows {
        val rows = sync().linkedDeviceRows(historyLimit)
        val shows = rows.shows.map { WearLinkShow(it.podcastId, it.title, it.author, it.imageUrl.takeIf(String::isNotBlank), it.feedUrl) }
        val completed = history.getCompletedEpisodeIds().toSet()
        val latest = rows.shows.mapNotNull { show -> show.latestEpisode?.copy(podcastId = show.podcastId, podcastTitle = show.title, podcastImageUrl = show.imageUrl) }
        val newEpisodes = PhoneListRules.newEpisodes(latest, completed).map { it.toLink(it.podcastId, it.podcastTitle) }
        return PhoneLibraryRows(shows, rows.history, newEpisodes, dailyMix(rows.shows))
    }

    /** The Daily Mix as the phone would play it, kept for [PhoneListRules.MIX_TTL_MS]. */
    private suspend fun dailyMix(shows: List<PodcastEntity>): List<WearLinkEpisode> {
        val now = clock()
        if (PhoneListRules.mixIsFresh(mixBuiltAt, now)) return mix
        val built = MixtapeEngine.build(subscriptions = shows.map { it.toPodcast() }, history = history.getRecentHistoryList(MIX_HISTORY_WINDOW))
        mix = built.episodes.map { it.toLink(it.podcastId, it.podcastTitle) }
        mixBuiltAt = now
        return mix
    }

    override suspend fun apply(subscriptions: List<UserSubscriptionSyncDto>, history: List<ListeningHistorySyncDto>) =
        sync().applyLinkedDeviceChanges(subscriptions, history)

    override suspend fun episodes(podcastId: String, limit: Int): List<WearLinkEpisode> =
        savedEpisodes().getPage(podcastId, limit = limit, offset = 0, sort = NEWEST).map { it.toLink(podcastId) }

    private companion object {
        const val NEWEST = "newest"

        /** How much recent listening the mix looks at, as on the phone's other mixes. */
        const val MIX_HISTORY_WINDOW = 200
    }
}

/** What the watch is told about the phone's player: the player's own position and length where it has them. */
internal object PhoneProgressRules {
    /**
     * [live] is trusted only for the episode the state shows. A mini player can show an episode
     * the player has not loaded yet, and that one keeps the state's own numbers.
     */
    fun state(episode: WearLinkEpisode?, isPlaying: Boolean, statePositionMs: Long, speed: Float, live: PlaybackProgressNow?): PhonePlayerState {
        val mine = live?.takeIf { it.episodeId == episode?.episodeId }
        val length = mine?.durationMs?.takeIf { it > 0L }
        return PhonePlayerState(
            episode = if (length != null) episode?.copy(durationMs = length) else episode,
            isPlaying = isPlaying,
            positionMs = mine?.positionMs ?: statePositionMs,
            speed = speed,
        )
    }

    /**
     * What plays after the current episode: the player's queue from the entry after [currentId] on.
     * A queue that does not hold the current episode, as after a hand-over from another device,
     * all comes after it.
     */
    fun upNext(queue: List<Episode>, currentId: String?): List<Episode> {
        val current = queue.indexOfFirst { it.id == currentId }
        return if (current >= 0) queue.drop(current + 1) else queue
    }
}

/** The phone's player for the watch link. */
internal class PhonePlayerAdapter(
    private val playback: () -> PlaybackRepository,
    private val shows: () -> PodcastRepository,
) : PhonePlayerPort {
    override val state: Flow<PhonePlayerState>
        get() =
            playback().playerState
                .map { state ->
                    // The state's position stands still while the app is off screen, so the player is asked.
                    val live = withContext(Dispatchers.Main.immediate) { playback().progressNow() }
                    val episode = state.currentEpisode?.toLink(state.currentPodcast?.id, state.currentPodcast?.title)
                    val upNext = PhoneProgressRules.upNext(state.queue, state.currentEpisode?.id).map { it.toLink(it.podcastId) }
                    PhoneProgressRules.state(episode, state.isPlaying, state.position, state.playbackSpeed, live).copy(upNext = upNext)
                }.distinctUntilChanged()

    override suspend fun togglePlayPause() = playback().togglePlayPause()

    override suspend fun pause() = playback().pause()

    override suspend fun seekBack() = playback().skipBackward()

    override suspend fun seekForward() = playback().skipForward()

    override suspend fun setSpeed(speed: Float) = playback().setPlaybackSpeed(speed)

    override suspend fun play(episode: WearLinkEpisode, positionMs: Long, upNext: List<WearLinkEpisode>) {
        val (found, show) = resolve(episode) ?: return
        // The player's controller only answers on the main thread; the link's work runs elsewhere.
        withContext(Dispatchers.Main.immediate) { playback().playEpisode(found, show, initialPositionMs = positionMs) }
        // What waited on the watch waits on the phone now. One that cannot be found is left out, not fatal.
        for (queued in upNext) {
            val (next, itsShow) = resolve(queued) ?: continue
            withContext(Dispatchers.Main.immediate) { playback().addToQueue(next, itsShow) }
        }
    }

    /** The phone's own copy of an episode the watch named, with its show; null when it has no audio to play. */
    private suspend fun resolve(episode: WearLinkEpisode): Pair<Episode, Podcast>? {
        val wanted = episode.recipeId?.toString() ?: episode.episodeId
        val found = (shows().resolveEpisodeLink(wanted, episode.podcastId) as? EpisodeLinkResult.Found)?.episode ?: episode.asEpisode() ?: return null
        val show =
            shows().getPodcastDetails(episode.podcastId)
                ?: Podcast(id = episode.podcastId, title = episode.podcastName, artist = "", imageUrl = episode.imageUrl.orEmpty())
        return found to show
    }

    /** The watch's own description of the episode, for one the phone cannot find by ID. */
    private fun WearLinkEpisode.asEpisode(): Episode? {
        val audio = audioUrl ?: return null
        return Episode(
            id = episodeId,
            title = title,
            description = "",
            audioUrl = audio,
            imageUrl = imageUrl,
            podcastTitle = podcastName,
            podcastId = podcastId,
            duration = (durationMs / MILLIS_PER_SECOND).toInt(),
        )
    }
}
