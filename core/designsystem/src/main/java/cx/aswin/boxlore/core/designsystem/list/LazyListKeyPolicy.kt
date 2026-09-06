package cx.aswin.boxlore.core.designsystem.list

/**
 * Policy and helpers to prevent Jetpack Compose LazyList, LazyGrid, and LazyStaggeredGrid
 * crashes caused by duplicate keys (IllegalArgumentException: Key "<id>" was already used).
 *
 * Backend feeds, search results, and recommendation rails frequently return duplicate items
 * or items with blank/null identifiers. This policy provides:
 * 1. Safe deduplication preserving order of first appearance.
 * 2. Positional and collision-free key generation ensuring uniqueness under all conditions.
 */
object LazyListKeyPolicy {

    /**
     * Deduplicates a list by its string identifier, preserving order of first occurrence.
     * Items with null, blank, or whitespace-only identifiers are dropped to prevent malformed cards.
     */
    fun <T> deduplicateById(
        items: List<T>,
        idSelector: (T) -> String?,
    ): List<T> {
        if (items.isEmpty()) return emptyList()
        val seenIds = HashSet<String>(items.size)
        val result = ArrayList<T>(items.size)
        for (item in items) {
            val id = idSelector(item)?.trim()
            if (!id.isNullOrEmpty() && seenIds.add(id)) {
                result.add(item)
            }
        }
        return result
    }

    /**
     * General-purpose deduplication preserving order of first occurrence.
     */
    fun <T, K> deduplicateBy(
        items: List<T>,
        keySelector: (T) -> K,
    ): List<T> {
        if (items.isEmpty()) return emptyList()
        val seen = HashSet<K>(items.size)
        val result = ArrayList<T>(items.size)
        for (item in items) {
            if (seen.add(keySelector(item))) {
                result.add(item)
            }
        }
        return result
    }

    /**
     * Generates a single safe key for an item with an optional prefix and index fallback.
     * If the ID is null or blank, falls back to "${prefix}item_${index}" (or "${prefix}item_unknown").
     */
    fun safeKey(
        id: String?,
        index: Int = -1,
        prefix: String = "",
    ): String {
        val rawId = id?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (index >= 0) "item_$index" else "item_unknown"
        return formatPrefixedKey(prefix, rawId)
    }

    /**
     * Generates a 1:1 list of collision-free keys for any list of items.
     * If duplicate IDs appear in the list, subsequent occurrences are disambiguated
     * with an occurrence suffix (`_dup_1`, `_dup_2`), guaranteeing zero collisions in Compose.
     */
    fun <T> disambiguateKeys(
        items: List<T>,
        prefix: String = "",
        idSelector: (T) -> String?,
    ): List<String> {
        if (items.isEmpty()) return emptyList()
        val seenKeys = HashSet<String>(items.size)
        val occurrenceCounts = HashMap<String, Int>(items.size)
        val result = ArrayList<String>(items.size)

        for (index in items.indices) {
            val rawId = idSelector(items[index])?.trim()?.takeIf { it.isNotEmpty() }
                ?: "item_$index"
            val baseKey = formatPrefixedKey(prefix, rawId)
            val count = occurrenceCounts[baseKey] ?: 0
            occurrenceCounts[baseKey] = count + 1

            var candidate = if (count == 0) baseKey else "${baseKey}_dup_$count"
            var suffix = count + 1
            while (!seenKeys.add(candidate)) {
                candidate = "${baseKey}_dup_${suffix++}"
            }
            result.add(candidate)
        }
        return result
    }

    /**
     * Returns a key selector mapping an item index to its disambiguated unique key.
     */
    fun <T> buildKeySelector(
        items: List<T>,
        prefix: String = "",
        idSelector: (T) -> String?,
    ): (Int) -> String {
        val keys = disambiguateKeys(items, prefix, idSelector)
        return { index -> keys[index] }
    }

    private fun formatPrefixedKey(prefix: String, key: String): String {
        val cleanPrefix = prefix.trim()
        return when {
            cleanPrefix.isEmpty() -> key
            cleanPrefix.endsWith("_") -> "$cleanPrefix$key"
            else -> "${cleanPrefix}_$key"
        }
    }
}
