package cx.aswin.boxlore.core.network.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Range checks and request validation for the `/v2` wire models. */
class V2ModelsTest {
    @Test
    fun `recipe range is exactly 2^52 to 2^53 - 1`() {
        assertEquals(4_503_599_627_370_496L, V2EpisodeIds.RECIPE_MIN)
        assertEquals(9_007_199_254_740_991L, V2EpisodeIds.RECIPE_MAX)
        assertFalse(V2EpisodeIds.isRecipeId(V2EpisodeIds.RECIPE_MIN - 1))
        assertTrue(V2EpisodeIds.isRecipeId(V2EpisodeIds.RECIPE_MIN))
        assertTrue(V2EpisodeIds.isRecipeId(V2EpisodeIds.RECIPE_MAX))
        assertFalse(V2EpisodeIds.isRecipeId(V2EpisodeIds.RECIPE_MAX + 1))
        assertFalse(V2EpisodeIds.isRecipeId(-4_503_599_627_370_496L))
        assertFalse(V2EpisodeIds.isRecipeId(null))
    }

    @Test
    fun `show IDs parse strictly from stored strings`() {
        assertEquals(920666L, V2EpisodeIds.parseShowId("920666"))
        assertEquals(9_007_199_254_740_991L, V2EpisodeIds.parseShowId("9007199254740991"))
        assertNull(V2EpisodeIds.parseShowId("9007199254740992"))
        assertNull(V2EpisodeIds.parseShowId("0"))
        assertNull(V2EpisodeIds.parseShowId("0920666"))
        assertNull(V2EpisodeIds.parseShowId("+920666"))
        assertNull(V2EpisodeIds.parseShowId("-920666"))
        assertNull(V2EpisodeIds.parseShowId(" 920666"))
        assertNull(V2EpisodeIds.parseShowId("rss:3f2a"))
        assertNull(V2EpisodeIds.parseShowId(""))
        assertNull(V2EpisodeIds.parseShowId(null))
    }

    @Test
    fun `only positive safe episode IDs are looked up, never local rss IDs`() {
        assertTrue(V2EpisodeIds.isLookupEpisodeId("7777"))
        assertTrue(V2EpisodeIds.isLookupEpisodeId("8825297254050678"))
        assertFalse(V2EpisodeIds.isLookupEpisodeId("-6917529027641081857"))
        assertFalse(V2EpisodeIds.isLookupEpisodeId("9007199254740992"))
        assertFalse(V2EpisodeIds.isLookupEpisodeId("briefing_us"))
        assertFalse(V2EpisodeIds.isLookupEpisodeId(null))
    }

    @Test
    fun `v2 identity needs a recipe ID and a show`() {
        val item = EpisodeItem(id = V2EpisodeIds.RECIPE_MIN, title = "T", feedId = 920666L, catalogKey = "", servedId = 0L)
        val identity = requireNotNull(item.v2Identity())
        assertEquals(V2EpisodeIds.RECIPE_MIN, identity.recipeId)
        assertEquals(920666L, identity.showId)
        assertNull(identity.catalogKey)
        assertNull(identity.servedId)

        assertNull(item.copy(id = 7777L).v2Identity())
        assertNull(item.copy(feedId = null).v2Identity())
        assertNull(item.copy(feedId = 0L).v2Identity())
    }

    @Test
    fun `shows feeds requests hold 1 to 500 show IDs`() {
        assertEquals(500, V2ShowsFeedsRequest(List(500) { it + 1L }).ids.size)
        assertThrows(IllegalArgumentException::class.java) { V2ShowsFeedsRequest(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { V2ShowsFeedsRequest(List(501) { it + 1L }) }
        assertThrows(IllegalArgumentException::class.java) { V2ShowsFeedsRequest(listOf(0L)) }
        assertThrows(IllegalArgumentException::class.java) { V2ShowsFeedsRequest(listOf(V2EpisodeIds.SHOW_ID_MAX + 1)) }
    }

    @Test
    fun `feed report statuses match what the route accepts`() {
        assertEquals("http_404", V2FeedReportStatus.http(404))
        assertEquals("http_100", V2FeedReportStatus.http(100))
        assertThrows(IllegalArgumentException::class.java) { V2FeedReportStatus.http(99) }
        assertThrows(IllegalArgumentException::class.java) { V2FeedReportStatus.http(600) }
        for (status in listOf("timeout", "dns", "tls", "parse", "network", "other", "http_503")) {
            assertTrue(V2FeedReportStatus.isValid(status), status)
        }
        for (status in listOf("http_600", "http_42", "HTTP_404", "unknown", "")) {
            assertFalse(V2FeedReportStatus.isValid(status), status)
        }
        assertThrows(IllegalArgumentException::class.java) { V2FeedReportRequest(1L, "broken") }
        assertThrows(IllegalArgumentException::class.java) { V2FeedReportRequest(0L, V2FeedReportStatus.TIMEOUT) }
    }

    @Test
    fun `register requests need a show below 2^52 and a key`() {
        V2EpisodeRegisterRequest(show = 920666L, enclosureUrl = "https://cdn.example.com/a.mp3")
        assertThrows(IllegalArgumentException::class.java) { V2EpisodeRegisterRequest(show = 920666L) }
        assertThrows(IllegalArgumentException::class.java) { V2EpisodeRegisterRequest(show = 920666L, guid = " ", enclosureUrl = "") }
        assertThrows(IllegalArgumentException::class.java) { V2EpisodeRegisterRequest(show = V2EpisodeIds.RECIPE_MIN, guid = "g") }
        assertThrows(IllegalArgumentException::class.java) { V2EpisodeRegisterRequest(show = 0L, guid = "g") }
    }

    @Test
    fun `register requests mirror the route's 2048-character catalog key limit`() {
        val limit = V2EpisodeRegisterRequest.MAX_CATALOG_KEY_LENGTH
        assertEquals(2048, limit)
        V2EpisodeRegisterRequest(show = 1L, guid = "g".repeat(limit))
        V2EpisodeRegisterRequest(show = 1L, guid = "  " + "g".repeat(limit) + "  ")
        V2EpisodeRegisterRequest(show = 1L, enclosureUrl = "https://cdn.example.com/" + "a".repeat(limit - 24))
        assertThrows(IllegalArgumentException::class.java) { V2EpisodeRegisterRequest(show = 1L, guid = "g".repeat(limit + 1)) }
        assertThrows(IllegalArgumentException::class.java) {
            V2EpisodeRegisterRequest(show = 1L, guid = " ", enclosureUrl = "https://cdn.example.com/" + "a".repeat(limit))
        }
        // The GUID is the key whenever it is present, so a long enclosure URL beside it is not the key.
        V2EpisodeRegisterRequest(show = 1L, guid = "g", enclosureUrl = "https://cdn.example.com/" + "a".repeat(limit))
    }

    @Test
    fun `episode routes take shows below 2^52 only`() {
        assertTrue(V2EpisodeIds.isEpisodeShowId(1L))
        assertTrue(V2EpisodeIds.isEpisodeShowId(V2EpisodeIds.RECIPE_MIN - 1))
        assertFalse(V2EpisodeIds.isEpisodeShowId(V2EpisodeIds.RECIPE_MIN))
        assertFalse(V2EpisodeIds.isEpisodeShowId(V2EpisodeIds.SHOW_ID_MAX))
        assertFalse(V2EpisodeIds.isEpisodeShowId(0L))
        assertFalse(V2EpisodeIds.isEpisodeShowId(null))
        // Still a valid show ID for the show routes.
        assertTrue(V2EpisodeIds.isShowId(V2EpisodeIds.RECIPE_MIN))
    }

    @Test
    fun `episode list queries mirror the route's bounds`() {
        assertTrue(V2EpisodesPaging.isValid(920666L, V2EpisodesPaging.DEFAULT_LIMIT, 0))
        assertTrue(V2EpisodesPaging.isValid(920666L, 1, 10_000))
        assertTrue(V2EpisodesPaging.isValid(920666L, 1000, 0))
        assertFalse(V2EpisodesPaging.isValid(920666L, 0, 0))
        assertFalse(V2EpisodesPaging.isValid(920666L, 1001, 0))
        assertFalse(V2EpisodesPaging.isValid(920666L, 100, -1))
        assertFalse(V2EpisodesPaging.isValid(920666L, 100, 10_001))
        assertFalse(V2EpisodesPaging.isValid(V2EpisodeIds.RECIPE_MIN, 100, 0))
        assertFalse(V2EpisodesPaging.isValid(0L, 100, 0))
    }

    @Test
    fun `register answers range-check the recipe ID`() {
        assertEquals(V2EpisodeIds.RECIPE_MAX, V2EpisodeRegisterResponse(recipeId = V2EpisodeIds.RECIPE_MAX).validRecipeId)
        assertNull(V2EpisodeRegisterResponse(recipeId = 7777L).validRecipeId)
        assertNull(V2EpisodeRegisterResponse(recipeId = null).validRecipeId)
    }

    @Test
    fun `the read target follows fetchVia and never hands out the transport URL`() {
        val direct = V2ShowAddress(id = 1L, fetchUrl = "https://a", fetchVia = V2FetchVia.DIRECT, transportUrl = null)
        val proxied = V2ShowAddress(id = 1L, fetchUrl = "http://a", fetchVia = V2FetchVia.PROXY, transportUrl = "https://api/v2/feed/1")
        assertEquals(V2FeedReadTarget.Direct("https://a"), direct.readTarget(1L))
        assertEquals(V2FeedReadTarget.Direct("https://a"), direct.copy(fetchVia = null).readTarget(1L))
        // A proxied show can only be read through the API client, by show ID.
        assertEquals(V2FeedReadTarget.ViaApi(1L), proxied.readTarget(1L))
        assertNull(proxied.copy(transportUrl = null).readTarget(1L))
        assertNull(direct.copy(fetchUrl = null).readTarget(1L))
        assertThrows(IllegalArgumentException::class.java) { direct.readTarget(0L) }
        assertNull(V2EpisodeReply.NeedsFeed(show = 1L).readTarget(1L))
        assertNull(V2EpisodeReply.NeedsFeed(show = 1L).feedSource)
    }
}
