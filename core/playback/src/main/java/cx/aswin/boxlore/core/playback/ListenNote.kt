package cx.aswin.boxlore.core.playback

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.PlaybackInterruptedListen

/**
 * The listen in progress, kept on disk so the next launch can report one that Android ended without
 * warning (`playback_interrupted`). A listen that ends normally clears it, so it normally sends
 * nothing. Replaces `playback_heartbeat`, which sent progress all the time to catch the same thing.
 */
internal data class ListenNote(
    val listenId: String,
    val episodeId: String,
    val podcastId: String?,
    val entryPoint: String?,
    val playbackMode: String,
    val controlledBy: String,
    val heardMs: Long,
    val elapsedMs: Long,
    val inBackground: Boolean,
) {
    fun toEvent() =
        PlaybackInterruptedListen(
            listenId = listenId,
            episodeId = episodeId,
            podcastId = podcastId,
            entryPoint = entryPoint,
            playbackMode = playbackMode,
            controlledBy = controlledBy,
            heardMs = heardMs,
            elapsedMs = elapsedMs,
            inBackground = inBackground,
        )
}

/** Where [ListenNote] lives: SharedPreferences in the app, a fake in tests. */
internal interface ListenNoteStore {
    fun load(): ListenNote?

    fun save(note: ListenNote)

    fun clear()
}

/**
 * Writes the note when a listen starts and then at most every [SAVE_EVERY_MS] while it plays, so a
 * cut-off listen loses at most that much; clears it when the listen ends.
 */
internal class ListenNoteKeeper(private val store: ListenNoteStore) {
    private var lastSavedAtMs: Long? = null

    fun started(nowMs: Long, note: ListenNote,) {
        store.save(note)
        lastSavedAtMs = nowMs
    }

    /** Saves [note] if the last save is old enough; [note] is built only then. */
    fun progressed(nowMs: Long, note: () -> ListenNote?,) {
        val last = lastSavedAtMs ?: return
        if (nowMs - last < SAVE_EVERY_MS) return
        note()?.let(store::save)
        lastSavedAtMs = nowMs
    }

    fun ended() {
        lastSavedAtMs = null
        store.clear()
    }

    companion object {
        const val SAVE_EVERY_MS = 30_000L
    }
}

/** Reports a listen the previous run left unfinished; see [ListenNote]. */
object ListenInterruptions {
    /**
     * Sends `playback_interrupted` for a note the previous run left, then clears it. Call once when
     * the process starts, before playback can write a new note.
     */
    fun reportLeftover(context: Context) = reportLeftover(PrefsListenNoteStore(context))

    internal fun reportLeftover(store: ListenNoteStore) {
        val note = runCatching(store::load).getOrNull() ?: return
        store.clear()
        AnalyticsHelper.trackPlaybackInterrupted(note.toEvent())
    }
}

/** [ListenNoteStore] in its own small prefs file, so writes stay cheap. */
internal class PrefsListenNoteStore(context: Context) : ListenNoteStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): ListenNote? {
        val listenId = prefs.getString(KEY_LISTEN_ID, null) ?: return null
        val episodeId = prefs.getString(KEY_EPISODE_ID, null) ?: return null
        return ListenNote(
            listenId = listenId,
            episodeId = episodeId,
            podcastId = prefs.getString(KEY_PODCAST_ID, null),
            entryPoint = prefs.getString(KEY_ENTRY_POINT, null),
            playbackMode = prefs.getString(KEY_PLAYBACK_MODE, null).orEmpty(),
            controlledBy = prefs.getString(KEY_CONTROLLED_BY, null).orEmpty(),
            heardMs = prefs.getLong(KEY_HEARD_MS, 0L),
            elapsedMs = prefs.getLong(KEY_ELAPSED_MS, 0L),
            inBackground = prefs.getBoolean(KEY_IN_BACKGROUND, false),
        )
    }

    override fun save(note: ListenNote) {
        prefs
            .edit()
            .putString(KEY_LISTEN_ID, note.listenId)
            .putString(KEY_EPISODE_ID, note.episodeId)
            .putString(KEY_PODCAST_ID, note.podcastId)
            .putString(KEY_ENTRY_POINT, note.entryPoint)
            .putString(KEY_PLAYBACK_MODE, note.playbackMode)
            .putString(KEY_CONTROLLED_BY, note.controlledBy)
            .putLong(KEY_HEARD_MS, note.heardMs)
            .putLong(KEY_ELAPSED_MS, note.elapsedMs)
            .putBoolean(KEY_IN_BACKGROUND, note.inBackground)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "boxlore_listen_note"
        const val KEY_LISTEN_ID = "listen_id"
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_PODCAST_ID = "podcast_id"
        const val KEY_ENTRY_POINT = "entry_point"
        const val KEY_PLAYBACK_MODE = "playback_mode"
        const val KEY_CONTROLLED_BY = "controlled_by"
        const val KEY_HEARD_MS = "heard_ms"
        const val KEY_ELAPSED_MS = "elapsed_ms"
        const val KEY_IN_BACKGROUND = "in_background"
    }
}
