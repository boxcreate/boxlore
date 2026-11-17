package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.HistoryItem

/*
 * Which followed shows recommendations start from, and which picks they leave out
 * ([v2RecommendationRequest] and the `/v2` answers it gets).
 */

/** One day in ms: [recommendationFollowOrder] groups follows by day and changes its shuffle daily. */
private const val DAY_MS = 24L * 60L * 60L * 1000L

/**
 * [follows] (distinct) in the order recommendations use them, so the follow seeds and the
 * [MAX_RECOMMENDATION_IDS] followed shows a request carries are the shows the listener uses, never
 * the first ones by title: shows in [history] (most recent first) by their latest play, then the
 * rest by the day they were followed ([followedAt], newest day first). Shows followed on the same
 * day, such as a whole OPML import or a restore, are in a shuffle that changes once a day
 * ([nowMillis]), so the same few never always win.
 */
internal fun recommendationFollowOrder(
    follows: List<String>,
    history: List<HistoryItem>,
    followedAt: Map<String, Long>,
    nowMillis: Long,
): List<String> {
    val distinct = follows.distinct()
    if (distinct.size < 2) return distinct
    val playRank = HashMap<String, Int>()
    history.forEachIndexed { index, item -> item.podcastId?.let { playRank.putIfAbsent(it, index) } }
    val day = Math.floorDiv(nowMillis, DAY_MS)
    return distinct.sortedWith(
        compareBy<String> { playRank[it] ?: Int.MAX_VALUE }
            .thenByDescending { Math.floorDiv(followedAt[it] ?: 0L, DAY_MS) }
            .thenBy { dailyShuffleKey(it, day) },
    )
}

/** A key that orders shows differently each [day] but the same all day (a SplitMix64 finish of the ID and the day). */
private fun dailyShuffleKey(id: String, day: Long): Long {
    var h = (id.hashCode().toLong() shl 32) xor day
    h = (h xor (h ushr 30)) * -0x40a7b892e31b1a47L
    h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L
    return h xor (h ushr 31)
}

/**
 * These episodes without the ones from shows in [followedIds]. The API leaves out the followed
 * shows a request names, at most [MAX_RECOMMENDATION_IDS]; this covers the rest of a larger
 * library, and last good picks kept from before a follow.
 */
internal fun List<Episode>.withoutFollowed(followedIds: Collection<String>): List<Episode> {
    if (followedIds.isEmpty()) return this
    val followed = followedIds.toHashSet()
    return filterNot { it.podcastId in followed }
}
