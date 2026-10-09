package cx.aswin.boxlore.feature.info.logic

/** Always prefer episode art over cached show art; only fall back after an unavailable/monochrome result. */
internal suspend fun loadArtworkSeed(urls: List<String>, cached: (String) -> Int?, fetch: suspend (String) -> Int?): Int? {
    for (url in urls) {
        val seed = cached(url) ?: fetch(url)
        if (seed != null) return seed
    }
    return null
}
