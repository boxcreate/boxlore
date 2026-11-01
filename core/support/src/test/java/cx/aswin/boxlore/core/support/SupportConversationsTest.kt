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

/** Conversations, replies and what the phone remembers about them, against the real API shapes. */
class SupportConversationsTest {
    private lateinit var server: MockWebServer
    private val local = SupportLocalStore.InMemory()
    private var now = 1_800_000_000_000L

    private fun repository() = SupportRepository(
        api = NetworkModule.createSupportApi(server.url("/").toString(), OkHttpClient()),
        legacySubmit = { _, _, _, _, _ -> true },
        ioDispatcher = Dispatchers.Unconfined,
        local = local,
        clock = { now },
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private val list = """{"meta":{"pushTopics":["support_a"]},"threads":[
        {"id":"a1b2c3d4e5f6a1b2c3d4e5f6","category":"bug","categoryLabel":"Bug report","title":"It stops","status":{"key":"new_reply","label":"New reply","showTime":true},"updatedAt":5,"unread":true,"canReply":true},
        {"id":"b1b2c3d4e5f6a1b2c3d4e5f6","category":"feature","categoryLabel":"Feature idea","title":"Fade out","status":{"key":"waiting","label":"Sent · Waiting for a reply","showTime":false},"updatedAt":4,"unread":false,"canReply":true}]}"""

    @Test
    fun `a refresh lists the conversations and remembers there are some, and a waiting reply`() = runTest {
        server.enqueue(MockResponse().setBody(list))
        val repo = repository()
        assertNull(repo.threads.value)
        assertFalse(repo.hasConversations)
        repo.refresh()
        assertEquals(listOf("It stops", "Fade out"), repo.threads.value?.map { it.title })
        assertEquals("Bug report", repo.threads.value?.first()?.categoryLabel)
        assertTrue(repo.hasConversations)
        assertTrue(local.hasConversations)
        assertTrue(repo.replyWaiting.value)
        assertEquals(now, local.lastCheckedAt)
    }

    @Test
    fun `opening a conversation reads its messages, ending and the cursor for older ones`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"thread":{"id":"a1b2c3d4e5f6a1b2c3d4e5f6","categoryLabel":"Bug report","title":"It stops","status":{"key":"solved","label":"Solved"},"canReply":true},
                "messages":[{"id":"m1","from":"you","author":"You","body":"It stops","photos":[{"id":"p1","path":"/support/photos/p1"}],"diagnostics":true,"createdAt":1},
                {"id":"m2","from":"team","author":"boxlore team","body":"Fixed","createdAt":2}],
                "earlier":"m0","ending":{"kind":"solved","title":"Solved","body":"Still happening? Reply.","at":3,"action":{"kind":"reply","label":"Reply"}}}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        val repo = repository()
        val loaded = repo.conversation("a1b2c3d4e5f6a1b2c3d4e5f6", before = "m9") as ConversationResult.Loaded
        val c = loaded.conversation
        assertEquals(listOf(false, true), c.messages.map { it.fromTeam })
        assertEquals(listOf("p1"), c.messages.first().photoIds)
        assertEquals("m0", c.earlier)
        assertEquals(SupportEnding("solved", "Solved", "Still happening? Reply.", 3, "reply", "Reply"), c.ending)
        assertEquals("/support/threads/a1b2c3d4e5f6a1b2c3d4e5f6?before=m9", server.takeRequest().path)
        assertEquals(ConversationResult.Gone, repo.conversation("x"))
        assertEquals(ConversationResult.Failed, repo.conversation("x"))
    }

    @Test
    fun `replies are sent with their retry key, a closed conversation and a limit say why`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"message":{"id":"m3","from":"you","body":"Still"}}"""))
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"closed","ending":{"kind":"closed","title":"This conversation is closed","action":{"kind":"new","label":"Send new feedback"}}}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"limit","message":"Try again tomorrow."}"""))
        server.enqueue(MockResponse().setResponseCode(503))
        val repo = repository()
        val sent = repo.reply("t1", "Still", requestId = "retry-key-0001") as ReplyResult.Sent
        assertEquals("m3", sent.message?.id)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"requestId\":\"retry-key-0001\""))
        assertEquals("Send new feedback", (repo.reply("t1", "Hello?", null) as ReplyResult.Closed).ending?.actionLabel)
        assertEquals(ReplyResult.Refused("Try again tomorrow."), repo.reply("t1", "More", null))
        assertEquals(ReplyResult.Failed, repo.reply("t1", "More", null))
    }

    @Test
    fun `reading the last unread reply clears the waiting flag`() = runTest {
        server.enqueue(MockResponse().setBody(list))
        server.enqueue(MockResponse().setResponseCode(204))
        val repo = repository()
        repo.refresh()
        assertTrue(repo.markRead("a1b2c3d4e5f6a1b2c3d4e5f6"))
        assertFalse(repo.threads.value!!.first().unread)
        assertFalse(repo.replyWaiting.value)
        assertFalse(local.replyWaiting)
    }

    @Test
    fun `a push marks a reply waiting until the next refresh says otherwise`() = runTest {
        val repo = repository()
        repo.onReplyPushed()
        assertTrue(repo.replyWaiting.value)
        assertTrue(local.replyWaiting && local.hasConversations)
        server.enqueue(MockResponse().setBody("""{"meta":{},"threads":[]}"""))
        repo.refresh()
        assertFalse(repo.replyWaiting.value)
        assertFalse(repo.hasConversations)
    }

    @Test
    fun `the start-up check runs only for phones with conversations, and not too often`() = runTest {
        val repo = repository()
        assertNull(repo.refreshIfDue(30 * 60_000L))
        assertEquals(0, server.requestCount)

        local.hasConversations = true
        server.enqueue(MockResponse().setBody(list))
        repo.refreshIfDue(30 * 60_000L)
        assertEquals(1, server.requestCount)
        now += 10 * 60_000L
        assertNull(repo.refreshIfDue(30 * 60_000L))
        assertEquals(1, server.requestCount)
        now += 30 * 60_000L
        server.enqueue(MockResponse().setBody(list))
        repo.refreshIfDue(30 * 60_000L)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a first conversation is remembered at once, and photos come back as bytes`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"thread":{"id":"a1b2c3d4e5f6a1b2c3d4e5f6"}}"""))
        server.enqueue(MockResponse().setBody("jpeg-bytes"))
        server.enqueue(MockResponse().setResponseCode(404))
        val repo = repository()
        repo.send(OutgoingFeedback("bug", "It stops", "0.0.30", null, null, null))
        assertTrue(local.hasConversations)
        assertEquals("jpeg-bytes", repo.photo("p1")?.decodeToString())
        assertNull(repo.photo("p2"))
    }

    @Test
    fun `push topics follow the server's list, and a phone without conversations leaves them all`() {
        val topics = SupportPushTopics(local)
        val meta = SupportMeta.Offline.copy(pushTopics = listOf("support_a", "support_b"))
        local.subscribedTopics = setOf("support_old", "support_a")
        assertEquals(PushTopicPlan(subscribe = setOf("support_b"), unsubscribe = setOf("support_old")), topics.planFor(meta, hasConversations = true))
        topics.joined("support_b")
        topics.left("support_old")
        assertEquals(setOf("support_a", "support_b"), local.subscribedTopics)
        assertTrue(topics.planFor(meta, hasConversations = true).isEmpty)
        assertEquals(setOf("support_a", "support_b"), topics.planFor(meta, hasConversations = false).unsubscribe)
    }
}
