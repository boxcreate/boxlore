package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.ExactPodcastLookupResult
import cx.aswin.boxlore.core.catalog.PodcastIndexSearchResult
import cx.aswin.boxlore.core.model.Podcast

internal sealed interface OpmlCatalogDecision {
    data class Found(
        val podcast: Podcast,
    ) : OpmlCatalogDecision

    data object ConfirmedAbsent : OpmlCatalogDecision

    data object Deferred : OpmlCatalogDecision
}

/**
 * OPML outlines carry publisher feed URLs. Matching the catalog first keeps
 * notifications and other PI-only features; true `rss:` rows are only the fallback
 * when the show is not in the index.
 *
 * Settings → Add RSS does not use this path.
 */
internal object OpmlImportLogic {
    fun finalCatalogDecision(
        opmlTitle: String,
        opmlXmlUrl: String,
        urlLookup: ExactPodcastLookupResult,
        guidLookup: ExactPodcastLookupResult,
        titleSearch: PodcastIndexSearchResult,
    ): OpmlCatalogDecision {
        if (urlLookup is ExactPodcastLookupResult.Failed ||
            guidLookup is ExactPodcastLookupResult.Failed
        ) {
            return OpmlCatalogDecision.Deferred
        }
        val urlPodcast = (urlLookup as? ExactPodcastLookupResult.Found)?.podcast
        val guidPodcast = (guidLookup as? ExactPodcastLookupResult.Found)?.podcast
        if (urlPodcast != null && guidPodcast != null && urlPodcast.id != guidPodcast.id) {
            return OpmlCatalogDecision.Deferred
        }
        (urlPodcast ?: guidPodcast)?.takeUnless { it.isRss }?.let {
            return OpmlCatalogDecision.Found(it)
        }
        if (titleSearch is PodcastIndexSearchResult.Failed) {
            return OpmlCatalogDecision.Deferred
        }
        val searchPodcasts = (titleSearch as PodcastIndexSearchResult.Success).podcasts
        val match =
            catalogMatch(
                opmlTitle = opmlTitle,
                opmlXmlUrl = opmlXmlUrl,
                urlLookup = null,
                titleSearch = searchPodcasts,
            )
        return match?.let { OpmlCatalogDecision.Found(it) } ?: OpmlCatalogDecision.ConfirmedAbsent
    }

    fun preferLookup(
        initial: ExactPodcastLookupResult,
        redirected: ExactPodcastLookupResult,
    ): ExactPodcastLookupResult =
        when {
            redirected is ExactPodcastLookupResult.Found -> redirected
            initial is ExactPodcastLookupResult.Found -> initial
            initial is ExactPodcastLookupResult.Failed ||
                redirected is ExactPodcastLookupResult.Failed ->
                ExactPodcastLookupResult.Failed
            else -> ExactPodcastLookupResult.NotFound
        }

    fun collapseCandidateLookups(results: List<ExactPodcastLookupResult>): ExactPodcastLookupResult {
        results.filterIsInstance<ExactPodcastLookupResult.Found>().firstOrNull()?.let { return it }
        return if (results.any { it is ExactPodcastLookupResult.Failed }) {
            ExactPodcastLookupResult.Failed
        } else {
            ExactPodcastLookupResult.NotFound
        }
    }

    suspend fun firstExactLookup(
        candidates: List<String>,
        lookup: suspend (String) -> ExactPodcastLookupResult,
    ): ExactPodcastLookupResult {
        val seen = ArrayList<ExactPodcastLookupResult>(candidates.size)
        for (candidate in candidates) {
            val result = lookup(candidate)
            if (result is ExactPodcastLookupResult.Found) return result
            seen += result
        }
        return collapseCandidateLookups(seen)
    }

    fun catalogMatch(
        opmlTitle: String,
        opmlXmlUrl: String,
        urlLookup: Podcast?,
        titleSearch: List<Podcast>,
    ): Podcast? {
        urlLookup?.takeUnless { it.isRss }?.let { return it }
        val titleKey = normalizeTitle(opmlTitle)
        val feedKey = canonicalFeedUrl(opmlXmlUrl)
        return titleSearch.firstOrNull { candidate ->
            if (candidate.isRss) return@firstOrNull false
            val sameTitle = titleKey.isNotEmpty() && normalizeTitle(candidate.title) == titleKey
            val sameFeed = feedKey != null && canonicalFeedUrl(candidate.feedUrl) == feedKey
            sameTitle || sameFeed
        }
    }

    /**
     * Outline / redirected feed URLs often differ from the index by scheme,
     * trailing slash, or `www`.
     */
    internal fun urlLookupCandidates(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        addSlashVariants(out, trimmed)
        schemeSwap(trimmed)?.let { addSlashVariants(out, it) }
        wwwToggle(trimmed)?.let { swapped ->
            addSlashVariants(out, swapped)
            schemeSwap(swapped)?.let { addSlashVariants(out, it) }
        }
        return out.toList()
    }

    /** RSS fetch requires HTTPS; rewrite `http://` outlines when peeking the feed. */
    internal fun httpsFeedUrl(raw: String): String? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) ->
                "https://" + trimmed.substring("http://".length)
            else -> null
        }
    }

    internal fun canonicalFeedUrl(url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return value.trimEnd('/').lowercase()
    }

    private fun addSlashVariants(
        out: MutableSet<String>,
        url: String,
    ) {
        out += url
        val noSlash = url.trimEnd('/')
        out += noSlash
        if (noSlash == url) out += "$url/"
    }

    private fun schemeSwap(url: String): String? =
        when {
            url.startsWith("http://", ignoreCase = true) ->
                "https://" + url.substring("http://".length)
            url.startsWith("https://", ignoreCase = true) ->
                "http://" + url.substring("https://".length)
            else -> null
        }

    private fun wwwToggle(url: String): String? {
        val schemeEnd = url.indexOf("://").takeIf { it >= 0 } ?: return null
        val scheme = url.substring(0, schemeEnd + 3)
        val rest = url.substring(schemeEnd + 3)
        return when {
            rest.startsWith("www.", ignoreCase = true) -> scheme + rest.substring("www.".length)
            else -> scheme + "www." + rest
        }
    }

    private fun normalizeTitle(value: String): String = value.trim().lowercase()
}
