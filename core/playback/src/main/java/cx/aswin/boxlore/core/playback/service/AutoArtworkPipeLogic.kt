package cx.aswin.boxlore.core.playback.service

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Handles safe streaming of Android Auto artwork to pipe file descriptors.
 * Ensures the destination stream is reliably closed and suppresses broken pipe
 * (EPIPE) / network disconnect exceptions when the receiving process closes prematurely.
 */
internal object AutoArtworkPipeLogic {
    private const val TAG = "AutoArtworkPipe"

    /**
     * Pipes artwork for [key] from [inputStreamProvider] into [output], wrapping it in an
     * [ParcelFileDescriptor.AutoCloseOutputStream] to guarantee descriptor closure.
     */
    fun pipeArtwork(
        output: ParcelFileDescriptor,
        key: String?,
        inputStreamProvider: (String) -> InputStream?,
    ): Boolean =
        pipeStreamSafely(
            output = ParcelFileDescriptor.AutoCloseOutputStream(output),
            key = key,
            inputStreamProvider = inputStreamProvider,
        )

    /**
     * Streams bytes from [inputStreamProvider] into [output], ensuring [output] is closed
     * and catching any [IOException] or unexpected [Exception] to avoid crashing the host process.
     */
    fun pipeStreamSafely(
        output: OutputStream,
        key: String?,
        inputStreamProvider: (String) -> InputStream?,
    ): Boolean {
        return try {
            output.use { pipe ->
                val nonBlankKey = key?.takeIf(String::isNotBlank) ?: return@use
                inputStreamProvider(nonBlankKey)?.use { input ->
                    input.copyTo(pipe)
                }
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, "Suppressed I/O error piping artwork for key=$key: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Suppressed unexpected error piping artwork for key=$key: ${e.message}")
            false
        }
    }
}
