package cx.aswin.boxlore.tv

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Settings Google Play and TVs depend on, which no screen test would notice missing. */
class TvBuildTest {
    private val phoneBuild = File("../app/build.gradle.kts").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun phone(pattern: String): String = Regex(pattern, RegexOption.MULTILINE).find(phoneBuild)!!.groupValues[1]

    @Test
    fun `the TV carries the phone's version name`() {
        assertEquals(phone("""^\s*versionName\s*=\s*"([^"]+)"\s*$"""), BuildConfig.VERSION_NAME)
    }

    // Google Play takes each version code once per app; the phone, the watch (+1,000,000) and the TV are one app there.
    @Test
    fun `the TV's version code is the phone's plus two million, so it never meets the phone's or the watch's`() {
        assertEquals(2_000_000 + phone("""^\s*versionCode\s*=\s*(\d+)\s*$""").toInt(), BuildConfig.VERSION_CODE)
    }

    // Without the TV launcher entry the app never shows in the TV's app row, and Play hides it from TVs (TV-ML).
    @Test
    fun `the TV opens from the TV launcher with a banner`() {
        assertTrue(manifest.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue(manifest.contains("android:banner=\"@drawable/tv_banner\""))
        assertTrue(File("src/main/res/drawable-xhdpi/tv_banner.png").isFile)
    }

    // Play never offers an app that requires a touchscreen to a TV (TV-MT), and this build is for TVs only.
    @Test
    fun `the TV needs no touchscreen and goes only to TVs`() {
        assertTrue(Regex("""android:name="android.hardware.touchscreen"\s+android:required="false"""").containsMatchIn(manifest))
        assertTrue(Regex("""android:name="android.software.leanback"\s+android:required="true"""").containsMatchIn(manifest))
    }

    // Play checks every bundle of the app against its advertising ID declaration (the watch learned this the hard way).
    @Test
    fun `the TV carries the advertising ID permission, as the phone does`() {
        val permission = "<uses-permission android:name=\"com.google.android.gms.permission.AD_ID\" />"
        assertTrue(manifest.contains(permission))
        assertTrue(File("../app/src/main/AndroidManifest.xml").readText().contains(permission))
    }

    @Test
    fun `plain http audio and artwork are allowed, as on the phone`() {
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(File("src/main/res/xml/network_security_config.xml").readText().contains("cleartextTrafficPermitted=\"true\""))
    }
}
