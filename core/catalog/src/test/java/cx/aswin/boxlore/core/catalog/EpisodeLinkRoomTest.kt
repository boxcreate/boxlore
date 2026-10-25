package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Episode links against real Room aliases and recorded `/v2` answers (own-IDs plan section 9,
 * episode page): trusted aliases resolve, ambiguous and unresolved ones never do, and a `needsFeed`
 * answer is matched in the show's feed read through `/v2/feed/<id>`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeLinkRoomTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var aliases: RoomEpisodeAliasReads
    private lateinit var server: MockWebServer
    private val dao get() = database.episodeIdentityDao()
    private var feedXml = feedXml("guid-1", "guid-2")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        aliases = RoomEpisodeAliasReads { database }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/v2/episode?") -> json(NEEDS_FEED_PROXY)
                request.path!!.startsWith("/v2/feed/$SHOW") -> MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(feedXml)
                request.path!!.startsWith("/episode?id=404") -> MockResponse().setResponseCode(404)
                else -> MockResponse().setResponseCode(503)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun `trusted aliases resolve an old ID and a recipe ID, ambiguous and unresolved ones never do`() = runBlocking {
        dao.insertAliasesIfAbsent(
            listOf(
                alias("5001", "guid-1", owner = true),
                alias("-9", "guid-1"),
                alias("6001", "guid-2", state = EpisodeAliasState.AMBIGUOUS),
                alias("-20", null, state = EpisodeAliasState.UNRESOLVED),
            ),
        )

        val key = aliases.trustedKeyOf("-9")!!
        assertEquals(listOf("5001", "-9"), key.memberIds)
        assertEquals("guid-1", key.catalogKey)
        assertEquals(recipe("guid-1"), key.recipeId)
        assertEquals(listOf("5001", "-9"), aliases.trustedKeyForRecipe(SHOW.toString(), recipe("guid-1"))!!.memberIds)
        assertNull(aliases.trustedKeyOf("6001"))
        assertNull(aliases.trustedKeyOf("-20"))
        assertNull(aliases.trustedKeyForRecipe(SHOW.toString(), recipe("guid-2")))
        assertNull(aliases.trustedKeyForRecipe("123", recipe("guid-1")))
    }

    @Test
    fun `a recipe ID two keys of a show share resolves to neither`() = runBlocking {
        val shared = recipe("guid-1")
        dao.insertAliasesIfAbsent(listOf(alias("5001", "guid-1", recipeId = shared), alias("5002", "guid-9", recipeId = shared)))

        assertNull(aliases.trustedKeyForRecipe(SHOW.toString(), shared))
    }

    @Test
    fun `the namespace is the stored one, else the show ID`() = runBlocking {
        assertEquals(SHOW, aliases.namespaceOf(SHOW.toString()))
        database.podcastDao().upsert(PodcastEntity(SHOW.toString(), "Show", "", "", null, epNs = 777L))
        assertEquals(777L, aliases.namespaceOf(SHOW.toString()))
        assertNull(aliases.namespaceOf("rss:abc"))
    }

    @Test
    fun `a link to an episode only the feed lists reads the show's feed through the API and matches it`() = runBlocking {
        val repository = repository()
        var readingFeed = false

        val result = repository.resolveEpisodeLink(recipe("guid-2").toString(), SHOW.toString()) { readingFeed = true }

        val episode = (result as EpisodeLinkResult.Found).episode
        assertTrue(readingFeed)
        assertEquals(recipe("guid-2").toString(), episode.id)
        assertEquals("Second", episode.title)
        assertEquals("https://cdn.example/2.mp3", episode.audioUrl)
        assertEquals(SHOW.toString(), episode.podcastId)
        assertEquals("guid-2", episode.catalogKey)
        assertEquals("/v2/episode?id=${recipe("guid-2")}&show=$SHOW", server.takeRequest().path)
        assertEquals("/v2/feed/$SHOW", server.takeRequest().path)
    }

    @Test
    fun `a link the feed no longer lists is not found, and getEpisode never reads the feed`() = runBlocking {
        feedXml = feedXml("guid-1")
        val repository = repository()

        assertEquals(EpisodeLinkResult.NotFound, repository.resolveEpisodeLink(recipe("guid-2").toString(), SHOW.toString()))
        assertEquals(2, server.requestCount)

        assertNull(repository.getEpisode(recipe("guid-2").toString(), SHOW.toString()))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `an old Podcast Index link tells a missing episode from an unreachable API`() = runBlocking {
        val repository = repository()

        assertEquals(EpisodeLinkResult.NotFound, repository.resolveEpisodeLink("404", null))
        assertEquals(EpisodeLinkResult.Unavailable, repository.resolveEpisodeLink("5555", null))
    }

    @Test
    fun `a push names its episode by recipe ID through the show's trusted aliases`() = runBlocking {
        dao.insertAliasesIfAbsent(listOf(alias("-9", "guid-1", owner = true)))
        val repository = repository()

        // No row holds the episode yet: the alias alone has nothing to show.
        assertNull(repository.localEpisodeForRecipe(SHOW.toString(), recipe("guid-1")))

        // The RSS feed the show was switched to holds it under its own ID.
        database.podcastDao().upsert(PodcastEntity("rss:feed", "Show", "", "", null))
        database.rssEpisodeDao().upsertAll(listOf(rssRow("-9")))
        val found = repository.localEpisodeForRecipe(SHOW.toString(), recipe("guid-1"))!!
        assertEquals("-9", found.id)
        assertEquals(recipe("guid-1"), found.recipeId)
        assertEquals("guid-1", found.catalogKey)

        assertNull(repository.localEpisodeForRecipe(SHOW.toString(), 42L))
        assertEquals(0, server.requestCount)
    }

    private fun rssRow(id: String) = cx.aswin.boxlore.core.database.RssEpisodeEntity(
        episodeId = id, podcastId = "rss:feed", guid = "guid-1", title = "First", description = "", audioUrl = "https://cdn.example/1.mp3",
        imageUrl = null, duration = 60, publishedDate = 1L, chaptersUrl = null, transcriptUrl = null, transcripts = null, persons = null,
        seasonNumber = null, episodeNumber = null, episodeType = null, enclosureType = null,
    )

    private fun repository(): PodcastRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient()
        val base = server.url("/").toString()
        return PodcastRepository(
            baseUrl = base,
            publicKey = "test-app-key",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context = context, database = database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
            episodeAliases = aliases,
        )
    }

    private fun alias(id: String, key: String?, owner: Boolean = false, state: String = EpisodeAliasState.TRUSTED, recipeId: Long? = key?.let(::recipe)) =
        EpisodeAliasEntity(
            aliasId = id,
            podcastId = SHOW.toString(),
            catalogKey = key,
            recipeId = recipeId,
            state = state,
            owner = owner,
            provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
            createdAt = 1L,
        )

    private fun recipe(key: String) = EpisodeRecipeId.recipeId(SHOW, key)

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun feedXml(vararg guids: String): String {
        val titles = mapOf("guid-1" to "First", "guid-2" to "Second")
        val items = guids.joinToString("") { guid ->
            val n = guid.removePrefix("guid-")
            """<item><title>${titles[guid]}</title><guid isPermaLink="false">$guid</guid><enclosure url="https://cdn.example/$n.mp3" type="audio/mpeg" length="1"/><pubDate>Mon, 0$n Jun 2026 10:00:00 GMT</pubDate></item>"""
        }
        return """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>Feed show</title><link>https://example.org</link><description>d</description>$items</channel></rss>"""
    }

    private companion object {
        const val SHOW = 920666L

        /** A recorded `/v2/episode` answer for an http-only show (fetchVia proxy). */
        const val NEEDS_FEED_PROXY =
            """{"needsFeed":true,"show":920666,"fetchUrl":"http://feeds.example/show.xml","fetchVia":"proxy","transportUrl":"https://api.example/v2/feed/920666"}"""
    }
}
