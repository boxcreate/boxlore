package cx.aswin.boxlore.core.playback.service

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoArtworkPipeLogicTest {

    private class TrackableOutputStream(
        private val delegate: OutputStream = ByteArrayOutputStream(),
        private val throwOnWrite: Boolean = false,
    ) : OutputStream() {
        var isClosed: Boolean = false
            private set

        override fun write(b: Int) {
            if (throwOnWrite) {
                throw IOException("write failed: EPIPE (Broken pipe)")
            }
            delegate.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (throwOnWrite) {
                throw IOException("write failed: EPIPE (Broken pipe)")
            }
            delegate.write(b, off, len)
        }

        override fun close() {
            isClosed = true
            delegate.close()
        }

        fun toByteArray(): ByteArray = (delegate as ByteArrayOutputStream).toByteArray()
    }

    @Test
    fun pipeStreamSafelyCopiesValidInputStreamToOutput() {
        val payload = "artwork-data-payload".toByteArray(Charsets.UTF_8)
        val output = TrackableOutputStream()

        val success =
            AutoArtworkPipeLogic.pipeStreamSafely(
                output = output,
                key = "valid_key",
                inputStreamProvider = { ByteArrayInputStream(payload) },
            )

        assertTrue(success)
        assertTrue(output.isClosed)
        assertEquals("artwork-data-payload", String(output.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun pipeStreamSafelySuppressesBrokenPipeIOException() {
        val payload = "broken-pipe-payload".toByteArray(Charsets.UTF_8)
        val output = TrackableOutputStream(throwOnWrite = true)

        val success =
            AutoArtworkPipeLogic.pipeStreamSafely(
                output = output,
                key = "valid_key",
                inputStreamProvider = { ByteArrayInputStream(payload) },
            )

        assertFalse(success)
        assertTrue(output.isClosed)
    }

    @Test
    fun pipeStreamSafelySuppressesUnexpectedException() {
        val output = TrackableOutputStream()

        val success =
            AutoArtworkPipeLogic.pipeStreamSafely(
                output = output,
                key = "valid_key",
                inputStreamProvider = { throw IllegalStateException("Boom") },
            )

        assertFalse(success)
        assertTrue(output.isClosed)
    }

    @Test
    fun pipeStreamSafelyClosesOutputStreamWhenKeyIsBlankOrNull() {
        val outputNull = TrackableOutputStream()
        val successNull =
            AutoArtworkPipeLogic.pipeStreamSafely(
                output = outputNull,
                key = null,
                inputStreamProvider = { ByteArrayInputStream(ByteArray(0)) },
            )
        assertTrue(successNull)
        assertTrue(outputNull.isClosed)

        val outputBlank = TrackableOutputStream()
        val successBlank =
            AutoArtworkPipeLogic.pipeStreamSafely(
                output = outputBlank,
                key = "   ",
                inputStreamProvider = { ByteArrayInputStream(ByteArray(0)) },
            )
        assertTrue(successBlank)
        assertTrue(outputBlank.isClosed)
    }

    @Test
    fun pipeStreamSafelyClosesOutputStreamWhenProviderReturnsNull() {
        val output = TrackableOutputStream()

        val success =
            AutoArtworkPipeLogic.pipeStreamSafely(
                output = output,
                key = "missing_key",
                inputStreamProvider = { null },
            )

        assertTrue(success)
        assertTrue(output.isClosed)
        assertEquals(0, output.toByteArray().size)
    }
}
