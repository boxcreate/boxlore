package cx.aswin.boxlore.feature.history.insights

import cx.aswin.boxlore.core.model.ListeningDayEpisode

/**
 * Where listening was heard, how new its episodes were and what started it, from each day's
 * details (recorded since Room v40, kept in summaries since v41). Listening from before has none,
 * so the cards show only once enough of the period's listening has them: [DETAILS_SHARE] of it
 * and at least [DETAILS_MIN_MS].
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
        val known = inPeriod.sumOf { it.outputKnownMs }
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

    /** Of the listening with known details: new episodes, Lore and the Smart Queue; null without enough details. */
    fun sources(inPeriod: List<ListeningDayEpisode>): ListeningSources? {
        if (!hasEnough(inPeriod)) return null
        val known = inPeriod.sumOf { it.outputKnownMs }.toFloat()
        fun share(ms: Long) = (ms / known).coerceAtMost(1f)
        return ListeningSources(
            newShare = share(inPeriod.sumOf { it.newReleaseMs }),
            loreShare = share(inPeriod.sumOf { it.loreMs }),
            smartQueueShare = share(inPeriod.sumOf { it.smartQueueMs }),
        )
    }
}
