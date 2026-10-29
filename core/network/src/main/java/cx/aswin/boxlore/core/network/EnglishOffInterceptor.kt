package cx.aswin.boxlore.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * Adds `englishOff` to discovery requests whose languages leave English out (D9). The app's
 * language list carries English unless the listener switched it off and their picks allow it
 * (`ContentRegions.normalizeLanguages` with `englishOff`, applied in `contentLanguagesStream`), so
 * a list without `en` is exactly that choice. One place covers every route that sends languages:
 * a `languages` query parameter gains `englishOff=1`; a JSON body with a `languages` array gains
 * `"englishOff": true`. Anything else, an empty list and a request that already says, pass
 * through unchanged, so requests from a listener with English on stay byte-identical.
 */
class EnglishOffInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(withEnglishOff(chain.request()))

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** True when [languages] names at least one language and none of them is English. */
        fun leavesEnglishOut(languages: List<String>): Boolean {
            val codes = languages.map { it.trim().lowercase().substringBefore('-') }.filter { it.isNotEmpty() }
            return codes.isNotEmpty() && "en" !in codes
        }

        fun withEnglishOff(request: Request): Request {
            val url = request.url
            val queryLanguages = url.queryParameter("languages")
            if (queryLanguages != null) {
                if (url.queryParameter("englishOff") != null || !leavesEnglishOut(queryLanguages.split(','))) return request
                return request.newBuilder().url(url.newBuilder().addQueryParameter("englishOff", "1").build()).build()
            }
            val body = request.body ?: return request
            if (body.contentType()?.subtype != "json") return request
            val text = Buffer().also { body.writeTo(it) }.readUtf8()
            val obj = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return request
            val languages = (obj["languages"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: return request
            if ("englishOff" in obj || !leavesEnglishOut(languages)) return request
            val updated = JsonObject(obj + ("englishOff" to JsonPrimitive(true)))
            return request.newBuilder().method(request.method, updated.toString().toRequestBody(body.contentType())).build()
        }
    }
}
