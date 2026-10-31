package cx.aswin.boxlore.feature.history.insights

import cx.aswin.boxlore.core.model.ListeningDayEpisode

/**
 * Where listening was heard, how new its episodes were and what started it, from each day's
 * details (recorded since Room v40, kept in summaries since v41). Listening from before has none,
 * so the cards show only once enough of the period's listening has them: [DETAILS_SHARE] of it
 * and at least [DETAILS_MIN_MS]. How new is a share of the listening whose episode release date is
 * known (kept in summaries since v42), and shows by the same rule once enough of it has one.
 */
internal object InsightsDetails {
    const val DETAILS_SHARE = 0.5f
    const val DETAILS_MIN_MS = 30L * 60_000L

    /**
     * Whether enough of [inPeriod]'s listening has known details to tell where and what: the time heard
     * on a known output, not the whole of a day that has some (one phone's detailed minute and another's
     * hour without details add up to the same day and episode).
     */
    fun hasEnough(inPeriod: List<ListeningDayEpisode>): Boolean {
        val total = inPeriod.sumOf { it.consumedMs }
        val known = inPeriod.sumOf { detailedMs(it) }
        return total > 0L && known >= DETAILS_MIN_MS && known >= total * DETAILS_SHARE
    }

    /** Each output's share of the listening heard on a known one, largest first; empty without enough details. */
    fun outputs(inPeriod: List<ListeningDayEpisode>): List<OutputShare> {
        if (!hasEnough(inPeriod)) return emptyList()
        val heard =
            mapOf(
                ListeningOutput.HEADPHONES to inPeriod.sumOf { it.headphonesMs },
                ListeningOutput.BLUETOOTH to inPeriod.sumOf { it.bluetoothMs },
                ListeningOutput.CAR to inPeriod.sumOf { it.carMs },
                ListeningOutput.SPEAKER to inPeriod.sumOf { it.speakerMs },
                ListeningOutput.CAST to inPeriod.sumOf { it.castMs },
            )
        val total = heard.values.sum().takeIf { it > 0L } ?: return emptyList()
        return heard.filterValues { it > 0L }.map { (output, ms) -> OutputShare(output, ms.toFloat() / total) }.sortedByDescending { it.share }
    }

    /**
     * Of the listening with known details: Lore and the Smart Queue, and of the listening whose release
     * date is known, new episodes ([newShare]); null without enough details.
     */
    fun sources(inPeriod: List<ListeningDayEpisode>): ListeningSources? {
        if (!hasEnough(inPeriod)) return null
        val known = inPeriod.sumOf { detailedMs(it) }.toFloat()
        fun share(ms: Long) = (ms / known).coerceAtMost(1f)
        return ListeningSources(
            newShare = newShare(inPeriod),
            loreShare = share(inPeriod.sumOf { it.loreMs }),
            smartQueueShare = share(inPeriod.sumOf { it.smartQueueMs }),
        )
    }

    /**
     * The share of new episodes (out a week or less) in the listening whose release date is known; an
     * episode without one is neither new nor older. Null until [DETAILS_SHARE] of [inPeriod]'s listening
     * and at least [DETAILS_MIN_MS] has a known date, so a few dated minutes never stand for the period.
     */
    fun newShare(inPeriod: List<ListeningDayEpisode>): Float? {
        val total = inPeriod.sumOf { it.consumedMs }
        val dated = inPeriod.sumOf { it.releaseKnownMs.coerceAtMost(it.consumedMs) }
        if (total <= 0L || dated < DETAILS_MIN_MS || dated < total * DETAILS_SHARE) return null
        return (inPeriod.sumOf { it.newOfReleaseKnownMs.coerceAtMost(it.consumedMs) }.toFloat() / dated).coerceAtMost(1f)
    }

    /**
     * How much of a day and episode was recorded with details, as far as its totals tell: the time on a
     * known output, or more where Lore, the Smart Queue or a known release date account for more (a
     * session on an output Android doesn't name still records what started it and how new it was).
     */
    private fun detailedMs(row: ListeningDayEpisode): Long =
        maxOf(row.outputKnownMs, row.releaseKnownMs, row.newReleaseMs, row.loreMs + row.smartQueueMs).coerceAtMost(row.consumedMs)
}
