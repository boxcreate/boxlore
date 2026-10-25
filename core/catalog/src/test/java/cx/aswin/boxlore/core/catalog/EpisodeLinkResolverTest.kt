package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.model.V2EpisodeReply
import cx.aswin.boxlore.core.rss.ParsedFeedItem
import cx.aswin.boxlore.core.rss.ParsedRssFeed
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The order an episode named by ID is resolved in (own-IDs plan section 9, episode page): stored
 * row, trusted alias, API, then one feed read for a `needsFeed` answer.
 */
class EpisodeLinkResolverTest {
    private val show = 920666L
    private val recipe = EpisodeRecipeId.recipeId(show, "guid-2")

    private val stored = mutableMapOf<String, Episode>()
    private val byKey = mutableMapOf<Pair<String, String>, Episode>()
    private val trustedById = mutableMapOf<String, EpisodeAliasKey>()
    private val trustedByRecipe = mutableMapOf<Pair<String, Long>, EpisodeAliasKey>()
    private var namespace: Long? = show
    private val lookups = mutableListOf<Pair<String, Long?>>()
    private var answer: EpisodeLookup = EpisodeLookup.Missing
    private var feed: ParsedRssFeed? = feedOf("guid-1", "guid-2", "guid-3")
    private var feedFailure: Exception? = null
    private var feedReads = 0

    private val aliases = object : EpisodeAliasReads {
        override suspend fun trustedKeyOf(episodeId: String) = trustedById[episodeId]

        override suspend fun trustedKeyForRecipe(podcastId: String, recipeId: Long) = trustedByRecipe[podcastId to recipeId]

        override suspend fun namespaceOf(podcastId: String) = namespace
    }

    private val resolver = EpisodeLinkResolver(
        storedEpisode = { id -> stored[id] },
        aliases = aliases,
        episodeForKey = { podcastId, key -> byKey[podcastId to key] },
        lookup = { id, showId ->
            lookups += id to showId
            answer
        },
        feedReader = { _, _ ->
            feedReads++
            feedFailure?.let { throw it }
            feed
        },
    )

    @Test
    fun `a stored row answers before aliases and the network`() = runBlocking {
        stored["5001"] = episode("5001")
        trustedById["5001"] = EpisodeAliasKey("920666", "guid-1", recipe, listOf("-9", "5001"))

        assertEquals(EpisodeLinkResult.Found(episode("5001")), resolve("5001"))
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun `an old ID resolves to the phone's own row of its episode with that row's ID`() = runBlocking {
        stored["-9"] = episode("-9")
        trustedById["5001"] = EpisodeAliasKey("920666", "guid-1", recipe, listOf("-9", "5001"))

        val found = resolve("5001") as EpisodeLinkResult.Found

        assertEquals("-9", found.episode.id)
        assertEquals(recipe, found.episode.recipeId)
        assertEquals("guid-1", found.episode.catalogKey)
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun `an alias whose rows were evicted falls back to the catalog row of its key`() = runBlocking {
        trustedById["5001"] = EpisodeAliasKey("920666", "guid-1", recipe, listOf("5001"))
        byKey["920666" to "guid-1"] = episode("-12")

        assertEquals("-12", (resolve("5001") as EpisodeLinkResult.Found).episode.id)
    }

    @Test
    fun `a shared recipe ID with its show resolves through the show's aliases`() = runBlocking {
        stored["-9"] = episode("-9")
        trustedByRecipe["920666" to recipe] = EpisodeAliasKey("920666", "guid-2", recipe, listOf("-9"))

        assertEquals("-9", (resolve(recipe.toString(), "920666") as EpisodeLinkResult.Found).episode.id)
        // Without the show, the recipe ID can't be placed locally and goes to the API.
        resolve(recipe.toString(), null)
        assertEquals(listOf(recipe.toString() to null), lookups)
    }

    @Test
    fun `a show at or above 2^52 is ignored`() = runBlocking {
        resolve(recipe.toString(), recipe.toString())
        assertEquals(listOf(recipe.toString() to null), lookups)
    }

    @Test
    fun `an API answer for an episode the phone stores under another ID resolves to the phone's row`() = runBlocking {
        stored["-9"] = episode("-9")
        trustedByRecipe["920666" to recipe] = EpisodeAliasKey("920666", "guid-2", recipe, listOf("-9"))
        answer = EpisodeLookup.Found(episode("5001", podcastId = "920666", recipeId = recipe))

        assertEquals("-9", (resolve("5001") as EpisodeLinkResult.Found).episode.id)

        trustedByRecipe.clear()
        byKey["920666" to "guid-2"] = episode("-12")
        answer = EpisodeLookup.Found(episode("5001", podcastId = "920666", catalogKey = "guid-2"))
        assertEquals("-12", (resolve("5001") as EpisodeLinkResult.Found).episode.id)

        byKey.clear()
        assertEquals("5001", (resolve("5001") as EpisodeLinkResult.Found).episode.id)
    }

    @Test
    fun `missing and failed lookups are told apart`() = runBlocking {
        answer = EpisodeLookup.Missing
        assertEquals(EpisodeLinkResult.NotFound, resolve("5001"))
        answer = EpisodeLookup.Failed
        assertEquals(EpisodeLinkResult.Unavailable, resolve("5001"))
    }

    @Test
    fun `needsFeed reads the show's feed once and matches the item by recipe ID`() = runBlocking {
        answer = needsFeed()
        var readingFeedShown = 0

        val found = resolver.resolve(recipe.toString(), "920666", readFeed = true) { readingFeedShown++ } as EpisodeLinkResult.Found

        assertEquals(1, readingFeedShown)
        assertEquals(1, feedReads)
        assertEquals(recipe.toString(), found.episode.id, "an episode only the feed lists keeps the link's ID")
        assertEquals("Episode guid-2", found.episode.title)
        assertEquals("920666", found.episode.podcastId)
        assertEquals("Feed show", found.episode.podcastTitle)
        assertEquals(recipe, found.episode.recipeId)
        assertEquals("guid-2", found.episode.catalogKey)
    }

    @Test
    fun `needsFeed is not found when the feed no longer lists the episode`() = runBlocking {
        answer = needsFeed()
        feed = feedOf("guid-1", "guid-3")

        assertEquals(EpisodeLinkResult.NotFound, resolver.resolve(recipe.toString(), "920666", readFeed = true))
        assertEquals(1, feedReads)
    }

    @Test
    fun `needsFeed matches in the show's stored namespace`() = runBlocking {
        val otherNamespace = 777L
        answer = needsFeed()
        namespace = otherNamespace
        val shared = EpisodeRecipeId.forNamespace(otherNamespace, "guid-3")

        val found = resolver.resolve(shared.toString(), "920666", readFeed = true) as EpisodeLinkResult.Found

        assertEquals("guid-3", found.episode.catalogKey)
    }

    @Test
    fun `needsFeed prefers a catalog row that holds the same key`() = runBlocking {
        answer = needsFeed()
        byKey["920666" to "guid-2"] = episode("-12")

        assertEquals("-12", (resolver.resolve(recipe.toString(), "920666", readFeed = true) as EpisodeLinkResult.Found).episode.id)
    }

    @Test
    fun `needsFeed without a feed read, a failed read, or a missing address`() = runBlocking {
        answer = needsFeed()
        assertEquals(EpisodeLinkResult.NotFound, resolver.resolve(recipe.toString(), "920666", readFeed = false))
        assertEquals(0, feedReads)

        feedFailure = IOException("offline")
        assertEquals(EpisodeLinkResult.Unavailable, resolver.resolve(recipe.toString(), "920666", readFeed = true))

        feedFailure = null
        feed = null
        assertEquals(EpisodeLinkResult.Unavailable, resolver.resolve(recipe.toString(), "920666", readFeed = true))
    }

    @Test
    fun `an item without a contract key never matches`() = runBlocking {
        answer = needsFeed()
        // GUID-less with no playable enclosure: keyed on its audio address, which the contract doesn't define.
        val audioKey = "https://cdn.example/only-audio.mp3"
        feed = ParsedRssFeed(
            title = "Feed show", author = "", description = null, imageUrl = null, genre = null,
            podcastType = "episodic", podcastGuid = null, declaredUpdatedAt = null,
            episodes = listOf(row(-1L, guid = null, audio = audioKey)),
        )

        val audioRecipe = EpisodeRecipeId.recipeId(show, audioKey)
        assertEquals(EpisodeLinkResult.NotFound, resolver.resolve(audioRecipe.toString(), "920666", readFeed = true))
    }

    @Test
    fun `a feed that lists the item twice gives its first copy in document order`() = runBlocking {
        answer = needsFeed()
        val copy = ParsedFeedItem(row(-2L, guid = "guid-2").copy(title = "Republished copy"), documentIndex = 5)
        val base = feedOf("guid-1", "guid-2")
        // A republished duplicate (the same GUID, so the same key) listed ahead of the original.
        feed = base.copy(episodes = base.episodes + copy.episode, items = listOf(copy) + base.items)

        val found = resolver.resolve(recipe.toString(), "920666", readFeed = true) as EpisodeLinkResult.Found

        assertEquals("Episode guid-2", found.episode.title)
        assertEquals("guid-2", found.episode.catalogKey)
    }

    @Test
    fun `an old Podcast Index link answered with its recipe ID resolves to the phone's recipe-ID row`() = runBlocking {
        // A followed show's new episode is stored under its recipe ID; an older build shared its PI ID.
        val own = recipe.toString()
        stored[own] = episode(own, recipeId = recipe, catalogKey = "guid-2")
        trustedByRecipe["920666" to recipe] = EpisodeAliasKey("920666", "guid-2", recipe, listOf(own))
        answer = EpisodeLookup.Found(episode("5001", recipeId = recipe, catalogKey = "guid-2"))

        assertEquals(own, (resolve("5001", "920666") as EpisodeLinkResult.Found).episode.id)
        assertEquals(listOf("5001" to show), lookups)

        // No alias yet: the answer's catalog key finds the stored row.
        trustedByRecipe.clear()
        byKey["920666" to "guid-2"] = episode(own, recipeId = recipe, catalogKey = "guid-2")
        assertEquals(own, (resolve("5001") as EpisodeLinkResult.Found).episode.id)
    }

    @Test
    fun `a v1 answer without identity finds the stored row by its enclosure address`() = runBlocking {
        val v1 = episode("5001").copy(audioUrl = " https://cdn.example/stored.mp3")
        byKey["920666" to "https://cdn.example/stored.mp3"] = episode("-12", catalogKey = "guid-12")
        answer = EpisodeLookup.Found(v1)

        val found = (resolve("5001") as EpisodeLinkResult.Found).episode
        assertEquals("-12", found.id)
        assertEquals("guid-12", found.catalogKey, "the stored row keeps its own key")

        byKey.clear()
        assertEquals("5001", (resolve("5001") as EpisodeLinkResult.Found).episode.id)
    }

    @Test
    fun `only 400, 404 and 410 mean the API doesn't list the episode`() {
        listOf(400, 404, 410).forEach { assertEquals(EpisodeLookup.Missing, it.toLookupFailure(), "HTTP $it") }
        listOf(401, 403, 408, 429, 500, 502, 503, 504).forEach { assertEquals(EpisodeLookup.Failed, it.toLookupFailure(), "HTTP $it") }
    }

    private suspend fun resolve(id: String, showId: String? = null) = resolver.resolve(id, showId, readFeed = false)

    private fun needsFeed() = EpisodeLookup.NeedsFeed(V2EpisodeReply.NeedsFeed(show = show, fetchUrl = "https://feeds.example/show.xml", fetchVia = "direct"))

    private fun episode(id: String, podcastId: String? = "920666", recipeId: Long? = null, catalogKey: String? = null) =
        Episode(id = id, title = "Episode $id", description = "", audioUrl = "https://cdn.example/$id.mp3", podcastId = podcastId, recipeId = recipeId, catalogKey = catalogKey)

    private fun feedOf(vararg guids: String): ParsedRssFeed {
        val rows = guids.mapIndexed { index, guid -> row(-(index + 1L), guid) }
        return ParsedRssFeed(
            title = "Feed show", author = "Host", description = null, imageUrl = "https://img.example/show.jpg", genre = "News",
            podcastType = "episodic", podcastGuid = null, declaredUpdatedAt = null, episodes = rows,
        )
    }

    private fun row(id: Long, guid: String?, audio: String = "https://cdn.example/${guid ?: id}.mp3") = RssEpisodeEntity(
        episodeId = id.toString(),
        podcastId = "920666",
        guid = guid,
        title = "Episode ${guid ?: id}",
        description = "",
        audioUrl = audio,
        imageUrl = null,
        duration = 60,
        publishedDate = 1_700_000_000L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = "audio/mpeg",
    )
}
