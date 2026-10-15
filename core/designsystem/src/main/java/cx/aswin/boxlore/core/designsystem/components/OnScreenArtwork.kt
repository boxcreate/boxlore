package cx.aswin.boxlore.core.designsystem.components

import android.util.LruCache
import coil.memory.MemoryCache

/**
 * Where each artwork's decoded bitmap sits in Coil's memory cache, keyed by its original URL: the
 * largest copy an [OptimizedImage] put on screen. Colour extraction reads the cover the listener
 * just tapped from here instead of fetching another copy, so a page can wear its colours at once.
 */
object OnScreenArtwork {
    private class Entry(val width: Int, val key: MemoryCache.Key)

    private val entries = LruCache<String, Entry>(256)

    internal fun record(source: String, width: Int, key: MemoryCache.Key?) {
        if (key == null) return
        synchronized(entries) {
            val known = entries[source]
            if (known == null || known.width <= width) entries.put(source, Entry(width, key))
        }
    }

    /** The memory-cache key of the largest on-screen copy of [source], if one has loaded. */
    fun memoryKey(source: String): MemoryCache.Key? = entries[source]?.key
}
