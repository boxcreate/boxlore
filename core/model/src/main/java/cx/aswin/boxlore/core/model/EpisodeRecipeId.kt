package cx.aswin.boxlore.core.model

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
 * Every implementation must reproduce `fixtures/episode-recipe-vectors.json`
 * (kept with its tests in `core/rss/src/test`, which the scripts copy also reads).
 *
 * Added in P0 of the own-episode-IDs plan (plan section 4, recipe spec). Moved
 * from `:core:rss` to `:core:model` unchanged in P4 (plan section 11) so the Room
 * migration in `:core:database` can seed episode aliases in the same transaction;
 * P4 also added [showIdOrNull] and [forNamespace].
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
    private const val MAX_SHOW_ID_DIGITS = 16

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

    /**
     * Recipe ID for an already-trimmed [catalogKey] in the episode namespace [epNs].
     *
     * The namespace is the value stored for the show when its first ID was issued.
     * Today it equals the show ID for every show, and it never changes once issued,
     * so callers that mint for a show pass the stored namespace, not a fresh lookup.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 4, recipe spec).
     * COMPAT(own-ids): the entry point new episode IDs are minted through. Permanent.
     *
     * @throws IllegalArgumentException when [epNs] is outside 1..[SHOW_ID_MAX] or the key isn't canonical
     */
    fun forNamespace(epNs: Long, catalogKey: String): Long = recipeId(epNs, catalogKey)

    /**
     * Show ID parsed from its stored decimal form, or null when the show can't have recipe IDs.
     *
     * Accepts exactly what the scripts copy accepts: ASCII digits with no sign, no leading
     * zero and no surrounding whitespace, at most 16 digits and at most [SHOW_ID_MAX].
     * `rss:` show IDs, `0123`, `+5`, ` 5` and `9007199254740992` all return null.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 4, recipe spec).
     * COMPAT(own-ids): the one parse from a stored podcast ID to a recipe namespace. Permanent.
     */
    fun showIdOrNull(podcastId: String): Long? {
        val canonical = podcastId.length in 1..MAX_SHOW_ID_DIGITS &&
            podcastId[0] in '1'..'9' &&
            podcastId.all { it in '0'..'9' }
        return if (canonical) podcastId.toLong().takeIf { it <= SHOW_ID_MAX } else null
    }

    /** Recipe ID for a feed item, or null when it has no catalog key. */
    fun forItem(showId: Long, guid: String?, enclosureUrl: String?): Long? =
        catalogKey(guid, enclosureUrl)?.let { recipeId(showId, it) }

    /** True for IDs in the recipe range; Podcast Index and negative RSS IDs never are. */
    fun isRecipeId(id: Long): Boolean = id in MIN..MAX
}
