package cx.aswin.boxlore.feature.widgets.logic

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers cover requests that ran out of time while a list row was waiting for them, so the rows
 * after it, and the same row on the next redraw, are not each kept waiting again. A request that
 * fails outright is the loader's to remember; this only covers the ones cut off before they finished.
 */
internal class ArtworkTimeoutCooldown(
    private val cooldownMs: Long = COOLDOWN_MS,
    private val nowMs: () -> Long,
) {
    private val timedOutAt = ConcurrentHashMap<String, Long>()

    /** Whether this cover, with these fallbacks, timed out recently and should not be waited for now. */
    fun isCoolingDown(url: String?, fallbackUrls: List<String>): Boolean {
        val key = key(url, fallbackUrls)
        val at = timedOutAt[key] ?: return false
        if (nowMs() - at < cooldownMs) return true
        timedOutAt.remove(key, at)
        return false
    }

    fun recordTimeout(url: String?, fallbackUrls: List<String>) {
        if (timedOutAt.size >= MAX_ENTRIES) timedOutAt.clear()
        timedOutAt[key(url, fallbackUrls)] = nowMs()
    }

    // The fallbacks are part of the request: the same cover with another fallback is another attempt.
    private fun key(url: String?, fallbackUrls: List<String>): String = (listOf(url.orEmpty()) + fallbackUrls).joinToString("\n")

    internal companion object {
        const val COOLDOWN_MS = 5 * 60_000L
        private const val MAX_ENTRIES = 256
    }
}
