package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
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
 * A pulled follow or unfollow (cloud pull or linked-watch merge) keeps a Podcast Index show's
 * stored catalog in step, as a follow or unfollow on this phone does: an unfollow starts the
 * 14-day countdown and clears `followed`, so the sweep and Settings › Clear saved shows can remove
 * the copy; a follow clears the countdown, so a followed show's copy is never swept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionSyncCatalogTtlTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var catalog: RecordingCatalog
    private lateinit var resolver: SubscriptionSyncResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        catalog = RecordingCatalog(LocalEpisodeCatalogRepository(database))
        resolver = SubscriptionSyncResolver(podcastDao = database.podcastDao(), localEpisodeCatalog = catalog)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun remoteUnfollowOfAFollowedShowStartsItsCatalogCountdown() = runTest {
        follow(SHOW, subscribedAt = 1_000L)
        storedCopy(SHOW, followed = true, ttl = null)
        val before = System.currentTimeMillis()

        resolver.resolveSubscription(tombstone(SHOW, unsubscribedAt = 2_000L), syncedAt = 3_000L)

        val feed = feed(SHOW)
        assertFalse("the copy is no longer the followed show's", feed.followed)
        val ttl = requireNotNull(feed.ttlExpiresAt)
        assertTrue(ttl in before + TTL..System.currentTimeMillis() + TTL)
        assertEquals("the sweep removes it once the countdown ends", listOf(SHOW), dao().listExpiredFeedIds(ttl))
        assertTrue("not before", dao().listExpiredFeedIds(ttl - 1).isEmpty())
        assertEquals("Clear saved shows can remove it now", listOf(SHOW), dao().listClearableCopyIds())
    }

    @Test
    fun linkedWatchUnfollowStartsTheCountdownToo() = runTest {
        follow(SHOW, subscribedAt = 1_000L)
        storedCopy(SHOW, followed = true, ttl = null)

        resolver.resolveSubscription(tombstone(SHOW, unsubscribedAt = 2_000L), syncedAt = 0L)

        assertFalse(feed(SHOW).followed)
        assertTrue(feed(SHOW).ttlExpiresAt != null)
    }

    @Test
    fun remoteUnfollowOlderThanTheFollowHereLeavesTheCatalogFollowed() = runTest {
        follow(SHOW, subscribedAt = 2_500L)
        storedCopy(SHOW, followed = true, ttl = null)

        resolver.resolveSubscription(tombstone(SHOW, unsubscribedAt = 2_000L), syncedAt = 3_000L)

        assertTrue(database.podcastDao().getPodcast(SHOW)!!.isSubscribed)
        assertTrue(feed(SHOW).followed)
        assertNull(feed(SHOW).ttlExpiresAt)
        assertTrue(catalog.ttlWrites.isEmpty())
    }

    @Test
    fun remoteFollowOfAShowUnfollowedHereClearsItsCountdown() = runTest {
        unfollowed(SHOW, unsubscribedAt = 1_000L)
        storedCopy(SHOW, followed = false, ttl = 5_000L)

        resolver.resolveSubscription(pulledFollow(SHOW, subscribedAt = 2_000L), syncedAt = 3_000L)

        assertTrue(database.podcastDao().getPodcast(SHOW)!!.isSubscribed)
        assertTrue(feed(SHOW).followed)
        assertNull(feed(SHOW).ttlExpiresAt)
        assertTrue("the followed show's copy is never swept", dao().listExpiredFeedIds(Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun remoteFollowOlderThanTheUnfollowHereKeepsTheCountdown() = runTest {
        unfollowed(SHOW, unsubscribedAt = 2_000L)
        storedCopy(SHOW, followed = false, ttl = 5_000L)

        resolver.resolveSubscription(pulledFollow(SHOW, subscribedAt = 1_000L), syncedAt = 3_000L)

        assertFalse(database.podcastDao().getPodcast(SHOW)!!.isSubscribed)
        assertFalse(feed(SHOW).followed)
        assertEquals(5_000L, feed(SHOW).ttlExpiresAt)
        assertTrue(catalog.ttlWrites.isEmpty())
    }

    @Test
    fun pulledNewFollowMarksAStoredShowPageCopyFollowed() = runTest {
        storedCopy(SHOW, followed = false, ttl = null)

        resolver.resolveSubscription(pulledFollow(SHOW, subscribedAt = 2_000L), syncedAt = 3_000L)

        assertTrue(feed(SHOW).followed)
        assertNull(feed(SHOW).ttlExpiresAt)
        assertEquals(listOf(SHOW to null), catalog.ttlWrites)
    }

    @Test
    fun rssShowsKeepTheirOwnPathAndNeverTouchTheCatalogCountdown() = runTest {
        database.podcastDao().upsert(show(RSS_SHOW, isSubscribed = true, subscribedAt = 1_000L, sourceType = PodcastEntity.SOURCE_RSS))
        resolver.resolveSubscription(tombstone(RSS_SHOW, unsubscribedAt = 2_000L), syncedAt = 3_000L)
        assertFalse(database.podcastDao().getPodcast(RSS_SHOW)!!.isSubscribed)

        resolver.resolveSubscription(pulledFollow(RSS_SHOW, subscribedAt = 4_000L), syncedAt = 5_000L)
        assertTrue(database.podcastDao().getPodcast(RSS_SHOW)!!.isSubscribed)

        resolver.resolveSubscription(pulledFollow(NEW_RSS_SHOW, subscribedAt = 4_000L), syncedAt = 5_000L)
        assertTrue(database.podcastDao().getPodcast(NEW_RSS_SHOW)!!.isSubscribed)

        assertTrue(catalog.ttlWrites.isEmpty())
    }

    @Test
    fun pulledUnfollowOfAShowUnfollowedHereKeepsItsCountdown() = runTest {
        unfollowed(SHOW, unsubscribedAt = 1_000L)
        storedCopy(SHOW, followed = false, ttl = 5_000L)

        resolver.resolveSubscription(tombstone(SHOW, unsubscribedAt = 2_000L), syncedAt = 3_000L)

        assertEquals("the countdown is not pushed back", 5_000L, feed(SHOW).ttlExpiresAt)
        assertTrue(catalog.ttlWrites.isEmpty())
    }

    @Test
    fun pulledFollowOfAShowFollowedHereLeavesTheCatalogAlone() = runTest {
        follow(SHOW, subscribedAt = 1_000L)
        storedCopy(SHOW, followed = true, ttl = null)

        resolver.resolveSubscription(pulledFollow(SHOW, subscribedAt = 2_000L), syncedAt = 3_000L)
        resolver.resolveSubscription(pulledFollow(SHOW, subscribedAt = 2_000L), syncedAt = 0L)

        assertTrue(feed(SHOW).followed)
        assertNull(feed(SHOW).ttlExpiresAt)
        assertTrue(catalog.ttlWrites.isEmpty())
    }

    @Test
    fun withoutACatalogPortAPulledUnfollowStillSaves() = runTest {
        follow(SHOW, subscribedAt = 1_000L)
        storedCopy(SHOW, followed = true, ttl = null)

        SubscriptionSyncResolver(podcastDao = database.podcastDao())
            .resolveSubscription(tombstone(SHOW, unsubscribedAt = 2_000L), syncedAt = 3_000L)

        assertFalse(database.podcastDao().getPodcast(SHOW)!!.isSubscribed)
        assertTrue("nothing to start the countdown with", feed(SHOW).followed)
    }

    private fun dao() = database.localEpisodeCatalogDao()

    private suspend fun feed(podcastId: String): LocalEpisodeFeedEntity = requireNotNull(dao().getFeed(podcastId))

    private suspend fun follow(podcastId: String, subscribedAt: Long) {
        database.podcastDao().upsert(show(podcastId, isSubscribed = true, subscribedAt = subscribedAt))
    }

    private suspend fun unfollowed(podcastId: String, unsubscribedAt: Long) {
        database.podcastDao().upsert(show(podcastId, isSubscribed = false, subscribedAt = 500L, unsubscribedAt = unsubscribedAt))
    }

    private suspend fun storedCopy(podcastId: String, followed: Boolean, ttl: Long?) {
        dao().replaceFeedRow(
            LocalEpisodeFeedEntity(
                podcastId = podcastId,
                feedUrl = "https://feeds.example/$podcastId.xml",
                feedEtag = null,
                feedLastModified = null,
                fetchedAt = 1L,
                itemCount = 3,
                feedOrder = LocalFeedOrder.NEWEST_FIRST,
                ttlExpiresAt = ttl,
                needsFullBackfill = false,
                copiedExtrasCount = 0,
                ready = true,
                feedUrlLookupAt = 0L,
                followed = followed,
            ),
        )
    }

    private fun show(
        podcastId: String,
        isSubscribed: Boolean,
        subscribedAt: Long,
        unsubscribedAt: Long = 0L,
        sourceType: String = PodcastEntity.SOURCE_PODCAST_INDEX,
    ) = PodcastEntity(
        podcastId = podcastId,
        title = "Show $podcastId",
        author = "",
        imageUrl = "",
        description = null,
        isSubscribed = isSubscribed,
        subscribedAt = subscribedAt,
        unsubscribedAt = unsubscribedAt,
        syncedAt = 100L,
        sourceType = sourceType,
        feedUrl = "https://feeds.example/$podcastId.xml",
    )

    private fun tombstone(podcastId: String, unsubscribedAt: Long) = UserSubscriptionSyncDto(
        podcastId = podcastId,
        isSubscribed = false,
        subscribedAt = 500L,
        unsubscribedAt = unsubscribedAt,
        updatedAt = unsubscribedAt,
    )

    private fun pulledFollow(podcastId: String, subscribedAt: Long) = UserSubscriptionSyncDto(
        podcastId = podcastId,
        isSubscribed = true,
        subscribedAt = subscribedAt,
        updatedAt = subscribedAt,
    )

    /** The real Room catalog, with each countdown write recorded. */
    private class RecordingCatalog(private val inner: LocalEpisodeCatalogPort) : LocalEpisodeCatalogPort by inner {
        val ttlWrites = mutableListOf<Pair<String, Long?>>()

        override suspend fun setUnsubscribedTtl(podcastId: String, ttlExpiresAt: Long?) {
            ttlWrites += podcastId to ttlExpiresAt
            inner.setUnsubscribedTtl(podcastId, ttlExpiresAt)
        }
    }

    private companion object {
        const val SHOW = "4242"
        const val RSS_SHOW = "rss:abc"
        const val NEW_RSS_SHOW = "rss:new"
        const val TTL = LocalEpisodeCatalogRepository.UNSUBSCRIBE_TTL_MS
    }
}
