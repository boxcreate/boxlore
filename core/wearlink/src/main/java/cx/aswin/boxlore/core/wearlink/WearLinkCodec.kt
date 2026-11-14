package cx.aswin.boxlore.core.wearlink

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Packs link values as compressed JSON. Unknown fields are ignored, so a newer app on one device
 * can talk to an older app on the other; bytes that cannot be read decode to null.
 */
object WearLinkCodec {
    /** The system refuses items over 100 KB; this leaves room for its own bookkeeping. */
    const val MAX_ITEM_BYTES = 90_000

    /** History rows the watch always gets, however long the list of shows is. */
    private const val MIN_HISTORY = 5

    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    inline fun <reified T> encode(value: T): ByteArray = pack(json.encodeToString(serializer<T>(), value))

    inline fun <reified T> decode(bytes: ByteArray?): T? {
        val text = unpack(bytes ?: return null) ?: return null
        return try {
            json.decodeFromString(serializer<T>(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * [library] packed to fit one item. A library too large for that loses its oldest history
     * rows first, then shows from the end of the list, and says so in `showsTruncated`.
     */
    fun encodeLibrary(library: PhoneLibrary, maxBytes: Int = MAX_ITEM_BYTES): ByteArray {
        var current = library
        var bytes = encode(current)
        while (bytes.size > maxBytes) {
            current =
                when {
                    // The lists that only fill a tile go first; follows and listening are what must arrive.
                    current.dailyMix.isNotEmpty() -> current.copy(dailyMix = emptyList())
                    current.newEpisodes.isNotEmpty() -> current.copy(newEpisodes = emptyList())
                    current.history.size > MIN_HISTORY -> current.copy(history = current.history.take(maxOf(MIN_HISTORY, current.history.size / 2)))
                    current.shows.size > 1 -> current.copy(shows = current.shows.take(current.shows.size * 3 / 4), showsTruncated = true)
                    else -> return bytes
                }
            bytes = encode(current)
        }
        return bytes
    }

    fun pack(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun unpack(bytes: ByteArray): String? =
        try {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: IOException) {
            null
        }
}
