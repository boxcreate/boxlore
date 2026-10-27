package cx.aswin.boxlore.core.rss

import java.io.InputStream

/**
 * The start of a feed document read from a stream, for [RssFeedClient.fetchNewest].
 *
 * [bytes] is a parseable document: when the read stopped early it is cut after the last whole item
 * and closed with the root's end tags. [complete] is true when the stream ended on its own, so
 * [bytes] is the whole document. [itemCount] counts the items whose end tag was read.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page: the newest
 * 100 come from a streaming read that can stop early).
 */
class FeedPrefix(val bytes: ByteArray, val complete: Boolean, val itemCount: Int)

/**
 * Reads a feed document until [read]'s `maxItems` items have ended, then stops, so a show page can
 * show a show's newest episodes without downloading a feed of thousands. Items are RSS `<item>` or
 * Atom `<entry>` elements, matched by their end tag without regard to case. A document that ends
 * before that is returned whole.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 */
internal object FeedPrefixReader {
    private val ITEM_END = "</item>".toByteArray(Charsets.US_ASCII)
    private val ENTRY_END = "</entry>".toByteArray(Charsets.US_ASCII)
    private val RSS_CLOSE = "\n</channel>\n</rss>\n".toByteArray(Charsets.US_ASCII)
    private val ATOM_CLOSE = "\n</feed>\n".toByteArray(Charsets.US_ASCII)
    private const val CHUNK = 16 * 1024

    /** Reads at most [maxBytes]; throws [IllegalArgumentException] past it, as a whole read does. */
    fun read(input: InputStream, maxItems: Int, maxBytes: Long): FeedPrefix {
        require(maxItems > 0) { "maxItems must be positive" }
        val buffer = GrowableBytes()
        val chunk = ByteArray(CHUNK)
        val scan = ItemScan()
        while (true) {
            val read = input.read(chunk)
            if (read < 0) return FeedPrefix(buffer.toArray(), complete = true, itemCount = scan.count)
            buffer.append(chunk, read)
            require(buffer.size <= maxBytes) { "Feed is larger than ${maxBytes / (1024 * 1024)} MB" }
            val cut = scan.advance(buffer, maxItems)
            if (cut != null) {
                val close = if (scan.atom) ATOM_CLOSE else RSS_CLOSE
                return FeedPrefix(buffer.toArray(cut) + close, complete = false, itemCount = scan.count)
            }
        }
    }

    /** Finds item end tags across chunk boundaries; [advance] returns the cut offset once [count] reaches the limit. */
    private class ItemScan {
        var count = 0
        var atom = false
        private var position = 0

        fun advance(buffer: GrowableBytes, maxItems: Int): Int? {
            // The last few bytes wait for the next chunk, so a tag split across chunks is still found.
            while (position <= buffer.size - ENTRY_END.size) {
                val end =
                    when {
                        buffer.matchesAt(position, ITEM_END) -> position + ITEM_END.size
                        buffer.matchesAt(position, ENTRY_END) -> {
                            atom = true
                            position + ENTRY_END.size
                        }
                        else -> null
                    }
                if (end == null) {
                    position++
                    continue
                }
                count++
                position = end
                if (count >= maxItems) return end
            }
            return null
        }
    }

    private class GrowableBytes {
        private var data = ByteArray(CHUNK)
        var size = 0
            private set

        fun append(source: ByteArray, length: Int) {
            if (size + length > data.size) data = data.copyOf(maxOf(data.size * 2, size + length))
            System.arraycopy(source, 0, data, size, length)
            size += length
        }

        fun matchesAt(offset: Int, tag: ByteArray): Boolean {
            for (index in tag.indices) {
                if (lower(data[offset + index]) != tag[index]) return false
            }
            return true
        }

        fun toArray(length: Int = size): ByteArray = data.copyOf(length)

        private fun lower(byte: Byte): Byte = if (byte in UPPER_A..UPPER_Z) (byte + CASE_OFFSET).toByte() else byte
    }

    private const val UPPER_A: Byte = 'A'.code.toByte()
    private const val UPPER_Z: Byte = 'Z'.code.toByte()
    private const val CASE_OFFSET = 'a'.code - 'A'.code
}
