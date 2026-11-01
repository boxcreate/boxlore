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
                """{"meta":{"notice":"Slow this week","canStartNew":true,"photos":{"enabled":true,"max":5,"maxBytes":5242880},"maxBody":4000,"pushTopics":["support_ab","support_cd","bad topic"],"replyAuthor":"boxlore team","categories":[{"id":"bug","label":"Bug report"},{"id":"account","label":"Account and sync"},{"id":"Bad Id","label":"x"},{"id":"empty","label":" "}],"extra":1},"threads":[]}""",
            ),
        )
        val repo = repository()
        val meta = repo.refresh()
        assertEquals(
            SupportMeta(
                true, "Slow this week", true, 5, 5_242_880, 4000, listOf("support_ab", "support_cd"),
                listOf(SupportCategory("bug", "Bug report"), SupportCategory("account", "Account and sync")),
            ),
            meta,
        )
        assertEquals(meta, repo.meta.value)
        assertEquals("/support/threads", server.takeRequest().path)
    }

    @Test
    fun `sends a conversation and keeps the audio category`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"thread":{"id":"a1b2c3d4e5f6a1b2c3d4e5f6","status":{"key":"waiting"}}}"""))
        val result = repository().send(feedback.copy(requestId = "retry-key-0001"))
        assertEquals(SendResult.Sent("a1b2c3d4e5f6a1b2c3d4e5f6"), result)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"requestId\":\"retry-key-0001\""))
        assertTrue(body.contains("\"category\":\"audio\""))
        assertTrue(body.contains("\"device\":\"Pixel 9\""))
        assertTrue(legacyCalls.isEmpty())
    }

    @Test
    fun `falls back to the old form when support is not there or the phone can't prove its key`() = runTest {
        for (reply in listOf(
            MockResponse().setResponseCode(404).setBody("""{"error":"Not Found"}"""),
            MockResponse().setResponseCode(401).setBody("""{"error":"device_key_required"}"""),
            MockResponse().setResponseCode(401).setBody("""{"error":"proof_required"}"""),
            MockResponse().setResponseCode(503).setBody("""{"error":"support_unavailable"}"""),
        )) {
            server.enqueue(reply)
            assertEquals(SendResult.SentByEmailForm, repository().send(feedback))
        }
        assertEquals(4, legacyCalls.size)
        assertEquals(listOf("bug", "Static after ten minutes", "0.0.30", "Android 17", "log line"), legacyCalls.first())
        // A category the old form doesn't know goes as "other".
        server.enqueue(MockResponse().setResponseCode(404))
        repository().send(feedback.copy(category = "account"))
        assertEquals("other", legacyCalls.last().first())
    }

    @Test
    fun `without any session the old form can't deliver either, so it is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"session_required"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"session_invalid"}"""))
        val repo = repository()
        assertEquals(SendResult.Failed, repo.send(feedback))
        assertEquals(SendResult.Failed, repo.send(feedback))
        assertTrue(legacyCalls.isEmpty())
    }

    @Test
    fun `a pause is forgotten once support goes away, so Send reaches the old form`() = runTest {
        server.enqueue(MockResponse().setBody("""{"meta":{"canStartNew":false,"notice":"Paused"},"threads":[]}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(404))
        val repo = repository()
        repo.refresh()
        assertFalse(repo.meta.value.canStartNew)
        // A plain server error keeps what was last known…
        assertNull(repo.refresh())
        assertFalse(repo.meta.value.canStartNew)
        // …but support being gone clears it.
        assertNull(repo.refresh())
        assertEquals(SupportMeta.Offline, repo.meta.value)
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
        assertEquals(PhotoUpload.Unavailable, repository().uploadPhoto(byteArrayOf(1, 2, 3), "image/jpeg"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `photo uploads tell a missing support API from a failed upload`() = runTest {
        server.enqueue(MockResponse().setBody("""{"meta":{"photos":{"enabled":true,"max":5,"maxBytes":100}},"threads":[]}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"0123456789abcdef01234567"}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"support_unavailable"}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"photos_off"}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"limit"}"""))
        val repo = repository()
        repo.refresh()
        assertEquals(PhotoUpload.Uploaded("0123456789abcdef01234567"), repo.uploadPhoto(byteArrayOf(1), "image/jpeg"))
        assertEquals(PhotoUpload.Unavailable, repo.uploadPhoto(byteArrayOf(1), "image/jpeg"))
        assertEquals(PhotoUpload.Unavailable, repo.uploadPhoto(byteArrayOf(1), "image/jpeg"))
        assertEquals(PhotoUpload.Failed, repo.uploadPhoto(byteArrayOf(1), "image/jpeg"))
        server.takeRequest()
        assertEquals("no-store", server.takeRequest().getHeader("Cache-Control"))
    }

    @Test
    fun `photo target size keeps the shape and never upscales`() {
        assertEquals(1600 to 800, PhotoPrep.targetSize(3200, 1600))
        assertEquals(720 to 1600, PhotoPrep.targetSize(1080, 2400))
        assertEquals(400 to 300, PhotoPrep.targetSize(400, 300))
        assertEquals(0 to 0, PhotoPrep.targetSize(0, 300))
    }
}
