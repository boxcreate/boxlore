package cx.aswin.boxlore.core.playback.service.auto

import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeDao
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.playback.QueueRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** When the car is told to reload: after a change on the phone has settled, and only while watching. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoBrowseChangeNotifierFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val completed = MutableStateFlow(0)
    private val queued = MutableStateFlow(emptyList<EpisodeItem>())
    private val session = mock(MediaLibrarySession::class.java)
    private val host = mock(AutoBrowseLibraryHost::class.java)
    private val history = mock(ListeningHistoryDao::class.java)

    private fun notifier(): AutoBrowseChangeNotifier {
        val database = mock(BoxLoreDatabase::class.java)
        val podcasts = mock(PodcastDao::class.java)
        val downloads = mock(DownloadedEpisodeDao::class.java)
        val queue = mock(QueueRepository::class.java)
        `when`(host.serviceScope).thenReturn(scope)
        `when`(host.database).thenReturn(database)
        `when`(host.queueRepository).thenReturn(queue)
        `when`(host.mediaSession).thenReturn(session)
        `when`(database.listeningHistoryDao()).thenReturn(history)
        `when`(database.podcastDao()).thenReturn(podcasts)
        `when`(database.downloadedEpisodeDao()).thenReturn(downloads)
        `when`(queue.queue).thenReturn(queued)
        `when`(podcasts.getSubscribedPodcasts()).thenReturn(MutableStateFlow(emptyList<PodcastEntity>()))
        `when`(downloads.getAllDownloads()).thenReturn(MutableStateFlow(emptyList<DownloadedEpisodeEntity>()))
        `when`(history.getLikedEpisodes()).thenReturn(MutableStateFlow(emptyList<ListeningHistoryEntity>()))
        `when`(history.completedEpisodeCountFlow()).thenReturn(completed)
        return AutoBrowseChangeNotifier(host)
    }

    private fun reloadsOf(node: String, times: Int) =
        verify(session, Mockito.times(times)).notifyChildrenChanged(Mockito.eq(node), Mockito.anyInt(), Mockito.isNull())

    @Test
    fun whatIsAlreadyThereWhenTheCarConnectsReloadsNothing() = scope.runTest {
        val notifier = notifier()
        notifier.start()
        advanceTimeBy(10_000)

        reloadsOf(AutoBrowseContract.HOME_ID, times = 0)
        notifier.stop()
    }

    @Test
    fun aBurstOfChangesReloadsEachListOnceAfterItSettles() = scope.runTest {
        val notifier = notifier()
        notifier.start()
        runCurrent()

        completed.value = 1
        advanceTimeBy(500)
        completed.value = 2
        advanceTimeBy(500)
        completed.value = 3
        reloadsOf(AutoBrowseContract.CONTINUE_ID, times = 0)
        advanceTimeBy(2_000)

        reloadsOf(AutoBrowseContract.CONTINUE_ID, times = 1)
        reloadsOf(AutoBrowseContract.OPEN_PREFIX + AutoBrowseContract.LIBRARY_HISTORY_ID, times = 1)
        // Rebuilt at once: left to its own pause, a block's artwork could stay stale.
        verify(host).requestAutoCollageRefresh(true)
        notifier.stop()
    }

    @Test
    fun aQueueChangeRebuildsNoArtwork() = scope.runTest {
        // The queue has no block on Home, and rebuilding its artwork reloads Home.
        val notifier = notifier()
        notifier.start()
        runCurrent()

        queued.value = listOf(mock(EpisodeItem::class.java))
        advanceTimeBy(2_000)

        reloadsOf(AutoBrowseContract.HOME_QUEUE_ID, times = 1)
        verify(host, Mockito.never()).requestAutoCollageRefresh(Mockito.anyBoolean())
        notifier.stop()
    }

    @Test
    fun startingTwiceWatchesOnce() = scope.runTest {
        val notifier = notifier()
        notifier.start()
        notifier.start()
        runCurrent()

        completed.value = 1
        advanceTimeBy(2_000)

        reloadsOf(AutoBrowseContract.HOME_ID, times = 1)
        notifier.stop()
    }

    @Test
    fun nothingReloadsAfterStop() = scope.runTest {
        val notifier = notifier()
        notifier.start()
        runCurrent()
        notifier.stop()

        completed.value = 1
        advanceTimeBy(5_000)

        reloadsOf(AutoBrowseContract.HOME_ID, times = 0)
    }

    @Test
    fun aFailingSourceEndsTheWatchWithoutCrashingAndItCanBeStartedAgain() = scope.runTest {
        val notifier = notifier()
        `when`(history.completedEpisodeCountFlow()).thenReturn(flow { throw IllegalStateException("database closed") })
        notifier.start()
        advanceTimeBy(5_000)
        val warnings = org.robolectric.shadows.ShadowLog.getLogsForTag("AutoBrowse").filter { it.type == android.util.Log.WARN }
        assertEquals(listOf("database closed"), warnings.map { it.throwable?.message })

        `when`(history.completedEpisodeCountFlow()).thenReturn(completed)
        notifier.start()
        runCurrent()
        completed.value = 7
        advanceTimeBy(2_000)

        reloadsOf(AutoBrowseContract.HOME_ID, times = 1)
        notifier.stop()
    }
}
