package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("UNCHECKED_CAST")
private fun <T> mockAny(): T {
    Mockito.any<T>()
    val dummy: Any? = null
    return dummy as T
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoBrowseLibraryCallbackTest {

    private lateinit var context: Context
    private lateinit var host: AutoBrowseLibraryHost
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var mediaResolver: AutoMediaResolver
    private lateinit var treeBuilder: AutoBrowseTreeBuilder
    private lateinit var resumptionHandler: AutoPlaybackResumptionHandler
    private lateinit var callback: AutoBrowseLibraryCallback
    private val recordedEvents = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restoreAnalytics: () -> Unit

    @Before
    fun setUp() = runBlocking {
        recordedEvents.clear()
        restoreAnalytics = AnalyticsHelper.installRecordingSink(recordedEvents)
        context = ApplicationProvider.getApplicationContext()
        host = mock(AutoBrowseLibraryHost::class.java)
        database = mock(BoxLoreDatabase::class.java)
        podcastDao = mock(PodcastDao::class.java)
        mediaResolver = mock(AutoMediaResolver::class.java)
        treeBuilder = mock(AutoBrowseTreeBuilder::class.java)
        resumptionHandler = mock(AutoPlaybackResumptionHandler::class.java)

        `when`(host.asContext()).thenReturn(context)
        `when`(host.serviceScope).thenReturn(CoroutineScope(Dispatchers.Unconfined))
        `when`(host.database).thenReturn(database)
        `when`(database.podcastDao()).thenReturn(podcastDao)
        `when`(host.getString(cx.aswin.boxlore.core.catalog.R.string.auto_app_name)).thenReturn("boxlore")
        `when`(host.getString(cx.aswin.boxlore.core.catalog.R.string.auto_group_subscriptions)).thenReturn("Subscriptions")
        val commandButton = mock(androidx.media3.session.CommandButton::class.java)
        `when`(host.seekBackAction).thenReturn(commandButton)
        `when`(host.seekForwardAction).thenReturn(commandButton)
        `when`(host.markCompleteAction).thenReturn(commandButton)
        `when`(host.skipNextAction).thenReturn(commandButton)

        `when`(treeBuilder.getRootChildren()).thenReturn(emptyList())
        `when`(treeBuilder.getHomeChildren()).thenReturn(emptyList())
        `when`(treeBuilder.getLibraryChildren()).thenReturn(emptyList())
        `when`(treeBuilder.getDiscoverChildren()).thenReturn(emptyList())

        callback = AutoBrowseLibraryCallback(
            host = host,
            mediaResolver = mediaResolver,
            resumptionHandler = resumptionHandler,
            treeBuilder = treeBuilder,
        )
    }

    @After
    fun tearDown() {
        restoreAnalytics()
    }

    @Test
    fun `resolveGetItem resolves known subscription to browsable podcast folder`() = runBlocking {
        val podcast = PodcastEntity(
            podcastId = "pod-123",
            title = "Hardcore History",
            author = "Dan Carlin",
            imageUrl = "https://example.com/art.jpg",
            description = "History podcast",
            isSubscribed = true,
        )
        `when`(podcastDao.getPodcast("pod-123")).thenReturn(podcast)

        val item = callback.resolveGetItem("subscription:pod-123")

        assertEquals("subscription:pod-123", item.mediaId)
        assertEquals("Hardcore History", item.mediaMetadata.title?.toString())
        assertEquals("Dan Carlin", item.mediaMetadata.subtitle?.toString())
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST, item.mediaMetadata.mediaType)
        assertTrue(item.mediaMetadata.isBrowsable == true)
    }

    @Test
    fun `resolveGetItem falls back to subscription children for unknown subscription in tree`() = runBlocking {
        `when`(podcastDao.getPodcast("pod-fallback")).thenReturn(null)
        val treeItem = AutoMediaItemFactory.browsable(
            id = "subscription:pod-fallback",
            title = "Tree Show",
            subtitle = "Tree Host",
        )
        `when`(treeBuilder.getSubscriptionsChildren()).thenReturn(listOf(treeItem))

        val item = callback.resolveGetItem("subscription:pod-fallback")

        assertEquals("subscription:pod-fallback", item.mediaId)
        assertEquals("Tree Show", item.mediaMetadata.title?.toString())
        assertEquals("Tree Host", item.mediaMetadata.subtitle?.toString())
    }

    @Test
    fun `resolveGetItem returns fallbackAutoItem for unknown subscription not in tree`() = runBlocking {
        `when`(podcastDao.getPodcast("pod-missing")).thenReturn(null)
        `when`(treeBuilder.getSubscriptionsChildren()).thenReturn(emptyList())

        val item = callback.resolveGetItem("subscription:pod-missing")

        assertEquals("subscription:pod-missing", item.mediaId)
        assertEquals("boxlore", item.mediaMetadata.title?.toString())
        assertTrue(item.mediaMetadata.isBrowsable == true)
    }

    @Test
    fun `resolveGetItem returns fallbackAutoItem for unresolvable raw episode`() = runBlocking {
        val unresolvableMediaItem = MediaItem.Builder()
            .setMediaId("raw-missing-ep")
            .build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(unresolvableMediaItem)

        val item = callback.resolveGetItem("raw-missing-ep")

        assertEquals("raw-missing-ep", item.mediaId)
        assertEquals("boxlore", item.mediaMetadata.title?.toString())
    }

    @Test
    fun `resolveGetItem returns resolved item for resolvable raw episode with URI`() = runBlocking {
        val resolvedMediaItem = MediaItem.Builder()
            .setMediaId("raw-playable-ep")
            .setUri(Uri.parse("https://example.com/audio.mp3"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Episode Title")
                    .build(),
            )
            .build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(resolvedMediaItem)

        val item = callback.resolveGetItem("raw-playable-ep")

        assertEquals("raw-playable-ep", item.mediaId)
        assertEquals(Uri.parse("https://example.com/audio.mp3"), item.localConfiguration?.uri)
        assertEquals("Episode Title", item.mediaMetadata.title?.toString())
    }

    @Test
    fun `resolveGetItem returns static tree item if mediaId matches static node`() = runBlocking {
        val staticHomeItem = AutoMediaItemFactory.browsable(
            id = AutoBrowseContract.HOME_ID,
            title = "Home",
        )
        `when`(treeBuilder.getRootChildren()).thenReturn(listOf(staticHomeItem))

        val item = callback.resolveGetItem(AutoBrowseContract.HOME_ID)

        assertEquals(AutoBrowseContract.HOME_ID, item.mediaId)
        assertEquals("Home", item.mediaMetadata.title?.toString())
    }

    @Test
    fun `resolveGetItem resolves prefixed episode via mediaResolver`() = runBlocking {
        val prefixedResolved = MediaItem.Builder()
            .setMediaId("episode:ep-456")
            .setUri(Uri.parse("https://example.com/ep456.mp3"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Prefixed Title")
                    .build(),
            )
            .build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(prefixedResolved)

        val item = callback.resolveGetItem("episode:ep-456")

        assertEquals("episode:ep-456", item.mediaId)
        assertEquals("Prefixed Title", item.mediaMetadata.title?.toString())
        assertEquals(Uri.parse("https://example.com/ep456.mp3"), item.localConfiguration?.uri)
    }

    @Test
    fun `onGetItem returns LibraryResult ofItem with resolved media item`() {
        val podcast = PodcastEntity(
            podcastId = "pod-contract",
            title = "Contract Show",
            author = "Contract Host",
            imageUrl = "https://example.com/art.jpg",
            description = "Contract Description",
            isSubscribed = true,
        )
        runBlocking {
            `when`(podcastDao.getPodcast("pod-contract")).thenReturn(podcast)
        }

        val session = mock(MediaLibrarySession::class.java)
        val controller = mock(MediaSession.ControllerInfo::class.java)

        val futureResult = callback.onGetItem(session, controller, "subscription:pod-contract")
        val result = futureResult.get()

        assertNotNull(result.value)
        assertEquals("subscription:pod-contract", result.value?.mediaId)
        assertEquals("Contract Show", result.value?.mediaMetadata?.title?.toString())
    }

    @Test
    fun `isAndroidAuto identifies Auto and Automotive controllers correctly`() {
        val session = mock(MediaSession::class.java)

        fun createController(packageName: String): MediaSession.ControllerInfo {
            val controller = mock(MediaSession.ControllerInfo::class.java)
            `when`(controller.packageName).thenReturn(packageName)
            return controller
        }

        val bluetooth = createController("com.android.bluetooth")
        val systemUi = createController("com.android.systemui")
        val appSelf = createController("cx.aswin.boxlore")
        val autoCompanion = createController("com.google.android.projection.gearhead")
        val automotiveGoogle = createController("com.google.android.apps.automotive.media")
        val automotiveAosp = createController("com.android.car.media")

        assertEquals(false, callback.isAndroidAuto(session, bluetooth))
        assertEquals(false, callback.isAndroidAuto(session, systemUi))
        assertEquals(false, callback.isAndroidAuto(session, appSelf))
        assertEquals(true, callback.isAndroidAuto(session, autoCompanion))
        assertEquals(true, callback.isAndroidAuto(session, automotiveGoogle))
        assertEquals(true, callback.isAndroidAuto(session, automotiveAosp))
    }

    @Test
    fun `isAndroidAuto returns true when MediaSession indicates companion or automotive`() {
        val session = mock(MediaSession::class.java)
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn("com.custom.auto.oem")

        `when`(session.isAutoCompanionController(controller)).thenReturn(true)
        assertEquals(true, callback.isAndroidAuto(session, controller))

        `when`(session.isAutoCompanionController(controller)).thenReturn(false)
        `when`(session.isAutomotiveController(controller)).thenReturn(true)
        assertEquals(true, callback.isAndroidAuto(session, controller))
    }

    @Test
    fun `onConnect and onDisconnected do not emit analytics for Bluetooth controller`() {
        val session = mock(MediaSession::class.java)
        val player = mock(Player::class.java)
        `when`(player.availableCommands).thenReturn(Player.Commands.EMPTY)
        `when`(session.player).thenReturn(player)
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn("com.android.bluetooth")

        callback.onConnect(session, controller)
        assertTrue(recordedEvents.none { it.first == "android_auto_connected" })

        callback.onDisconnected(session, controller)
        assertTrue(recordedEvents.none { it.first == "android_auto_disconnected" })
    }

    @Test
    fun `onConnect and onDisconnected emit analytics for Android Auto controller in a car`() {
        val session = mock(MediaSession::class.java)
        val player = mock(Player::class.java)
        `when`(player.availableCommands).thenReturn(Player.Commands.EMPTY)
        `when`(session.player).thenReturn(player)
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn("com.google.android.projection.gearhead")
        val uiMode = context.getSystemService(android.app.UiModeManager::class.java)
        uiMode.enableCarMode(0)
        try {
            callback.onConnect(session, controller)
            assertTrue(recordedEvents.any { it.first == "android_auto_connected" })

            callback.onDisconnected(session, controller)
            assertTrue(recordedEvents.any { it.first == "android_auto_disconnected" })
        } finally {
            uiMode.disableCarMode(0)
        }
    }

    @Test
    fun `Android Auto connecting without a car sends no connection analytics`() {
        val session = mock(MediaSession::class.java)
        val player = mock(Player::class.java)
        `when`(player.availableCommands).thenReturn(Player.Commands.EMPTY)
        `when`(session.player).thenReturn(player)
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn("com.google.android.projection.gearhead")

        callback.onConnect(session, controller)
        callback.onDisconnected(session, controller)

        assertTrue(recordedEvents.none { it.first == "android_auto_connected" || it.first == "android_auto_disconnected" })
    }

    @Test
    fun `onGetChildren does not emit android_auto_browse for non-Auto controller`() {
        val session = mock(MediaLibrarySession::class.java)
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn("com.android.bluetooth")

        callback.onGetChildren(session, controller, AutoBrowseContract.ROOT_ID, 0, 10, null)
        assertTrue(recordedEvents.none { it.first == "android_auto_browse" })
    }

    @Test
    fun `onGetChildren emits android_auto_browse for Android Auto controller`() {
        val session = mock(MediaLibrarySession::class.java)
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn("com.google.android.projection.gearhead")

        callback.onGetChildren(session, controller, AutoBrowseContract.ROOT_ID, 0, 10, null)
        assertTrue(recordedEvents.any { it.first == "android_auto_browse" })
    }

    @Test
    fun `Next episode joins the buttons only while another episode is queued, and skips to the next episode`() {
        val next = mock(androidx.media3.session.CommandButton::class.java)
        `when`(host.skipNextAction).thenReturn(next)
        val session = mock(MediaSession::class.java)
        val player = mock(Player::class.java)
        `when`(player.availableCommands).thenReturn(Player.Commands.EMPTY)
        `when`(session.player).thenReturn(player)
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn("com.android.systemui")

        `when`(player.hasNextMediaItem()).thenReturn(false)
        assertFalse(callback.onConnect(session, controller).mediaButtonPreferences!!.contains(next))
        `when`(player.hasNextMediaItem()).thenReturn(true)
        assertTrue(callback.onConnect(session, controller).mediaButtonPreferences!!.contains(next))

        callback.onCustomCommand(session, controller, SessionCommand(AutoBrowseContract.COMMAND_SKIP_NEXT, Bundle.EMPTY), Bundle.EMPTY)
        verify(player).seekToNext()
    }

    private fun episodeItem(id: String): MediaItem = AutoMediaItemFactory.playable(
        AutoPlayableSpec(mediaId = "episode:$id", title = "Episode $id", podcastTitle = "Show", source = "any", uri = "https://example.com/$id.mp3"),
    )

    private fun controllerFrom(packageName: String): MediaSession.ControllerInfo {
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn(packageName)
        return controller
    }

    @Test
    fun `an episode tapped in the car's New episodes list plays on through the list`() {
        // Regression: the car sends only the media ID, so every tap was a single play labelled "discover".
        val session = mock(MediaLibrarySession::class.java)
        val car = controllerFrom("com.google.android.projection.gearhead")
        runBlocking {
            `when`(treeBuilder.getNewEpisodesChildren()).thenReturn(listOf(episodeItem("1"), episodeItem("2"), episodeItem("3")))
            `when`(mediaResolver.resolveMediaItem(mockAny())).thenAnswer { it.arguments[0] }
        }
        callback.onGetChildren(session, car, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 50, null).get()

        val played = callback.onAddMediaItems(session, car, mutableListOf(MediaItem.Builder().setMediaId("episode:2").build())).get()

        assertEquals(listOf("episode:2", "episode:3"), played.map { it.mediaId })
        assertEquals("android_auto_new_episodes", cx.aswin.boxlore.core.analytics.PendingEntryPoint.consume()?.get("entry_point"))
        // Each row goes through the resolver, which is what points a downloaded episode at its saved file.
        runBlocking { verify(mediaResolver, Mockito.times(2)).resolveMediaItem(mockAny()) }
    }

    @Test
    fun `a list browsed by another controller never becomes the car's list`() {
        // The tracker is shared: a Bluetooth or system browse of Downloads must not relabel or redirect a car tap.
        val session = mock(MediaLibrarySession::class.java)
        val car = controllerFrom("com.google.android.projection.gearhead")
        val other = controllerFrom("com.android.bluetooth")
        runBlocking {
            `when`(treeBuilder.getNewEpisodesChildren()).thenReturn(listOf(episodeItem("1"), episodeItem("2")))
            `when`(treeBuilder.getDownloadsChildren()).thenReturn(listOf(episodeItem("1"), episodeItem("9")))
            `when`(mediaResolver.resolveMediaItem(mockAny())).thenAnswer { it.arguments[0] }
        }
        callback.onGetChildren(session, car, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 50, null).get()
        callback.onGetChildren(session, other, AutoBrowseContract.DOWNLOADS_ID, 0, 50, null).get()

        val played = callback.onAddMediaItems(session, car, mutableListOf(MediaItem.Builder().setMediaId("episode:1").build())).get()

        assertEquals(listOf("episode:1", "episode:2"), played.map { it.mediaId })
    }

    @Test
    fun `a list opened from a Home block is the same list, and continues the same way`() {
        val session = mock(MediaLibrarySession::class.java)
        val car = controllerFrom("com.google.android.projection.gearhead")
        runBlocking {
            `when`(treeBuilder.getDownloadsChildren()).thenReturn(listOf(episodeItem("a"), episodeItem("b")))
            `when`(mediaResolver.resolveMediaItem(mockAny())).thenAnswer { it.arguments[0] }
        }
        callback.onGetChildren(session, car, AutoBrowseContract.OPEN_PREFIX + AutoBrowseContract.DOWNLOADS_ID, 0, 50, null).get()

        val played = callback.onAddMediaItems(session, car, mutableListOf(MediaItem.Builder().setMediaId("episode:a").build())).get()

        assertEquals(listOf("episode:a", "episode:b"), played.map { it.mediaId })
        assertEquals("android_auto_downloads", cx.aswin.boxlore.core.analytics.PendingEntryPoint.consume()?.get("entry_point"))
    }

    @Test
    fun `a play from the phone is never turned into a car list`() {
        val session = mock(MediaLibrarySession::class.java)
        val car = controllerFrom("com.google.android.projection.gearhead")
        val phone = controllerFrom("cx.aswin.boxlore")
        val resolved = episodeItem("2")
        runBlocking {
            `when`(treeBuilder.getNewEpisodesChildren()).thenReturn(listOf(episodeItem("1"), episodeItem("2"), episodeItem("3")))
            `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(resolved)
        }
        callback.onGetChildren(session, car, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 50, null).get()

        val played = callback.onAddMediaItems(session, phone, mutableListOf(MediaItem.Builder().setMediaId("episode:2").build())).get()

        assertEquals(listOf("episode:2"), played.map { it.mediaId })
    }

    @Test
    fun `an episode tapped on a show's page plays alone and is labelled as a show play`() {
        val session = mock(MediaLibrarySession::class.java)
        val car = controllerFrom("com.google.android.projection.gearhead")
        val resolved = episodeItem("s2")
        runBlocking {
            `when`(treeBuilder.getPodcastEpisodes("77")).thenReturn(listOf(episodeItem("s1"), episodeItem("s2")))
            `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(resolved)
        }
        callback.onGetChildren(session, car, "subscription:77", 0, 50, null).get()

        val played = callback.onAddMediaItems(session, car, mutableListOf(MediaItem.Builder().setMediaId("episode:s2").build())).get()

        assertEquals(listOf("episode:s2"), played.map { it.mediaId })
        assertEquals("android_auto_show", cx.aswin.boxlore.core.analytics.PendingEntryPoint.consume()?.get("entry_point"))
    }

    @Test
    fun `a car tap that finds nothing playable fails instead of clearing what is playing`() {
        // Regression: an empty answer made the player drop its current episode and go silent.
        val session = mock(MediaLibrarySession::class.java)
        val car = controllerFrom("com.google.android.projection.gearhead")
        val unresolved = MediaItem.Builder().setMediaId("episode:gone").build()
        runBlocking { `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(unresolved) }

        val request = callback.onAddMediaItems(session, car, mutableListOf(MediaItem.Builder().setMediaId("episode:gone").build()))

        val failure = runCatching { request.get() }.exceptionOrNull()
        assertTrue(failure is java.util.concurrent.ExecutionException)
        assertEquals(null, cx.aswin.boxlore.core.analytics.PendingEntryPoint.consume()?.get("entry_point"))
    }

    @Test
    fun `opening a list in the car does not rebuild the other screens first`() {
        // Regression: each subscription went through onGetItem, which rebuilt Home, Library and Explore.
        val session = mock(MediaLibrarySession::class.java)
        val car = controllerFrom("com.google.android.projection.gearhead")

        val result = callback.onSubscribe(session, car, AutoBrowseContract.OPEN_PREFIX + AutoBrowseContract.DOWNLOADS_ID, null).get()

        assertEquals(androidx.media3.session.LibraryResult.RESULT_SUCCESS, result.resultCode)
        runBlocking { verify(treeBuilder, Mockito.never()).getHomeChildren() }
        verify(treeBuilder, Mockito.never()).getDiscoverChildren()
    }

    @Test
    fun `an episode resumed from Continue listening is followed by the saved queue`() {
        // The car has no queue screen of its own, so the queue has to be in the player.
        val session = mock(MediaLibrarySession::class.java)
        val car = controllerFrom("com.google.android.projection.gearhead")
        val queueRepository = mock(cx.aswin.boxlore.core.playback.QueueRepository::class.java)
        val queued = cx.aswin.boxlore.core.model.Episode("next", "Next", "", "https://example.com/next.mp3")
        `when`(host.queueRepository).thenReturn(queueRepository)
        val resolved = episodeItem("resume")
        runBlocking {
            `when`(treeBuilder.getContinueListeningChildren()).thenReturn(listOf(episodeItem("resume")))
            `when`(queueRepository.getQueueEpisodeSnapshot()).thenReturn(listOf(queued))
            `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(resolved)
        }
        callback.onGetChildren(session, car, AutoBrowseContract.CONTINUE_ID, 0, 50, null).get()

        val played = callback.onAddMediaItems(session, car, mutableListOf(MediaItem.Builder().setMediaId("episode:resume").build())).get()

        assertEquals(listOf("episode:resume", "queue:next"), played.map { it.mediaId })
        assertEquals("android_auto_continue", cx.aswin.boxlore.core.analytics.PendingEntryPoint.consume()?.get("entry_point"))
    }

    private fun carSessionWith(player: Player): MediaSession {
        val session = mock(MediaSession::class.java)
        `when`(player.availableCommands).thenReturn(Player.Commands.EMPTY)
        `when`(session.player).thenReturn(player)
        return session
    }

    private fun inCarMode(block: () -> Unit) {
        val uiMode = context.getSystemService(android.app.UiModeManager::class.java)
        uiMode.enableCarMode(0)
        try {
            block()
        } finally {
            uiMode.disableCarMode(0)
        }
    }

    @Test
    fun `a car connecting to an empty player gets the last episode loaded and paused`() {
        // Regression: the car's card read "Tap to open" with a disabled play button until something was played.
        val player = mock(Player::class.java)
        val session = carSessionWith(player)
        val last = MediaSession.MediaItemsWithStartPosition(listOf(episodeItem("last"), episodeItem("queued")), 0, 42_000L)
        runBlocking { `when`(resumptionHandler.resolveResumption(session)).thenReturn(last) }

        inCarMode { callback.onConnect(session, controllerFrom("com.google.android.projection.gearhead")) }

        verify(player).setMediaItems(last.mediaItems, 0, 42_000L)
        verify(player).prepare()
        verify(player, Mockito.never()).play()
    }

    @Test
    fun `the last episode still loads when the car signal arrives a moment after Android Auto connects`() {
        // Regression: seen on a real head unit. Android Auto connected first and reported the car a moment later.
        val player = mock(Player::class.java)
        val session = carSessionWith(player)
        val last = MediaSession.MediaItemsWithStartPosition(listOf(episodeItem("last")), 0, 0L)
        runBlocking { `when`(resumptionHandler.resolveResumption(session)).thenReturn(last) }
        val uiMode = context.getSystemService(android.app.UiModeManager::class.java)

        callback.onConnect(session, controllerFrom("com.google.android.projection.gearhead"))
        verify(player, Mockito.never()).prepare()
        uiMode.enableCarMode(0)
        try {
            verify(player, Mockito.timeout(5_000)).prepare()
        } finally {
            uiMode.disableCarMode(0)
        }
    }

    @Test
    fun `a car connecting while something is loaded leaves the player alone`() {
        val player = mock(Player::class.java)
        `when`(player.mediaItemCount).thenReturn(1)
        val session = carSessionWith(player)

        inCarMode { callback.onConnect(session, controllerFrom("com.google.android.projection.gearhead")) }

        verify(player, Mockito.never()).prepare()
        runBlocking { verify(resumptionHandler, Mockito.never()).resolveResumption(session) }
    }

    @Test
    fun `Android Auto connecting without a car, or a phone controller, loads nothing`() {
        val player = mock(Player::class.java)
        val session = carSessionWith(player)

        // Android Auto's own app connects in the background to list media apps.
        callback.onConnect(session, controllerFrom("com.google.android.projection.gearhead"))
        inCarMode { callback.onConnect(session, controllerFrom("com.android.systemui")) }

        verify(player, Mockito.never()).prepare()
    }

    @Test
    fun `a car connecting with nothing to resume changes nothing`() {
        val player = mock(Player::class.java)
        val session = carSessionWith(player)
        runBlocking {
            `when`(resumptionHandler.resolveResumption(session)).thenThrow(UnsupportedOperationException("No playback resumption items available"))
        }

        inCarMode { callback.onConnect(session, controllerFrom("com.google.android.projection.gearhead")) }

        verify(player, Mockito.never()).prepare()
    }
}
