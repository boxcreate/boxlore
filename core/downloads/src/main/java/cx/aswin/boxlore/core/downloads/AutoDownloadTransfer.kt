package cx.aswin.boxlore.core.downloads

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal object AutoDownloadTransfers {
    const val WAITING_FOR_WORK = 1068
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val leased = ConcurrentHashMap.newKeySet<String>()
    fun lock(episodeId: String): Mutex = locks.getOrPut(episodeId) { Mutex() }
    fun acquire(episodeId: String) {
        leased.add(episodeId)
    }
    fun release(episodeId: String) {
        leased.remove(episodeId)
    }
    fun isLeased(episodeId: String): Boolean = episodeId in leased
    fun isAuto(download: Download): Boolean = String(download.request.data, Charsets.UTF_8).split("|").lastOrNull() == "auto"

    /** Prevent restored auto requests from running until a constrained worker owns them. */
    fun stopUnowned(manager: DownloadManager) {
        for (download in manager.currentDownloads) {
            if (isAuto(download) && !isLeased(download.request.id)) {
                manager.setStopReason(download.request.id, WAITING_FOR_WORK)
            }
        }
    }
}

internal suspend fun <T> DownloadManager.onApplicationThread(block: (DownloadManager) -> T): T =
    withContext(Handler(applicationLooper).asCoroutineDispatcher().immediate) { block(this@onApplicationThread) }

internal fun DownloadManager.postOnApplicationThread(block: (DownloadManager) -> Unit) {
    if (Looper.myLooper() == applicationLooper) {
        block(this)
    } else {
        Handler(applicationLooper).post { block(this) }
    }
}

/** A progressing large episode can use the entire worker slice; stalled work yields and resumes. */
internal class AutoDownloadProgress(private val startedAt: Long, private val idleLimitMs: Long = 90_000L) {
    private var lastProgressAt = startedAt
    private var lastBytes = -1L
    fun stalled(now: Long, bytes: Long): Boolean {
        if (bytes > lastBytes) {
            lastBytes = bytes
            lastProgressAt = now
        }
        return now - lastProgressAt >= idleLimitMs
    }
}
