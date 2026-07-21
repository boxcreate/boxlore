package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Durable + immediately-visible registry of Android Auto artwork source URLs/paths.
 *
 * [SharedPreferences.apply] is asynchronous; Auto hosts often open a content URI before the
 * mapping lands on disk, which produced blank artwork. This store keeps an in-process map and
 * [commit]s prefs so [cx.aswin.boxlore.core.playback.service.AutoCollageProvider] can resolve
 * sources as soon as [AutoArtworkRepository] returns a URI.
 */
internal object AutoArtworkSourceStore {
    const val SOURCE_PREFS = "android_auto_artwork_sources"

    private val memory = ConcurrentHashMap<String, String>()

    fun put(
        context: Context,
        key: String,
        value: String,
    ) {
        memory[key] = value
        val prefs = context.getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
        // commit() so a ContentProvider open that races the browse-tree build still resolves.
        prefs.edit().putString(key, value).commit()
    }

    fun get(
        context: Context,
        key: String,
    ): String? {
        memory[key]?.let { return it }
        val persisted =
            context
                .getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
                .getString(key, null)
                ?: return null
        memory[key] = persisted
        return persisted
    }

    /** Test-only: clear in-memory overlay without wiping SharedPreferences. */
    internal fun clearMemoryForTests() {
        memory.clear()
    }
}
