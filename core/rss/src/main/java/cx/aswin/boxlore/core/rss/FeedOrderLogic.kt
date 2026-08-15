package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalFeedOrder

/**
 * Classifies publisher feed item order from published dates in document order.
 * Prefix-stop for tip-only is allowed only for [LocalFeedOrder.NEWEST_FIRST].
 */
object FeedOrderLogic {
    private const val MIN_PAIRS = 2
    private const val DIRECTION_RATIO = 0.8

    fun classify(publishedDatesInDocOrder: List<Long>): String {
        if (publishedDatesInDocOrder.size <= MIN_PAIRS) return LocalFeedOrder.MIXED
        var descending = 0
        var ascending = 0
        for (index in 1 until publishedDatesInDocOrder.size) {
            val prev = publishedDatesInDocOrder[index - 1]
            val next = publishedDatesInDocOrder[index]
            if (next < prev) descending++
            if (next > prev) ascending++
        }
        val pairs = publishedDatesInDocOrder.size - 1
        val threshold = pairs * DIRECTION_RATIO
        return when {
            descending >= threshold -> LocalFeedOrder.NEWEST_FIRST
            ascending >= threshold -> LocalFeedOrder.OLDEST_FIRST
            else -> LocalFeedOrder.MIXED
        }
    }

    fun newestByPublishedDate(dates: List<Long>): Long? = dates.maxOrNull()
}
