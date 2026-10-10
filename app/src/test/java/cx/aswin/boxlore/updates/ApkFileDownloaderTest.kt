package cx.aswin.boxlore.updates

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApkFileDownloaderTest {
    @Test fun `interrupted transfer resumes exact offset without corrupting bytes`() = runBlocking {
        val directory = Files.createTempDirectory("boxlore-update").toFile()
        try {
            val bytes = ByteArray(70_000) { (it % 127).toByte() }
            val original = File(directory, "fixture").apply { writeBytes(bytes) }
            val manifest = updateManifest().copy(apkBytes = bytes.size.toLong(), apkSha256 = ApkFileDownloader.sha256(original))
            var range: String? = null
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                range = chain.request().header("Range")
                val offset = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (offset > 0) 206 else 200).message("OK")
                    .header("Content-Range", "bytes $offset-${bytes.size - 1}/${bytes.size}")
                    .body(bytes.copyOfRange(offset, bytes.size).toResponseBody()).build()
            }.build()
            val downloader = ApkFileDownloader(directory, client)
            assertTrue(runCatching { downloader.download(manifest) { if (it > 0f) throw IOException("connection lost") } }.isFailure)
            val partial = File(directory, "${manifest.apkSha256}.part")
            val offset = partial.length()
            assertTrue(offset in 1 until bytes.size.toLong())
            val ready = downloader.download(manifest) { }
            assertEquals("bytes=$offset-", range)
            assertArrayEquals(bytes, ready.readBytes())
            assertFalse(partial.exists())
            range = "unchanged"
            assertEquals(ready, downloader.download(manifest) { })
            assertEquals("unchanged", range)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `server ignoring range safely restarts instead of appending`() = runBlocking {
        val directory = Files.createTempDirectory("boxlore-update").toFile()
        try {
            val bytes = "complete apk".toByteArray()
            val fixture = File(directory, "fixture").apply { writeBytes(bytes) }
            val manifest = updateManifest().copy(apkBytes = bytes.size.toLong(), apkSha256 = ApkFileDownloader.sha256(fixture))
            File(directory, "${manifest.apkSha256}.part").writeText("part")
            val client = responseClient(bytes)
            assertArrayEquals(bytes, ApkFileDownloader(directory, client).download(manifest) { }.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `corrupted APK is deleted and cannot become ready`() = runBlocking {
        val directory = Files.createTempDirectory("boxlore-update").toFile()
        try {
            val manifest = updateManifest().copy(apkBytes = 6)
            val result = runCatching { ApkFileDownloader(directory, responseClient("broken".toByteArray())).download(manifest) { } }
            assertTrue(result.isFailure)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun `invalid resume range fails rather than mixing downloads`() {
        for (value in listOf(null, "bytes 0-9/100", "bytes 10-99/200", "bytes 10-100/100")) {
            assertThrows(Exception::class.java) { validateContentRange(value, 10, 100) }
        }
        validateContentRange("bytes 10-99/100", 10, 100)
    }

    @Test fun `complete corrupted partial restarts instead of requesting a range past the end`() = runBlocking {
        val directory = Files.createTempDirectory("boxlore-update").toFile()
        try {
            val bytes = "complete apk".toByteArray()
            val fixture = File(directory, "fixture").apply { writeBytes(bytes) }
            val manifest = updateManifest().copy(apkBytes = bytes.size.toLong(), apkSha256 = ApkFileDownloader.sha256(fixture))
            File(directory, "${manifest.apkSha256}.part").writeBytes(ByteArray(bytes.size))
            var range: String? = "not requested"
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                range = chain.request().header("Range")
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (range == null) 200 else 416)
                    .message("Fixture").body(bytes.toResponseBody()).build()
            }.build()
            assertArrayEquals(bytes, ApkFileDownloader(directory, client).download(manifest) { }.readBytes())
            assertEquals(null, range)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun responseClient(bytes: ByteArray): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody()).build()
    }.build()
}
