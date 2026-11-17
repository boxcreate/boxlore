package cx.aswin.boxlore.feature.home.logic

/**
 * When selecting a show chip on Home reads that show's feed: only a listener's tap (never the
 * automatic selection of a library's only show), and at most once per show per [minIntervalMs],
 * even inside the feed's six-hour wait. Tapping between chips never repeats a publisher read.
 *
 * [shared] is process-wide, so a recreated Home screen keeps the same limit.
 */
internal class HomeChipFeedReads(private val minIntervalMs: Long = MIN_INTERVAL_MS) {
    private val lastReadAtMs = HashMap<String, Long>()

    /** Whether this selection reads [podcastId]'s feed now; a yes counts as its read. */
    @Synchronized
    fun claim(
        podcastId: String,
        explicitTap: Boolean,
        nowMs: Long,
    ): Boolean {
        if (!explicitTap) return false
        val last = lastReadAtMs[podcastId]
        if (last != null && nowMs - last in 0 until minIntervalMs) return false
        lastReadAtMs[podcastId] = nowMs
        return true
    }

    companion object {
        const val MIN_INTERVAL_MS = 15 * 60 * 1000L

        val shared = HomeChipFeedReads()
    }
}
