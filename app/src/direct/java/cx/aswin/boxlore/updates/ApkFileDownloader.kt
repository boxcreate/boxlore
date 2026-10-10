package cx.aswin.boxlore.updates

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Range-resumable app-private transfer, keyed by checksum rather than mutable version name. */
internal class ApkFileDownloader(private val directory: File, private val client: OkHttpClient) {
    suspend fun download(manifest: UpdateManifest, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        manifest.validate()
        directory.mkdirs()
        val stem = manifest.apkSha256.lowercase()
        val ready = File(directory, "$stem.apk")
        val partial = File(directory, "$stem.part")
        if (ready.exists() && matches(ready, manifest)) return@withContext ready
        ready.delete()
        if (partial.length() > manifest.apkBytes) partial.delete()
        if (partial.length() == manifest.apkBytes) {
            if (matches(partial, manifest)) {
                require(partial.renameTo(ready))
                return@withContext ready
            }
            require(partial.delete()) { "Could not restart a corrupted transfer" }
        }
        val offset = partial.length()
        val request = Request.Builder().url(manifest.apkUrl).header("Accept-Encoding", "identity")
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
        client.newCall(request).withUpdateResponse { response ->
            if (response.code !in listOf(200, 206)) throw IOException("APK transfer failed: ${response.code}")
            require(response.request.url.isHttps || (cx.aswin.boxlore.BuildConfig.BOXLORE_ISOLATED_TESTS && manifest.testOnly && response.request.url.toString() == "http://127.0.0.1:8765/candidate.apk")) { "Insecure download redirect" }
            val append = response.code == 206
            if (append) validateContentRange(response.header("Content-Range"), offset, manifest.apkBytes)
            val body = response.body ?: throw IOException("Empty APK")
            var received = if (append) offset else 0L
            onProgress(received.toFloat() / manifest.apkBytes)
            body.byteStream().use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var lastReported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        received += read
                        if (received > manifest.apkBytes) {
                            partial.delete()
                            throw IOException("APK larger than published size")
                        }
                        output.write(buffer, 0, read)
                        val time = System.nanoTime()
                        if (time - lastReported > 100_000_000L) {
                            onProgress(received.toFloat() / manifest.apkBytes)
                            lastReported = time
                        }
                    }
                }
            }
        }
        if (partial.length() != manifest.apkBytes) throw IOException("Incomplete APK; retry to resume")
        if (!matches(partial, manifest)) {
            partial.delete()
            throw IOException("APK checksum mismatch")
        }
        require(partial.renameTo(ready)) { "Could not prepare APK" }
        onProgress(1f)
        ready
    }

    companion object {
        fun matches(file: File, manifest: UpdateManifest): Boolean = file.isFile &&
            file.length() == manifest.apkBytes &&
            sha256(file).equals(manifest.apkSha256, ignoreCase = true)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

internal fun validateContentRange(value: String?, offset: Long, total: Long) {
    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.orEmpty()) ?: throw IOException("Invalid resume response")
    val (start, end, size) = match.destructured
    require(start.toLong() == offset && size.toLong() == total && end.toLong() in offset until total) { "Mismatched resume response" }
}
