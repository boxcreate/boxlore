package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InstallAttributionTest {
    private val setOnce = mutableListOf<Map<String, Any>>()
    private lateinit var previousSetOnce: (Map<String, Any>) -> Unit

    private class FakeStore : InstallAttribution.Store {
        var notPlay = false
        var kept: Pair<String, String?>? = null

        override fun notPlaySaved() = notPlay

        override fun markNotPlaySaved() {
            notPlay = true
        }

        override fun pending() = kept

        override fun keep(channel: String, referrerRaw: String?) {
            kept = channel to referrerRaw
        }

        override fun clearPending() {
            kept = null
        }
    }

    @BeforeEach
    fun setUp() {
        previousSetOnce = AnalyticsEmit.personSetOnceSink
        AnalyticsEmit.personSetOnceSink = { setOnce += it }
    }

    @AfterEach
    fun tearDown() {
        AnalyticsEmit.personSetOnceSink = previousSetOnce
    }

    @Test
    fun `a non-Play install is saved as not_play once`() {
        val store = FakeStore()
        InstallAttribution.onInstallerKnown(store, fromPlayStore = false)
        InstallAttribution.onInstallerKnown(store, fromPlayStore = false)

        assertEquals(listOf(mapOf("install_channel" to "not_play")), setOnce)
    }

    @Test
    fun `a referrer kept while the installer was unknown is saved once Android says it was Play`() {
        // Regression: the lookup failed when the referrer answered, and the referrer was already marked handled.
        val store = FakeStore()
        store.keep("share", "type=episode&id=1")

        InstallAttribution.onInstallerKnown(store, fromPlayStore = true)
        InstallAttribution.onInstallerKnown(store, fromPlayStore = true)

        assertEquals(listOf(mapOf("install_channel" to "share", "referrer_raw" to "type=episode&id=1")), setOnce)
        assertNull(store.kept)
    }

    @Test
    fun `a kept referrer is dropped when the copy turns out not to be from Play`() {
        val store = FakeStore()
        store.keep("organic", "utm_source=google-play&utm_medium=organic")

        InstallAttribution.onInstallerKnown(store, fromPlayStore = false)

        assertEquals(listOf(mapOf("install_channel" to "not_play")), setOnce)
        assertNull(store.kept)
    }

    @Test
    fun `a Play install with nothing kept saves nothing at launch`() {
        InstallAttribution.onInstallerKnown(FakeStore(), fromPlayStore = true)

        assertTrue(setOnce.isEmpty())
    }
}
