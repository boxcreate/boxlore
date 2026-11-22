package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reading an OPML file gives each feed address once, so the import screen counts the shows it imports. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpmlParseFeedsTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun aFileListingAFeedTwiceGivesItOnceAndKeepsTheFirstOutline() {
        val opml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0"><body>
              <outline text="Show A" xmlUrl="https://feeds.example/a"/>
              <outline text="Show B" xmlUrl="https://feeds.example/b"/>
              <outline text="Show A again" xmlUrl="https://feeds.example/a"/>
              <outline text="Show C" xmlUrl="https://feeds.example/c"/>
            </body></opml>
            """.trimIndent()

        val feeds = manager().parseOpmlFeeds(opml.byteInputStream())

        assertEquals(
            listOf(
                OpmlFeed("Show A", "https://feeds.example/a"),
                OpmlFeed("Show B", "https://feeds.example/b"),
                OpmlFeed("Show C", "https://feeds.example/c"),
            ),
            feeds,
        )
    }

    private fun manager() = LibraryBackupManager(
        subscriptionRepository = SubscriptionRepository(database.podcastDao()),
        listeningHistory = NoHistory,
        podcastRepository =
        PodcastRepository(
            baseUrl = "https://api.example/",
            publicKey = "test-app-key",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
        ),
        context = context,
        adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java),
        rssPodcastRepository = mock(RssPodcastRepository::class.java),
        database = database,
        pendingOpmlImports = MemoryPendingOpmlImports(),
        playlistRepository = null,
    )

    private object NoHistory : ListeningHistoryBackupPort {
        override fun getRecentHistory(): Flow<List<ListeningHistoryEntity>> = emptyFlow()

        override suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = emptyList()

        override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) = Unit

        override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) = Unit
    }
}
