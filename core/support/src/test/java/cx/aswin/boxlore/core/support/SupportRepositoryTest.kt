package cx.aswin.boxlore.core.support

import cx.aswin.boxlore.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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

class SupportRepositoryTest {
    private lateinit var server: MockWebServer
    private val legacyCalls = mutableListOf<List<String?>>()
    private var legacyOk = true

    private fun repository(): SupportRepository {
        val api = NetworkModule.createSupportApi(server.url("/").toString(), OkHttpClient())
        return SupportRepository(
            api = api,
            legacySubmit = { category, message, version, diagnostics, logs ->
                legacyCalls += listOf(category, message, version, diagnostics, logs)
                legacyOk
            },
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private val feedback = OutgoingFeedback(
        category = "audio",
        body = "Static after ten minutes",
        appVersion = "0.0.30",
        device = "Pixel 9",
        diagnostics = "Android 17",
        logs = "log line",
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `reads the switches and limits from the server`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"meta":{"notice":"Slow this week","canStartNew":true,"photos":{"enabled":true,"max":5,"maxBytes":5242880},"maxBody":2000,"pushTopic":"support_ab","replyAuthor":"boxlore team","extra":1},"threads":[]}""",
            ),
        )
        val repo = repository()
        val meta = repo.refresh()
        assertEquals(SupportMeta(true, "Slow this week", true, 5, 5_242_880, 2000, "support_ab"), meta)
        assertEquals(meta, repo.meta.value)
        assertEquals("/support/threads", server.takeRequest().path)
    }

    @Test
    fun `sends a conversation and keeps the audio category`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"thread":{"id":"a1b2c3d4e5f6a1b2c3d4e5f6","status":{"key":"waiting"}}}"""))
        val result = repository().send(feedback)
        assertEquals(SendResult.Sent("a1b2c3d4e5f6a1b2c3d4e5f6"), result)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"category\":\"audio\""))
        assertTrue(body.contains("\"device\":\"Pixel 9\""))
        assertTrue(legacyCalls.isEmpty())
    }

    @Test
    fun `falls back to the old form when the support API is not there or has no session`() = runTest {
        for (reply in listOf(
            MockResponse().setResponseCode(404).setBody("""{"error":"Not Found"}"""),
            MockResponse().setResponseCode(401).setBody("""{"error":"session_required"}"""),
            MockResponse().setResponseCode(503).setBody("""{"error":"support_unavailable"}"""),
        )) {
            server.enqueue(reply)
            assertEquals(SendResult.SentByEmailForm, repository().send(feedback))
        }
        assertEquals(3, legacyCalls.size)
        assertEquals(listOf("bug", "Static after ten minutes", "0.0.30", "Android 17", "log line"), legacyCalls.first())
    }

    @Test
    fun `a failing support API is not mistaken for a missing one`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"support_failed"}"""))
        assertEquals(SendResult.Failed, repository().send(feedback))
        assertTrue(legacyCalls.isEmpty())
    }

    @Test
    fun `shows the server's reason when paused or over the limit`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"paused","message":"Back on Monday"}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"limit"}"""))
        val repo = repository()
        assertEquals(SendResult.Refused("Back on Monday"), repo.send(feedback))
        val limited = repo.send(feedback)
        assertTrue(limited is SendResult.Refused)
        assertFalse((limited as SendResult.Refused).message.isBlank())
    }

    @Test
    fun `no connection is a failure, and the old form result is passed on`() = runTest {
        server.shutdown()
        assertEquals(SendResult.Failed, repository().send(feedback))
        server = MockWebServer().apply { start() }
        legacyOk = false
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(SendResult.Failed, repository().send(feedback))
    }

    @Test
    fun `photos are not uploaded while switched off`() = runTest {
        assertNull(repository().uploadPhoto(byteArrayOf(1, 2, 3), "image/jpeg"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `photo target size keeps the shape and never upscales`() {
        assertEquals(1600 to 800, PhotoPrep.targetSize(3200, 1600))
        assertEquals(720 to 1600, PhotoPrep.targetSize(1080, 2400))
        assertEquals(400 to 300, PhotoPrep.targetSize(400, 300))
        assertEquals(0 to 0, PhotoPrep.targetSize(0, 300))
    }
}
