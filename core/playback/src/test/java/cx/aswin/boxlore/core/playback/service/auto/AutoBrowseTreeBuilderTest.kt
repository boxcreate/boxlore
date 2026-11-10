package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.R
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.QueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the car's tabs, Home blocks and lists hold, read from a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoBrowseTreeBuilderTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var host: AutoBrowseLibraryHost
    private lateinit var queueRepository: QueueRepository
    private lateinit var treeBuilder: AutoBrowseTreeBuilder
    private val nowSeconds = System.currentTimeMillis() / 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        // Strings read as empty unless a test names one; contents and order are what's checked.
        host = mock(AutoBrowseLibraryHost::class.java, Mockito.RETURNS_MOCKS)
        queueRepository = mock(QueueRepository::class.java)
        `when`(host.asContext()).thenReturn(context)
        `when`(host.database).thenReturn(database)
        `when`(host.queueRepository).thenReturn(queueRepository)
        `when`(host.autoCollageUris).thenReturn(emptyMap())
        // This module's tests run without app resources, so icons and counts come from stand-ins.
        `when`(host.drawableUri(org.mockito.ArgumentMatchers.anyInt())).thenAnswer { Uri.parse("android.resource://test/drawable/${it.arguments[0]}") }
        `when`(host.getQuantityString(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer { "${it.arguments[1]} of ${it.arguments[0]}" }
        runBlocking { `when`(queueRepository.getQueueEpisodeSnapshot()).thenReturn(emptyList()) }
        treeBuilder = AutoBrowseTreeBuilder(host, mock(AutoMediaResolver::class.java))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theCarOpensOnFourTabsWithOneColourIcons() {
        val tabs = treeBuilder.getRootChildren()

        assertEquals(
            listOf(
                AutoBrowseContract.HOME_ID,
                AutoBrowseContract.CONTINUE_ID,
                AutoBrowseContract.HOME_NEW_EPISODES_ID,
                AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID,
            ),
            tabs.map { it.mediaId },
        )
        // Tab icons are app drawables, never downloaded artwork or collages.
        assertTrue(tabs.all { it.mediaMetadata.artworkUri?.scheme == "android.resource" && it.mediaMetadata.isBrowsable == true })
        assertEquals("/drawable/${R.drawable.ic_auto_tab_home}", tabs.first().mediaMetadata.artworkUri?.path)
    }

    @Test
    fun aNewListenersHomeOffersOnlyWhatHasSomethingInside() = runBlocking {
        // No shows, plays, downloads or likes yet: every other block would open an empty screen.
        val home = treeBuilder.getHomeChildren()

        assertEquals(
            listOf(AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID, AutoBrowseContract.DISCOVER_ID).map { AutoBrowseContract.OPEN_PREFIX + it },
            home.map { it.mediaId },
        )
        assertTrue(home.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.artworkUri != null })
    }

    @Test
    fun homeLeadsWithTheEpisodesInProgressAsFullRowsThenTheBlocks() = runBlocking {
        `when`(host.getString(R.string.auto_group_continue)).thenReturn("Continue listening")
        `when`(host.getString(R.string.auto_group_browse)).thenReturn("Browse")
        database.podcastDao().upsertRow(show("1", latest = episode("e1", publishedSeconds = nowSeconds)))
        database.listeningHistoryDao().upsertAll(
            listOf(
                history("oldest", progressMs = 60_000L, playedAt = 10L),
                history("older", progressMs = 60_000L, playedAt = 15L),
                history("latest", progressMs = 600_000L, playedAt = 20L),
                history("finished", progressMs = 1_800_000L, playedAt = 30L, completed = true),
            ),
        )

        val home = treeBuilder.getHomeChildren()
        val groups = home.map { it.mediaMetadata.extras?.getString(androidx.media3.session.MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE) }

        // The two most recent, as playable rows carrying the whole title and the time left.
        assertEquals(listOf("episode:latest", "episode:older"), home.take(2).map { it.mediaId })
        assertEquals("Episode latest", home.first().mediaMetadata.title.toString())
        assertEquals("Show · 20 min left", home.first().mediaMetadata.subtitle.toString())
        assertEquals(listOf("Continue listening", "Continue listening"), groups.take(2))
        // Then the blocks, under their own title, with no queue among them.
        assertTrue(home.drop(2).all { it.mediaMetadata.isBrowsable == true })
        assertTrue(groups.drop(2).all { it == "Browse" })
        assertFalse(home.any { it.mediaId.endsWith(AutoBrowseContract.HOME_QUEUE_ID) })
    }

    @Test
    fun homeBlocksSayHowMuchIsInside() = runBlocking {
        database.podcastDao().upsertRow(show("1", latest = episode("e1", publishedSeconds = nowSeconds)))
        database.podcastDao().upsertRow(show("2", latest = null))

        val subtitles = treeBuilder.getHomeChildren().associate { it.mediaId to it.mediaMetadata.subtitle?.toString() }

        assertEquals("2 of ${R.plurals.auto_show_count}", subtitles[AutoBrowseContract.OPEN_PREFIX + AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID])
        assertEquals("1 of ${R.plurals.auto_new_count}", subtitles[AutoBrowseContract.OPEN_PREFIX + AutoBrowseContract.HOME_NEW_EPISODES_ID])
    }

    @Test
    fun theContinueTabListsEveryEpisodeInProgressAndNothingFromTheQueue() = runBlocking {
        database.listeningHistoryDao().upsertAll(
            listOf(history("a", progressMs = 60_000L, playedAt = 10L), history("b", progressMs = 60_000L, playedAt = 20L)),
        )
        `when`(queueRepository.getQueueEpisodeSnapshot()).thenReturn(listOf(episode("queued")))

        assertEquals(listOf("episode:b", "episode:a"), treeBuilder.getContinueListeningChildren().map { it.mediaId })
    }

    @Test
    fun aRowWithNoImageOfItsOwnShowsItsShowsCoverAndNeverNothing() = runBlocking {
        // Regression: queue entries carry no image, so those rows had no artwork while others did.
        database.podcastDao().upsertRow(show("100", latest = null))
        database.listeningHistoryDao().upsertAll(listOf(history("a", progressMs = 60_000L, playedAt = 10L)))
        `when`(queueRepository.getQueueEpisodeSnapshot()).thenReturn(listOf(episode("known").copy(podcastId = "100"), episode("orphan")))

        val covered = treeBuilder.getContinueListeningChildren().single().mediaMetadata.artworkUri
        val (known, orphan) = treeBuilder.getQueueChildren().map { it.mediaMetadata.artworkUri }

        assertEquals("content", covered?.scheme)
        assertEquals(covered, known)
        // No show to borrow a cover from: a plain icon stands in.
        assertEquals("android.resource", orphan?.scheme)
    }

    @Test
    fun newEpisodesListsUnplayedEpisodesNewestFirstWithNoPlayAllRow() = runBlocking {
        `when`(host.getString(R.string.auto_today)).thenReturn("Today")
        database.podcastDao().upsertRow(show("1", latest = episode("old", publishedSeconds = nowSeconds - 5 * 86_400L)))
        database.podcastDao().upsertRow(show("2", latest = episode("fresh", publishedSeconds = nowSeconds, durationSeconds = 1_680)))
        database.podcastDao().upsertRow(show("3", latest = episode("heard", publishedSeconds = nowSeconds)))
        database.listeningHistoryDao().upsertAll(listOf(history("heard", progressMs = 1_800_000L, playedAt = 5L, completed = true)))

        val rows = treeBuilder.getNewEpisodesChildren()

        assertEquals(listOf("episode:fresh", "episode:old"), rows.map { it.mediaId })
        assertTrue(rows.all { it.mediaMetadata.isPlayable == true })
        assertEquals("Show 2 · Today · 28 min", rows.first().mediaMetadata.subtitle.toString())
    }

    @Test
    fun aShowWithAFreshUnplayedEpisodeSaysSoOnItsTile() = runBlocking {
        `when`(host.getString(R.string.auto_new_today)).thenReturn("New today")
        database.podcastDao().upsertRow(show("1", latest = episode("fresh", publishedSeconds = nowSeconds)))
        database.podcastDao().upsertRow(show("2", latest = episode("old", publishedSeconds = nowSeconds - 30 * 86_400L)))

        val subtitles = treeBuilder.getSubscriptionsChildren().associate { it.mediaId to it.mediaMetadata.subtitle?.toString() }

        assertEquals("New today", subtitles["subscription:1"])
        // The publisher is never shown: a tile's two lines belong to the show's name.
        assertNull(subtitles["subscription:2"])
    }

    @Test
    fun aLongShowNameCarriesOnOntoTheTilesSecondLine() = runBlocking {
        `when`(host.getString(R.string.auto_new_today)).thenReturn("New today")
        database.podcastDao().upsertRow(
            show("1", latest = episode("fresh", publishedSeconds = nowSeconds)).copy(title = "The Rest Is History"),
        )

        val tile = treeBuilder.getSubscriptionsChildren().single()

        // The title stays whole: the car also uses it as the heading of the show's own screen.
        assertEquals("The Rest Is History", tile.mediaMetadata.title.toString())
        assertEquals("History", tile.mediaMetadata.subtitle.toString())
    }

    @Test
    fun listRowsCarryADisplayTitleSoTheCarShowsTheirSecondLine() = runBlocking {
        // Regression: without a display title Android Auto showed the show's name there, never "Today · 28 min".
        database.podcastDao().upsertRow(show("2", latest = episode("fresh", publishedSeconds = nowSeconds)))

        val row = treeBuilder.getNewEpisodesChildren().single()

        assertEquals(row.mediaMetadata.title.toString(), row.mediaMetadata.displayTitle.toString())
    }

    @Test
    fun drivePicksLeaveOutTrailersAndClipsOfAFewMinutes() = runBlocking {
        // Regression: the first Drive pick on a real car screen was a 2 minute "Coming Soon".
        val prefs = mock(cx.aswin.boxlore.core.prefs.UserPreferencesRepository::class.java)
        val repository = mock(cx.aswin.boxlore.core.catalog.PodcastRepository::class.java)
        `when`(prefs.regionStream).thenReturn(kotlinx.coroutines.flow.flowOf("us"))
        `when`(host.userPreferencesRepository).thenReturn(prefs)
        `when`(host.podcastRepository).thenReturn(repository)
        fun pick(id: String, episode: Episode) = cx.aswin.boxlore.core.model.Podcast(id = id, title = "Show $id", artist = "", imageUrl = "", latestEpisode = episode)
        val shows = listOf(
            pick("1", episode("clip", durationSeconds = 120)),
            pick("2", episode("trailer", durationSeconds = 600).copy(episodeType = "trailer")),
            pick("3", episode("full", durationSeconds = 2_400)),
            pick("4", episode("unknownLength", durationSeconds = 0)),
        )
        `when`(repository.getCuratedVibes(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(mapOf("any" to shows))

        val picks = treeBuilder.getDrivePicksChildren().map { it.mediaId }

        assertEquals(listOf("episode:full", "episode:unknownLength"), picks)
    }

    @Test
    fun exploreIsOneScreenOfMoodsAndEveryGenre() {
        `when`(host.getTimeBasedGenres(org.mockito.ArgumentMatchers.anyInt())).thenReturn(listOf("morning_news" to "What's happening"))

        val tiles = treeBuilder.getDiscoverChildren()

        assertEquals("discover_curated_morning_news", tiles.first().mediaId)
        assertEquals(19, tiles.count { it.mediaId.startsWith(AutoBrowseContract.GENRE_PREFIX) })
        assertFalse(tiles.any { it.mediaId == AutoBrowseContract.DISCOVER_GENRES_ID })
        assertTrue(tiles.all { it.mediaMetadata.artworkUri?.scheme == "android.resource" })
    }

    private fun episode(id: String, publishedSeconds: Long = 0L, durationSeconds: Int = 1_800) = Episode(
        id = id,
        title = "Episode $id",
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        podcastTitle = "Show",
        duration = durationSeconds,
        publishedDate = publishedSeconds,
    )

    private fun show(id: String, latest: Episode?) = PodcastEntity(
        podcastId = id,
        title = "Show $id",
        author = "Author $id",
        imageUrl = "https://example.com/$id.jpg",
        description = null,
        isSubscribed = true,
        latestEpisode = latest,
    )

    private fun history(id: String, progressMs: Long, playedAt: Long, completed: Boolean = false) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "100",
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = progressMs,
        durationMs = 1_800_000L,
        isCompleted = completed,
        lastPlayedAt = playedAt,
    )
}
