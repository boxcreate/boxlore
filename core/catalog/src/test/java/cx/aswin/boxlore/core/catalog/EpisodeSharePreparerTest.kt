package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.model.EpisodeShareTarget
import cx.aswin.boxlore.core.model.EpisodeShareTargetSource
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.V2EpisodeRegisterRequest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Episode share links (own-IDs plan section 9, links): the recipe ID and show, registration with a
 * short wait, the link going out offline, and the background retry.
 */
class EpisodeSharePreparerTest {
    private val show = 920666L
    private val recipe = EpisodeRecipeId.recipeId(show, "guid-1")
    private val registered = mutableListOf<V2EpisodeRegisterRequest>()
    private val retries = mutableListOf<V2EpisodeRegisterRequest>()
    private var outcome = EpisodeShareRegistrationOutcome.DONE
    private var hang = false
    private val trusted = mutableMapOf<String, EpisodeAliasKey>()
    private lateinit var server: MockWebServer

    private val preparer = EpisodeSharePreparer(
        aliases = object : EpisodeAliasReads {
            override suspend fun trustedKeyOf(episodeId: String) = trusted[episodeId]
        },
        register = { request ->
            registered += request
            if (hang) awaitCancellation()
            outcome
        },
        scheduleRetry = { retries += it },
        timeoutMs = 200L,
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `an episode with a trusted alias is shared by its recipe ID and show, after registering it`() = runBlocking {
        trusted["-9"] = EpisodeAliasKey("920666", "guid-1", recipe, listOf("-9"))

        val target = preparer.targetFor(episode("-9", podcastId = "920666"))

        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), target)
        assertEquals(listOf(V2EpisodeRegisterRequest(show = show, guid = "guid-1", enclosureUrl = "https://cdn.example/-9.mp3")), registered)
        assertTrue(retries.isEmpty())
    }

    @Test
    fun `an episode of an RSS show linked to a Podcast Index show is shared under that show`() = runBlocking {
        trusted["-9"] = EpisodeAliasKey("920666", "guid-1", recipe, listOf("5001", "-9"))

        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), preparer.targetFor(episode("-9", podcastId = "rss:abc")))
    }

    @Test
    fun `a key equal to the audio address is registered as the enclosure`() = runBlocking {
        val audio = "https://cdn.example/a.mp3"
        trusted["-9"] = EpisodeAliasKey("920666", audio, EpisodeRecipeId.recipeId(show, audio), listOf("-9"))

        preparer.targetFor(episode("-9", podcastId = "920666", audioUrl = " $audio"))

        assertEquals(listOf(V2EpisodeRegisterRequest(show = show, enclosureUrl = audio)), registered)
    }

    @Test
    fun `offline or slow, the link still goes out at once and registration is retried in the background`() = runBlocking {
        trusted["-9"] = EpisodeAliasKey("920666", "guid-1", recipe, listOf("-9"))
        outcome = EpisodeShareRegistrationOutcome.RETRY
        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), preparer.targetFor(episode("-9", podcastId = "920666")))
        assertEquals(1, retries.size)

        hang = true
        val started = System.nanoTime()
        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), preparer.targetFor(episode("-9", podcastId = "920666")))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "the share waits only the short timeout")
        assertEquals(2, retries.size)
        assertEquals(V2EpisodeRegisterRequest(show = show, guid = "guid-1", enclosureUrl = "https://cdn.example/-9.mp3"), retries.last())
    }

    @Test
    fun `a request the API refuses is not retried`() = runBlocking {
        trusted["-9"] = EpisodeAliasKey("920666", "guid-1", recipe, listOf("-9"))
        outcome = EpisodeShareRegistrationOutcome.DROPPED

        preparer.targetFor(episode("-9", podcastId = "920666"))

        assertTrue(retries.isEmpty())
    }

    @Test
    fun `an RSS show keeps today's link, and an episode without a key isn't registered`() = runBlocking {
        assertEquals(EpisodeShareTarget.Episode("-77", null), preparer.targetFor(episode("-77", podcastId = "rss:abc")))
        assertEquals(EpisodeShareTarget.Episode("5001", "920666"), preparer.targetFor(episode("5001", podcastId = "920666")))
        assertEquals(EpisodeShareTarget.Show("920666"), preparer.targetFor(episode("-5", podcastId = "920666")))
        assertTrue(registered.isEmpty())
        assertTrue(retries.isEmpty())
    }

    @Test
    fun `a phone-only ID with a known key is shared by the key's recipe ID in the show's namespace`() = runBlocking {
        val target = preparer.targetFor(episode("-5", podcastId = "920666", catalogKey = "guid-1"))

        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), target)
        assertEquals(listOf(V2EpisodeRegisterRequest(show = show, guid = "guid-1", enclosureUrl = "https://cdn.example/-5.mp3")), registered)

        val renamed = EpisodeSharePreparer(
            aliases = object : EpisodeAliasReads {
                override suspend fun namespaceOf(podcastId: String) = 777L
            },
            register = { EpisodeShareRegistrationOutcome.DONE },
            scheduleRetry = {},
        )
        assertEquals(
            EpisodeShareTarget.Episode(EpisodeRecipeId.forNamespace(777L, "guid-1").toString(), "920666"),
            renamed.targetFor(episode("-5", podcastId = "920666", catalogKey = "guid-1")),
        )
        // A key that isn't trimmed isn't a catalog key: the link opens the show.
        assertEquals(EpisodeShareTarget.Show("920666"), preparer.targetFor(episode("-5", podcastId = "920666", catalogKey = " guid-1")))
    }

    @Test
    fun `an episode that carries its own recipe ID and key is registered from them`() = runBlocking {
        val target = preparer.targetFor(episode("5001", podcastId = "920666", recipeId = recipe, catalogKey = "guid-1"))

        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), target)
        assertEquals(listOf(V2EpisodeRegisterRequest(show = show, guid = "guid-1", enclosureUrl = "https://cdn.example/5001.mp3")), registered)
    }

    @Test
    fun `the default source needs no aliases`() = runBlocking {
        assertEquals(
            EpisodeShareTarget.Episode(recipe.toString(), "920666"),
            EpisodeShareTargetSource.FROM_EPISODE.targetFor(episode("-5", podcastId = "920666", recipeId = recipe)),
        )
    }

    @Test
    fun `registration sends the recorded body and reads the answer`() = runBlocking {
        val api = NetworkModule.createBoxLoreApiV2(server.url("/").toString(), OkHttpClient())
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"recipeId":$recipe,"servedId":null,"confirmed":true,"show":920666,"catalogKey":"guid-1"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"unknown_show"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"session_required"}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"register_limit"}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"register_unavailable"}"""))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val request = V2EpisodeRegisterRequest(show = show, guid = "guid-1")

        assertEquals(EpisodeShareRegistrationOutcome.DONE, EpisodeShareRegistrar.registerOnce(api, request))
        val recorded = server.takeRequest()
        assertEquals("/v2/episodes/register", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("""{"show":920666,"guid":"guid-1"}""", recorded.body.readUtf8())
        assertEquals(EpisodeShareRegistrationOutcome.DROPPED, EpisodeShareRegistrar.registerOnce(api, request))
        repeat(4) { assertEquals(EpisodeShareRegistrationOutcome.RETRY, EpisodeShareRegistrar.registerOnce(api, request)) }
    }

    @Test
    fun `a register body is built only for a Podcast Index show and a usable key`() {
        assertEquals(
            V2EpisodeRegisterRequest(show = show, guid = "guid-1", enclosureUrl = "https://cdn.example/a.mp3"),
            EpisodeShareRegistrar.requestFor("920666", " guid-1\n", " https://cdn.example/a.mp3"),
        )
        assertEquals(V2EpisodeRegisterRequest(show = show, guid = "guid-1"), EpisodeShareRegistrar.requestFor("920666", "guid-1", null))
        // An audio address over the route's limit is left out; the GUID still registers the episode.
        val longAudio = "https://cdn.example/" + "a".repeat(V2EpisodeRegisterRequest.MAX_CATALOG_KEY_LENGTH)
        assertEquals(V2EpisodeRegisterRequest(show = show, guid = "guid-1"), EpisodeShareRegistrar.requestFor("920666", "guid-1", longAudio))
        assertNull(EpisodeShareRegistrar.requestFor("rss:abc", "guid-1", null))
        assertNull(EpisodeShareRegistrar.requestFor(recipe.toString(), "guid-1", null))
        assertNull(EpisodeShareRegistrar.requestFor("920666", "  ", null))
        assertNull(EpisodeShareRegistrar.requestFor("920666", "x".repeat(V2EpisodeRegisterRequest.MAX_CATALOG_KEY_LENGTH + 1), null))
    }

    private fun episode(id: String, podcastId: String?, audioUrl: String = "https://cdn.example/$id.mp3", recipeId: Long? = null, catalogKey: String? = null) =
        Episode(id = id, title = "Episode", description = "", audioUrl = audioUrl, podcastId = podcastId, recipeId = recipeId, catalogKey = catalogKey)
}
