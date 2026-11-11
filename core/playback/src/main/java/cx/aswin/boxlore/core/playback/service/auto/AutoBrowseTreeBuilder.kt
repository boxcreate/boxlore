package cx.aswin.boxlore.core.playback.service.auto

import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import cx.aswin.boxlore.core.catalog.R
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.database.toScorable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isTrailer
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Builds what each Android Auto screen shows.
 *
 * The car opens on four tabs: Home, Continue, New and Shows. Home leads with the episodes in
 * progress as full rows, then a grid of blocks that each open a list. There is no queue screen: the
 * queue belongs to the player, and follows an episode resumed from Continue listening. Lists carry
 * no "Play all" row: tapping an episode plays on through its list ([AutoPlayContextTracker]).
 */
@Suppress("LargeClass", "TooManyFunctions")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AutoBrowseTreeBuilder(private val host: AutoBrowseLibraryHost, private val mediaResolver: AutoMediaResolver,) {
    /** Lists show an episode stored under several IDs once, with its projected state (own-episode-IDs P4). */
    private val listenerStore get() = host.database.episodeListenerStore()

    @Volatile
    private var lastDrivePicks: List<MediaItem> = emptyList()

    @Volatile
    private var lastMixtape: List<MediaItem> = emptyList()

    @Volatile
    private var lastMixtapeUpdatedAt: Long = 0L

    /** Show covers already looked up for rows without an image; an empty value means the show has none. */
    private val showCovers = java.util.concurrent.ConcurrentHashMap<String, String>()

    // ── Tabs ────────────────────────────────────────────────────────────────────────────────

    fun getRootChildren(): List<MediaItem> = listOf(
        tab(AutoBrowseContract.HOME_ID, R.string.auto_home, R.drawable.ic_auto_tab_home, AutoBrowseContract.gridChildrenExtras()),
        tab(AutoBrowseContract.CONTINUE_ID, R.string.auto_continue, R.drawable.ic_auto_tab_up_next, AutoBrowseContract.listChildrenExtras()),
        tab(AutoBrowseContract.HOME_NEW_EPISODES_ID, R.string.auto_tab_new, R.drawable.ic_auto_tab_new, AutoBrowseContract.listChildrenExtras()),
        tab(AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID, R.string.auto_tab_shows, R.drawable.ic_auto_tab_shows, AutoBrowseContract.gridChildrenExtras()),
    )

    private fun tab(id: String, @StringRes title: Int, @DrawableRes icon: Int, childStyle: Bundle): MediaItem = AutoMediaItemFactory.browsable(
        id = id,
        title = host.getString(title),
        artworkUri = iconUri(icon),
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
        childStyleExtras = childStyle,
    )

    // ── Home ────────────────────────────────────────────────────────────────────────────────

    /**
     * Home: the episodes in progress as full rows under "Continue listening" (a row has room for
     * the whole title, the show and the time left; a tile has two short lines), then the blocks.
     * A block whose list would be empty is left out, so no tap ends on an empty screen.
     */
    suspend fun getHomeChildren(): List<MediaItem> {
        val resume = resumeItems().take(HOME_CONTINUE_ROWS)
        val subscriptions = subscriptions()
        val newCount = newEpisodes(subscriptions).size
        val downloadCount = runCatching { host.database.downloadedEpisodeDao().getCompletedDownloads(LIST_LIMIT).size }.getOrDefault(0)
        val likedCount = runCatching { likedRows().size }.getOrDefault(0)
        val hasHistory = runCatching { host.database.listeningHistoryDao().getRecentHistoryList(1).isNotEmpty() }.getOrDefault(false)
        // The blocks get a title only when the rows above need telling apart from them.
        val blocksTitle = host.getString(R.string.auto_group_browse).takeIf { resume.isNotEmpty() }
        val blocks = buildList {
            if (newCount > 0) {
                add(block(AutoBrowseContract.HOME_NEW_EPISODES_ID, R.string.auto_new_episodes, countLabel(R.plurals.auto_new_count, newCount), R.drawable.ic_auto_tab_new))
            }
            if (subscriptions.isNotEmpty()) {
                add(
                    block(
                        AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID,
                        R.string.auto_your_shows,
                        countLabel(R.plurals.auto_show_count, subscriptions.size),
                        R.drawable.ic_auto_tab_shows,
                        childStyle = AutoBrowseContract.gridChildrenExtras(),
                        collageId = AutoBrowseContract.LIBRARY_ID,
                    ),
                )
                add(block(AutoBrowseContract.HOME_DRIVE_MIX_ID, R.string.auto_daily_mix, host.getString(R.string.auto_daily_mix_subtitle), R.drawable.ic_auto_tab_new))
            }
            if (downloadCount > 0) {
                add(block(AutoBrowseContract.DOWNLOADS_ID, R.string.auto_downloads, countLabel(R.plurals.auto_episode_count, downloadCount), R.drawable.ic_download))
            }
            add(block(AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID, R.string.auto_drive_picks, host.getString(R.string.auto_drive_picks_subtitle), R.drawable.ic_auto_explore))
            if (likedCount > 0) {
                add(block(AutoBrowseContract.LIBRARY_LIKED_ID, R.string.auto_liked, countLabel(R.plurals.auto_episode_count, likedCount), R.drawable.ic_auto_like))
            }
            if (hasHistory) {
                add(
                    block(
                        AutoBrowseContract.LIBRARY_HISTORY_ID,
                        R.string.auto_listening_history,
                        host.getString(R.string.auto_listening_history_subtitle),
                        R.drawable.ic_auto_tab_up_next,
                    ),
                )
            }
            add(block(AutoBrowseContract.DISCOVER_ID, R.string.auto_explore, host.getString(R.string.auto_explore_subtitle), R.drawable.ic_auto_explore, useCollage = false))
        }
        return resumeRows(resume, host.getString(R.string.auto_group_continue)) + blocks.map { it.withGroupTitle(blocksTitle) }
    }

    private fun MediaItem.withGroupTitle(title: String?): MediaItem {
        if (title == null) return this
        val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, title) }
        return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    /** A Home block that opens [target]'s list. Its artwork is the list's collage, or [icon] while there is none. */
    @Suppress("LongParameterList")
    private fun block(
        target: String,
        @StringRes title: Int,
        subtitle: String,
        @DrawableRes icon: Int,
        childStyle: Bundle = AutoBrowseContract.listChildrenExtras(),
        collageId: String = target,
        useCollage: Boolean = true,
    ): MediaItem = AutoMediaItemFactory.browsable(
        id = AutoBrowseContract.OPEN_PREFIX + target,
        title = host.getString(title),
        subtitle = subtitle,
        artworkUri = (if (useCollage) folderArtwork(collageId) else null) ?: iconUri(icon),
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
        childStyleExtras = childStyle,
        singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
    )

    private fun countLabel(@PluralsRes plural: Int, count: Int): String = if (count <= 0) {
        host.getString(R.string.auto_none_yet)
    } else {
        host.getQuantityString(plural, count)
    }

    // ── Continue listening ──────────────────────────────────────────────────────────────────

    /** The Continue tab: every episode in progress, most recently played first. */
    suspend fun getContinueListeningChildren(): List<MediaItem> = resumeRows(resumeItems(), null)

    /** The saved queue, for a car that still remembers the old Up next screen. The queue now lives in the player. */
    suspend fun getQueueChildren(): List<MediaItem> = queueRows(queueEpisodes().take(LIST_LIMIT), null)

    private suspend fun resumeRows(items: List<ListeningHistoryEntity>, groupTitle: String?): List<MediaItem> {
        val downloaded = downloadedIds()
        return items.map { entity ->
            AutoMediaItemFactory.fromHistory(
                history = entity,
                source = AutoBrowseContract.SOURCE_CONTINUE,
                artworkUri = rowArt(entity.episodeImageUrl, entity.podcastImageUrl, entity.podcastId),
                subtitle = buildProgressSubtitle(entity.podcastName, entity.progressMs, entity.durationMs),
                groupTitle = groupTitle,
                isDownloaded = entity.episodeId in downloaded,
            )
        }
    }

    private suspend fun queueRows(queue: List<Episode>, groupTitle: String?): List<MediaItem> {
        if (queue.isEmpty()) return emptyList()
        val history = historyById()
        val downloaded = downloadedIds()
        return queue.map { episode ->
            val played = history[episode.id]
            AutoMediaItemFactory.fromEpisode(
                episode = episode,
                source = AutoBrowseContract.SOURCE_QUEUE,
                artworkUri = rowArt(episode.imageUrl, episode.podcastImageUrl ?: played?.podcastImageUrl, episode.podcastId),
                podcastTitle = episode.podcastTitle,
                row = AutoEpisodeRow(mediaIdPrefix = AutoBrowseContract.QUEUE_PREFIX, groupTitle = groupTitle, isDownloaded = episode.id in downloaded, subtitle = episodeSubtitle(episode.podcastTitle, episode, played, withDate = false), progress = played?.progressFraction(), isCompleted = played?.isCompleted == true),
            )
        }
    }

    // ── New episodes ────────────────────────────────────────────────────────────────────────

    suspend fun getNewEpisodesChildren(): List<MediaItem> {
        val newEpisodes = newEpisodes(subscriptions())
        if (newEpisodes.isEmpty()) return emptyList()
        val history = historyById()
        val downloaded = downloadedIds()
        return newEpisodes.map { (episode, podcast) ->
            val played = history[episode.id]
            AutoMediaItemFactory.fromEpisode(
                episode = episode,
                source = AutoBrowseContract.SOURCE_NEW,
                artworkUri = rowArt(episode.imageUrl, podcast.imageUrl, podcast.podcastId),
                podcastTitle = podcast.title,
                row = AutoEpisodeRow(isDownloaded = episode.id in downloaded, subtitle = episodeSubtitle(podcast.title, episode, played, withDate = true), progress = played?.progressFraction()),
            )
        }
    }

    /** The newest unplayed episode of each subscribed show, newest first. */
    private suspend fun newEpisodes(subscriptions: List<PodcastEntity>): List<Pair<Episode, PodcastEntity>> {
        if (subscriptions.isEmpty()) return emptyList()
        val completedIds = runCatching {
            listenerStore.projectCompletedIds(host.database.listeningHistoryDao().getCompletedEpisodeIds())
        }.getOrDefault(emptySet())
        return subscriptions
            .mapNotNull { entity -> entity.latestEpisode?.takeIf { it.id !in completedIds }?.let { it to entity } }
            .sortedByDescending { (episode, _) -> episode.publishedDate }
            .take(NEW_EPISODES_LIMIT)
    }

    // ── Shows ───────────────────────────────────────────────────────────────────────────────

    /** Subscribed shows, most listened first. A show whose name fits on one line says when it has a fresh unplayed episode. */
    suspend fun getSubscriptionsChildren(): List<MediaItem> {
        val subscriptions = subscriptions()
        if (subscriptions.isEmpty()) return emptyList()
        val history = listenerStore.projectRows(host.database.listeningHistoryDao().getRecentHistoryList(HISTORY_WINDOW))
        // A ranking failure must not empty the Shows tab: the shows then keep their stored order.
        val scores: Map<String, Double> = runCatching {
            host.adaptiveCandidateScorer.scorePodcasts(
                podcasts = subscriptions.map { it.toScorable() },
                history = history,
                objective = RankingObjective.YOUR_SHOWS,
                surface = RankingSurface.ANDROID_AUTO,
            )
        }.getOrDefault(emptyMap())
        val freshIds = newEpisodes(subscriptions).associate { (episode, podcast) -> podcast.podcastId to episode.publishedDate }
        val now = System.currentTimeMillis()
        return subscriptions.sortedByDescending { scores[it.podcastId] ?: 0.0 }.map { entity ->
            val freshLabel = when (freshIds[entity.podcastId]?.let { AutoDayLabels.daysAgo(it, now) }) {
                0L -> host.getString(R.string.auto_new_today)
                1L -> host.getString(R.string.auto_new_yesterday)
                else -> null
            }
            showTile("${AutoBrowseContract.SUBSCRIPTION_PREFIX}${entity.podcastId}", entity.title, freshLabel, entity.imageUrl)
        }
    }

    /**
     * A show as a tile. Both lines belong to its name: the second carries on where the first is
     * cut, and only a name that fits on one line leaves room for [note]. The publisher is not shown.
     */
    private fun showTile(id: String, title: String, note: String?, imageUrl: String?): MediaItem = AutoMediaItemFactory.browsable(
        id = id,
        title = title,
        subtitle = AutoShowTitles.secondLine(title) ?: note,
        artworkUri = episodeArt(imageUrl) ?: iconUri(R.drawable.ic_auto_episode),
        mediaType = MediaMetadata.MEDIA_TYPE_PODCAST,
        childStyleExtras = AutoBrowseContract.listChildrenExtras(),
        singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
    )

    /**
     * A show's episodes, newest first, with the ones in progress moved to the top under
     * "Jump back in". Rows say when the episode came out, not the show's name again.
     */
    suspend fun getPodcastEpisodes(podcastId: String): List<MediaItem> {
        val podcastEntity = host.database.podcastDao().getPodcast(podcastId)
        val episodes = host.podcastRepository.getEpisodesPaginated(podcastId, limit = LIST_LIMIT, sort = "newest").episodes
        // Looked up by the listed ID: an episode stored under several IDs shows its state under any of them.
        val history = historyById()
        val downloaded = downloadedIds()
        val (started, rest) = episodes.partition { episode -> history[episode.id]?.let { !it.isCompleted && it.progressMs > 0 } == true }
        val titled = started.isNotEmpty() && rest.isNotEmpty()
        suspend fun rows(list: List<Episode>, groupTitle: String?) = list.map { episode ->
            val played = history[episode.id]
            val showTitle = podcastEntity?.title ?: episode.podcastTitle
            AutoMediaItemFactory.fromEpisode(
                episode = episode,
                source = AutoBrowseContract.SOURCE_SHOW,
                artworkUri = rowArt(episode.imageUrl, podcastEntity?.imageUrl ?: episode.podcastImageUrl, podcastId),
                podcastTitle = showTitle,
                row = AutoEpisodeRow(groupTitle = groupTitle, isDownloaded = episode.id in downloaded, subtitle = episodeSubtitle(null, episode, played, withDate = true), progress = played?.progressFraction(), isCompleted = played?.isCompleted == true),
            )
        }
        return rows(started, host.getString(R.string.auto_group_continue).takeIf { titled }) +
            rows(rest, host.getString(R.string.auto_group_latest).takeIf { titled })
    }

    // ── Lists opened from Home ──────────────────────────────────────────────────────────────

    suspend fun getLikedChildren(): List<MediaItem> {
        val downloaded = downloadedIds()
        return likedRows().map {
            AutoMediaItemFactory.fromHistory(
                history = it,
                source = AutoBrowseContract.SOURCE_LIKED,
                artworkUri = rowArt(it.episodeImageUrl, it.podcastImageUrl, it.podcastId),
                subtitle = AutoMediaItemFactory.buildDurationSubtitle(it.podcastName, it.durationMs),
                groupTitle = null,
                isDownloaded = it.episodeId in downloaded,
            )
        }
    }

    private suspend fun likedRows(): List<ListeningHistoryEntity> =
        listenerStore.projectRows(host.database.listeningHistoryDao().getLikedEpisodesList(LIST_LIMIT)) { it.isLiked }

    suspend fun getHistoryChildren(): List<MediaItem> {
        val downloaded = downloadedIds()
        return listenerStore.projectRows(host.database.listeningHistoryDao().getRecentHistoryList(LIST_LIMIT)).map {
            AutoMediaItemFactory.fromHistory(
                history = it,
                source = AutoBrowseContract.SOURCE_HISTORY,
                artworkUri = rowArt(it.episodeImageUrl, it.podcastImageUrl, it.podcastId),
                subtitle = if (!it.isCompleted && it.progressMs > 0) {
                    buildProgressSubtitle(it.podcastName, it.progressMs, it.durationMs)
                } else {
                    AutoMediaItemFactory.buildDurationSubtitle(it.podcastName, it.durationMs)
                },
                groupTitle = null,
                isDownloaded = it.episodeId in downloaded,
            )
        }
    }

    suspend fun getMixtapeChildren(): List<MediaItem> {
        val now = System.currentTimeMillis()
        if (lastMixtape.isNotEmpty() && now - lastMixtapeUpdatedAt < MIXTAPE_TTL_MS) {
            return lastMixtape
        }
        val subscriptionEntities = subscriptions()
        val subscriptions = subscriptionEntities.map { host.toAutoPodcast(it) }
        val history = listenerStore.projectRows(host.database.listeningHistoryDao().getRecentHistoryList(HISTORY_WINDOW))
        val ranking = MixtapeEngine.AdaptiveRanking(scorer = host.adaptiveCandidateScorer, surface = RankingSurface.ANDROID_AUTO)
        var result = MixtapeEngine.build(subscriptions = subscriptions, history = history, adaptiveRanking = ranking)
        if (result.episodes.size < MIN_MIXTAPE_EPISODES) {
            val recommendations =
                runCatching {
                    withTimeout(NETWORK_TIMEOUT_MS) {
                        host.smartQueueSources.getPersonalizedRecommendations(
                            history = host.smartQueueSources.getHistoryForRecommendations(25),
                            interests = host.smartQueueSources.getInterests(),
                            country = host.smartQueueSources.getRegion(),
                            subscribedPodcastIds = subscriptions.map { it.id },
                            subscribedGenres = subscriptionEntities.mapNotNull { it.genre }.distinct(),
                        )
                    }
                }.onFailure {
                    android.util.Log.w("AutoBrowse", "Mixtape fallback unavailable", it)
                }.getOrDefault(emptyList())
            result = MixtapeEngine.build(subscriptions = subscriptions, history = history, recommendations = recommendations, adaptiveRanking = ranking)
        }
        val downloaded = downloadedIds()
        val played = historyById()
        val items =
            result.podcasts
                .mapNotNull { podcast -> podcast.latestEpisode?.let { it to podcast } }
                // The same episode can reach the mix through two copies of a show; the listener must see it once.
                .distinctBy { (episode, podcast) -> podcast.title.trim().lowercase() to episode.title.trim().lowercase() }
                .map { (episode, podcast) ->
                    val history = played[episode.id]
                    AutoMediaItemFactory.fromEpisode(
                        episode = episode,
                        source = AutoBrowseContract.SOURCE_MIXTAPE,
                        artworkUri = rowArt(episode.imageUrl, episode.podcastImageUrl ?: podcast.imageUrl, podcast.id),
                        podcastTitle = podcast.title,
                        row = AutoEpisodeRow(isDownloaded = episode.id in downloaded, subtitle = episodeSubtitle(podcast.title, episode, history, withDate = false), progress = history?.progressFraction()),
                    )
                }
        lastMixtape = items
        lastMixtapeUpdatedAt = now
        return items
    }

    suspend fun getDownloadsChildren(): List<MediaItem> = getDownloadEpisodeItems()

    suspend fun getDownloadEpisodeItems(): List<MediaItem> = host.database.downloadedEpisodeDao().getCompletedDownloads(LIST_LIMIT).map { download ->
        val sourceUri =
            download.localFilePath
                .takeIf {
                    it.isNotBlank() && it != "CACHED" && java.io.File(it).exists()
                }?.let {
                    Uri
                        .fromFile(java.io.File(it))
                        .toString()
                }
                ?: mediaResolver.resolveDownloadRequestUri(download.episodeId)
                ?: listenerStore
                    .projectedHistory(download.episodeId)
                    ?.episodeAudioUrl
                ?: host.queueRepository.getQueueItemByEpisodeId(download.episodeId)?.audioUrl
        AutoMediaItemFactory.fromDownload(
            download = download,
            artworkUri = rowArt(download.episodeImageUrl, download.podcastImageUrl, download.podcastId),
            uri = sourceUri,
            groupTitle = null,
        )
    }

    suspend fun getDrivePicksChildren(): List<MediaItem> {
        val calendar = Calendar.getInstance()
        val driveVibes = AutoBrowseContract.driveVibes(calendar)
        val region = region()
        val driveFeeds = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { host.podcastRepository.getCuratedVibes(driveVibes, region) }.orEmpty()
        val fallbackFeeds =
            if (driveFeeds.values.all { it.isEmpty() }) {
                val fallbackIds = host.getTimeBasedGenres(calendar.get(Calendar.HOUR_OF_DAY)).map { it.first }
                withTimeoutOrNull(NETWORK_TIMEOUT_MS) { host.podcastRepository.getCuratedVibes(fallbackIds, region) }.orEmpty()
            } else {
                emptyMap()
            }
        val completedIds = listenerStore.projectCompletedIds(host.database.listeningHistoryDao().getCompletedEpisodeIds())
        val recentIds = host.database.listeningHistoryDao().getRecentHistoryList(30).mapTo(mutableSetOf()) { it.episodeId }
        val feedMap = if (driveFeeds.values.any { it.isNotEmpty() }) driveFeeds else fallbackFeeds
        val episodes =
            (driveVibes + feedMap.keys.sorted())
                .distinct()
                .flatMap { feedMap[it].orEmpty() }
                .distinctBy { it.id }
                .mapNotNull { podcast -> podcast.latestEpisode?.let { episode -> episode to podcast } }
                .filter { (episode, _) -> episode.id !in completedIds && episode.id !in recentIds && episode.isWorthADrive() }
                .take(NEW_EPISODES_LIMIT)
        if (episodes.isEmpty()) {
            // Offline or nothing new to offer: what the listener already has is better than an empty screen.
            return lastDrivePicks.ifEmpty {
                getDownloadEpisodeItems().take(NEW_EPISODES_LIMIT).ifEmpty { getQueueChildren().take(NEW_EPISODES_LIMIT) }
            }
        }
        val items =
            episodes.map { (episode, podcast) ->
                AutoMediaItemFactory.fromEpisode(
                    episode =
                    episode.copy(
                        podcastId = podcast.id,
                        podcastTitle = podcast.title,
                        podcastArtist = podcast.artist,
                        podcastImageUrl = podcast.imageUrl,
                    ),
                    source = AutoBrowseContract.SOURCE_DRIVE,
                    artworkUri = rowArt(episode.imageUrl, podcast.imageUrl, podcast.id),
                    podcastTitle = podcast.title,
                )
            }
        lastDrivePicks = items
        return items
    }

    // ── Explore ─────────────────────────────────────────────────────────────────────────────

    /** Explore: the curated moods for this time of day, then every genre, as one list. */
    fun getDiscoverChildren(): List<MediaItem> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val nowTitle = host.getString(
            when (hour) {
                in 5..11 -> R.string.auto_explore_morning
                in 12..16 -> R.string.auto_explore_afternoon
                in 17..22 -> R.string.auto_explore_evening
                else -> R.string.auto_explore_night
            },
        )
        val moods = host.getTimeBasedGenres(hour).map { (vibeId, title) ->
            category("${AutoBrowseContract.CURATED_PREFIX}$vibeId", title, R.drawable.ic_auto_explore, nowTitle)
        }
        return moods + genreTiles(host.getString(R.string.auto_group_genres))
    }

    fun getExplorePicksChildren(): List<MediaItem> = host.getTimeBasedGenres(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)).map { (vibeId, title) ->
        category("${AutoBrowseContract.CURATED_PREFIX}$vibeId", title, R.drawable.ic_auto_explore, null)
    }

    fun getGenresChildren(): List<MediaItem> = genreTiles(null)

    private fun genreTiles(groupTitle: String?): List<MediaItem> = GENRES.map { (category, title) ->
        category("${AutoBrowseContract.GENRE_PREFIX}$category", title, R.drawable.ic_auto_genre, groupTitle)
    }

    /**
     * A row that opens a grid of shows. Moods and genres have no artwork of their own, and as tiles
     * the car drew them as large identical icons with their names cut off, so they are rows: a small
     * icon and the whole name.
     */
    private fun category(id: String, title: String, @DrawableRes icon: Int, groupTitle: String?): MediaItem = AutoMediaItemFactory.browsable(
        id = id,
        title = title,
        artworkUri = iconUri(icon),
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
        childStyleExtras = AutoBrowseContract.mergeExtras(
            AutoBrowseContract.gridChildrenExtras(),
            groupTitle?.let { Bundle().apply { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) } },
        ),
        singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
    )

    suspend fun getCuratedChildren(vibeId: String): List<MediaItem> {
        legacyAutoGenreCategory(vibeId)?.let { return getGenreChildren(it) }
        val curatedPodcasts =
            host.podcastRepository.getCuratedPodcasts(
                vibeId,
                region(),
                languages = withTimeoutOrNull(PREFS_TIMEOUT_MS) { host.userPreferencesRepository.contentLanguagesStream.first() },
            )
        return buildPodcastFolderItems(curatedPodcasts)
    }

    suspend fun getGenreChildren(category: String): List<MediaItem> {
        val podcasts =
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                host.podcastRepository.getTrendingPodcasts(country = region(), limit = LIST_LIMIT, category = category.lowercase())
            }.orEmpty()
        return buildPodcastFolderItems(podcasts)
    }

    private fun buildPodcastFolderItems(podcasts: List<Podcast>): List<MediaItem> = podcasts.map { podcast ->
        showTile("${AutoBrowseContract.SUBSCRIPTION_PREFIX}${podcast.id}", podcast.title, null, podcast.imageUrl)
    }

    private fun legacyAutoGenreCategory(genreId: String): String? = when (genreId) {
        "true_crime" -> AutoBrowseContract.GENRE_TRUE_CRIME
        "comedy" -> "Comedy"
        "news" -> "News"
        "technology" -> "Technology"
        "science" -> "Science"
        "health" -> "Health"
        "business" -> "Business"
        "sports" -> "Sports"
        "history" -> "History"
        "society" -> "Society & Culture"
        "education" -> "Education"
        "arts" -> "Arts"
        "music" -> "Music"
        "fiction" -> "Fiction"
        "kids" -> "Kids & Family"
        "self_improvement" -> "Health"
        else -> null
    }

    // ── Nodes kept for cars that remembered the old layout ──────────────────────────────────

    /** The old Library node: shows, then Liked and Listening history. */
    suspend fun getLibraryChildren(): List<MediaItem> = getSubscriptionsChildren() +
        listOf(
            AutoMediaItemFactory.browsable(
                id = AutoBrowseContract.LIBRARY_LIKED_ID,
                title = host.getString(R.string.auto_liked),
                artworkUri = iconUri(R.drawable.ic_auto_like),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            ),
            AutoMediaItemFactory.browsable(
                id = AutoBrowseContract.LIBRARY_HISTORY_ID,
                title = host.getString(R.string.auto_listening_history),
                artworkUri = iconUri(R.drawable.ic_auto_tab_up_next),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                singleItemStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            ),
        )

    // ── Shared reads ────────────────────────────────────────────────────────────────────────

    private suspend fun resumeItems(): List<ListeningHistoryEntity> = runCatching {
        listenerStore.projectRows(host.database.listeningHistoryDao().getResumeItemsList()) { !it.isCompleted && it.progressMs > 0 }
    }.getOrDefault(emptyList())

    private suspend fun queueEpisodes(): List<Episode> = runCatching { host.queueRepository.getQueueEpisodeSnapshot() }.getOrNull().orEmpty()

    private suspend fun subscriptions(): List<PodcastEntity> = runCatching { host.database.podcastDao().getSubscribedPodcastsList() }.getOrDefault(emptyList())

    private suspend fun historyById(): Map<String, ListeningHistoryEntity> = runCatching {
        listenerStore.projectRowsByMember(host.database.listeningHistoryDao().getRecentHistoryList(HISTORY_WINDOW)).associateBy { it.episodeId }
    }.getOrDefault(emptyMap())

    /** IDs of the episodes saved on the phone, so every list can mark them as downloaded. */
    private suspend fun downloadedIds(): Set<String> = runCatching {
        host.database.downloadedEpisodeDao().getCompletedDownloads(DOWNLOAD_MARK_LIMIT).mapTo(HashSet()) { it.episodeId }
    }.getOrDefault(emptySet())

    private suspend fun region(): String = withTimeoutOrNull(PREFS_TIMEOUT_MS) { host.userPreferencesRepository.regionStream.first() } ?: "us"

    /** Drive picks are for listening on the road: no trailers, and no clips of a few minutes ("Coming Soon"). */
    private fun Episode.isWorthADrive(): Boolean = !isTrailer && audioUrl.isNotBlank() && (duration <= 0 || duration >= MIN_DRIVE_PICK_SECONDS)

    private fun ListeningHistoryEntity.progressFraction(): Double = if (durationMs > 0) progressMs.toDouble() / durationMs.toDouble() else 0.0

    /**
     * A row's second line: the show (when [showTitle] is given), the day it came out (when
     * [withDate]), then the time left for a started episode or its length otherwise.
     */
    private fun episodeSubtitle(showTitle: String?, episode: Episode, played: ListeningHistoryEntity?, withDate: Boolean): String {
        val durationMs = episode.duration.toLong() * 1_000L
        val started = played?.takeIf { !it.isCompleted && it.progressMs > 0 }
        val totalMs = started?.durationMs?.takeIf { it > 0 } ?: durationMs
        // With no known length there is no time left to state; the row then says nothing rather than "Almost done".
        val length = if (started != null && totalMs > 0) {
            AutoMediaItemFactory.formatTimeLeft(started.progressMs, totalMs)
        } else {
            AutoMediaItemFactory.formatDuration(durationMs)
        }
        return AutoMediaItemFactory.joinSubtitle(showTitle, dayLabel(episode.publishedDate).takeIf { withDate }, length)
    }

    private fun dayLabel(publishedSeconds: Long): String? = when (val days = AutoDayLabels.daysAgo(publishedSeconds, System.currentTimeMillis())) {
        null -> null
        0L -> host.getString(R.string.auto_today)
        1L -> host.getString(R.string.auto_yesterday)
        else -> AutoDayLabels.shortDate(publishedSeconds, sameYear = days < DAYS_IN_YEAR)
    }

    /**
     * Artwork for an episode row, so that no row is ever left blank: the episode's image, then the
     * show's cover (from the row, else looked up by [podcastId]), then a plain icon. Queue entries,
     * for one, often carry no image of their own.
     */
    private suspend fun rowArt(episodeImage: String?, showImage: String?, podcastId: String?): Uri {
        val cover = showImage?.takeIf { it.isNotBlank() } ?: showCover(podcastId)
        return episodeArt(episodeImage, cover) ?: iconUri(R.drawable.ic_auto_episode)
    }

    /** A queue entry's cover for the player: the episode's image, else its show's cover, looked up when the entry has none. */
    internal suspend fun queueArt(episode: Episode): Uri? = episodeArt(episode.imageUrl, episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: showCover(episode.podcastId))

    private suspend fun showCover(podcastId: String?): String? {
        val id = podcastId?.takeIf { it.isNotBlank() } ?: return null
        showCovers[id]?.let { return it.ifBlank { null } }
        val cover = runCatching { host.database.podcastDao().getPodcast(id)?.imageUrl }.getOrNull().orEmpty()
        showCovers[id] = cover
        return cover.ifBlank { null }
    }

    /** Artwork for a row or tile: [url], with [fallbackUrl] (the show's cover) standing in when it can't be loaded. */
    private fun episodeArt(url: String?, fallbackUrl: String? = null): Uri? = AutoArtworkRepository.remoteUri(host.asContext(), url, fallbackUrl)

    private fun folderArtwork(folderId: String): Uri? = host.autoCollageUris[folderId]
        ?: AutoArtworkRepository.collageUri(host.asContext(), folderId)

    private fun iconUri(@DrawableRes id: Int): Uri = host.drawableUri(id)

    fun slicePage(items: List<MediaItem>, page: Int, pageSize: Int,): List<MediaItem> {
        val safePageSize = pageSize.takeIf { it > 0 }?.coerceAtMost(LIST_LIMIT) ?: LIST_LIMIT
        val start = page.coerceAtLeast(0) * safePageSize
        if (start >= items.size) return emptyList()
        return items.subList(start, minOf(start + safePageSize, items.size))
    }

    /** A started episode's second line, e.g. "Show name · 35 min left". */
    fun buildProgressSubtitle(podcastName: String, progressMs: Long, durationMs: Long,): String {
        if (durationMs <= 0) return podcastName
        return AutoMediaItemFactory.joinSubtitle(podcastName, AutoMediaItemFactory.formatTimeLeft(progressMs, durationMs))
    }

    private companion object {
        const val LIST_LIMIT = 50
        const val NEW_EPISODES_LIMIT = 20
        const val HOME_CONTINUE_ROWS = 2
        const val MIN_DRIVE_PICK_SECONDS = 5 * 60
        const val HISTORY_WINDOW = 300
        const val DOWNLOAD_MARK_LIMIT = 500
        const val MIN_MIXTAPE_EPISODES = 3
        const val MIXTAPE_TTL_MS = 15 * 60_000L
        const val NETWORK_TIMEOUT_MS = 6_000L
        const val PREFS_TIMEOUT_MS = 1_000L
        const val DAYS_IN_YEAR = 365L

        /** Chart category to the short name shown on its tile. */
        val GENRES = listOf(
            "News" to "News",
            "Technology" to "Tech",
            "Business" to "Business",
            "Comedy" to "Comedy",
            AutoBrowseContract.GENRE_TRUE_CRIME to AutoBrowseContract.GENRE_TRUE_CRIME,
            "Sports" to "Sports",
            "Health" to "Health",
            "History" to "History",
            "Arts" to "Arts",
            "Society & Culture" to "Society",
            "Education" to "Education",
            "Science" to "Science",
            AutoBrowseContract.GENRE_TV_FILM to AutoBrowseContract.GENRE_TV_FILM,
            "Fiction" to "Fiction",
            "Music" to "Music",
            "Religion & Spirituality" to "Religion",
            "Kids & Family" to "Family",
            "Leisure" to "Leisure",
            "Government" to "Government",
        )
    }
}
