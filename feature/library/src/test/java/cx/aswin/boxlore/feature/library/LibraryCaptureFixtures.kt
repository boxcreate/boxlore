package cx.aswin.boxlore.feature.library

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.library.playlists.PlaylistCreateRequest
import cx.aswin.boxlore.feature.library.playlists.PlaylistDetailActions
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizeLook
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuTarget
import cx.aswin.boxlore.feature.library.subscriptions.FolderInterSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderIntraSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderMembershipChange
import cx.aswin.boxlore.feature.library.subscriptions.FolderPagesActions
import cx.aswin.boxlore.feature.library.subscriptions.FolderPagesData
import cx.aswin.boxlore.feature.library.subscriptions.FolderShowMove
import cx.aswin.boxlore.feature.library.subscriptions.ReorderMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * One listener's Library for audit captures and layout tests: forty followed shows (a long title,
 * genres, latest episodes), folders of every kind (shelves with `+2` and `+7` overflows, 1-3
 * show folders, the German name `Nachrichten-Ecke`), likes, downloads, history and playlists.
 */
internal object LibraryCaptureFixtures {
    const val ART = PhoneGoldenRule.ARTWORK_URL
    const val LONG_TITLE = "How we find our way through the universe and back again"
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    private val now = System.currentTimeMillis()

    private val genres = listOf("True Crime", "Tech", "News", "Comedy", "History", "Science", "Fiction", "Sports")

    /** Show [index]: the first has the long title; each has a latest episode, the first few published within the hour. */
    fun show(index: Int): Podcast {
        val id = "show-$index"
        val title = if (index == 0) LONG_TITLE else "Show number ${index + 1}"
        val episode = TestFixtures.episode(
            id = "ep-$index",
            title = if (index % 5 == 1) "A two line episode title about the long road home and what we learned on the way" else "Episode ${40 - index}: the week in review",
            podcastId = id,
            podcastTitle = title,
            publishedDate = (now - (index + 1) * HOUR_MS) / 1_000L,
            imageUrl = ART,
        )
        return TestFixtures.podcast(id = id, title = title, artist = "Host ${index + 1}", imageUrl = ART, subscribedAt = now - 30 * DAY_MS, genre = genres[index % genres.size])
            .copy(latestEpisode = episode)
    }

    val podcasts: List<Podcast> = List(40) { show(it) }

    private fun ids(vararg index: Int) = index.map { "show-$it" }

    val folders: List<SubscriptionFolder> = listOf(
        SubscriptionFolder("crime", "True Crime", icon = "true_crime", displaySize = FolderDisplaySize.SHELF, linkedGenre = "True Crime", podcastCount = 4, podcastIds = ids(0, 8, 16, 24)),
        SubscriptionFolder("tech", "Tech", icon = "tech", displaySize = FolderDisplaySize.SHELF, linkedGenre = "Tech", podcastCount = 9, podcastIds = ids(1, 9, 17, 25, 33, 2, 10, 18, 26)),
        SubscriptionFolder("news", "Nachrichten-Ecke", icon = "news", podcastCount = 3, podcastIds = ids(3, 11, 19)),
        SubscriptionFolder("commute", "Commute", podcastCount = 2, showPodcastGrid = true, podcastIds = ids(4, 12)),
        SubscriptionFolder("solo", "Solo", icon = "comedy", podcastCount = 1, podcastIds = ids(5)),
    )

    fun liked(count: Int = 12): List<ListeningHistoryEntity> = List(count) { index ->
        ListeningHistoryEntity(
            episodeId = "liked-$index",
            podcastId = "show-$index",
            episodeTitle = if (index == 1) "$LONG_TITLE, part two of a much longer story" else "How we find our way through the universe $index",
            episodeImageUrl = ART,
            podcastImageUrl = ART,
            episodeAudioUrl = "https://audio.example.invalid/episode.mp3",
            podcastName = "Curious world",
            progressMs = 120_000L * index,
            durationMs = 3_600_000L,
            isCompleted = false,
            isLiked = true,
            likedAt = now - index * HOUR_MS,
            lastPlayedAt = now - index * HOUR_MS,
        )
    }

    fun downloads(): List<DownloadedEpisodeEntity> = List(12) { index ->
        val show = index % 4
        DownloadedEpisodeEntity(
            episodeId = "dl-$index",
            podcastId = "show-$show",
            episodeTitle = if (index == 0) LONG_TITLE else "How we find our way through the universe $index",
            episodeDescription = "Episode description",
            episodeImageUrl = ART,
            podcastName = if (show == 0) LONG_TITLE else "Downloaded show ${show + 1}",
            podcastImageUrl = ART,
            durationMs = 3_600_000L,
            publishedDate = 1_760_000_000L,
            localFilePath = "/tmp/fixture-$index.mp3",
            downloadId = index.toLong(),
            downloadedAt = now - index * HOUR_MS,
            sizeBytes = 20_000_000L,
            status = DownloadedEpisodeEntity.STATUS_COMPLETED,
        )
    }

    fun loaded(empty: Boolean = false, subscriptions: List<Podcast> = podcasts): LibraryUiState.Success =
        if (empty) {
            LibraryUiState.Success(subscribedPodcasts = subscriptions.take(3))
        } else {
            LibraryUiState.Success(
                subscribedPodcasts = subscriptions,
                likedEpisodes = liked(),
                downloadedEpisodes = downloads(),
                recentHistory = liked(4),
                smartOrderIds = subscriptions.map { it.id },
            )
        }

    /** A Library view model that serves [state]; every flow a Library screen reads is stubbed. */
    fun viewModel(
        state: LibraryUiState,
        tabStyle: String = SubscriptionsTabStyle.TOP,
        gridView: Boolean = true,
        folders: List<SubscriptionFolder> = LibraryCaptureFixtures.folders,
    ): LibraryViewModel {
        val vm = mock(LibraryViewModel::class.java)
        `when`(vm.uiState).thenReturn(MutableStateFlow(state))
        `when`(vm.lastSeenEpisodes).thenReturn(MutableStateFlow(emptyMap()))
        `when`(vm.homeShortcutsInLibrary).thenReturn(MutableStateFlow(false))
        `when`(vm.subscriptionsGridView).thenReturn(MutableStateFlow(gridView))
        `when`(vm.isRefreshingNewEpisodes).thenReturn(MutableStateFlow(false))
        `when`(vm.pendingPlayedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        // Newest first: smart order scores through a suspend call the mock can't answer.
        `when`(vm.useSmartRank).thenReturn(MutableStateFlow(false))
        `when`(vm.hideCompletedInSubs).thenReturn(MutableStateFlow(false))
        `when`(vm.pinnedPodcastIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(vm.autoOrganizeFolders).thenReturn(MutableStateFlow(true))
        `when`(vm.autoOrganizeLook).thenReturn(MutableStateFlow(AutoOrganizeLook()))
        `when`(vm.folderSort).thenReturn(MutableStateFlow(FolderInterSort.Inherit))
        `when`(vm.folderManualOrder).thenReturn(MutableStateFlow(emptyList()))
        `when`(vm.intraFolderSort).thenReturn(MutableStateFlow(FolderIntraSort.Inherit))
        `when`(vm.folders).thenReturn(MutableStateFlow(folders))
        `when`(vm.foldersLoaded).thenReturn(MutableStateFlow(true))
        `when`(vm.loadedFolders).thenReturn(MutableStateFlow(folders))
        `when`(vm.subscriptionsTabStyle).thenReturn(MutableStateFlow(tabStyle))
        `when`(vm.downloadsSortOrder).thenReturn(MutableStateFlow(DownloadsSortOrder.RECENT))
        `when`(vm.showSortOrder).thenReturn(MutableStateFlow(ShowSortOrder.NEWEST))
        return vm
    }

    /** Folder pages' data for this listener: smart order, auto-organize on with the default look. */
    val folderPagesData = FolderPagesData(
        podcasts = podcasts,
        folders = folders,
        sort = SubscriptionSort.SmartRank,
        smartOrderIds = podcasts.map { it.id },
        intraFolderSort = FolderIntraSort.Inherit,
        autoOrganizeOn = true,
        autoOrganizeLook = AutoOrganizeLook(),
        reorderMode = ReorderMode.Inactive,
    )

    /** Folder page actions that do nothing. */
    val noFolderActions = object : FolderPagesActions {
        override fun openShow(podcastId: String) = Unit
        override fun showMenu(target: ContextMenuTarget) = Unit
        override fun addShows(folder: cx.aswin.boxlore.core.model.SubscriptionFolder) = Unit
        override fun openSort() = Unit
        override fun setReorderMode(mode: ReorderMode) = Unit
        override fun saveFolderOrder(folderId: String, orderedIds: List<String>) = Unit
        override fun deleteFolder(folder: cx.aswin.boxlore.core.model.SubscriptionFolder) = Unit
        override fun createFolder(params: FolderSaveParams, podcastIds: List<String>, move: FolderShowMove?) = Unit
        override fun updateFolder(folder: cx.aswin.boxlore.core.model.SubscriptionFolder) = Unit
        override fun changeMembership(podcastId: String, change: FolderMembershipChange) = Unit
        override fun turnOnAutoOrganize(look: AutoOrganizeLook) = Unit
        override fun turnOffAutoOrganize() = Unit
        override fun setAutoOrganizeLook(look: AutoOrganizeLook) = Unit
        override fun applyLookToGenreFolders(look: AutoOrganizeLook) = Unit
    }

    /** Playlist page actions that do nothing. */
    val noPlaylistActions = object : PlaylistDetailActions {
        override fun open(playlistId: String) = Unit
        override fun create(request: PlaylistCreateRequest) = Unit
        override fun rename(playlistId: String, name: String) = Unit
        override fun delete(playlistId: String) = Unit
        override fun play() = Unit
        override fun setPlayback(mix: PlaylistMix, perShowLimit: Int?) = Unit
        override fun addShows(podcastIds: List<String>) = Unit
        override fun removeShow(show: PlaylistShow) = Unit
    }

    fun smartDownloadPrefs(enabled: Boolean = true): UserPreferencesRepository {
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.smartDownloadsEnabledStream).thenReturn(flowOf(enabled))
        `when`(prefs.smartDownloadsMaxEpisodesStream).thenReturn(flowOf(10))
        `when`(prefs.smartDownloadsStorageBudgetStream).thenReturn(flowOf(250L))
        `when`(prefs.smartDownloadsLastSyncTimeStream).thenReturn(flowOf(0L))
        return prefs
    }

    val playlists: List<Playlist> = listOf(
        Playlist("p1", "Morning mix", 1L, 1L, showCount = 6, coverUrls = List(4) { ART }),
        Playlist("p2", "A very long playlist name for the drive to the coast", 1L, 1L, showCount = 3, mix = PlaylistMix.NEWEST_FIRST, coverUrls = List(3) { ART }),
        Playlist("p3", "Comedy", 1L, 1L, showCount = 2, iconKey = "comedy"),
        Playlist("p4", "Deep dives", 1L, 1L, showCount = 4, perShowLimit = null, coverUrls = List(2) { ART }),
        Playlist("p5", "Weekend", 1L, 1L, showCount = 5, coverUrls = List(4) { ART }),
    )

    val playlistShows: List<PlaylistShow> = List(6) { index ->
        PlaylistShow("p1", "show-$index", index, 0L, podcasts[index].title, ART, isFollowed = index != 5)
    }

    val comingUp: List<Episode> = List(10) { index ->
        TestFixtures.episode(id = "up-$index", title = "Coming up episode ${index + 1}", podcastId = "show-$index", podcastTitle = podcasts[index].title, imageUrl = ART)
    }
}
