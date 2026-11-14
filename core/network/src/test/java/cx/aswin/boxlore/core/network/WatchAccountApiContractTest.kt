package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.network.model.WatchTokenRequest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** MockWebServer contract tests for [WatchAccountApi]: methods, paths, what is sent and how answers decode. */
class WatchAccountApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: WatchAccountApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = NetworkModule.createWatchAccountApi(server.url("/api/").toString(), client())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `the phone asks for a code under its own authorization`() {
        enqueueJson("""{"code":"code-1","expiresInSeconds":120,"later":"ignored"}""")

        val answer = api.createCode("Bearer id-token").execute()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/watch/code", request.path)
        assertEquals("Bearer id-token", request.getHeader("Authorization"))
        assertEquals("code-1", answer.body()?.code)
        assertEquals(120L, answer.body()?.expiresInSeconds)
    }

    @Test
    fun `the watch trades the code for its token, sending the code in the body and no authorization`() {
        enqueueJson("""{"token":"watch-token","expiresAt":1700000000000}""")

        val answer = api.redeem(WatchTokenRequest("code-1")).execute()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/watch/token", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("""{"code":"code-1"}""", request.body.readUtf8())
        assertEquals("watch-token", answer.body()?.token)
        assertEquals(1_700_000_000_000L, answer.body()?.expiresAt)
    }

    @Test
    fun `a token answer without an expiry still decodes`() {
        enqueueJson("""{"token":"watch-token"}""")

        assertEquals(0L, api.redeem(WatchTokenRequest("code-1")).execute().body()?.expiresAt)
    }

    @Test
    fun `the watch gives its token up under that token`() {
        enqueueJson("{}")

        val answer = api.revoke("Bearer watch-token").execute()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/watch/revoke", request.path)
        assertEquals("Bearer watch-token", request.getHeader("Authorization"))
        assertTrue(answer.isSuccessful)
    }

    @Test
    fun `the watch's push carries its rows under its token`() {
        enqueueJson("""{"status":"ok","syncedAt":900}""")
        val push =
            SyncPushRequest(
                subscriptions = listOf(UserSubscriptionSyncDto(podcastId = "7", subscribedAt = 5L, updatedAt = 5L)),
                history = listOf(ListeningHistorySyncDto(episodeId = "a", podcastId = "7", progressMs = 60_000L, updatedAt = 9L)),
                clientTimestamp = 11L,
            )

        val answer = api.syncPush("Bearer watch-token", push).execute()

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/api/user/sync/push", request.path)
        assertEquals("Bearer watch-token", request.getHeader("Authorization"))
        assertTrue(body.contains(""""podcastId":"7""""), body)
        assertTrue(body.contains(""""episodeId":"a""""), body)
        assertTrue(body.contains(""""progressMs":60000"""), body)
        assertEquals(900L, answer.body()?.syncedAt)
    }

    @Test
    fun `the watch's pull asks from where it left off and decodes follows and listening`() {
        enqueueJson(
            """
            {"subscriptions":[{"podcastId":"7","isSubscribed":false,"subscribedAt":1,"unsubscribedAt":8,"updatedAt":8}],
             "history":[{"episodeId":"a","podcastId":"7","progressMs":120000,"isCompleted":true,"lastPlayedAt":6,"updatedAt":6}],
             "syncedAt":950}
            """.trimIndent(),
        )

        val answer = api.syncPull("Bearer watch-token", SyncPullRequest(since = 40L)).execute().body()!!

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/user/sync/pull", request.path)
        assertEquals("Bearer watch-token", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains(""""since":40"""))
        assertFalse(answer.subscriptions.single().isSubscribed)
        assertTrue(answer.history.single().isCompleted)
        assertEquals(950L, answer.syncedAt)
    }

    @Test
    fun `a sync answer can carry a fresh token in its renew header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader(WatchAccountApi.RENEW_HEADER, "fresh-token")
                .setBody("""{"syncedAt":1}"""),
        )
        enqueueJson("""{"syncedAt":2}""")

        assertEquals("fresh-token", api.syncPull("Bearer watch-token", SyncPullRequest()).execute().headers()[WatchAccountApi.RENEW_HEADER])
        assertNull(api.syncPull("Bearer watch-token", SyncPullRequest()).execute().headers()[WatchAccountApi.RENEW_HEADER])
    }

    @Test
    fun `an unsuccessful answer comes back as one, with its body for the caller to read`() {
        enqueueJson("""{"error":"watch_token_invalid"}""", code = 401)
        enqueueJson("""{"error":"code_expired"}""", code = 400)

        val refused = api.syncPull("Bearer old-token", SyncPullRequest()).execute()
        val expired = api.redeem(WatchTokenRequest("old-code")).execute()

        assertFalse(refused.isSuccessful)
        assertEquals(401, refused.code())
        assertTrue(refused.errorBody()?.string().orEmpty().contains("watch_token_invalid"))
        assertNull(expired.body())
        assertEquals(400, expired.code())
    }

    private fun client(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }
}
