package cx.aswin.boxlore.core.downloads

import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowLooper

/** Run suspend I/O while pumping Media3's real application looper in Robolectric's paused mode. */
internal fun <T> withDownloadTestLooper(block: suspend () -> T): T {
    val future = FutureTask { runBlocking { block() } }
    val thread = Thread(future, "download-test-io")
    thread.start()
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
    while (!future.isDone && System.nanoTime() < deadline) {
        ShadowLooper.idleMainLooper()
        Thread.sleep(5)
    }
    try {
        return future.get(1, TimeUnit.SECONDS)
    } finally {
        thread.interrupt()
    }
}
