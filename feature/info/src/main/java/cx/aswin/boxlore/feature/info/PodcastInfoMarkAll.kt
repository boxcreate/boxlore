package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the show page's Mark all as played or unplayed ([markWholeShow]) one at a time. The read and
 * the write run in [workScope], which outlives the page, so leaving the page while a long show is
 * read still marks it; [pageScope] only follows it for the page. A repeat tap while one runs is
 * ignored, and the page's [PodcastInfoUiState.Success.isMarkingAll] is true until it ends, whether
 * it wrote or failed (the page then says nothing was marked), also when the page reloads meanwhile.
 * Mark all for one show from a page opened again waits for the one still running
 * ([markAllLock]), so the last tap's write lands last.
 */
internal class PodcastInfoMarkAll(
    private val pageScope: CoroutineScope,
    private val uiState: MutableStateFlow<PodcastInfoUiState>,
    private val loadWholeShow: suspend () -> List<Episode>,
    private val onFailure: (Exception) -> Unit = {},
    private val workScope: CoroutineScope = pageScope,
) {
    private var job: Job? = null

    /** Starts Mark all for [podcastId], handing every episode to [write]; false, starting nothing, while one runs. */
    fun start(podcastId: String, write: suspend (List<Episode>) -> Unit): Boolean {
        if (job?.isActive == true) return false
        uiState.setMarkingAll(true)
        val outcome = CompletableDeferred<MarkAllOutcome>()
        workScope
            .launch { outcome.complete(markAllLock(podcastId).withLock { markWholeShow(loadWholeShow, onFailure, write) }) }
            .invokeOnCompletion { cause -> if (cause != null) outcome.completeExceptionally(cause) }
        job =
            pageScope.launch {
                // A reload replaces the page's state while this runs: keep showing it running.
                val keepShown = launch { uiState.collect { if (it is PodcastInfoUiState.Success && it.podcast.id == podcastId) uiState.setMarkingAll(true) } }
                try {
                    val result = outcome.await()
                    if (result != MarkAllOutcome.MARKED) uiState.showMarkAllFailed(podcastId, result)
                } catch (e: CancellationException) {
                    // The page is gone; the work carries on in its own scope.
                    throw e
                } catch (_: Exception) {
                    // The write failed; the work's own scope reports it.
                } finally {
                    keepShown.cancel()
                    uiState.setMarkingAll(false)
                }
            }
        return true
    }
}

/** One Mark all per show across pages: a page opened again waits for the first to write. Shows share a few locks. */
private val markAllLocks = Array(MARK_ALL_LOCKS) { Mutex() }

private fun markAllLock(podcastId: String): Mutex = markAllLocks[(podcastId.hashCode() and Int.MAX_VALUE) % markAllLocks.size]

private const val MARK_ALL_LOCKS = 8

private fun MutableStateFlow<PodcastInfoUiState>.setMarkingAll(running: Boolean) {
    val latest = value as? PodcastInfoUiState.Success ?: return
    if (latest.isMarkingAll != running) value = latest.copy(isMarkingAll = running)
}
