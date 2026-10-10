package cx.aswin.boxlore.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UpdateManifestTest {
    @Test fun `manifest round trips with listener copy intact and accepts future optional fields`() {
        val manifest = updateManifest().copy(notes = "• Better playback\n• Faster startup")
        assertEquals(manifest, UpdateManifest.parse(manifest.encode()))
        assertEquals(manifest, UpdateManifest.parse(manifest.encode().dropLast(1) + ",\"future\":true}"))
    }

    @Test fun `untrusted URLs cannot supply an installer or release notes`() {
        for (url in listOf(
            "http://github.com/boxcreate/boxlore/releases/download/v1/a.apk",
            "https://github.com.evil.example/boxcreate/boxlore/releases/download/v1/a.apk",
            "https://github.com/other/repo/releases/download/v1/a.apk",
            "https://github.com/boxcreate/boxlore/releases/download/../a.apk",
            "https://github.com/boxcreate/boxlore/releases/download/%2e%2e/a.apk",
            "https://user@github.com/boxcreate/boxlore/releases/download/v1/a.apk",
        )) {
            assertThrows(IllegalArgumentException::class.java) { updateManifest().copy(apkUrl = url).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { updateManifest().copy(notesUrl = "https://example.com").validate() }
    }

    @Test fun `missing integrity bounds unsupported schema and wrong package fail closed`() {
        for (manifest in listOf(
            updateManifest().copy(schemaVersion = 2),
            updateManifest().copy(packageName = "other.app"),
            updateManifest().copy(apkSha256 = "missing"),
            updateManifest().copy(apkBytes = 0),
            updateManifest().copy(apkBytes = 513L * 1024 * 1024),
            updateManifest().copy(versionCode = -1),
        )) {
            assertThrows(IllegalArgumentException::class.java) { manifest.validate() }
        }
    }
}
