package cx.aswin.boxlore.core.playback.service

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class CoilBitmapLoaderTest {
    @Test
    fun `decode is dispatched asynchronously and returns decoded artwork`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val loader = CoilBitmapLoader(RuntimeEnvironment.getApplication(), backgroundScope, dispatcher)
        val data = ByteArrayOutputStream().also {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val future = loader.decodeBitmap(data)
        assertFalse(future.isDone)
        runCurrent()
        assertEquals(8, future.get().width)
    }

    @Test
    fun `invalid bytes fail through the future instead of throwing on caller thread`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)
        val loader = CoilBitmapLoader(RuntimeEnvironment.getApplication(), serviceScope, dispatcher)
        val future = loader.decodeBitmap(byteArrayOf())
        assertFalse(future.isDone)
        runCurrent()
        val failure = assertThrows(ExecutionException::class.java) { future.get() }
        assertTrue(failure.cause is IllegalArgumentException)
        assertTrue(serviceScope.isActive)
        serviceScope.cancel()
    }

    @Test
    fun `service shutdown cancels pending decode work`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)
        val loader = CoilBitmapLoader(RuntimeEnvironment.getApplication(), serviceScope, dispatcher)
        val future = loader.decodeBitmap(byteArrayOf())
        serviceScope.cancel()
        runCurrent()
        assertTrue(future.isCancelled)
    }
}
