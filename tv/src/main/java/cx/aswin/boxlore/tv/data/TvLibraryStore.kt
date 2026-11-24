package cx.aswin.boxlore.tv.data

import android.content.Context
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
 * Keeps [TvLibraryState] across restarts in one small file. Reads are from memory; a change is
 * written shortly after it happens, and a burst of changes is written once.
 */
class TvLibraryStore(
    private val file: File,
    scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO,
    private val writeDelayMs: Long = WRITE_DELAY_MS,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val loaded = load()
    private val current = MutableStateFlow(loaded)

    val state: StateFlow<TvLibraryState> = current.asStateFlow()

    init {
        scope.launch(io) {
            // Compare with what is on disk rather than skipping the first value: a change made
            // before this collector starts is the first value it sees, and must still be saved.
            var saved = loaded
            current.collectLatest { latest ->
                if (latest != saved) {
                    delay(writeDelayMs)
                    // A failed save is tried again, waiting longer each time, until it lands or a newer library replaces it.
                    var wait = RETRY_FIRST_MS
                    while (!write(latest)) {
                        delay(wait)
                        wait = (wait * 2).coerceAtMost(RETRY_LONGEST_MS)
                    }
                    saved = latest
                }
            }
        }
    }

    fun update(change: (TvLibraryState) -> TvLibraryState) = current.update(change)

    /** An unreadable or missing file starts an empty library instead of failing the app. */
    private fun load(): TvLibraryState =
        runCatching { json.decodeFromString(TvLibraryState.serializer(), file.readText()) }.getOrDefault(TvLibraryState())

    /** Writes beside the file and swaps it in, so a crash mid-write never leaves half a library. False when it failed. */
    private fun write(state: TvLibraryState): Boolean =
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(TvLibraryState.serializer(), state))
            // Some file systems will not rename over an existing file, so that one is removed first.
            val swapped = temp.renameTo(file) || (file.delete() && temp.renameTo(file))
            check(swapped) { "Could not replace the saved library" }
        }.isSuccess

    companion object {
        /** Stable across releases: renaming it loses the TV's shows and progress. */
        const val FILE_NAME = "boxlore_tv_library.json"
        private const val WRITE_DELAY_MS = 400L
        private const val RETRY_FIRST_MS = 2_000L
        private const val RETRY_LONGEST_MS = 60_000L

        fun create(context: Context, scope: CoroutineScope): TvLibraryStore = TvLibraryStore(File(context.filesDir, FILE_NAME), scope)
    }
}
