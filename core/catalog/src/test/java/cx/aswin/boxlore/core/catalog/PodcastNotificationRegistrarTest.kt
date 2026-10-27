package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.sync.SubscriptionSyncResolver
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Acknowledged notification registration of Podcast Index shows after an address change
 * (own-episode-IDs P4 PR 14, plan section 11): the same `tracked_podcasts/<id>` node and topic as
 * turning notifications on, including after a cloud pull that changes only the address.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastNotificationRegistrarTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PodcastDao
    private val firebase = FakeFirebase()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a followed show with notifications on is registered on its node and topic, with its address`() = runTest {
        dao.upsertRow(show("920666", notifications = true))

        assertEquals(NotificationRegistrationOutcome.DONE, PodcastNotificationRegistrar(dao, firebase).register("920666"))

        assertEquals(mapOf("920666" to mapOf("title" to "Show", "imageUrl" to "https://img.example/a.jpg", "feedUrl" to NEW)), firebase.nodes)
        assertEquals(listOf("new_ep_920666"), firebase.topics)
    }

    @Test
    fun `nothing is registered for a show without notifications, and rss shows are dropped`() = runTest {
        dao.upsertRow(show("1", notifications = false))
        dao.upsertRow(show("2", notifications = true).copy(isSubscribed = false))
        val registrar = PodcastNotificationRegistrar(dao, firebase)

        assertEquals(NotificationRegistrationOutcome.DONE, registrar.register("1"))
        assertEquals(NotificationRegistrationOutcome.DONE, registrar.register("2"))
        assertEquals(NotificationRegistrationOutcome.DONE, registrar.register("missing"))
        assertEquals(NotificationRegistrationOutcome.DROPPED, registrar.register("rss:abc"))
        assertTrue(firebase.nodes.isEmpty())
        assertTrue(firebase.topics.isEmpty())
    }

    @Test
    fun `no acknowledgement retries, and refused rules stop`() = runTest {
        dao.upsertRow(show("920666", notifications = true))

        firebase.failure = IOException("offline")
        assertEquals(NotificationRegistrationOutcome.RETRY, PodcastNotificationRegistrar(dao, firebase).register("920666"))
        firebase.failure = TrackedPodcastWriteRefused()
        assertEquals(NotificationRegistrationOutcome.DROPPED, PodcastNotificationRegistrar(dao, firebase).register("920666"))
        firebase.failure = null
        firebase.hang = true
        assertEquals(NotificationRegistrationOutcome.RETRY, PodcastNotificationRegistrar(dao, firebase, timeoutMs = 10L).register("920666"))
    }

    @Test
    fun `a pull that changes only the address re-registers on the same node and topic`() = runTest {
        dao.upsertRow(show("920666", notifications = true).copy(feedUrl = OLD, isDirty = false, syncedAt = 1L, subscribedAt = 1L))
        val queued = mutableListOf<String>()
        val updater = FeedAddressUpdater(dao, requestNotificationRegistration = { queued += it })
        val resolver = SubscriptionSyncResolver(podcastDao = dao, feedAddresses = updater)

        resolver.resolveSubscription(
            UserSubscriptionSyncDto(podcastId = "920666", isSubscribed = true, subscribedAt = 1L, notificationsEnabled = true, feedUrl = NEW, updatedAt = 2L),
            syncedAt = 3L,
        )
        queued.forEach { PodcastNotificationRegistrar(dao, firebase).register(it) }

        val row = dao.getPodcast("920666")!!
        assertEquals(NEW, row.feedUrl)
        assertEquals(OLD, row.previousFeedUrl)
        assertEquals(FeedUrlSources.CLOUD_SYNC, row.feedUrlSource)
        assertFalse("a pulled address is not pushed back", row.isDirty)
        assertEquals(listOf("920666"), queued)
        assertEquals(NEW, firebase.nodes.getValue("920666")["feedUrl"])
        assertEquals(listOf("new_ep_920666"), firebase.topics)
    }

    @Test
    fun `a pull that brings the same address registers nothing`() = runTest {
        dao.upsertRow(show("920666", notifications = true).copy(isDirty = false, syncedAt = 1L, subscribedAt = 1L))
        val queued = mutableListOf<String>()
        val resolver = SubscriptionSyncResolver(podcastDao = dao, feedAddresses = FeedAddressUpdater(dao, requestNotificationRegistration = { queued += it }))

        resolver.resolveSubscription(
            UserSubscriptionSyncDto(podcastId = "920666", isSubscribed = true, subscribedAt = 1L, notificationsEnabled = true, feedUrl = NEW, updatedAt = 2L),
            syncedAt = 3L,
        )

        assertTrue(queued.isEmpty())
    }

    private fun show(id: String, notifications: Boolean) = PodcastEntity(
        podcastId = id,
        title = "Show",
        author = "Author",
        imageUrl = "https://img.example/a.jpg",
        description = null,
        isSubscribed = true,
        notificationsEnabled = notifications,
        feedUrl = NEW,
        latestEpisode = Episode("1", "Episode", "", "https://cdn.example/1.mp3", podcastId = id),
    )

    private class FakeFirebase : TrackedPodcastFirebase {
        val nodes = mutableMapOf<String, Map<String, String>>()
        val topics = mutableListOf<String>()
        var failure: Exception? = null
        var hang = false

        override suspend fun set(key: String, payload: Map<String, String>) {
            failure?.let { throw it }
            if (hang) awaitCancellation()
            nodes[key] = payload
        }

        override suspend fun delete(path: String) = Unit

        override suspend fun subscribe(topic: String) {
            topics += topic
        }

        override suspend fun unsubscribe(topic: String) = Unit
    }

    private companion object {
        const val OLD = "https://old.example/feed.xml"
        const val NEW = "https://new.example/feed.xml"
    }
}
