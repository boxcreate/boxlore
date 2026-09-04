package cx.aswin.boxlore.feature.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NowPlayingWidgetCoordinatorTest {
    @Test
    fun metadataIsPersistedBeforeArtworkCachePathIsSet() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val backgroundScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val playback =
            object : WidgetPlaybackSource {
                override val state =
                    kotlinx.coroutines.flow.MutableStateFlow(
                        WidgetPlaybackState(
                            episodeId = "ep-1",
                            episodeTitle = "Episode title",
                            podcastTitle = "Podcast title",
                            artworkUrl = null,
                        ),
                    )

                override suspend fun restoreBeforeCollect() = Unit

                override suspend fun restoreBeforeAction() = Unit

                override suspend fun togglePlayPause() = Unit

                override suspend fun previous() = Unit

                override suspend fun next() = Unit

                override suspend fun skipForward() = Unit

                override suspend fun skipBackward() = Unit
            }
        val deps =
            object : NowPlayingWidgetDependencies {
                override val context: Context = context
                override val scope: CoroutineScope = backgroundScope
                override val playback = playback
            }

        NowPlayingWidgetCoordinator.start(deps)
        advanceUntilIdle()

        val snapshot = NowPlayingWidgetSnapshotStore(context).read()
        assertEquals("Episode title", snapshot?.episodeTitle)
        assertEquals("Podcast title", snapshot?.podcastTitle)
        assertNull(snapshot?.artworkCachePath)
    }
}
