package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.ListeningDaySyncDto
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueRemovalSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncEpisodeIdentityFields
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BoxLoreApiSyncContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BoxLoreApi

    companion object {
        private const val APP_KEY = "test-app-key-123"
        private const val AUTH_HEADER = "Bearer test-firebase-token-abc"
        private const val RECIPE = 4_503_599_627_371_001L

        private fun okPush() = MockResponse().setResponseCode(200).setBody("""{"status":"ok","syncedAt":1}""")

        /**
         * A pull answer in the shape the sync API writes it: column aliases, numbers for numeric
         * columns, null recipe fields for rows older builds pushed, and rich removals because the
         * request asked for them.
         */
        private val RECORDED_PULL = """
            {"subscriptions":[{"podcastId":"77","isSubscribed":true,"subscribedAt":1000,"unsubscribedAt":0,"customGenre":null,
              "autoDownloadEnabled":false,"notificationsEnabled":false,"feedUrl":"https://example.com/feed.xml","updatedAt":1000}],
             "history":[
              {"episodeId":"4503599627371001","podcastId":"77","episodeTitle":"One","episodeImageUrl":null,"podcastImageUrl":null,
               "podcastName":"Show","episodeAudioUrl":"https://example.com/1.mp3","progressMs":5,"durationMs":100,"isCompleted":false,
               "isLiked":false,"likedAt":0,"lastPlayedAt":2000,"updatedAt":2000,"recipeId":4503599627371001,"catalogKey":"guid-1"},
              {"episodeId":"61842097688","podcastId":"77","episodeTitle":"Two","episodeImageUrl":null,"podcastImageUrl":null,
               "podcastName":"Show","episodeAudioUrl":null,"progressMs":9,"durationMs":0,"isCompleted":true,"isLiked":true,
               "likedAt":3000,"lastPlayedAt":3000,"updatedAt":3000,"recipeId":null,"catalogKey":null}],
             "queue":{"items":[{"episodeId":"555","podcastId":"77","title":null,"podcastTitle":null,"imageUrl":null,
               "podcastImageUrl":null,"audioUrl":null,"duration":null,"pubDate":null,"description":null,"position":0,"addedAt":1,
               "contextType":"MANUAL","contextSourceId":null,"updatedAt":30,"recipeId":null,"catalogKey":null}],
              "queueUpdatedAt":30,"queueSequence":3,"lastModifiedDeviceId":null,
              "recentRemovedEpisodeIds":["4503599627371001","61842097688"],"recentRemovedEpisodes":[{"id":"4503599627371001","recipeId":4503599627371001,"show":"77","catalogKey":"guid-1"}]},
             "syncedAt":9000}
        """.trimIndent()

        private const val RECORDED_RICH_FIELD =
            ""","recentRemovedEpisodes":[{"id":"4503599627371001","recipeId":4503599627371001,"show":"77","catalogKey":"guid-1"}]"""
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        api = NetworkModule.createBoxLoreApi(server.url("/").toString(), client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `syncPush sends payload and headers, and decodes SyncPushResponse`() {
        val jsonResponse = """
            {
              "status": "ok",
              "syncedAt": 1711200000000
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val pushRequest = SyncPushRequest(
            subscriptions = listOf(
                UserSubscriptionSyncDto(
                    podcastId = "920666",
                    isSubscribed = true,
                    subscribedAt = 1711100000000L,
                    updatedAt = 1711150000000L,
                )
            ),
            history = listOf(
                ListeningHistorySyncDto(
                    episodeId = "ep_1",
                    podcastId = "920666",
                    progressMs = 15000L,
                    durationMs = 60000L,
                    isCompleted = false,
                    updatedAt = 1711160000000L,
                )
            ),
            queue = QueueSyncDto(
                items = listOf(
                    QueueItemSyncDto(
                        episodeId = "ep_1",
                        podcastId = "920666",
                        position = 0,
                        addedAt = 1711100000000L,
                        updatedAt = 1711150000000L,
                    )
                ),
                queueUpdatedAt = 1711150000000L,
                queueSequence = 1L,
            ),
            clientTimestamp = 1711190000000L,
        )

        val response = api.syncPush(
            publicKey = APP_KEY,
            authorization = AUTH_HEADER,
            request = pushRequest,
        ).execute()

        assertTrue(response.isSuccessful)
        val body = response.body()
        assertNotNull(body)
        assertEquals("ok", body?.status)
        assertEquals(1711200000000L, body?.syncedAt)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/user/sync/push", recorded.path)
        assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
        assertEquals(AUTH_HEADER, recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("920666"))
    }

    @Test
    fun `syncPull sends since parameter and decodes SyncPullResponse`() {
        val jsonResponse = """
            {
              "subscriptions": [
                {
                  "podcastId": "920666",
                  "isSubscribed": true,
                  "subscribedAt": 1711100000000,
                  "unsubscribedAt": 0,
                  "customGenre": null,
                  "autoDownloadEnabled": false,
                  "notificationsEnabled": true,
                  "updatedAt": 1711150000000
                }
              ],
              "history": [
                {
                  "episodeId": "ep_1",
                  "podcastId": "920666",
                  "progressMs": 45000,
                  "durationMs": 120000,
                  "isCompleted": false,
                  "isLiked": true,
                  "likedAt": 1711140000000,
                  "lastPlayedAt": 1711150000000,
                  "updatedAt": 1711160000000
                }
              ],
              "queue": {
                "items": [
                  {
                    "episodeId": "ep_1",
                    "podcastId": "920666",
                    "position": 0,
                    "addedAt": 1711100000000,
                    "contextType": "MANUAL",
                    "contextSourceId": null,
                    "updatedAt": 1711150000000
                  }
                ],
                "queueUpdatedAt": 1711150000000,
                "queueSequence": 2,
                "lastModifiedDeviceId": "phone-1",
                "recentRemovedEpisodeIds": ["ep_old"]
              },
              "syncedAt": 1711200000000
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val response = api.syncPull(
            publicKey = APP_KEY,
            authorization = AUTH_HEADER,
            request = SyncPullRequest(since = 1711000000000L),
        ).execute()

        assertTrue(response.isSuccessful)
        val body = response.body()
        assertNotNull(body)
        assertEquals(1, body?.subscriptions?.size)
        assertEquals("920666", body?.subscriptions?.first()?.podcastId)
        assertEquals(1, body?.history?.size)
        assertEquals("ep_1", body?.history?.first()?.episodeId)
        assertEquals(45000L, body?.history?.first()?.progressMs)
        assertEquals(1, body?.queue?.items?.size)
        assertEquals(2L, body?.queue?.queueSequence)
        assertEquals(listOf("ep_old"), body?.queue?.recentRemovedEpisodeIds)
        assertEquals(1711200000000L, body?.syncedAt)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/user/sync/pull", recorded.path)
        assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
        assertEquals(AUTH_HEADER, recorded.getHeader("Authorization"))
    }

    @Test
    fun `deleteSyncAccount sends DELETE and decodes response`() {
        val jsonResponse = """
            {
              "status": "ok"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val response = api.deleteSyncAccount(
            publicKey = APP_KEY,
            authorization = AUTH_HEADER,
        ).execute()

        assertTrue(response.isSuccessful)
        val body = response.body()
        assertNotNull(body)
        assertEquals("ok", body?.status)

        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/user/sync/account", recorded.path)
        assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
        assertEquals(AUTH_HEADER, recorded.getHeader("Authorization"))
    }

    @Test
    fun `syncPush omits Authorization header when authorization parameter is null`() {
        val jsonResponse = """{"status": "ok", "syncedAt": 1711200000000}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val response = api.syncPush(
            publicKey = APP_KEY,
            authorization = null,
            request = SyncPushRequest(),
        ).execute()

        assertTrue(response.isSuccessful)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/user/sync/push", recorded.path)
        assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
        org.junit.jupiter.api.Assertions.assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `syncPush sends each row's episode identity and the rich removals`() {
        server.enqueue(okPush())
        val request = SyncPushRequest(
            history = listOf(
                ListeningHistorySyncDto(episodeId = RECIPE.toString(), podcastId = "77", updatedAt = 10L, recipeId = RECIPE, catalogKey = "guid-1"),
                // An older-build ID with no known identity is sent exactly as before.
                ListeningHistorySyncDto(episodeId = "61842097688", podcastId = "77", updatedAt = 10L),
            ),
            queue = QueueSyncDto(
                items = listOf(QueueItemSyncDto(episodeId = "-42", podcastId = "77", position = 0, catalogKey = "guid-2")),
                queueUpdatedAt = 20L,
                queueSequence = 2L,
                recentRemovedEpisodeIds = listOf(RECIPE.toString()),
                recentRemovedEpisodes = listOf(QueueRemovalSyncDto(id = RECIPE.toString(), recipeId = RECIPE, show = "77", catalogKey = "guid-1")),
            ),
        )

        api.syncPush(APP_KEY, AUTH_HEADER, request).execute()

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val history = body.getValue("history").jsonArray.map { it.jsonObject }
        assertEquals(RECIPE, history[0].getValue("recipeId").jsonPrimitive.long)
        assertEquals("guid-1", history[0].getValue("catalogKey").jsonPrimitive.content)
        assertFalse("recipeId" in history[1])
        assertFalse("catalogKey" in history[1])
        val queue = body.getValue("queue").jsonObject
        val item = queue.getValue("items").jsonArray.single().jsonObject
        assertFalse("recipeId" in item)
        assertEquals("guid-2", item.getValue("catalogKey").jsonPrimitive.content)
        val removal = queue.getValue("recentRemovedEpisodes").jsonArray.single().jsonObject
        assertEquals(RECIPE.toString(), removal.getValue("id").jsonPrimitive.content)
        assertEquals(RECIPE, removal.getValue("recipeId").jsonPrimitive.long)
        assertEquals("77", removal.getValue("show").jsonPrimitive.content)
        assertEquals("guid-1", removal.getValue("catalogKey").jsonPrimitive.content)
        assertEquals(listOf(RECIPE.toString()), queue.getValue("recentRemovedEpisodeIds").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `syncPush says its days carry their release date time, and a zero is left out for the server to read as 0`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","syncedAt":1,"listeningAccepted":true,"listeningTotals":true,"listeningReleaseKnown":true}"""))
        val request = SyncPushRequest(
            listeningDays = listOf(
                ListeningDaySyncDto(deviceId = "d", localDay = 1L, episodeId = "e1", podcastId = "77", consumedMs = 60_000L, newReleaseMs = 20_000L, releaseKnownMs = 40_000L),
                ListeningDaySyncDto(deviceId = "d", localDay = 2L, episodeId = "e2", podcastId = "77", consumedMs = 60_000L),
            ),
            listeningTotals = true,
            listeningReleaseKnown = true,
        )

        val answer = api.syncPush(APP_KEY, AUTH_HEADER, request).execute().body()!!

        assertTrue(answer.listeningReleaseKnown)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertTrue(body.getValue("listeningReleaseKnown").jsonPrimitive.boolean)
        val days = body.getValue("listeningDays").jsonArray.map { it.jsonObject }
        assertEquals(40_000L, days[0].getValue("releaseKnownMs").jsonPrimitive.long)
        // Defaults stay out of the JSON, as for every total; a push with the flag has the server store a missing one as 0.
        assertFalse("releaseKnownMs" in days[1])
    }

    @Test
    fun `an older server's answers read as not storing the release date time`() {
        server.enqueue(okPush())
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"subscriptions":[],"history":[],"listeningDays":[{"deviceId":"d","localDay":1,"episodeId":"e1","podcastId":"77","consumedMs":5,"newReleaseMs":5}],"listeningTotals":true,"syncedAt":9}""",
            ),
        )

        assertFalse(api.syncPush(APP_KEY, AUTH_HEADER, SyncPushRequest()).execute().body()!!.listeningReleaseKnown)
        val pulled = api.syncPull(APP_KEY, AUTH_HEADER, SyncPullRequest(since = 0L)).execute().body()!!
        assertFalse(pulled.listeningReleaseKnown)
        assertEquals(0L, pulled.listeningDays!!.single().releaseKnownMs)
    }

    @Test
    fun `syncPush sends an empty rich removal list and leaves it out only when unset`() {
        repeat(2) { server.enqueue(okPush()) }

        api.syncPush(APP_KEY, AUTH_HEADER, SyncPushRequest(queue = QueueSyncDto(queueSequence = 1L, recentRemovedEpisodes = emptyList()))).execute()
        api.syncPush(APP_KEY, AUTH_HEADER, SyncPushRequest(queue = QueueSyncDto(queueSequence = 1L))).execute()

        // The server replaces its rich list with the one sent, and keeps it only when the field is absent.
        val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("queue").jsonObject
        assertEquals(JsonArray(emptyList()), sent.getValue("recentRemovedEpisodes"))
        val unset = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("queue").jsonObject
        assertFalse("recentRemovedEpisodes" in unset)
    }

    @Test
    fun `syncPull asks for rich removals explicitly and decodes the recorded answer`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(RECORDED_PULL))

        val body = api.syncPull(APP_KEY, AUTH_HEADER, SyncPullRequest(since = 1234L, richRemovals = true)).execute().body()!!

        val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8())
        assertEquals(JsonObject(mapOf("since" to JsonPrimitive(1234L), "richRemovals" to JsonPrimitive(true))), sent)
        assertEquals(RECIPE, body.history[0].recipeId)
        assertEquals("guid-1", body.history[0].catalogKey)
        assertNull(body.history[1].recipeId)
        assertNull(body.history[1].catalogKey)
        val queue = body.queue!!
        assertNull(queue.items.single().recipeId)
        assertEquals(listOf(RECIPE.toString(), "61842097688"), queue.recentRemovedEpisodeIds)
        assertEquals(listOf(QueueRemovalSyncDto(id = RECIPE.toString(), recipeId = RECIPE, show = "77", catalogKey = "guid-1")), queue.recentRemovedEpisodes)
    }

    @Test
    fun `a pull answer without rich removals reads as not sent`() {
        assertTrue(RECORDED_RICH_FIELD in RECORDED_PULL)
        server.enqueue(MockResponse().setResponseCode(200).setBody(RECORDED_PULL.replace(RECORDED_RICH_FIELD, "")))

        val body = api.syncPull(APP_KEY, AUTH_HEADER, SyncPullRequest(since = 0L)).execute().body()!!

        // A request with the defaults sends neither field; the server reads since 0 and no rich removals.
        assertEquals(JsonObject(emptyMap()), Json.parseToJsonElement(server.takeRequest().body.readUtf8()))
        val queue = body.queue!!
        assertNull(queue.recentRemovedEpisodes)
        assertEquals(2, queue.recentRemovedEpisodeIds.size)
    }

    @Test
    fun `identity fields read from the wire are range checked`() {
        assertEquals(RECIPE, SyncEpisodeIdentityFields.recipeIdOrNull(RECIPE))
        assertEquals((1L shl 53) - 1, SyncEpisodeIdentityFields.recipeIdOrNull((1L shl 53) - 1))
        assertNull(SyncEpisodeIdentityFields.recipeIdOrNull((1L shl 52) - 1))
        assertNull(SyncEpisodeIdentityFields.recipeIdOrNull(1L shl 53))
        assertNull(SyncEpisodeIdentityFields.recipeIdOrNull(61842097688L))
        assertNull(SyncEpisodeIdentityFields.recipeIdOrNull(null))
        assertEquals("guid", SyncEpisodeIdentityFields.catalogKeyOrNull("guid"))
        assertNull(SyncEpisodeIdentityFields.catalogKeyOrNull(" "))
        assertNull(SyncEpisodeIdentityFields.catalogKeyOrNull("k".repeat(SyncEpisodeIdentityFields.MAX_CATALOG_KEY_LENGTH + 1)))
    }
}
