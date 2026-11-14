package cx.aswin.boxlore.wear.cloud

import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What the watch reads from the API's real answers: the body, a renewed token, and a refusal. */
@OptIn(ExperimentalCoroutinesApi::class)
class RetrofitWatchCloudApiTest {
    private class Answer(val code: Int = 200, val body: String = """{"syncedAt":7}""", val renew: String? = null)

    private val answers = ArrayDeque<Answer>()
    private val sent = mutableListOf<Request>()

    /** Answers each call from [answers] without a network, through the real Retrofit service and converter. */
    private fun api(dispatcher: CoroutineDispatcher): RetrofitWatchCloudApi {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    sent += chain.request()
                    val answer = answers.removeFirst()
                    val response =
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(answer.code)
                            .message("")
                            .body(answer.body.toResponseBody("application/json".toMediaType()))
                    answer.renew?.let { response.header(WatchAccountApi.RENEW_HEADER, it) }
                    response.build()
                }.build()
        return RetrofitWatchCloudApi(NetworkModule.createWatchAccountApi("https://api.example.org/", client), dispatcher)
    }

    @Test
    fun `a sync answer's renew header becomes the renewed token, and a missing or blank one becomes none`() =
        runTest {
            val cloud = api(UnconfinedTestDispatcher(testScheduler))
            answers += listOf(Answer(renew = "fresh-token"), Answer(), Answer(renew = "  "))

            val renewed = cloud.pull("t", SyncPullRequest()) as CloudAnswer.Ok
            val plain = cloud.pull("t", SyncPullRequest()) as CloudAnswer.Ok
            val blank = cloud.pull("t", SyncPullRequest()) as CloudAnswer.Ok

            assertEquals("fresh-token", renewed.renewedToken)
            assertEquals(7L, renewed.value.syncedAt)
            assertNull(plain.renewedToken)
            assertNull(blank.renewedToken)
            assertEquals("Bearer t", sent.first().header("Authorization"))
            assertEquals("/user/sync/pull", sent.first().url.encodedPath)
        }

    @Test
    fun `only the API's own refusal of the token reads as refused, and anything else as worth another try`() =
        runTest {
            val cloud = api(UnconfinedTestDispatcher(testScheduler))
            answers +=
                listOf(
                    Answer(code = 401, body = """{"error":"watch_token_invalid"}"""),
                    Answer(code = 401, body = """{"error":"session_expired"}"""),
                    Answer(code = 500, body = "{}"),
                )

            assertTrue(cloud.pull("t", SyncPullRequest()) === CloudAnswer.Refused)
            assertTrue(cloud.pull("t", SyncPullRequest()) === CloudAnswer.Unreachable)
            assertTrue(cloud.pull("t", SyncPullRequest()) === CloudAnswer.Unreachable)
        }

    @Test
    fun `a code is traded for the token in the answer, and a refused code for nothing`() =
        runTest {
            val cloud = api(UnconfinedTestDispatcher(testScheduler))
            answers += listOf(Answer(body = """{"token":"watch-token","expiresAt":9}"""), Answer(code = 400, body = """{"error":"invalid_code"}"""))

            assertEquals("watch-token", cloud.redeem("code-1"))
            assertNull(cloud.redeem("used-code"))
            val body = Buffer().also { sent.first().body!!.writeTo(it) }.readUtf8()
            assertEquals("""{"code":"code-1"}""", body)
        }
}
