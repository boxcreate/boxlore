package cx.aswin.boxlore.wear

import cx.aswin.boxlore.core.wearlink.WearLinkProtocol
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Settings a real phone, real podcast hosts and Google Play depend on, which no screen test would notice missing. */
class WatchManifestTest {
    private fun source(path: String): String = File("src/main/$path").readText()

    // Regression: a BBC episode started on the phone would not play on the watch; its audio is plain http.
    @Test
    fun `plain http audio is allowed, as on the phone`() {
        assertTrue(source("AndroidManifest.xml").contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(source("res/xml/network_security_config.xml").contains("<base-config cleartextTrafficPermitted=\"true\""))
    }

    @Test
    fun `the watch declares the capability the phone looks for, and shrinking keeps it`() {
        assertTrue(source("res/values/wear.xml").contains("<item>${WearLinkProtocol.CAPABILITY_WATCH}</item>"))
        assertTrue(source("res/raw/boxlore_wear_keep.xml").contains("@array/android_wear_capabilities"))
    }

    // Regression: Play flagged the watch bundle on every release because the app's advertising ID
    // declaration covers it and only the phone manifest carried the permission.
    @Test
    fun `the watch carries the advertising ID permission, as the phone does`() {
        val permission = "<uses-permission android:name=\"com.google.android.gms.permission.AD_ID\" />"
        assertTrue(source("AndroidManifest.xml").contains(permission))
        assertTrue(File("../app/src/main/AndroidManifest.xml").readText().contains(permission))
    }
}
