package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.wearlink.WearLinkProtocol
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WearLinkResourcesTest {
    private fun source(path: String): String = File("src/main/$path").readText()

    // Regression: the release build dropped the capability, so the watch never saw the phone as reachable.
    @Test
    fun `the phone declares the capability the watch looks for, and shrinking keeps it`() {
        assertTrue(source("res/values/wear.xml").contains("<item>${WearLinkProtocol.CAPABILITY_PHONE}</item>"))
        assertTrue(source("res/raw/boxlore_resource_keep.xml").contains("@array/android_wear_capabilities"))
    }

    @Test
    fun `the listener service hears the watch's items, messages and requests`() {
        val manifest = source("AndroidManifest.xml")
        assertTrue(manifest.contains("android:name=\".wearlink.WearLinkListenerService\""))
        assertTrue(manifest.contains("com.google.android.gms.wearable.DATA_CHANGED"))
        assertTrue(manifest.contains("com.google.android.gms.wearable.MESSAGE_RECEIVED"))
        assertTrue(manifest.contains("com.google.android.gms.wearable.REQUEST_RECEIVED"))
        assertTrue(manifest.contains("android:pathPrefix=\"${WearLinkProtocol.WATCH_PREFIX}\""))
    }
}
