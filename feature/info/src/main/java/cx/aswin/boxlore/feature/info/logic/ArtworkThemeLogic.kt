package cx.aswin.boxlore.feature.info.logic

/** Always prefer episode art over cached show art; only fall back after an unavailable/monochrome result. */
internal suspend fun <T : Any> loadArtworkSeed(urls: List<String>, cached: (String) -> T?, fetch: suspend (String) -> T?): T? {
    for (url in urls) {
        val seed = cached(url) ?: fetch(url)
        if (seed != null) return seed
    }
    return null
}
