package cx.aswin.boxlore.core.model

/**
 * Media3 [customCacheKey] for episode audio.
 *
 * Briefing audio is overwritten in place on the same date (same episode id
 * `briefing_{region}_{date}`) while the signed URL carries a content hash in `v=`.
 * Cache keys that ignore `v` keep playing yesterday's bytes after a same-day regen.
 */
object EpisodeMediaCacheKey {
    fun of(episodeId: String, audioUrl: String?,): String {
        if (!episodeId.startsWith("briefing_")) return episodeId
        val version = audioVersionQueryParam(audioUrl) ?: return episodeId
        return "${episodeId}_$version"
    }

    /** Extracts the `v` query param without Android Uri (pure JVM). */
    fun audioVersionQueryParam(audioUrl: String?): String? {
        if (audioUrl.isNullOrBlank()) return null
        val query = audioUrl.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        for (part in query.split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            if (part.substring(0, eq) != "v") continue
            val value = part.substring(eq + 1).trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }
}
