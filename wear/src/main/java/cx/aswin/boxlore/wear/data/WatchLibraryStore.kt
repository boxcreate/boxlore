package cx.aswin.boxlore.wear.data

import android.content.Context
import cx.aswin.boxlore.wear.model.WatchDownload
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Keeps [WatchLibraryState] across restarts in one small file. Reads are from memory; a change is
 * written shortly after it happens, and a burst of changes is written once.
 */
class WatchLibraryStore(
    private val file: File,
    scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO,
    private val writeDelayMs: Long = WRITE_DELAY_MS,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val loaded = load()
    private val current = MutableStateFlow(loaded)

    val state: StateFlow<WatchLibraryState> = current.asStateFlow()

    init {
        scope.launch(io) {
            // Compare with what is on disk rather than skipping the first value: a change made
            // before this collector starts is the first value it sees, and must still be saved.
            var saved = loaded
            current.collectLatest { latest ->
                if (latest != saved) {
                    delay(writeDelayMs)
                    write(latest)
                    saved = latest
                }
            }
        }
    }

    fun update(change: (WatchLibraryState) -> WatchLibraryState) = current.update(change)

    /** An unreadable or missing file starts an empty library instead of failing the app. */
    private fun load(): WatchLibraryState =
        runCatching { json.decodeFromString(WatchLibraryState.serializer(), file.readText()) }
            .getOrDefault(WatchLibraryState())
            .withoutUnfinishedDownloads()

    /** Writes beside the file and swaps it in, so a crash mid-write never leaves half a library. */
    private fun write(state: WatchLibraryState) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(WatchLibraryState.serializer(), state))
            // Some file systems will not rename over an existing file, so that one is removed first.
            val swapped = temp.renameTo(file) || (file.delete() && temp.renameTo(file))
            check(swapped) { "Could not replace the saved library" }
        }
    }

    companion object {
        /** Stable across releases: renaming it loses the watch's subscriptions and progress. */
        const val FILE_NAME = "boxlore_watch_library.json"
        private const val WRITE_DELAY_MS = 400L

        fun create(context: Context, scope: CoroutineScope): WatchLibraryStore =
            WatchLibraryStore(File(context.filesDir, FILE_NAME), scope)
    }
}

/** A download that was running when the app stopped did not finish; it starts again when asked. */
internal fun WatchLibraryState.withoutUnfinishedDownloads(): WatchLibraryState =
    copy(
        episodes =
            episodes.mapValues { (_, episode) ->
                if (episode.download is WatchDownload.InProgress) episode.copy(download = WatchDownload.None) else episode
            },
    )
