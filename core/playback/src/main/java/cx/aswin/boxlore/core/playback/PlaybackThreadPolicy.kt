package cx.aswin.boxlore.core.playback

import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Thread-affinity policy for Media3 [androidx.media3.session.MediaController] operations.
 * MediaController requires calls to run on the application thread (Main Looper).
 */
internal object PlaybackThreadPolicy {
    @Volatile
    var mainDispatcher: CoroutineDispatcher =
        try {
            Dispatchers.Main.immediate
        } catch (_: Throwable) {
            Dispatchers.Unconfined
        }

    @Volatile
    var isMainThreadOverride: (() -> Boolean)? = null

    fun isMainThread(): Boolean {
        isMainThreadOverride?.let { return it() }
        return runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Looper.myLooper() == mainLooper
        }.getOrDefault(false)
    }
    fun runOnMainThread(
        scope: CoroutineScope?,
        block: () -> Unit,
    ) {
        if (isMainThread()) {
            block()
        } else {
            scope?.launch(mainDispatcher) {
                block()
            } ?: block()
        }
    }

    inline fun <T> runCatchingOnMain(
        defaultValue: T,
        block: () -> T,
    ): T =
        if (isMainThread()) {
            runCatching(block).getOrDefault(defaultValue)
        } else {
            android.util.Log.w(
                "PlaybackRepo",
                "MediaController method queried from non-main thread ${Thread.currentThread().name}; returning fallback",
            )
            defaultValue
        }
}

internal fun PlaybackRepository.runOnMainThread(block: () -> Unit) {
    PlaybackThreadPolicy.runOnMainThread(repositoryScope, block)
}

internal inline fun <T> PlaybackRepository.runCatchingOnMain(
    defaultValue: T,
    block: () -> T,
): T = PlaybackThreadPolicy.runCatchingOnMain(defaultValue, block)
