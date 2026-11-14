package cx.aswin.boxlore.wear

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The watch app is released with the phone app; its version must follow the phone's without anyone editing it. */
class WatchVersionTest {
    private val phoneBuild = File("../app/build.gradle.kts").readText()

    private fun phone(pattern: String): String = Regex(pattern, RegexOption.MULTILINE).find(phoneBuild)!!.groupValues[1]

    @Test
    fun `the watch carries the phone's version name`() {
        assertEquals(phone("""^\s*versionName\s*=\s*"([^"]+)"\s*$"""), BuildConfig.VERSION_NAME)
    }

    // Google Play takes each version code once per app, and the watch app and the phone app are one app there.
    @Test
    fun `the watch's version code is the phone's plus a million, so the two never collide`() {
        assertEquals(1_000_000 + phone("""^\s*versionCode\s*=\s*(\d+)\s*$""").toInt(), BuildConfig.VERSION_CODE)
    }
}
