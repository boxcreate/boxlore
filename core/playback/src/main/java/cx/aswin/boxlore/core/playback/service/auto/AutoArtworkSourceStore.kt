package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Durable + immediately-visible registry of Android Auto artwork source URLs/paths.
 *
 * An in-process map makes sources visible to [cx.aswin.boxlore.core.playback.service.AutoCollageProvider]
 * as soon as [AutoArtworkRepository] returns a URI. Prefs are [commit]ted on a background thread;
 * [put] waits briefly and only reports success when that commit lands, so callers can avoid handing
 * Auto a content URI that would not survive process restart.
 */
internal object AutoArtworkSourceStore {
    const val SOURCE_PREFS = "android_auto_artwork_sources"
    private const val TAG = "AutoArtworkSources"
    private const val COMMIT_WAIT_MS = 500L
    private const val MAX_SAVED_SOURCES = 4_000
    private val pruneScheduled = AtomicBoolean(false)
    private val pruneLock = Any()

    private val memory = ConcurrentHashMap<String, String>()

    /** What is known to be on disk: read from it, or written by a commit that succeeded. */
    private val saved = ConcurrentHashMap<String, String>()
    private val persistExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "auto-artwork-prefs").apply { isDaemon = true }
        }

    /**
     * @return true when the in-memory mapping is published and prefs [commit] succeeded
     * within [COMMIT_WAIT_MS].
     */
    fun put(context: Context, key: String, value: String,): Boolean {
        // Already saved: nothing to wait for. Lists are rebuilt on the main thread, and without this
        // every row of every reload waited on a disk write for artwork the car already knew. A source
        // another call is still saving does not count: if that save fails, this call must not have
        // vouched for it.
        if (savedValue(context, key) == value) return true
        memory[key] = value
        val appContext = context.applicationContext
        if (pruneScheduled.compareAndSet(false, true)) persistExecutor.execute { pruneIfOversized(appContext) }
        val committed = AtomicBoolean(false)
        val done = CountDownLatch(1)
        persistExecutor.execute {
            try {
                committed.set(
                    appContext
                        .getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(key, value)
                        .commit(),
                )
                if (committed.get()) saved[key] = value
            } finally {
                done.countDown()
            }
        }
        val finished =
            try {
                done.await(COMMIT_WAIT_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        if (!finished || !committed.get()) {
            memory.remove(key, value)
            Log.w(
                TAG,
                "Artwork source commit failed/pending for key=$key finished=$finished committed=${committed.get()}",
            )
            return false
        }
        return true
    }

    /**
     * Sources are never removed one by one, so once the file holds more than [maxSaved] (far more
     * than any car session needs) it is cut back to the sources this run has used; lists put the
     * rest back as they are next built. Holding [pruneLock] with [savedValue] keeps a read from
     * bringing back, as saved, a source the prune is dropping: a later equal [put] would then skip
     * the save it needs.
     */
    @androidx.annotation.VisibleForTesting
    internal fun pruneIfOversized(appContext: Context, maxSaved: Int = MAX_SAVED_SOURCES) {
        runCatching {
            val prefs = appContext.getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
            synchronized(pruneLock) {
                if (prefs.all.size <= maxSaved) return
                val kept = HashMap(memory)
                val editor = prefs.edit().clear()
                kept.forEach { (key, value) -> editor.putString(key, value) }
                if (editor.commit()) {
                    saved.clear()
                    saved.putAll(kept)
                } else {
                    Log.w(TAG, "Artwork source prune was not saved")
                }
            }
        }.onFailure { Log.w(TAG, "Unable to prune artwork sources", it) }
    }

    /** The source for [key]: one being saved right now is visible at once, ahead of what is on disk. */
    fun get(context: Context, key: String,): String? = memory[key] ?: savedValue(context, key)

    /** The source for [key] that is on disk, so that it survives a restart. */
    private fun savedValue(context: Context, key: String): String? {
        saved[key]?.let { return it }
        synchronized(pruneLock) {
            val persisted =
                context
                    .getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
                    .getString(key, null)
                    ?: return null
            saved[key] = persisted
            memory.putIfAbsent(key, persisted)
            return persisted
        }
    }

    /** Test-only: clear in-memory overlay without wiping SharedPreferences. */
    internal fun clearMemoryForTests() {
        memory.clear()
        saved.clear()
    }

    /** Test-only: wait for queued prefs commits to finish. */
    internal fun flushPersistsForTests() {
        val done = CountDownLatch(1)
        persistExecutor.execute { done.countDown() }
        check(done.await(2, TimeUnit.SECONDS)) {
            "Timed out waiting for Auto artwork prefs commits"
        }
    }
}
