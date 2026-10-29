package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.sync.SubscriptionSyncResolver
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.rss.MemoryRenumberedShows
import cx.aswin.boxlore.core.rss.RenumberedShows
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
 * One follow for a show Podcast Index renumbered, through [SubscriptionRepository] on real Room:
 * the listener's case (both shows followed after tapping Subscribe on the renumbered page), a follow
 * of the old show only, cloud pulls that must not undo the move, notifications that move through
 * the pending-registration journal, the foreground pass and the show page's toggle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenumberedShowFollowsTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var repository: SubscriptionRepository
    private val shows = MemoryRenumberedShows()
    private val pending = MemoryPendingNotificationRegistrations()
    private val registrationRequests = mutableListOf<String>()
    private val links = mutableListOf<Pair<String, String>>()
    private val folders = MembershipFolders()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        podcastDao = database.podcastDao()
        repository =
            SubscriptionRepository(
                podcastDao = podcastDao,
                folderRepository = folders,
                feedAddressWiring = FeedAddressWiring(requestNotificationRegistration = { registrationRequests += it }, pendingRegistrations = pending),
                renumbering = RenumberingWiring(shows = shows, linkEpisodes = { old, new -> links += old to new }),
            )
    }

    @After
    fun tearDown() {
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun bothShowsFollowedCollapseIntoTheNewShowWithMergedSettings() = runTest {
        podcastDao.upsert(row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = null))
        podcastDao.upsert(row(NEW, notifications = false, subscribedAt = FOLLOWED_AT, epNs = NEW.toLong(), genre = "True crime"))
        folders.add(OLD, "folder-1")

        assertTrue(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        val kept = podcastDao.getPodcast(NEW)!!
        assertTrue(kept.isSubscribed)
        assertTrue("notifications on when either was", kept.notificationsEnabled)
        assertEquals(FOLLOWED_AT, kept.subscribedAt)
        assertEquals("True crime", kept.customGenre)
        assertEquals("the new show keeps its namespace", NEW.toLong(), kept.epNs)
        assertTrue("the follow reaches the next push", kept.isDirty)
        val retired = podcastDao.getPodcast(OLD)!!
        assertFalse(retired.isSubscribed)
        assertFalse(retired.notificationsEnabled)
        assertTrue("the unfollow reaches the next push", retired.isDirty)
        assertTrue(retired.unsubscribedAt > FOLLOWED_AT)
        assertEquals(setOf(NEW), pending.all())
        assertEquals(listOf(NEW), registrationRequests)
        assertEquals(setOf(NEW), folders.membersOf("folder-1"))
        assertEquals(NEW, shows.currentId(OLD))
        assertEquals(listOf(OLD to NEW), links)
        assertEquals(setOf(NEW), repository.subscribedPodcastIds.first())
    }

    @Test
    fun aFollowOfTheOldShowOnlyMovesWithItsSettingsButNotItsNamespace() = runTest {
        podcastDao.upsert(
            row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = OLD.toLong(), genre = "Crime")
                .copy(autoDownloadEnabled = true, preferredSort = "oldest", type = "serial", skipBeginningOverrideMs = 30_000L),
        )

        assertTrue(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        val kept = podcastDao.getPodcast(NEW)!!
        assertTrue(kept.isSubscribed)
        assertTrue("followed at the move, never at the old show's earlier time", kept.subscribedAt > FOLLOWED_AT)
        assertTrue(kept.notificationsEnabled)
        assertTrue(kept.autoDownloadEnabled)
        assertEquals("Crime", kept.customGenre)
        assertEquals("oldest", kept.preferredSort)
        assertEquals(30_000L, kept.skipBeginningOverrideMs)
        assertEquals("The Desi Crime Podcast", kept.title)
        assertEquals(FEED, kept.feedUrl)
        assertNull("a new row never takes the old show's namespace", kept.epNs)
        assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
        assertEquals(listOf(NEW), registrationRequests)
    }

    @Test
    fun aMergeTheServerConfirmedMovesTheFollowWithoutAnyDetailsLookup() = runTest {
        podcastDao.upsert(row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = null))

        assertTrue(repository.renumberedFollows.adoptKnown(OLD, confirmedNewId = NEW))

        assertTrue(podcastDao.getPodcast(NEW)!!.isSubscribed)
        assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
        assertEquals(NEW, repository.renumberedFollows.currentId(OLD))
        assertFalse("the same merge again moves nothing", repository.renumberedFollows.adoptKnown(OLD, confirmedNewId = NEW))
        assertFalse("not a renumbering", repository.renumberedFollows.adoptKnown(NEW, confirmedNewId = NEW))
        assertFalse("rss: shows are never merged", repository.renumberedFollows.adoptKnown("rss:abc", confirmedNewId = NEW))
    }

    @Test
    fun aMoveIsIdempotentAndAPullOfTheOldFollowDoesNotUndoIt() = runTest {
        podcastDao.upsert(row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = null))
        podcastDao.upsert(row(NEW, notifications = false, subscribedAt = FOLLOWED_AT, epNs = NEW.toLong()))
        repository.renumberedFollows.adoptAnswer(OLD, answer())

        assertFalse("a second answer moves nothing", repository.renumberedFollows.adoptAnswer(OLD, answer()))
        assertEquals(listOf(NEW), registrationRequests)

        // The cloud still holds both follows from before the move.
        val resolver = SubscriptionSyncResolver(podcastDao = podcastDao)
        resolver.resolveSubscription(remote(OLD, notifications = true), syncedAt = FOLLOWED_AT + 10)
        resolver.resolveSubscription(remote(NEW, notifications = false), syncedAt = FOLLOWED_AT + 10)

        val retired = podcastDao.getPodcast(OLD)!!
        assertFalse("the older follow loses to the newer unfollow", retired.isSubscribed)
        assertTrue("and the unfollow is pushed again", retired.isDirty)
        val kept = podcastDao.getPodcast(NEW)!!
        assertTrue(kept.isSubscribed)
        assertTrue("an unpushed merge isn't overwritten by the pull", kept.notificationsEnabled)
    }

    @Test
    fun aRepeatedFollowOfTheOldShowFromAnotherDeviceCollapsesOnTheNextPass() = runTest {
        podcastDao.upsert(row(OLD, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null))
        repository.renumberedFollows.adoptAnswer(OLD, answer())
        // An older build elsewhere follows the old number again later; the pull brings it back.
        podcastDao.upsert(podcastDao.getPodcast(OLD)!!.copy(isSubscribed = true, subscribedAt = FOLLOWED_AT * 2, unsubscribedAt = 0L, isDirty = false))
        var lookups = 0

        repository.renumberedFollows.collapseFollows(
            lookupDetails = {
                lookups++
                null
            },
        )

        assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
        assertTrue(podcastDao.getPodcast(NEW)!!.isSubscribed)
        assertEquals("a recorded renumbering needs no lookup", 0, lookups)
    }

    @Test
    fun theForegroundPassAsksOnlyShowsFollowedTwiceUnderOneFeedOncePerProcess() = runTest {
        podcastDao.upsert(row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = null))
        podcastDao.upsert(row(NEW, notifications = false, subscribedAt = FOLLOWED_AT, epNs = NEW.toLong()).copy(feedUrl = FEED.replace("https://", "http://") + "/"))
        podcastDao.upsert(row(LOOKALIKE, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null).copy(feedUrl = "https://feeds.megaphone.example/desi"))
        val asked = mutableListOf<String>()
        val lookup: suspend (String) -> Podcast? = { id ->
            asked += id
            if (id == OLD) answer() else answer().copy(id = id)
        }

        repository.renumberedFollows.collapseFollows(lookupDetails = lookup)
        repository.renumberedFollows.collapseFollows(lookupDetails = lookup)

        assertEquals("never matched by title, asked once each", setOf(OLD, NEW), asked.toSet())
        assertEquals(2, asked.size)
        assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
        assertTrue(podcastDao.getPodcast(NEW)!!.isSubscribed)
        assertTrue(podcastDao.getPodcast(LOOKALIKE)!!.isSubscribed)
    }

    @Test
    fun aMovedFollowSurvivesAPullOfANewerUnfollowOfTheNewShow() = runTest {
        podcastDao.upsert(row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = null))
        // The new show was followed by mistake and unfollowed since, here and on another device.
        podcastDao.upsert(row(NEW, notifications = false, subscribedAt = 0L, epNs = NEW.toLong()).copy(isSubscribed = false, unsubscribedAt = UNFOLLOWED_AT))
        assertTrue(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        val resolver = SubscriptionSyncResolver(podcastDao = podcastDao)
        resolver.resolveSubscription(tombstone(NEW, UNFOLLOWED_AT), syncedAt = UNFOLLOWED_AT + 10)

        val kept = podcastDao.getPodcast(NEW)!!
        assertTrue("the older unfollow never beats the moved follow", kept.isSubscribed)
        assertTrue("and the follow is pushed again", kept.isDirty)
        assertTrue(kept.notificationsEnabled)
        assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
    }

    @Test
    fun aPullFromTheStartMovesTheOldFollowAndKeepsItAgainstTheNewShowsUnfollow() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(DETAILS_OF_NEW))
            val resolver = SubscriptionSyncResolver(podcastDao = podcastDao, podcastRepository = podcastRepository(server))

            // A new device pulls the old follow, whose details name the new show, then the new show's unfollow.
            resolver.resolveSubscription(remote(OLD, notifications = true), syncedAt = UNFOLLOWED_AT + 10)
            resolver.resolveSubscription(tombstone(NEW, UNFOLLOWED_AT), syncedAt = UNFOLLOWED_AT + 10)

            assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
            val kept = podcastDao.getPodcast(NEW)!!
            assertTrue("the show stays followed", kept.isSubscribed)
            assertTrue(kept.notificationsEnabled)
            assertEquals(NEW, shows.currentId(OLD))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun theShowPageToggleOfferingUnfollowEndsEveryFollow() = runTest {
        podcastDao.upsert(row(OLD, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null))
        shows.record(OLD, NEW)

        // The page for the new show read the old follow as its own and offered Unfollow.
        repository.toggleSubscription(answer(), shownFollowed = true)

        assertFalse(repository.isSubscribed(OLD))
        assertFalse(repository.isSubscribed(NEW))
    }

    @Test
    fun aSubscribeOfferedWhileTheOldShowIsFollowedFollowsTheNewShowWithItsSettings() = runTest {
        podcastDao.upsert(row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = null, genre = "Crime"))
        shows.record(OLD, NEW)

        // A search card or a page opened on the new number shows Subscribe from the new show's own row.
        repository.toggleSubscription(answer())

        val kept = podcastDao.getPodcast(NEW)!!
        assertTrue(kept.isSubscribed)
        assertTrue("the old follow's settings come along", kept.notificationsEnabled)
        assertEquals("Crime", kept.customGenre)
        assertFalse(repository.isSubscribed(OLD))

        // The same from the show page, which says what it showed.
        podcastDao.upsert(kept.copy(isSubscribed = false, unsubscribedAt = kept.subscribedAt + 1))
        podcastDao.upsert(podcastDao.getPodcast(OLD)!!.copy(isSubscribed = true, subscribedAt = kept.subscribedAt + 2, unsubscribedAt = 0L))
        repository.toggleSubscription(answer(), shownFollowed = false)
        assertTrue(repository.isSubscribed(NEW))
        assertFalse(repository.isSubscribed(OLD))
    }

    @Test
    fun theForegroundPassAsksBeyondTheFirstLookupsAndRetriesFailedOnes() = runTest {
        // Four shows each followed twice under one feed: eight candidates, the renumbered pair among them.
        val pairs = listOf("1001" to "1002", "1003" to "1004", "1005" to "1006")
        for ((index, pair) in pairs.withIndex()) {
            for (id in pair.toList()) {
                podcastDao.upsert(row(id, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null).copy(title = "A$index", feedUrl = "https://feeds.example/$index"))
            }
        }
        podcastDao.upsert(row(OLD, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null).copy(title = "Z"))
        podcastDao.upsert(row(NEW, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null).copy(title = "Z"))
        val asked = mutableListOf<String>()
        var offline = true
        val lookup: suspend (String) -> Podcast? = { id ->
            asked += id
            when {
                offline -> error("offline")
                id == OLD -> answer()
                else -> answer().copy(id = id)
            }
        }

        repository.renumberedFollows.collapseFollows(lookupDetails = lookup)
        assertEquals("at most six per pass", 6, asked.size)
        assertTrue("nothing settled offline", podcastDao.getPodcast(OLD)!!.isSubscribed)

        offline = false
        asked.clear()
        repository.renumberedFollows.collapseFollows(lookupDetails = lookup)
        repository.renumberedFollows.collapseFollows(lookupDetails = lookup)

        assertEquals("every candidate is asked, failed ones again, answered ones once", 8, asked.size)
        assertEquals(8, asked.toSet().size)
        assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
        assertTrue(podcastDao.getPodcast(NEW)!!.isSubscribed)
    }

    @Test
    fun rssShowsAndNonRenumberingsAreNeverTouched() = runTest {
        podcastDao.upsert(row(OLD, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null))
        val feedShow =
            row("rss:abc", notifications = false, subscribedAt = FOLLOWED_AT, epNs = null)
                .copy(sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = NEW)
        podcastDao.upsert(feedShow)

        assertFalse(repository.renumberedFollows.adoptAnswer("rss:abc", answer()))
        assertFalse(repository.renumberedFollows.adoptAnswer(OLD, answer().copy(id = OLD)))
        assertTrue(podcastDao.getPodcast(OLD)!!.isSubscribed)

        // The new show is followed through its own feed: that follow stays, the old one ends.
        folders.add(OLD, "folder-1")
        assertTrue(repository.renumberedFollows.adoptAnswer(OLD, answer()))
        assertEquals(feedShow.copy(isDirty = feedShow.isDirty), podcastDao.getPodcast("rss:abc"))
        assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
        assertNull("no follow is added under the Podcast Index number", podcastDao.getPodcast(NEW))
        assertEquals("the folder place goes to the feed's show", setOf("rss:abc"), folders.membersOf("folder-1"))
    }

    @Test
    fun aFollowWithNotificationsStaysWhenTheNewShowIsFollowedThroughItsFeedWithout() = runTest {
        podcastDao.upsert(row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = null))
        podcastDao.upsert(
            row("rss:abc", notifications = false, subscribedAt = FOLLOWED_AT, epNs = null)
                .copy(sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = NEW),
        )
        folders.add(OLD, "folder-1")

        assertFalse(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        val old = podcastDao.getPodcast(OLD)!!
        assertTrue("notifications are never dropped", old.isSubscribed && old.notificationsEnabled)
        assertEquals(setOf(OLD), folders.membersOf("folder-1"))
        assertFalse(podcastDao.getPodcast("rss:abc")!!.notificationsEnabled)
    }

    @Test
    fun theFollowedFeedCountsWhenAnUnfollowedOneIsLinkedToo() = runTest {
        podcastDao.upsert(row(OLD, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null))
        podcastDao.upsert(
            row("rss:earlier", notifications = false, subscribedAt = 0L, epNs = null)
                .copy(sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = NEW, isSubscribed = false),
        )
        podcastDao.upsert(
            row("rss:abc", notifications = false, subscribedAt = FOLLOWED_AT, epNs = null)
                .copy(sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = NEW),
        )

        assertTrue(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        assertNull("the feed's follow is the show's: none is added under the new number", podcastDao.getPodcast(NEW))
        assertTrue(podcastDao.getPodcast("rss:abc")!!.isSubscribed)
        assertFalse(podcastDao.getPodcast(OLD)!!.isSubscribed)
    }

    @Test
    fun unfollowingTheFeedAlsoEndsTheFollowKeptUnderTheOldNumber() = runTest {
        podcastDao.upsert(row(OLD, notifications = true, subscribedAt = FOLLOWED_AT, epNs = null))
        podcastDao.upsert(
            row("rss:abc", notifications = false, subscribedAt = FOLLOWED_AT, epNs = null)
                .copy(sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = NEW),
        )
        assertFalse(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        repository.toggleSubscription(podcastDao.getPodcast("rss:abc")!!.toPodcast(), shownFollowed = true)

        assertFalse(podcastDao.getPodcast("rss:abc")!!.isSubscribed)
        assertFalse("the page no longer counts a follow", podcastDao.getPodcast(OLD)!!.isSubscribed)
    }

    @Test
    fun aRenumberingThatCouldNotBeSavedMovesNoFollow() = runTest {
        val unsaved =
            object : RenumberedShows {
                override fun all(): Map<String, String> = emptyMap()

                override fun record(oldId: String, newId: String): Boolean = false
            }
        val repo =
            SubscriptionRepository(
                podcastDao = podcastDao,
                folderRepository = folders,
                feedAddressWiring = FeedAddressWiring(requestNotificationRegistration = { registrationRequests += it }, pendingRegistrations = pending),
                renumbering = RenumberingWiring(shows = unsaved, linkEpisodes = { old, new -> links += old to new }),
            )
        podcastDao.upsert(row(OLD, notifications = false, subscribedAt = FOLLOWED_AT, epNs = null))

        assertFalse(repo.renumberedFollows.adoptAnswer(OLD, answer()))
        assertFalse(repo.renumberedFollows.adoptKnown(OLD, confirmedNewId = NEW))

        assertTrue("the follow stays where the phone can still find it", podcastDao.getPodcast(OLD)!!.isSubscribed)
        assertNull(podcastDao.getPodcast(NEW))
    }

    private fun row(id: String, notifications: Boolean, subscribedAt: Long, epNs: Long?, genre: String? = null) = PodcastEntity(
        podcastId = id,
        title = "The Desi Crime Podcast",
        author = "Host",
        imageUrl = "https://img.example/$id.jpg",
        description = null,
        isSubscribed = true,
        subscribedAt = subscribedAt,
        isDirty = false,
        notificationsEnabled = notifications,
        feedUrl = FEED,
        epNs = epNs,
        customGenre = genre,
    )

    private fun tombstone(id: String, at: Long) = UserSubscriptionSyncDto(
        podcastId = id,
        isSubscribed = false,
        subscribedAt = 0L,
        unsubscribedAt = at,
        updatedAt = at,
    )

    private fun podcastRepository(server: MockWebServer): PodcastRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        return PodcastRepository(
            baseUrl = base,
            publicKey = "unused",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
            onShowRenumbered = { old, answer -> repository.renumberedFollows.adoptAnswer(old, answer) },
        )
    }

    private fun answer() = Podcast(id = NEW, title = "The Desi Crime Podcast", artist = "Host", imageUrl = "https://img.example/new.jpg", feedUrl = FEED)

    private fun remote(id: String, notifications: Boolean) = UserSubscriptionSyncDto(
        podcastId = id,
        isSubscribed = true,
        subscribedAt = FOLLOWED_AT,
        notificationsEnabled = notifications,
        feedUrl = FEED,
        updatedAt = FOLLOWED_AT,
    )

    /** Folders that only record memberships. */
    private class MembershipFolders : FolderRepository {
        private val members = HashMap<String, MutableSet<String>>()

        fun add(podcastId: String, folderId: String) {
            members.getOrPut(folderId) { mutableSetOf() } += podcastId
        }

        fun membersOf(folderId: String): Set<String> = members[folderId].orEmpty()

        override val folders: Flow<List<SubscriptionFolder>> = emptyFlow()
        override val folderNames: Flow<List<String>> = emptyFlow()

        override suspend fun getFolders(): List<SubscriptionFolder> = members.map { (id, ids) -> SubscriptionFolder(id = id, name = id, podcastIds = ids.toList()) }

        override suspend fun getFolder(folderId: String): SubscriptionFolder? = getFolders().firstOrNull { it.id == folderId }

        override fun getFolderFlow(folderId: String): Flow<SubscriptionFolder?> = emptyFlow()

        override suspend fun createFolder(
            name: String,
            icon: String?,
            displaySize: FolderDisplaySize,
            linkedGenre: String?,
            showPodcastGrid: Boolean,
            podcastIds: List<String>,
        ): SubscriptionFolder = SubscriptionFolder(id = name, name = name)

        override suspend fun restoreFolder(folder: SubscriptionFolder): SubscriptionFolder = folder

        override suspend fun updateFolder(folder: SubscriptionFolder) = Unit

        override suspend fun deleteFolder(folderId: String) = Unit

        override suspend fun addPodcastToFolder(podcastId: String, folderId: String) = add(podcastId, folderId)

        override suspend fun removePodcastFromFolder(podcastId: String, folderId: String) {
            members[folderId]?.remove(podcastId)
        }

        override suspend fun removePodcastFromAllFolders(podcastId: String) {
            members.values.forEach { it.remove(podcastId) }
        }

        override suspend fun setPodcastsForFolder(folderId: String, podcastIds: List<String>) {
            members[folderId] = podcastIds.toMutableSet()
        }

        override suspend fun syncLinkedGenres() = Unit

        override suspend fun autoOrganizeSubscribedShows(defaultDisplaySize: FolderDisplaySize?, showPodcastGrid: Boolean) = Unit
    }

    private companion object {
        const val OLD = "1258562"
        const val NEW = "7976624"
        const val LOOKALIKE = "5200907"
        const val FOLLOWED_AT = 1_700_000_000_000L

        /** After [FOLLOWED_AT], before the test runs. */
        const val UNFOLLOWED_AT = FOLLOWED_AT + 60_000L
        const val DETAILS_OF_NEW = """{"status":"true","feed":{"id":7976624,"title":"The Desi Crime Podcast","url":"https://feeds.acast.example/public/shows/desi-crime"}}"""
        const val FEED = "https://feeds.acast.example/public/shows/desi-crime"
    }
}
