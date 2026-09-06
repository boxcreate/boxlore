package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
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

    @Before
    fun setUp() = runBlocking {
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
}
