package cx.aswin.boxlore.feature.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NowPlayingWidgetSnapshotStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun writesAndReadsSnapshotJson() {
        val store = NowPlayingWidgetSnapshotStore(context)
        val snapshot =
            NowPlayingWidgetSnapshot(
                episodeId = "ep-1",
                episodeTitle = "Episode",
                podcastTitle = "Podcast",
                isPlaying = true,
                positionMs = 12_000L,
                durationMs = 60_000L,
                seekForwardMs = 30_000L,
                seekBackwardMs = 10_000L,
                updatedAtMs = 123L,
            )

        store.write(snapshot)

        assertEquals(snapshot, store.read())
    }

    @Test
    fun clearRemovesPersistedSnapshot() {
        val store = NowPlayingWidgetSnapshotStore(context)
        store.write(NowPlayingWidgetSnapshot(episodeId = "ep-1"))
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun usesStablePrefsFileName() {
        assertEquals("boxlore_now_playing_widget", NowPlayingWidgetSnapshotStore.PREFS_NAME)
    }

    @Test
    fun malformedSnapshotJsonReturnsNull() {
        val prefs =
            context.getSharedPreferences(
                NowPlayingWidgetSnapshotStore.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
        prefs.edit().putString("snapshot", "{not-valid-json").commit()

        assertNull(NowPlayingWidgetSnapshotStore(context).read())
    }
}
