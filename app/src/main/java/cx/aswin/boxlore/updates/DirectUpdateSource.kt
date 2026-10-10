package cx.aswin.boxlore.updates

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun updateHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followSslRedirects(false)
    .build()

/** Dedicated unauthenticated HTTP client: never send backend tokens to release URLs. */
internal class DirectUpdateSource(
    private val client: OkHttpClient,
    private val manifestUrl: String,
    private val installedVersion: Long,
    private val sdkInt: Int,
) : UpdateSource {
    override suspend fun lookup(): UpdateLookup = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(manifestUrl).header("Cache-Control", "no-cache").build()
        client.newCall(request).withUpdateResponse { response ->
            if (!response.isSuccessful) throw IOException("Update lookup failed: ${response.code}")
            val body = response.body ?: throw IOException("Empty update response")
            val source = body.source()
            source.request(MAX_MANIFEST_BYTES + 1)
            val bytes = source.readByteArray(source.buffer.size.coerceAtMost(MAX_MANIFEST_BYTES + 1))
            require(bytes.size <= MAX_MANIFEST_BYTES) { "Update manifest too large" }
            val manifest = UpdateManifest.parse(bytes.toString(Charsets.UTF_8))
            if (cx.aswin.boxlore.BuildConfig.BOXLORE_ISOLATED_TESTS) require(manifest.testOnly)
            if (manifest.versionCode <= installedVersion) return@withUpdateResponse UpdateLookup(upcoming = manifest.upcoming)
            if (manifest.minSdk > sdkInt) return@withUpdateResponse UpdateLookup(incompatible = true, upcoming = manifest.upcoming)
            UpdateLookup(UpdateOffer(manifest.versionCode, manifest.versionName, manifest), upcoming = manifest.upcoming)
        }
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 64L * 1024
    }
}
