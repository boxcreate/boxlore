package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one saved-address writer (own-episode-IDs P4 PR 14, plan section 11): one atomic update, the
 * old address kept for undo, `rss:` refused, notifications registered again and one
 * `feed_url_corrected` per swap of a followed show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedAddressUpdaterTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PodcastDao
    private val registrations = mutableListOf<String>()
    private val corrections = mutableListOf<FeedUrlCorrection>()
    private lateinit var updater: FeedAddressUpdater

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.podcastDao()
        val telemetry =
            object : FeedTelemetryPort {
                override fun feedFetched(report: FeedFetchReport) = Unit

                override fun feedUrlCorrected(correction: FeedUrlCorrection) {
                    corrections += correction
                }
            }
        updater = FeedAddressUpdater(dao, requestNotificationRegistration = { registrations += it }, telemetry = telemetry)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a correction replaces the address, keeps the old one, resets validators, re-registers and reports once`() = runTest {
        dao.upsertRow(show("920666", OLD, notifications = true).copy(feedEtag = "\"e\"", isDirty = false))
        database.localEpisodeCatalogDao().upsertFeed(catalogFeed("920666"))

        val result = updater.update(correction("920666"))

        assertEquals(FeedAddressUpdate.UPDATED, result)
        val row = dao.getPodcast("920666")!!
        assertEquals(NEW, row.feedUrl)
        assertEquals(OLD, row.previousFeedUrl)
        assertEquals("registry:apple", row.feedUrlSource)
        assertEquals(7L, row.feedUrlCheckedAt)
        assertNull(row.feedEtag)
        assertTrue("an address found on the phone is pushed", row.isDirty)
        assertNull(database.localEpisodeCatalogDao().getFeed("920666")!!.feedEtag)
        assertEquals(listOf("920666"), registrations)
        assertEquals(listOf(FeedUrlCorrection("920666", FeedUrlCorrectionReason.REGISTRY, FeedUrlCorrectionStep.DAILY_CHECK)), corrections)

        assertEquals(FeedAddressUpdate.UNCHANGED, updater.update(correction("920666")))
        assertEquals("an unchanged address does nothing more", 1, registrations.size)
        assertEquals(1, corrections.size)
    }

    @Test
    fun `undo keeps the old address`() = runTest {
        dao.upsertRow(show("920666", OLD))
        updater.update(correction("920666"))

        assertEquals(FeedAddressUpdate.UPDATED, updater.update(FeedAddressChange("920666", OLD, source = "undo")))

        val row = dao.getPodcast("920666")!!
        assertEquals(OLD, row.feedUrl)
        assertEquals(NEW, row.previousFeedUrl)
    }

    @Test
    fun `rss shows and addresses that aren't HTTPS are refused`() = runTest {
        dao.upsertRow(show("rss:abc", OLD).copy(sourceType = PodcastEntity.SOURCE_RSS, notificationsEnabled = true))
        dao.upsertRow(show("920666", OLD))

        assertEquals(FeedAddressUpdate.REFUSED, updater.update(correction("rss:abc")))
        assertEquals(FeedAddressUpdate.REFUSED, updater.update(correction("920666").copy(feedUrl = "http://new.example/feed.xml")))
        assertEquals(FeedAddressUpdate.NO_SHOW, updater.update(correction("404")))

        assertEquals(OLD, dao.getPodcast("rss:abc")!!.feedUrl)
        assertEquals(OLD, dao.getPodcast("920666")!!.feedUrl)
        assertTrue(registrations.isEmpty())
        assertTrue(corrections.isEmpty())
    }

    @Test
    fun `an address that changed after it was read is left alone`() = runTest {
        dao.upsertRow(show("920666", "https://moved.example/feed.xml"))

        assertEquals(FeedAddressUpdate.CHANGED_MEANWHILE, updater.update(correction("920666").copy(onlyReplacing = OLD)))

        assertEquals("https://moved.example/feed.xml", dao.getPodcast("920666")!!.feedUrl)
    }

    @Test
    fun `a filled missing address, a cloud address and an unfollowed show are not corrections`() = runTest {
        dao.upsertRow(show("1", null, notifications = true))
        dao.upsertRow(show("2", OLD, notifications = true).copy(isDirty = false))
        dao.upsertRow(show("3", OLD).copy(isSubscribed = false, isDirty = false))

        updater.update(correction("1"))
        updater.update(correction("2").copy(fromCloud = true, reason = null, step = null))
        updater.update(correction("3"))

        assertTrue(corrections.isEmpty())
        assertEquals(listOf("1", "2"), registrations)
        assertFalse("an address from the cloud is not pushed back", dao.getPodcast("2")!!.isDirty)
        assertEquals(NEW, dao.getPodcast("3")!!.feedUrl)
        assertFalse("an unfollowed show is never pushed: its push could end a follow on another device", dao.getPodcast("3")!!.isDirty)
    }

    @Test
    fun `a registration stays pending until Firebase acknowledges it, and is asked for again`() = runTest {
        val pending = MemoryPendingNotificationRegistrations()
        val tracking = FeedAddressUpdater(dao, requestNotificationRegistration = { registrations += it }, pendingRegistrations = pending)
        dao.upsertRow(show("1", OLD, notifications = true))
        dao.upsertRow(show("2", OLD, notifications = true))
        dao.upsertRow(show("3", OLD))

        listOf("1", "2", "3").forEach { tracking.update(correction(it)) }
        assertEquals(setOf("1", "2"), pending.all())
        tracking.registrationSettled("1", NotificationRegistrationOutcome.DONE)
        tracking.registrationSettled("2", NotificationRegistrationOutcome.RETRY) // the job gave up after its last run
        registrations.clear()

        tracking.resumePendingRegistrations()

        assertEquals(listOf("2"), registrations)
        tracking.registrationSettled("2", NotificationRegistrationOutcome.DROPPED)
        assertTrue(pending.all().isEmpty())
    }

    @Test
    fun `an address is never written when its notification request couldn't be saved`() = runTest {
        val unsaved =
            object : PendingNotificationRegistrations {
                override fun all(): Set<String> = emptySet()

                override fun mark(podcastId: String): Long? = null

                override fun add(podcastId: String): Boolean = false

                override fun remove(podcastId: String, mark: Long) = Unit
            }
        dao.upsertRow(show("920666", OLD, notifications = true))

        val result = FeedAddressUpdater(dao, requestNotificationRegistration = { registrations += it }, pendingRegistrations = unsaved).update(correction("920666"))

        assertEquals(FeedAddressUpdate.NOT_SAVED, result)
        assertEquals(OLD, dao.getPodcast("920666")!!.feedUrl)
        assertTrue(registrations.isEmpty())
    }

    @Test
    fun `a registration lost with the process is asked for again on the next launch`() = runTest {
        val pending = MemoryPendingNotificationRegistrations()
        dao.upsertRow(show("920666", OLD, notifications = true))
        // The process dies before the background job is queued.
        FeedAddressUpdater(dao, requestNotificationRegistration = { throw RuntimeException("process gone") }, pendingRegistrations = pending)
            .update(correction("920666"))

        FeedAddressUpdater(dao, requestNotificationRegistration = { registrations += it }, pendingRegistrations = pending).resumePendingRegistrations()

        assertEquals(NEW, dao.getPodcast("920666")!!.feedUrl)
        assertEquals(listOf("920666"), registrations)
    }

    @Test
    fun `a push or restore address that redirects never replaces a different saved address`() = runTest {
        dao.upsertRow(show("920666", NEW))
        val repository = SubscriptionRepository(dao)

        // A push read the shared node's stale address, which redirects somewhere else.
        repository.adoptMovedFeed("920666", readFrom = OLD, movedTo = "https://elsewhere.example/feed.xml")
        assertEquals(NEW, dao.getPodcast("920666")!!.feedUrl)

        repository.adoptMovedFeed("920666", readFrom = NEW, movedTo = "https://moved.example/feed.xml")
        val row = dao.getPodcast("920666")!!
        assertEquals("https://moved.example/feed.xml", row.feedUrl)
        assertEquals(NEW, row.previousFeedUrl)
        assertEquals(FeedUrlSources.FEED_MOVE, row.feedUrlSource)
    }

    @Test
    fun `following a show again keeps its corrected address`() = runTest {
        dao.upsertRow(show("920666", OLD).copy(isSubscribed = false))
        FeedAddressUpdater(dao).update(correction("920666"))
        val repository = SubscriptionRepository(dao)

        repository.toggleSubscription(Podcast(id = "920666", title = "Show", artist = "Author", imageUrl = "", feedUrl = OLD))

        val row = dao.getPodcast("920666")!!
        assertTrue(row.isSubscribed)
        assertEquals(NEW, row.feedUrl)
        assertEquals("registry:apple", row.feedUrlSource)
    }

    @Test
    fun `a failed request for registration never undoes the saved address`() = runTest {
        val failing = FeedAddressUpdater(dao, requestNotificationRegistration = { throw IllegalStateException("WorkManager not ready") })
        val broken = FeedAddressUpdater(dao, requestNotificationRegistration = { throw IllegalArgumentException("bad request") })
        dao.upsertRow(show("920666", OLD, notifications = true))
        dao.upsertRow(show("1", OLD, notifications = true))

        assertEquals(FeedAddressUpdate.UPDATED, failing.update(correction("920666")))
        assertEquals(NEW, dao.getPodcast("920666")!!.feedUrl)
        assertEquals("any failure, not only WorkManager's", FeedAddressUpdate.UPDATED, broken.update(correction("1")))
    }

    private fun correction(id: String) = FeedAddressChange(
        podcastId = id,
        feedUrl = NEW,
        source = "registry:apple",
        checkedAt = 7L,
        reason = FeedUrlCorrectionReason.REGISTRY,
        step = FeedUrlCorrectionStep.DAILY_CHECK,
    )

    private fun show(id: String, feedUrl: String?, notifications: Boolean = false) = PodcastEntity(
        podcastId = id,
        title = "Show",
        author = "Author",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        notificationsEnabled = notifications,
        feedUrl = feedUrl,
    )

    private fun catalogFeed(id: String) = LocalEpisodeFeedEntity(
        podcastId = id,
        feedUrl = OLD,
        feedEtag = "\"etag\"",
        feedLastModified = "Wed",
        fetchedAt = 1L,
        itemCount = 1,
        feedOrder = LocalFeedOrder.NEWEST_FIRST,
        ttlExpiresAt = null,
        needsFullBackfill = false,
        copiedExtrasCount = 0,
        ready = true,
        feedUrlLookupAt = 0L,
    )

    private companion object {
        const val OLD = "https://old.example/feed.xml"
        const val NEW = "https://new.example/feed.xml"
    }
}
