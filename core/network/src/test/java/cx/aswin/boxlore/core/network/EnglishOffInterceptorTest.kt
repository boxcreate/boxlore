package cx.aswin.boxlore.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [EnglishOffInterceptor]: every discovery request whose languages leave English out says englishOff (D9). */
class EnglishOffInterceptorTest {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun get(query: String) = Request.Builder().url("https://api.example/v2/curated/vibe?id=comedy&$query").build()

    private fun post(body: String) = Request.Builder().url("https://api.example/v2/recommendations/v2").post(body.toRequestBody(jsonType)).build()

    private fun bodyOf(request: Request) = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

    @Test
    fun aQueryWithoutEnglishSaysEnglishOff() {
        val request = EnglishOffInterceptor.withEnglishOff(get("languages=de,fr&country=de"))
        assertEquals("1", request.url.queryParameter("englishOff"))
    }

    @Test
    fun aQueryWithEnglishOrNoLanguagesIsUnchanged() {
        for (query in listOf("languages=en,de", "languages=en-us", "country=de", "languages=")) {
            val original = get(query)
            assertSame(query, original, EnglishOffInterceptor.withEnglishOff(original))
        }
    }

    @Test
    fun aJsonBodyWithoutEnglishSaysEnglishOff() {
        val request = EnglishOffInterceptor.withEnglishOff(post("""{"country":"de","languages":["de"],"limit":20}"""))
        val body = bodyOf(request)
        assertTrue(body, body.contains("\"englishOff\":true"))
        assertTrue(body.contains("\"limit\":20"))
        assertEquals("POST", request.method)
    }

    @Test
    fun aJsonBodyWithEnglishIsSentByteForByte() {
        val original = post("""{"country":"in","languages":["en","hi"]}""")
        assertSame(original, EnglishOffInterceptor.withEnglishOff(original))
    }

    @Test
    fun aBodyThatAlreadySaysOrHasNoLanguagesIsUnchanged() {
        for (body in listOf("""{"languages":["de"],"englishOff":false}""", """{"country":"de"}""", "[1,2]", "not json")) {
            val original = post(body)
            assertSame(body, original, EnglishOffInterceptor.withEnglishOff(original))
        }
    }

    @Test
    fun leavesEnglishOutNeedsAtLeastOneLanguage() {
        assertTrue(EnglishOffInterceptor.leavesEnglishOut(listOf("de")))
        assertFalse(EnglishOffInterceptor.leavesEnglishOut(listOf("de", "EN-us")))
        assertFalse(EnglishOffInterceptor.leavesEnglishOut(listOf(" ")))
        assertFalse(EnglishOffInterceptor.leavesEnglishOut(emptyList()))
    }

    @Test
    fun aRequestWithoutABodyOrQueryIsUnchanged() {
        val original = Request.Builder().url("https://api.example/v2/podcast?id=1").build()
        assertSame(original, EnglishOffInterceptor.withEnglishOff(original))
        assertNull(original.body)
    }

    @Test
    fun aBodyOutsideTheDiscoveryRoutesIsNeverRead() {
        val sync = Request.Builder().url("https://api.example/sync/push").post("""{"languages":["de"],"history":[]}""".toRequestBody(jsonType)).build()
        assertSame(sync, EnglishOffInterceptor.withEnglishOff(sync))
        val similar = Request.Builder().url("https://api.example/v2/episodes/similar").post("""{"languages":["de"]}""".toRequestBody(jsonType)).build()
        assertTrue(bodyOf(EnglishOffInterceptor.withEnglishOff(similar)).contains("\"englishOff\":true"))
    }
}
