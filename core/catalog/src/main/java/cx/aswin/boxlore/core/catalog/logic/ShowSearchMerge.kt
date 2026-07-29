package cx.aswin.boxlore.core.catalog.logic

import cx.aswin.boxlore.core.model.Podcast

/** Where a show search hit came from for grouped UI. */
enum class ShowSearchGroup {
    /** Meili catalog typeahead (episodeCount≥4 index). */
    CATALOG,
    /** Hybrid PodIndex / iTunes / Turso — coverage for shows missing from Meili. */
    ALSO_FOUND,
}

data class GroupedShowSearchResult(
    val catalog: List<Podcast>,
    val alsoFound: List<Podcast>,
    val correctedQuery: String? = null,
) {
    val all: List<Podcast>
        get() = catalog + alsoFound
}

/**
 * Merge Meili typeahead + hybrid `/search` into catalog / also-found groups.
 * Prefer catalog (Meili) when the same show appears in both (matched by id, itunes:, or feed URL).
 */
fun mergeShowSearchResults(
    typeahead: List<Podcast>,
    hybrid: List<Podcast>,
): GroupedShowSearchResult {
    val seenKeys = mutableSetOf<String>()
    val catalog = mutableListOf<Podcast>()
    val alsoFound = mutableListOf<Podcast>()

    fun tryAdd(
        target: MutableList<Podcast>,
        podcast: Podcast,
    ) {
        val keys = podcastIdentityKeys(podcast)
        if (keys.any { it in seenKeys }) return
        seenKeys.addAll(keys)
        target.add(podcast)
    }

    for (p in typeahead) tryAdd(catalog, p)
    for (p in hybrid) tryAdd(alsoFound, p)
    return GroupedShowSearchResult(catalog = catalog, alsoFound = alsoFound)
}

/** Identity keys used to dedupe across Meili pid, itunes:, and feed URL. */
fun podcastIdentityKeys(podcast: Podcast): Set<String> {
    val keys = linkedSetOf<String>()
    val id = podcast.id.trim().lowercase()
    if (id.isNotEmpty() && id != "0") {
        keys.add("id:$id")
        if (id.startsWith("itunes:")) {
            val itunes = id.removePrefix("itunes:")
            if (itunes.isNotEmpty()) keys.add("itunes:$itunes")
        }
    }
    val feed = podcast.feedUrl?.trim()?.lowercase().orEmpty()
    if (feed.isNotEmpty()) keys.add("url:$feed")
    if (keys.isEmpty()) {
        keys.add(
            "title:${podcast.title.trim().lowercase()}|${podcast.artist.trim().lowercase()}",
        )
    }
    return keys
}

fun podcastDedupeKey(podcast: Podcast): String =
    podcastIdentityKeys(podcast).firstOrNull() ?: "unknown"
