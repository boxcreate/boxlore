package cx.aswin.boxlore.core.rss

import java.security.MessageDigest

/**
 * boxlore's own episode IDs (recipe "boxlore-ep1").
 *
 * An episode is identified by (show ID, catalog key). The catalog key is the
 * GUID, or the enclosure URL when the GUID is blank, trimmed with an explicit
 * character set: exactly what Kotlin's `String.trim()` removes on Android,
 * written out so identity never depends on the runtime's Unicode tables.
 * Android's libcore counts U+180E as whitespace and the desktop JVM doesn't;
 * the set follows Android because that's where stored keys were trimmed.
 *
 * ```
 * recipeId = 2^52 + (first 8 bytes of SHA-256("boxlore-ep1" \0 showId \0 key),
 *                    read big-endian, AND 2^52 - 1)
 * ```
 *
 * A recipe ID is a compact handle, not a unique key: storage that holds one
 * also keeps the catalog key and never merges two keys that share an ID.
 * Every implementation must reproduce `fixtures/episode-recipe-vectors.json`.
 *
 * Added in P0 of the own-episode-IDs plan (plan section 4, recipe spec).
 * COMPAT(own-ids): the recipe and trim set behind every issued episode ID. Permanent: never remove (MIGRATION.md §5).
 */
object EpisodeRecipeId {
    const val RECIPE: String = "boxlore-ep1"
    const val MIN: Long = 1L shl 52
    const val MAX: Long = (1L shl 53) - 1
    private const val LOW_52_BITS: Long = MIN - 1

    /** Largest show ID with recipe IDs: 2^53 - 1, so JS (`Number.MAX_SAFE_INTEGER`) agrees on every namespace. */
    const val SHOW_ID_MAX: Long = (1L shl 53) - 1
    private const val DIGEST_PREFIX_BYTES = 8

    /** True for the UTF-16 units the recipe trims (Char.isWhitespace on Android). */
    fun isTrimChar(c: Char): Boolean = when (c.code) {
        in 0x0009..0x000D,
        in 0x001C..0x0020,
        0x00A0,
        0x1680,
        0x180E,
        in 0x2000..0x200A,
        0x2028,
        0x2029,
        0x202F,
        0x205F,
        0x3000,
        -> true
        else -> false
    }

    /** Trimmed [value], or null when nothing is left. */
    fun trimKey(value: String?): String? = value?.trim(::isTrimChar)?.takeIf(String::isNotEmpty)

    /** Trimmed GUID, else trimmed enclosure URL; null means the item has no identity and is skipped. */
    fun catalogKey(guid: String?, enclosureUrl: String?): String? = trimKey(guid) ?: trimKey(enclosureUrl)

    /**
     * Recipe ID for an already-trimmed [catalogKey] on a numeric show.
     *
     * @throws IllegalArgumentException when [showId] is outside 1..[SHOW_ID_MAX] or the key isn't canonical
     */
    fun recipeId(showId: Long, catalogKey: String): Long {
        require(showId in 1..SHOW_ID_MAX) { "Recipe IDs need a numeric show ID in 1..2^53 - 1" }
        require(catalogKey.isNotEmpty() && trimKey(catalogKey) == catalogKey) {
            "Recipe IDs need a trimmed, non-empty catalog key"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$RECIPE\u0000$showId\u0000$catalogKey".toByteArray(Charsets.UTF_8))
        var prefix = 0L
        for (i in 0 until DIGEST_PREFIX_BYTES) {
            prefix = (prefix shl Byte.SIZE_BITS) or (digest[i].toLong() and 0xFF)
        }
        return MIN + (prefix and LOW_52_BITS)
    }

    /** Recipe ID for a feed item, or null when it has no catalog key. */
    fun forItem(showId: Long, guid: String?, enclosureUrl: String?): Long? =
        catalogKey(guid, enclosureUrl)?.let { recipeId(showId, it) }

    /** True for IDs in the recipe range; Podcast Index and negative RSS IDs never are. */
    fun isRecipeId(id: Long): Boolean = id in MIN..MAX
}
