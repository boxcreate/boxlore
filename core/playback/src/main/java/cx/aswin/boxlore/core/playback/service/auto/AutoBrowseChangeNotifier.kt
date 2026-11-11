package cx.aswin.boxlore.core.playback.service.auto

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Keeps the car's lists in step with the phone while Android Auto is connected.
 *
 * Queueing an episode, following a show, finishing a download or liking an episode on the phone
 * changed nothing in the car until its screen was reopened. This watches those four things and
 * tells the car which lists to reload. It runs only during a drive, and waits
 * for changes to settle so a burst (a sync, "mark all played") reloads each list once.
 */
internal class AutoBrowseChangeNotifier(private val host: AutoBrowseLibraryHost) {
    /** What changed on the phone. */
    enum class Change { QUEUE, SHOWS, DOWNLOADS, LIKED, PLAYED }

    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = host.serviceScope.launch {
            try {
                changes().collect(::notifyCar)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // The car keeps working without live updates; it reloads a list whenever it is opened.
                Log.w(TAG, "Stopped watching for phone changes", error)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    @OptIn(FlowPreview::class)
    private fun changes(): Flow<Change> {
        val history = host.database.listeningHistoryDao()
        return merge(
            host.queueRepository.queue.map { queue -> queue.map { it.id } }.settled(Change.QUEUE),
            host.database.podcastDao().getSubscribedPodcasts().map { shows -> shows.map { it.podcastId to it.latestEpisode?.id } }.settled(Change.SHOWS),
            host.database.downloadedEpisodeDao().getAllDownloads().map { rows -> rows.map { it.episodeId to it.status } }.settled(Change.DOWNLOADS),
            history.getLikedEpisodes().map { rows -> rows.map { it.episodeId } }.settled(Change.LIKED),
            history.completedEpisodeCountFlow().settled(Change.PLAYED),
        )
    }

    /** Emits [change] once the watched value has changed and then stayed still; the value at connect is skipped. */
    @OptIn(FlowPreview::class)
    private fun <T> Flow<T>.settled(change: Change): Flow<Change> = distinctUntilChanged().drop(1).debounce(SETTLE_MS).map { change }

    private fun notifyCar(change: Change) {
        val session = host.mediaSession ?: return
        nodesFor(change).forEach { node ->
            session.notifyChildrenChanged(node, CHANGED_ITEM_COUNT, null)
            session.notifyChildrenChanged(AutoBrowseContract.OPEN_PREFIX + node, CHANGED_ITEM_COUNT, null)
        }
        // Home's blocks draw their artwork from these lists (Drive picks from the queue too), so
        // it is rebuilt now rather than when the refresh's own pause allows. Home itself is only
        // reloaded if a block's artwork comes out different.
        host.requestAutoCollageRefresh(force = true)
    }

    companion object {
        private const val TAG = "AutoBrowse"
        private const val SETTLE_MS = 1_500L

        /** Media3 wants an item count with a change notice; the car reloads the list whatever the number. */
        private const val CHANGED_ITEM_COUNT = 50

        /** The car screens that show something [change] affects. Home shows all of it but the queue, which lives in the player. */
        fun nodesFor(change: Change): List<String> = when (change) {
            Change.QUEUE -> listOf(AutoBrowseContract.HOME_QUEUE_ID)
            Change.SHOWS -> listOf(AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID, AutoBrowseContract.HOME_NEW_EPISODES_ID, AutoBrowseContract.HOME_ID)
            Change.DOWNLOADS -> listOf(AutoBrowseContract.DOWNLOADS_ID, AutoBrowseContract.HOME_ID)
            Change.LIKED -> listOf(AutoBrowseContract.LIBRARY_LIKED_ID, AutoBrowseContract.HOME_ID)
            Change.PLAYED -> listOf(
                AutoBrowseContract.CONTINUE_ID,
                AutoBrowseContract.HOME_NEW_EPISODES_ID,
                AutoBrowseContract.LIBRARY_HISTORY_ID,
                AutoBrowseContract.HOME_ID,
            )
        }
    }
}
