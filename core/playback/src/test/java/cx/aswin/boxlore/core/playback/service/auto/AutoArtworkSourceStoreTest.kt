package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoArtworkSourceStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AutoArtworkSourceStore.clearMemoryForTests()
        context
            .getSharedPreferences(AutoArtworkSourceStore.SOURCE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun putReturnsTrueWhenCommitSucceeds() {
        assertTrue(AutoArtworkSourceStore.put(context, "ok", "https://cdn.example/ok.jpg"))
        assertEquals("https://cdn.example/ok.jpg", AutoArtworkSourceStore.get(context, "ok"))
    }

    @Test
    fun putIsReadableImmediatelyFromMemoryAndPrefs() {
        assertTrue(AutoArtworkSourceStore.put(context, "abc", "https://cdn.example/cover.jpg"))

        assertEquals(
            "https://cdn.example/cover.jpg",
            AutoArtworkSourceStore.get(context, "abc"),
        )
        // Simulate a fresh reader that only has prefs (e.g. after process restart).
        AutoArtworkSourceStore.flushPersistsForTests()
        AutoArtworkSourceStore.clearMemoryForTests()
        assertEquals(
            "https://cdn.example/cover.jpg",
            AutoArtworkSourceStore.get(context, "abc"),
        )
    }

    @Test
    fun getReturnsNullWhenUnset() {
        assertNull(AutoArtworkSourceStore.get(context, "missing"))
    }

    @Test
    fun remoteUriPersistsMappingBeforeReturning() {
        val uri = AutoArtworkRepository.remoteUri(context, "https://cdn.example/art.png")!!
        val key = uri.pathSegments.last()

        assertEquals(
            "https://cdn.example/art.png",
            AutoArtworkSourceStore.get(context, key),
        )
        AutoArtworkSourceStore.flushPersistsForTests()
        AutoArtworkSourceStore.clearMemoryForTests()
        assertEquals(
            "https://cdn.example/art.png",
            AutoArtworkSourceStore.get(context, key),
        )
    }
}
