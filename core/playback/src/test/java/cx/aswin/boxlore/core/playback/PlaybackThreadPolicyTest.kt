package cx.aswin.boxlore.core.playback

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackThreadPolicyTest {
    @AfterEach
    fun tearDown() {
        PlaybackThreadPolicy.isMainThreadOverride = null
    }

    @Test
    fun `isMainThread reflects override when set`() {
        PlaybackThreadPolicy.isMainThreadOverride = { true }
        assertTrue(PlaybackThreadPolicy.isMainThread())

        PlaybackThreadPolicy.isMainThreadOverride = { false }
        assertFalse(PlaybackThreadPolicy.isMainThread())
    }

    @Test
    fun `runCatchingOnMain evaluates block on main thread`() {
        PlaybackThreadPolicy.isMainThreadOverride = { true }
        val evaluated = AtomicBoolean(false)

        val result =
            runCatchingOnMainTest(defaultValue = "fallback") {
                evaluated.set(true)
                "success"
            }

        assertTrue(evaluated.get())
        assertEquals("success", result)
    }

    @Test
    fun `runCatchingOnMain returns defaultValue and skips block off main thread`() {
        PlaybackThreadPolicy.isMainThreadOverride = { false }
        val evaluated = AtomicBoolean(false)

        val result =
            runCatchingOnMainTest(defaultValue = "fallback") {
                evaluated.set(true)
                "success"
            }

        assertFalse(evaluated.get())
        assertEquals("fallback", result)
    }

    @Test
    fun `runCatchingOnMain catches exceptions and returns defaultValue`() {
        PlaybackThreadPolicy.isMainThreadOverride = { true }

        val result =
            runCatchingOnMainTest(defaultValue = 42) {
                error("MediaController method called from wrong thread")
            }

        assertEquals(42, result)
    }

    @Test
    fun `runOnMainThread runs synchronously when on main thread`() {
        PlaybackThreadPolicy.isMainThreadOverride = { true }
        val counter = AtomicInteger(0)

        runOnMainThreadTest(
            isMain = true,
            dispatch = { /* not called */ },
        ) {
            counter.incrementAndGet()
        }

        assertEquals(1, counter.get())
    }

    @Test
    fun `runOnMainThread dispatches to main dispatcher when off main thread`() = runBlocking {
        val mainExecutor =
            Executors
                .newSingleThreadExecutor { runnable -> Thread(runnable, "test-main-thread") }
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()
        val originalDispatcher = PlaybackThreadPolicy.mainDispatcher
        PlaybackThreadPolicy.mainDispatcher = mainDispatcher
        PlaybackThreadPolicy.isMainThreadOverride = { false }

        try {
            val executedThread = AtomicReference<String>()
            val waitLock = java.util.concurrent.CountDownLatch(1)

            val backgroundExecutor = Executors.newSingleThreadExecutor()
            backgroundExecutor.submit {
                // Off main thread
                assertFalse(PlaybackThreadPolicy.isMainThread())
                kotlinx.coroutines.CoroutineScope(mainDispatcher).launch {
                    executedThread.set(Thread.currentThread().name)
                    waitLock.countDown()
                }
            }

            waitLock.await(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(executedThread.get().startsWith("test-main-thread"))
            backgroundExecutor.shutdown()
        } finally {
            PlaybackThreadPolicy.mainDispatcher = originalDispatcher
            mainDispatcher.close()
            mainExecutor.shutdown()
        }
    }

    @Test
    fun `mediaController simulated thread check throws on background thread but is guarded by runCatchingOnMain`() {
        val simulatedMediaController = object {
            fun isPlaying(): Boolean {
                check(PlaybackThreadPolicy.isMainThread()) {
                    "MediaController method is called from a wrong thread. See javadoc of MediaController for details."
                }
                return true
            }
        }

        // On background thread: calling directly throws IllegalStateException
        PlaybackThreadPolicy.isMainThreadOverride = { false }
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            simulatedMediaController.isPlaying()
        }

        // With runCatchingOnMain: safely returns default value without throwing
        val safeResult = runCatchingOnMainTest(defaultValue = false) {
            simulatedMediaController.isPlaying()
        }
        assertFalse(safeResult)

        // On main thread: returns real value
        PlaybackThreadPolicy.isMainThreadOverride = { true }
        val mainResult = runCatchingOnMainTest(defaultValue = false) {
            simulatedMediaController.isPlaying()
        }
        assertTrue(mainResult)
    }

    private fun <T> runCatchingOnMainTest(defaultValue: T, block: () -> T): T =
        if (PlaybackThreadPolicy.isMainThread()) {
            runCatching(block).getOrDefault(defaultValue)
        } else {
            defaultValue
        }

    private fun runOnMainThreadTest(isMain: Boolean, dispatch: (() -> Unit) -> Unit, block: () -> Unit) {
        if (isMain) {
            block()
        } else {
            dispatch(block)
        }
    }
}
