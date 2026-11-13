package cx.aswin.boxlore.wear

import cx.aswin.boxlore.core.wearlink.WearLinkProtocol
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Settings a real phone and real podcast hosts depend on, which no screen test would notice missing. */
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
}
