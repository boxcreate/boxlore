package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BoxLoreApiSyncContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BoxLoreApi

    companion object {
        private const val APP_KEY = "test-app-key-123"
        private const val AUTH_HEADER = "Bearer test-firebase-token-abc"
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
}
