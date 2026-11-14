package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/** Episode audio stored on the watch, so it plays with no connection. */
interface WatchDownloads {
    /** A download that could not start, for the screen to explain. */
    val problems: Flow<WatchDownloadProblem>
        get() = emptyFlow()

    fun start(episode: WatchEpisode)

    /** Stops a download in progress and deletes the stored audio. */
    fun remove(episodeId: String)

    /** The stored audio for an episode, or null when it is not fully downloaded. */
    fun file(episodeId: String): File?

    /** Removes stored audio the storage rules no longer keep: finished, stale or over the limit. */
    fun enforceLimit() = Unit
}

/** What the storage rules need to know about the watch right now. */
class WatchStorageLimits(
    /** The listener's limit for all downloads together. */
    val limitBytes: () -> Long,
    /** Free space left on the watch. */
    val freeBytes: () -> Long,
    val now: () -> Long = System::currentTimeMillis,
) {
    /** The episode in the player, which is never removed. Set once the player exists. */
    @Volatile
    var playingId: () -> String? = { null }
}

/**
 * Downloads straight from the podcast's own address into the app's files. The state of each
 * download lives in [WatchLibraryStore], so every screen sees the same progress, and
 * [StoragePolicy] decides what may be stored and what has to go.
 *
 * Downloads run while the app's process is alive; one cut short starts again when asked.
 */
class FileWatchDownloads(
    private val directory: File,
    private val client: OkHttpClient,
    private val store: WatchLibraryStore,
    private val limits: WatchStorageLimits,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WatchDownloads {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val refusals = MutableSharedFlow<WatchDownloadProblem>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val problems: Flow<WatchDownloadProblem> = refusals.asSharedFlow()

    override fun start(episode: WatchEpisode) {
        val url = episode.audioUrl ?: return
        if (jobs.containsKey(episode.id) || file(episode.id) != null) return
        jobs[episode.id] =
            scope.launch(io) {
                store.update { it.download(episode, WatchDownload.InProgress(0f)) }
                val bytes = runDownload(episode, url)
                jobs.remove(episode.id)
                if (bytes == null) {
                    store.update { it.download(episode, WatchDownload.None) }
                } else {
                    store.update { it.stored(episode, bytes, limits.now()) }
                    enforceLimit()
                }
            }
    }

    override fun remove(episodeId: String) {
        jobs.remove(episodeId)?.cancel()
        discard(episodeId)
    }

    override fun file(episodeId: String): File? = target(episodeId).takeIf { it.isFile && it.length() > 0 }

    override fun enforceLimit() {
        val state = store.state.value
        StoragePolicy.cleanup(state, limits.limitBytes(), limits.now(), limits.playingId()).forEach(::discard)
        deleteStrays()
    }

    /** The size of the finished download, or null when it failed or was refused. */
    private suspend fun runDownload(episode: WatchEpisode, url: String): Long? =
        try {
            fetch(episode, url)
            target(episode.id).length()
        } catch (cancelled: CancellationException) {
            partial(episode.id).remove()
            throw cancelled
        } catch (refused: DownloadRefused) {
            partial(episode.id).remove()
            refusals.tryEmit(refused.problem)
            null
        } catch (_: IOException) {
            partial(episode.id).remove()
            null
        }

    private suspend fun fetch(episode: WatchEpisode, url: String) {
        directory.mkdirs()
        val partial = partial(episode.id)
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) throw IOException("HTTP ${response.code}")
            val total = body.contentLength().takeIf { it > 0 }
            makeRoom(total)
            body.byteStream().use { input -> partial.outputStream().use { output -> copy(input, output, total, episode) } }
        }
        if (!partial.renameTo(target(episode.id))) throw IOException("Could not keep the download")
    }

    /** Copies the audio to disk, reporting progress and stopping when the download is cancelled. */
    private suspend fun copy(input: InputStream, output: OutputStream, total: Long?, episode: WatchEpisode) {
        val buffer = ByteArray(BUFFER_BYTES)
        var done = 0L
        var reported = 0f
        var checkedAt = 0L
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            done += read
            // A host that gave no size is checked as the bytes arrive instead: against the limit
            // with everything else stored, and against the room left on the watch.
            if (total == null && done - checkedAt >= UNKNOWN_SIZE_CHECK_BYTES) {
                checkedAt = done
                makeRoom(done, arriving = true)
            }
            val fraction = if (total != null) done.toFloat() / total else 0f
            if (fraction - reported >= PROGRESS_STEP) {
                reported = fraction
                store.update { it.download(episode, WatchDownload.InProgress(fraction)) }
            }
        }
    }

    /**
     * Clears older downloads for one of [bytes], or refuses it when the rules leave no room.
     * [arriving] is for a download of unknown size that has already written [bytes] to the watch.
     */
    private fun makeRoom(bytes: Long?, arriving: Boolean = false) {
        val state = store.state.value
        val room =
            if (arriving) {
                StoragePolicy.roomWhileArriving(bytes ?: 0L, state, limits.limitBytes(), limits.freeBytes(), limits.playingId())
            } else {
                StoragePolicy.room(bytes, state, limits.limitBytes(), limits.freeBytes(), limits.playingId())
            }
        when (room) {
            is StorageRoom.Refused -> throw DownloadRefused(room.problem)
            is StorageRoom.Available -> room.evict.forEach(::discard)
        }
    }

    private fun discard(episodeId: String) {
        target(episodeId).remove()
        partial(episodeId).remove()
        store.update { state -> state.episodes[episodeId]?.let { state.download(it, WatchDownload.None) } ?: state }
    }

    /** Deletes files no saved download points to, such as ones left by an interrupted run. */
    private fun deleteStrays() {
        val known = store.state.value.downloads.map { safeName(it.id) }.toSet() + jobs.keys.map(::safeName)
        directory.listFiles()?.filter { it.nameWithoutExtension !in known }?.forEach { it.remove() }
    }

    /** Deletes the file; one that will not go now is left for the next clean-up of strays. */
    private fun File.remove() {
        if (exists() && !delete()) deleteOnExit()
    }

    private fun target(episodeId: String): File = File(directory, safeName(episodeId) + ".audio")

    private fun partial(episodeId: String): File = File(directory, safeName(episodeId) + ".part")

    private class DownloadRefused(val problem: WatchDownloadProblem) : IOException(problem.name)

    companion object {
        /** Stable across releases: stored audio is found by this folder and the episode ID. */
        const val DIRECTORY_NAME = "watch_downloads"
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_STEP = 0.02f

        /** How often a download of unknown size is held against the storage rules. */
        private const val UNKNOWN_SIZE_CHECK_BYTES = 4L * 1024 * 1024

        /** Episode IDs become file names; anything but letters, digits, dash and underscore is replaced. */
        fun safeName(episodeId: String): String = episodeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
