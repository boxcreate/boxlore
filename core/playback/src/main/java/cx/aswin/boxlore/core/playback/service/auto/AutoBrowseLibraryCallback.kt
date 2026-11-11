package cx.aswin.boxlore.core.playback.service.auto

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.playback.service.CarConnectionState
import cx.aswin.boxlore.core.playback.service.playbackButtonPreferences
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Suppress("LargeClass")
internal class AutoBrowseLibraryCallback(
    private val host: AutoBrowseLibraryHost,
    mediaResolver: AutoMediaResolver? = null,
    resumptionHandler: AutoPlaybackResumptionHandler? = null,
    treeBuilder: AutoBrowseTreeBuilder? = null,
) : MediaLibrarySession.Callback {
    private val mediaResolver = mediaResolver ?: AutoMediaResolver(host)
    private val treeBuilder = treeBuilder ?: AutoBrowseTreeBuilder(host, this.mediaResolver)
    private val voiceSearch = AutoVoiceSearchHandler(host, this.treeBuilder)
    private val resumptionHandler =
        resumptionHandler ?: AutoPlaybackResumptionHandler(host, this.mediaResolver)

    private val playContext = AutoPlayContextTracker()
    private val phoneChanges = AutoBrowseChangeNotifier(host)
    private val queueArtwork = AutoQueueArtworkSync(host)
    private val carConnection by lazy {
        cx.aswin.boxlore.core.playback.service.CarConnectionWatcher(host.asContext()) { host.serviceScope.launch { onCarConnectionChanged() } }
    }
    private val carBrowsers = java.util.concurrent.ConcurrentHashMap.newKeySet<MediaSession.ControllerInfo>()

    private val connectedAtMs =
        java.util.concurrent.ConcurrentHashMap<MediaSession.ControllerInfo, Long>()
    private val rootChildLimits =
        java.util.concurrent.ConcurrentHashMap<MediaSession.ControllerInfo, Int>()
    private val searchCache =
        object : LinkedHashMap<String, List<MediaItem>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MediaItem>>): Boolean = size > 8
        }

    private val rootId = AutoBrowseContract.ROOT_ID
    private val HOME_ID = AutoBrowseContract.HOME_ID
    private val LIBRARY_ID = AutoBrowseContract.LIBRARY_ID
    private val DOWNLOADS_ID = AutoBrowseContract.DOWNLOADS_ID
    private val DISCOVER_ID = AutoBrowseContract.DISCOVER_ID
    private val EXPLORE_ID = AutoBrowseContract.LEGACY_EXPLORE_ID
    private val SUBSCRIPTIONS_ID = "subscriptions"
    private val HOME_CONTINUE_LISTENING_ID = AutoBrowseContract.HOME_CONTINUE_ID
    private val HOME_SUBSCRIPTIONS_ID = "home_subscriptions"
    private val HOME_QUEUE_ID = AutoBrowseContract.HOME_QUEUE_ID
    private val HOME_NEW_EPISODES_ID = AutoBrowseContract.HOME_NEW_EPISODES_ID
    private val PLAY_ALL_NEW_EPISODES_ID = AutoBrowseContract.PLAY_ALL_NEW_ID
    private val SUBSCRIPTION_PREFIX = AutoBrowseContract.SUBSCRIPTION_PREFIX

    private val seekBackCommand = androidx.media3.session.SessionCommand("SEEK_BACK", Bundle.EMPTY)
    private val SEEK_FORWARD_CMD = androidx.media3.session.SessionCommand("SEEK_FORWARD", Bundle.EMPTY)
    private val MARK_COMPLETED_SKIP_CMD = androidx.media3.session.SessionCommand("MARK_COMPLETED_SKIP", Bundle.EMPTY)
    private val TOGGLE_LIKE_CMD =
        androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_TOGGLE_LIKE,
            Bundle.EMPTY,
        )
    private val addToQueueCommand =
        androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_ADD_TO_QUEUE,
            Bundle.EMPTY,
        )
    private val markCompleteCommand =
        androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_MARK_COMPLETE,
            Bundle.EMPTY,
        )
    private val skipNextCommand =
        androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_SKIP_NEXT,
            Bundle.EMPTY,
        )

    internal fun isAndroidAuto(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean =
        AutoBrowseControllerMatcher.isAndroidAuto(session, controller)

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        if (isAndroidAuto(session, controller)) {
            carBrowsers.add(controller)
            // Android Auto's app also connects without a car (to list media apps); only a real drive counts.
            carConnection.start()
            onceInCar(controller) { onCarConnected(session, controller) }
        }
        val defaultResult = super.onConnect(session, controller)
        val sessionCommands =
            defaultResult.availableSessionCommands
                .buildUpon()
                .add(seekBackCommand)
                .add(SEEK_FORWARD_CMD)
                .add(MARK_COMPLETED_SKIP_CMD)
                .add(TOGGLE_LIKE_CMD)
                .add(addToQueueCommand)
                .add(markCompleteCommand)
                .add(skipNextCommand)
                .build()
        return MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(defaultResult.availablePlayerCommands)
            .setMediaButtonPreferences(
                playbackButtonPreferences(
                    host.seekBackAction,
                    host.seekForwardAction,
                    host.markCompleteAction,
                    host.skipNextAction.takeIf { session.player.hasNextMediaItem() },
                ),
            )
            .build()
    }

    /**
     * Runs [action] once the phone is really in a car. Android Auto connects to the player a moment
     * before it reports that it is projecting to a screen, so the answer at connect time can be a
     * false "no"; this keeps asking for a short while, and stops when the controller goes away.
     */
    private fun onceInCar(controller: MediaSession.ControllerInfo, action: suspend () -> Unit) {
        host.serviceScope.launch {
            try {
                var waitedMs = 0L
                while (!CarConnectionState.isInCar(host.asContext())) {
                    if (waitedMs >= IN_CAR_WAIT_MS || controller !in carBrowsers) return@launch
                    kotlinx.coroutines.delay(IN_CAR_POLL_MS)
                    waitedMs += IN_CAR_POLL_MS
                }
                if (controller in carBrowsers) action()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("AutoBrowse", "Unable to act on the car connecting", error)
            }
        }
    }

    /**
     * Loads the last episode, paused, when a car connects and nothing is loaded.
     *
     * Until the player holds an item the car has nothing to show: its card reads "Tap to open" with
     * a disabled play button, and the last episode is only looked up once a play command arrives.
     * This runs the same lookup ([AutoPlaybackResumptionHandler.resolveResumption]) up front, so the
     * card shows the episode and one tap resumes it. Nothing starts playing.
     */
    private suspend fun preloadLastEpisode(session: MediaSession) {
        try {
            val player = session.player
            if (player.mediaItemCount > 0) return
            val last = resumptionHandler.resolveResumption(session)
            // Anything that started while the episode was being looked up keeps the player.
            if (player.mediaItemCount > 0 || last.mediaItems.isEmpty()) return
            player.setMediaItems(last.mediaItems, last.startIndex, last.startPositionMs)
            player.prepare()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Nothing to resume (a new listener, or everything finished): the card stays as it was.
            android.util.Log.d("AutoBrowse", "No episode to load for the car: ${error.message}")
        }
    }

    /**
     * Starts what only a drive needs, once for each Android Auto controller: the count of the
     * drive, live list updates, queue covers and the last episode on the car's screen.
     */
    private suspend fun onCarConnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (connectedAtMs.putIfAbsent(controller, System.currentTimeMillis()) != null) return
        AnalyticsHelper.trackAndroidAutoConnected(sessionId = controller.packageName)
        phoneChanges.start()
        queueArtwork.start(session.player)
        preloadLastEpisode(session)
    }

    /** Ends [controller]'s drive, if it was on one. */
    private fun onCarGone(controller: MediaSession.ControllerInfo) {
        val started = connectedAtMs.remove(controller) ?: return
        val durationSeconds = ((System.currentTimeMillis() - started) / 1000L).toInt().coerceAtLeast(0)
        AnalyticsHelper.trackAndroidAutoDisconnected(sessionId = controller.packageName, durationSeconds = durationSeconds)
        if (connectedAtMs.isEmpty()) {
            phoneChanges.stop()
            queueArtwork.stop()
        }
    }

    /**
     * Android Auto reported a change in its car connection. A controller that connected with no
     * car present starts its drive now; one whose car has gone ends it, though it stays connected.
     */
    private suspend fun onCarConnectionChanged() {
        val session = host.mediaSession ?: return
        val inCar = CarConnectionState.isInCar(host.asContext())
        carBrowsers.toList().forEach { controller ->
            if (inCar) onCarConnected(session, controller) else onCarGone(controller)
        }
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        onCarGone(controller)
        rootChildLimits.remove(controller)
        if (carBrowsers.remove(controller) && carBrowsers.isEmpty()) carConnection.stop()
        super.onDisconnected(session, controller)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: androidx.media3.session.SessionCommand,
        args: Bundle,
    ): ListenableFuture<androidx.media3.session.SessionResult> {
        if (
            customCommand.customAction == AutoBrowseContract.COMMAND_TOGGLE_LIKE ||
            customCommand.customAction == AutoBrowseContract.COMMAND_ADD_TO_QUEUE ||
            customCommand.customAction == AutoBrowseContract.COMMAND_MARK_COMPLETE
        ) {
            return host.serviceScope.future {
                val episodeId =
                    args
                        .getString(androidx.media3.session.MediaConstants.EXTRA_KEY_MEDIA_ID)
                        ?.stripEpisodePrefix()
                        ?: session.player.currentMediaItem
                            ?.mediaId
                            ?.stripEpisodePrefix()
                val handled =
                    if (episodeId != null) {
                        when (customCommand.customAction) {
                            AutoBrowseContract.COMMAND_TOGGLE_LIKE -> toggleEpisodeLike(episodeId)
                            AutoBrowseContract.COMMAND_ADD_TO_QUEUE ->
                                addEpisodeToQueue(episodeId, session.player)
                            AutoBrowseContract.COMMAND_MARK_COMPLETE -> markEpisodeComplete(episodeId)
                            else -> false
                        }
                    } else {
                        false
                    }
                androidx.media3.session.SessionResult(
                    if (!handled) {
                        androidx.media3.session.SessionResult.RESULT_ERROR_BAD_VALUE
                    } else {
                        androidx.media3.session.SessionResult.RESULT_SUCCESS
                    },
                )
            }
        }
        when (customCommand.customAction) {
            "SEEK_BACK" -> {
                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                    .setSeekSource("seek_backward")
                session.player.seekBack()
                android.util.Log.d("AutoBrowse", "Seek backward")
            }
            "SEEK_FORWARD" -> {
                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                    .setSeekSource("seek_forward")
                session.player.seekForward()
                android.util.Log.d("AutoBrowse", "Seek forward")
            }
            "MARK_COMPLETED_SKIP" -> {
                host.markCurrentEpisodeCompletedAndSkip(session)
            }
            // The session's player hands this to the service's skip (handleSkipNext), so the skip setting applies.
            AutoBrowseContract.COMMAND_SKIP_NEXT -> session.player.seekToNext()
            else -> return super.onCustomCommand(session, controller, customCommand, args)
        }
        return Futures.immediateFuture(
            androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS),
        )
    }

    /**
     * Flips the like of [episodeId]'s episode. An episode stored under one ID toggles its row as
     * before and records the decision in its group settings; an episode stored under several IDs
     * gets the projected target written to every trusted ID ([EpisodeListenerStore.setLiked]).
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun toggleEpisodeLike(episodeId: String): Boolean {
        val listenerStore = host.database.episodeListenerStore()
        val now = System.currentTimeMillis()
        if (listenerStore.group(episodeId).isShared) {
            val liked = !(listenerStore.projectedHistory(episodeId)?.isLiked ?: false)
            if (listenerStore.setLiked(episodeId, liked, now) == 0) {
                val created = getOrCreateHistoryItem(episodeId) ?: return false
                // The owner keeps its own show ([EpisodeListenerStore.ownerRow]).
                host.database.listeningHistoryDao().upsert(
                    listenerStore.ownerRow(created.copy(episodeId = listenerStore.historyId(episodeId), isLiked = liked, likedAt = now, isDirty = true), episodeId),
                )
            }
        } else {
            val existing = getOrCreateHistoryItem(episodeId) ?: return false
            host.database.listeningHistoryDao().upsert(
                existing.copy(
                    isLiked = !existing.isLiked,
                    isDirty = true,
                ),
            )
            listenerStore.recordLike(episodeId, !existing.isLiked, now)
        }
        notifyListsChanged(AutoBrowseContract.LIBRARY_LIKED_ID, AutoBrowseContract.HOME_ID)
        return true
    }

    /** Marks [episodeId]'s episode played on its owner's row, then for the episode's other IDs. */
    @androidx.annotation.VisibleForTesting
    internal suspend fun markEpisodeComplete(episodeId: String): Boolean {
        val listenerStore = host.database.episodeListenerStore()
        val historyId = listenerStore.historyId(episodeId)
        // A missing owner row is seeded from the played ID's metadata, never looked up by the owner ID.
        val existing =
            host.database.listeningHistoryDao().getHistoryItem(historyId)
                ?: getOrCreateHistoryItem(episodeId)?.let { listenerStore.ownerRow(it.copy(episodeId = historyId), episodeId) }
                ?: return false
        host.observeManualCompletion(episodeId)
        val dao = host.database.listeningHistoryDao()
        dao.insertIfAbsent(existing)
        val completedAt = System.currentTimeMillis()
        dao.completeFromPlayback(
            episodeId = historyId,
            durationMs = existing.durationMs,
            lastPlayedAt = completedAt,
            isManualCompletion = true,
        )
        listenerStore.recordCompletion(historyId, completed = true, nowMs = completedAt)
        notifyListsChanged(
            AutoBrowseContract.HOME_ID,
            AutoBrowseContract.CONTINUE_ID,
            AutoBrowseContract.HOME_NEW_EPISODES_ID,
            AutoBrowseContract.HOME_CONTINUE_ID,
            AutoBrowseContract.LIBRARY_HISTORY_ID,
        )
        host.requestAutoCollageRefresh(force = true)
        return true
    }

    /** Tells the car that [nodes] changed, under their own IDs and as lists opened from a Home block. */
    private fun notifyListsChanged(vararg nodes: String) {
        val session = host.mediaSession ?: return
        nodes.forEach { node ->
            session.notifyChildrenChanged(node, CHANGED_ITEM_COUNT, null)
            session.notifyChildrenChanged(AutoBrowseContract.OPEN_PREFIX + node, CHANGED_ITEM_COUNT, null)
        }
    }

    private suspend fun getOrCreateHistoryItem(episodeId: String): cx.aswin.boxlore.core.database.ListeningHistoryEntity? {
        host.database
            .listeningHistoryDao()
            .getHistoryItem(episodeId)
            ?.let { return it }
        val episode = mediaResolver.resolveDomainEpisode(episodeId) ?: return null
        return cx.aswin.boxlore.core.database.ListeningHistoryEntity(
            episodeId = episode.id,
            podcastId = episode.podcastId.orEmpty(),
            episodeTitle = episode.title,
            episodeImageUrl = episode.imageUrl,
            podcastImageUrl = episode.podcastImageUrl,
            episodeAudioUrl = episode.audioUrl,
            podcastName = episode.podcastTitle.orEmpty(),
            progressMs = 0L,
            durationMs = episode.duration.toLong() * 1_000L,
            isCompleted = false,
            lastPlayedAt = System.currentTimeMillis(),
            enclosureType = episode.enclosureType,
            episodeDescription = episode.description,
        )
    }

    private suspend fun addEpisodeToQueue(episodeId: String, player: Player,): Boolean {
        val existingQueue = host.queueRepository.getQueueEpisodeSnapshot()
        val queuedEpisode = existingQueue.firstOrNull { it.id == episodeId }
        // The queue holds 50 episodes on the phone; the car keeps to the same limit.
        if (queuedEpisode == null && existingQueue.size >= cx.aswin.boxlore.core.playback.PlaybackRepository.QUEUE_LIMIT) return false
        val episode = queuedEpisode ?: mediaResolver.resolveDomainEpisode(episodeId) ?: return false
        val playerHasEpisode =
            (0 until player.mediaItemCount).any { index ->
                player.getMediaItemAt(index).mediaId.stripEpisodePrefix() == episodeId
            }
        if (!playerHasEpisode) {
            player.addMediaItem(
                AutoMediaItemFactory.fromEpisode(
                    episode = episode,
                    source = AutoBrowseContract.SOURCE_QUEUE,
                    artworkUri =
                    AutoArtworkRepository.remoteUri(
                        host.asContext(),
                        episode.imageUrl ?: episode.podcastImageUrl,
                    ),
                    row = AutoEpisodeRow(mediaIdPrefix = AutoBrowseContract.QUEUE_PREFIX),
                ),
            )
        }
        if (queuedEpisode == null) host.queueRepository.replaceQueue(existingQueue + episode)
        notifyListsChanged(AutoBrowseContract.HOME_QUEUE_ID)
        host.requestAutoCollageRefresh(force = true)
        return true
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent,): Boolean {
        host.noteMediaButton(session, controllerInfo)
        val keyEvent =
            androidx.core.content.IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_KEY_EVENT,
                android.view.KeyEvent::class.java,
            )
        if (keyEvent != null && keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .setSeekSource("seek_forward")
                    session.player.seekForward()
                    android.util.Log.d("BoxLorePlaybackService", "onMediaButtonEvent: KEYCODE_MEDIA_NEXT intercepted, seeking forward")
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .setSeekSource("seek_backward")
                    session.player.seekBack()
                    android.util.Log.d("BoxLorePlaybackService", "onMediaButtonEvent: KEYCODE_MEDIA_PREVIOUS intercepted, seeking backward")
                    return true
                }
            }
        }
        return super.onMediaButtonEvent(session, controllerInfo, intent)
    }

    @Deprecated("Deprecated in Media3; delegates to 3-parameter overload.")
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        resumptionHandler.onPlaybackResumption(mediaSession, controller)

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isStartedFromMediaNotification: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        resumptionHandler.onPlaybackResumption(
            mediaSession,
            controller,
            isStartedFromMediaNotification,
        )

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        android.util.Log.d("AutoBrowse", "onGetLibraryRoot called")

        rootChildLimits[browser] = params
            ?.extras
            ?.getInt(androidx.media3.session.MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 4)
            ?.takeIf { it > 0 }
            ?.coerceAtMost(4)
            ?: 4
        // The car says how large it draws artwork; covers are cached at that size.
        params?.extras?.getInt(androidx.media3.session.MediaConstants.EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS, 0)?.let {
            cx.aswin.boxlore.core.playback.service.AutoArtworkScaler.noteHostHint(it)
        }
        val rootExtras = AutoBrowseContract.listChildrenExtras()

        val rootItem =
            MediaItem
                .Builder()
                .setMediaId(rootId)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(host.getString(cx.aswin.boxlore.core.catalog.R.string.auto_app_name))
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(rootExtras)
                        .build(),
                ).build()

        val resultParams = LibraryParams.Builder().setExtras(rootExtras).build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, resultParams))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        android.util.Log.d("AutoBrowse", "onGetChildren: parentId=$parentId, page=$page")
        if (isAndroidAuto(session, browser)) {
            AnalyticsHelper.trackAndroidAutoBrowse(node = parentId, action = "get_children")
        }

        return host.serviceScope.future {
            try {
                val node = AutoBrowseContract.targetNode(parentId)
                val items = childrenOf(node, browser)
                // Only what the car was shown counts: another controller's browse must not replace the car's list.
                if (page == 0 && isAndroidAuto(session, browser)) rememberForPlay(node, items)
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(treeBuilder.slicePage(items, page, pageSize)),
                    params,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AutoBrowse", "onGetChildren error for $parentId", e)
                LibraryResult.ofItemList(ImmutableList.of(), params)
            }
        }
    }

    /** The children of [node]. IDs the old layout used still answer, so a car that remembered one is never left empty. */
    private suspend fun childrenOf(node: String, browser: MediaSession.ControllerInfo): List<MediaItem> =
        currentLayoutChildren(node, browser) ?: legacyLayoutChildren(node) ?: prefixedChildren(node)

    private suspend fun currentLayoutChildren(node: String, browser: MediaSession.ControllerInfo): List<MediaItem>? = when (node) {
        rootId -> treeBuilder.getRootChildren().take(rootChildLimits[browser] ?: 4)
        HOME_ID -> treeBuilder.getHomeChildren()
        AutoBrowseContract.CONTINUE_ID -> treeBuilder.getContinueListeningChildren()
        HOME_NEW_EPISODES_ID -> treeBuilder.getNewEpisodesChildren()
        AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID -> treeBuilder.getSubscriptionsChildren()
        AutoBrowseContract.HOME_DRIVE_MIX_ID -> treeBuilder.getMixtapeChildren()
        DOWNLOADS_ID -> treeBuilder.getDownloadsChildren()
        AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID -> treeBuilder.getDrivePicksChildren()
        AutoBrowseContract.LIBRARY_LIKED_ID -> treeBuilder.getLikedChildren()
        AutoBrowseContract.LIBRARY_HISTORY_ID -> treeBuilder.getHistoryChildren()
        DISCOVER_ID -> treeBuilder.getDiscoverChildren()
        else -> null
    }

    private suspend fun legacyLayoutChildren(node: String): List<MediaItem>? = when (node) {
        HOME_SUBSCRIPTIONS_ID, SUBSCRIPTIONS_ID -> treeBuilder.getSubscriptionsChildren()
        HOME_CONTINUE_LISTENING_ID -> treeBuilder.getContinueListeningChildren()
        HOME_QUEUE_ID -> treeBuilder.getQueueChildren()
        LIBRARY_ID -> treeBuilder.getLibraryChildren()
        EXPLORE_ID -> treeBuilder.getDiscoverChildren()
        AutoBrowseContract.DISCOVER_TIME_PICKS_ID, "explore_picks" -> treeBuilder.getExplorePicksChildren()
        AutoBrowseContract.DISCOVER_GENRES_ID, "explore_genres" -> treeBuilder.getGenresChildren()
        else -> null
    }

    private suspend fun prefixedChildren(node: String): List<MediaItem> = when {
        node.startsWith(AutoBrowseContract.GENRE_PREFIX) -> treeBuilder.getGenreChildren(node.removePrefix(AutoBrowseContract.GENRE_PREFIX))
        node.startsWith("home_curated_") || node.startsWith("explore_curated_") || node.startsWith(AutoBrowseContract.CURATED_PREFIX) ->
            treeBuilder.getCuratedChildren(
                node.removePrefix("home_curated_").removePrefix("explore_curated_").removePrefix(AutoBrowseContract.CURATED_PREFIX),
            )
        node.startsWith(SUBSCRIPTION_PREFIX) -> treeBuilder.getPodcastEpisodes(node.removePrefix(SUBSCRIPTION_PREFIX))
        else -> emptyList()
    }

    /**
     * Keeps [items] as the last content sent for [node], with what a tap on one of its episodes
     * means: which list it is labelled with, and whether playback carries on down the list.
     */
    private fun rememberForPlay(node: String, items: List<MediaItem>) {
        val (source, continues) = PLAY_CONTEXT_BY_NODE[node]
            ?: (AutoBrowseContract.SOURCE_SHOW to false).takeIf { node.startsWith(SUBSCRIPTION_PREFIX) }
            ?: return
        playContext.record(node, source, continues, items)
    }

    /**
     * Accepts a subscription straight away. Media3's default first loads the folder through
     * [onGetItem], which for the car meant rebuilding Home, the old Library and Explore every time
     * it opened a list.
     */
    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        // The platform already loads the children for legacy browsers such as Android Auto.
        if (browser.controllerVersion != MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION) {
            session.notifyChildrenChanged(browser, parentId, Int.MAX_VALUE, params)
        }
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        android.util.Log.d("AutoBrowse", "onSearch: query='$query'")
        return host.serviceScope.future {
            val results = voiceSearch.buildSearchResults(query)
            synchronized(searchCache) { searchCache[query] = results }
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        android.util.Log.d("AutoBrowse", "onGetSearchResult: query='$query'")

        return host.serviceScope.future {
            try {
                val results =
                    synchronized(searchCache) { searchCache[query] }
                        ?: voiceSearch.buildSearchResults(query).also {
                            synchronized(searchCache) { searchCache[query] = it }
                        }
                if (page == 0 && isAndroidAuto(session, browser)) {
                    playContext.record(SEARCH_NODE, AutoBrowseContract.SOURCE_SEARCH, continues = false, items = results)
                }
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(treeBuilder.slicePage(results, page, pageSize)),
                    params,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AutoBrowse", "Search failed for '$query'", e)
                LibraryResult.ofItemList(ImmutableList.of(), params)
            }
        }
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        android.util.Log.d("AutoBrowse", "onGetItem: mediaId=$mediaId")
        return host.serviceScope.future {
            val item = resolveGetItem(mediaId)
            LibraryResult.ofItem(item, null)
        }
    }

    @androidx.annotation.VisibleForTesting
    internal suspend fun resolveGetItem(mediaId: String): MediaItem = when {
        isEpisodeOrLearnMediaId(mediaId) -> resolvePrefixedEpisode(mediaId)
        mediaId.startsWith(AutoBrowseContract.SUBSCRIPTION_PREFIX) -> resolveSubscriptionFolder(mediaId)
        else -> resolveStaticOrRawEpisode(mediaId)
    }

    private fun isEpisodeOrLearnMediaId(mediaId: String): Boolean =
        mediaId.startsWith(AutoBrowseContract.EPISODE_PREFIX) ||
            mediaId.startsWith(AutoBrowseContract.QUEUE_PREFIX) ||
            mediaId.startsWith(AutoBrowseContract.LEARN_PREFIX)

    private suspend fun resolvePrefixedEpisode(mediaId: String): MediaItem {
        val resolved = mediaResolver.resolveMediaItem(
            MediaItem.Builder().setMediaId(mediaId).build(),
        )
        return if (resolved.localConfiguration?.uri != null || !resolved.mediaMetadata.title.isNullOrBlank()) {
            resolved
        } else {
            fallbackAutoItem(mediaId)
        }
    }

    private suspend fun resolveSubscriptionFolder(mediaId: String): MediaItem {
        val podcastId = mediaId.removePrefix(AutoBrowseContract.SUBSCRIPTION_PREFIX)
        val podcast = host.database.podcastDao().getPodcast(podcastId)
        return if (podcast != null) {
            AutoMediaItemFactory.browsable(
                id = mediaId,
                title = podcast.title,
                subtitle = podcast.author,
                artworkUri = AutoArtworkRepository.remoteUri(host.asContext(), podcast.imageUrl),
                mediaType = MediaMetadata.MEDIA_TYPE_PODCAST,
                childStyleExtras = AutoBrowseContract.mergeExtras(
                    AutoBrowseContract.listChildrenExtras(),
                    Bundle().apply {
                        putString(
                            androidx.media3.session.MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                            host.getString(cx.aswin.boxlore.core.catalog.R.string.auto_group_subscriptions),
                        )
                    },
                ),
                singleItemStyle = androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            )
        } else {
            treeBuilder.getSubscriptionsChildren().firstOrNull { it.mediaId == mediaId }
                ?: fallbackAutoItem(mediaId)
        }
    }

    private suspend fun resolveStaticOrRawEpisode(mediaId: String): MediaItem {
        val staticNodes = treeBuilder.getRootChildren() +
            treeBuilder.getHomeChildren() +
            treeBuilder.getLibraryChildren() +
            treeBuilder.getDiscoverChildren()
        val staticNode = staticNodes.firstOrNull { it.mediaId == mediaId }
        if (staticNode != null) {
            return staticNode
        }
        val resolvedEpisode = mediaResolver.resolveMediaItem(
            MediaItem.Builder().setMediaId(mediaId).build(),
        )
        return if (resolvedEpisode.localConfiguration?.uri != null) {
            resolvedEpisode
        } else {
            fallbackAutoItem(mediaId)
        }
    }

    private fun fallbackAutoItem(mediaId: String): MediaItem =
        AutoMediaItemFactory.browsable(
            id = mediaId,
            title = host.getString(cx.aswin.boxlore.core.catalog.R.string.auto_app_name),
        )

    /**
     * Android Auto browse plays a single episode; this routes the follow-up queue
     * build through the same guarded SmartQueueEngine refill path as the transition
     * listener (shared host.isRefilling flag), so Auto no longer bypasses dedup/ranking
     * or persists rows without provenance.
     */
    private fun buildAndAppendQueueAsync(episodeId: String, mediaSession: MediaSession,) {
        host.serviceScope.launch {
            try {
                val player = mediaSession.player
                // Wait briefly for the selected episode to become the current item so
                // the engine refills relative to it (playback start is asynchronous).
                var attempts = 0
                while (attempts < 20) {
                    val currentId =
                        player.currentMediaItem
                            ?.mediaId
                            ?.removePrefix(
                                AutoBrowseContract.LEARN_PREFIX,
                            )?.removePrefix(AutoBrowseContract.EPISODE_PREFIX)
                            ?.removePrefix(AutoBrowseContract.QUEUE_PREFIX)
                    if (currentId == episodeId) break
                    kotlinx.coroutines.delay(250)
                    attempts++
                }
                if (host.isRefilling) {
                    android.util.Log.d("AutoBrowse", "Refill already in flight; skipping Auto queue build")
                    return@launch
                }
                host.isRefilling = true
                try {
                    host.refillQueue(player)
                } finally {
                    host.isRefilling = false
                }
            } catch (e: Exception) {
                android.util.Log.e("AutoBrowse", "Async queue build failed", e)
            }
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> {
        android.util.Log.d("AutoBrowse", "onAddMediaItems: ${mediaItems.size} items")
        // The app's own plays come through here too (Media3 sends setMediaItems this way).
        val fromCar = isAndroidAuto(mediaSession, controller)

        return host.serviceScope.future {
            val items = itemsToAdd(mediaSession, mediaItems, fromCar)
            if (!fromCar) return@future items
            // An item with no audio address would crash the player, so the car only ever gets ones that can play.
            val playable = items.filterTo(mutableListOf()) { it.localConfiguration != null }
            if (playable.isEmpty()) {
                // The request's own label goes with it, so a later phone play can't take it.
                discardCarPlayLabel()
                reportUnplayable(mediaSession, controller)
                // An empty answer would clear the player and stop what is playing; a failed one is ignored.
                throw UnplayableCarRequestException()
            }
            playable
        }
    }

    private fun reportUnplayable(mediaSession: MediaSession, controller: MediaSession.ControllerInfo) {
        runCatching {
            mediaSession.sendError(
                controller,
                androidx.media3.session.SessionError(
                    androidx.media3.session.SessionError.ERROR_IO,
                    host.getString(cx.aswin.boxlore.core.catalog.R.string.auto_cant_play),
                ),
            )
        }.onFailure { android.util.Log.w("AutoBrowse", "Unable to tell the car a request can't play", it) }
    }

    /** A car play request with nothing that can play; failing it leaves the current playback alone. */
    private class UnplayableCarRequestException : RuntimeException("Android Auto request has nothing playable")

    private suspend fun itemsToAdd(
        mediaSession: MediaSession,
        mediaItems: List<MediaItem>,
        fromCar: Boolean,
    ): MutableList<MediaItem> {
        if (mediaItems.isEmpty()) return mutableListOf()
        if (mediaItems.size > 1) {
            labelCarPlay(fromCar, "android_auto_play_all")
            return mediaItems.map { mediaResolver.resolveMediaItem(it) }.toMutableList()
        }

        val selectedItem = mediaItems.first()
        val searchQuery = selectedItem.requestMetadata.searchQuery

        if (!searchQuery.isNullOrBlank()) {
            android.util.Log.d("AutoBrowse", "Voice play request: '$searchQuery'")
            labelCarPlay(fromCar, "android_auto_voice")
            return voiceSearch.handleVoiceSearchQuery(searchQuery, fromCar)
        }

        playAllFor(selectedItem.mediaId, fromCar)?.let { return it }

        if (selectedItem.mediaId.startsWith(AutoBrowseContract.QUEUE_PREFIX)) {
            return voiceSearch.handlePlayFromQueue(selectedItem.mediaId.stripEpisodePrefix(), fromCar)
        }
        // The car sends the media ID alone, so the list it was tapped in comes from what was last sent there.
        // The phone's own plays come through here too and must never be matched to a car list.
        val tapped = if (fromCar) playContext.find(selectedItem.mediaId.stripEpisodePrefix()) else null
        restOfList(tapped, fromCar)?.let { return it }
        val source = tapped?.source ?: sourceInExtras(selectedItem)
        if (source == AutoBrowseContract.SOURCE_MIXTAPE) {
            return voiceSearch.handlePlayFromMixtape(selectedItem.mediaId.stripEpisodePrefix(), fromCar)
        }
        resumedWithQueue(selectedItem, source, fromCar)?.let { return it }

        return handleSingleMediaItemSelection(mediaSession, selectedItem, source, fromCar)
    }

    /** The tapped episode and the rest of its list, for the lists that play on; null for any other tap. */
    private suspend fun restOfList(tapped: AutoPlayContextTracker.Match?, fromCar: Boolean): MutableList<MediaItem>? {
        if (tapped == null || !tapped.continues || tapped.itemsFromHere.size <= 1) return null
        labelCarPlay(fromCar, carEntryPoint(tapped.source))
        // Resolved like any other play, so a row downloaded under another of its IDs plays the saved file.
        return tapped.itemsFromHere.mapTo(mutableListOf()) { mediaResolver.resolveMediaItem(it).forPlayback() }
    }

    /** The source a play request carries itself, for the phone's own controller; the car sends none. */
    private fun sourceInExtras(item: MediaItem): String =
        item.mediaMetadata.extras?.getString(AutoBrowseContract.EXTRA_SOURCE).orEmpty().ifEmpty { AutoBrowseContract.SOURCE_DISCOVER }

    /**
     * An episode resumed from Continue listening, followed by the saved queue. The car has no queue
     * screen of its own, so the queue has to be in the player. Null when this is not such a tap, or
     * nothing is queued (the episode then plays alone and is refilled as usual).
     */
    private suspend fun resumedWithQueue(selectedItem: MediaItem, source: String, fromCar: Boolean): MutableList<MediaItem>? {
        if (!fromCar || source != AutoBrowseContract.SOURCE_CONTINUE) return null
        val queued = voiceSearch.queueAfter(selectedItem.mediaId.stripEpisodePrefix())
        if (queued.isEmpty()) return null
        labelCarPlay(fromCar, carEntryPoint(source))
        return (listOf(mediaResolver.resolveMediaItem(selectedItem)) + queued).toMutableList()
    }

    /** "Play all" rows left the lists, but a car that still shows one must find it works. */
    private suspend fun playAllFor(mediaId: String, fromCar: Boolean): MutableList<MediaItem>? = when (mediaId) {
        PLAY_ALL_NEW_EPISODES_ID -> voiceSearch.handlePlayAllNewEpisodes(fromCar)
        AutoBrowseContract.PLAY_ALL_LIKED_ID -> voiceSearch.handlePlayAllLiked(fromCar)
        AutoBrowseContract.PLAY_ALL_DOWNLOADS_ID -> voiceSearch.handlePlayAllDownloads(fromCar)
        AutoBrowseContract.PLAY_ALL_DRIVE_ID -> voiceSearch.handlePlayAllDrivePicks(fromCar)
        AutoBrowseContract.PLAY_ALL_MIXTAPE_ID -> voiceSearch.handlePlayAllMixtape(fromCar)
        else -> null
    }

    private suspend fun handleSingleMediaItemSelection(
        mediaSession: MediaSession,
        selectedItem: MediaItem,
        source: String,
        fromCar: Boolean,
    ): MutableList<MediaItem> {
        android.util.Log.d(
            "BoxCastPlayer",
            "onAddMediaItems: selectedItem.mediaId=${selectedItem.mediaId}, " +
                "extrasKeys=${selectedItem.mediaMetadata.extras?.keySet()?.joinToString(", ")}",
        )
        labelCarPlay(fromCar, carEntryPoint(source))
        val resolvedItem = mediaResolver.resolveMediaItem(selectedItem)
        val episodeId = selectedItem.mediaId.stripEpisodePrefix()
        android.util.Log.d(
            "AutoBrowse",
            "Returning episode instantly: $episodeId, startsWithLearn=${selectedItem.mediaId.startsWith(AutoBrowseContract.LEARN_PREFIX)}",
        )

        val skipSmartRefill =
            selectedItem.mediaId.startsWith(AutoBrowseContract.LEARN_PREFIX) ||
                source == AutoBrowseContract.SOURCE_DOWNLOADS ||
                source == AutoBrowseContract.SOURCE_QUEUE
        android.util.Log.d("AutoBrowse", "onAddMediaItems skipSmartRefill=$skipSmartRefill")
        if (!skipSmartRefill) {
            buildAndAppendQueueAsync(episodeId, mediaSession)
        } else {
            android.util.Log.d("AutoBrowse", "Explicit/offline source: skipping async queue append")
        }
        return mutableListOf(resolvedItem)
    }

    private companion object {
        /** Media3 wants an item count with a change notice; the car reloads the list whatever the number. */
        const val CHANGED_ITEM_COUNT = 50
        const val SEARCH_NODE = "search"

        /** How long to keep asking whether a connected Android Auto is in a car, and how often. */
        const val IN_CAR_WAIT_MS = 15_000L
        const val IN_CAR_POLL_MS = 1_000L

        /** What a tap means in each list: the source it is labelled with, and whether playback carries on down the list. */
        val PLAY_CONTEXT_BY_NODE: Map<String, Pair<String, Boolean>> = mapOf(
            AutoBrowseContract.HOME_NEW_EPISODES_ID to (AutoBrowseContract.SOURCE_NEW to true),
            AutoBrowseContract.HOME_DRIVE_MIX_ID to (AutoBrowseContract.SOURCE_MIXTAPE to true),
            AutoBrowseContract.DOWNLOADS_ID to (AutoBrowseContract.SOURCE_DOWNLOADS to true),
            AutoBrowseContract.LIBRARY_LIKED_ID to (AutoBrowseContract.SOURCE_LIKED to true),
            AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID to (AutoBrowseContract.SOURCE_DRIVE to true),
            AutoBrowseContract.LIBRARY_HISTORY_ID to (AutoBrowseContract.SOURCE_HISTORY to false),
            AutoBrowseContract.HOME_ID to (AutoBrowseContract.SOURCE_CONTINUE to false),
            AutoBrowseContract.CONTINUE_ID to (AutoBrowseContract.SOURCE_CONTINUE to false),
            AutoBrowseContract.HOME_CONTINUE_ID to (AutoBrowseContract.SOURCE_CONTINUE to false),
        )

        /** The analytics entry point for a play tapped in a list labelled [source]. */
        fun carEntryPoint(source: String): String = "android_auto_$source"
    }
}
