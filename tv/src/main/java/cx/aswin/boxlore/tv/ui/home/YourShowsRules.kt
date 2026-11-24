package cx.aswin.boxlore.tv.ui.home

/**
 * How Home lays out Your shows: up to three rows of eight covers, filling the width. The cover the
 * remote is on widens into a pill with the show's name, and the other covers in its row shrink to
 * make room, so the whole row stays on screen. More shows than that wait behind All shows.
 */
object YourShowsRules {
    /** Covers per row. */
    const val PER_ROW = 8

    private const val MAX_ROWS = 3

    /** Home shows at most this many, so no row runs off the screen; All shows opens the rest in Library. */
    const val HOME_LIMIT = PER_ROW * MAX_ROWS

    /** Sizes in dp: a cover at rest, the pill one widens into, a cover shrunk beside it, and the gap. */
    const val COVER = 80f
    const val PILL = 252f
    const val SHRUNK = 56f
    const val GAP = 20f

    /** One row up to 8 shows, two up to 16, three beyond that. */
    fun rowCount(shows: Int): Int = (shows.coerceIn(0, HOME_LIMIT) + PER_ROW - 1) / PER_ROW

    /** Home's shows in rows of eight, best-ranked first, left to right and top to bottom. */
    fun <T> arrange(shows: List<T>): List<List<T>> = shows.take(HOME_LIMIT).chunked(PER_ROW)

    /**
     * The column Up or Down lands on in row [row], coming from [column]: the same column, or the
     * row's last cover when it is shorter. Null when there is no such row, so the remote leaves the
     * covers as usual.
     */
    fun sameColumn(rowSizes: List<Int>, row: Int, column: Int): Int? {
        val size = rowSizes.getOrNull(row) ?: return null
        return if (size <= 0) null else column.coerceAtMost(size - 1)
    }

    /** How wide a row of [count] covers is, with one of them widened into its pill or none. */
    fun rowWidth(count: Int, withPill: Boolean): Float {
        if (count <= 0) return 0f
        val gaps = (count - 1) * GAP
        return if (withPill) PILL + (count - 1) * SHRUNK + gaps else count * COVER + gaps
    }
}

/** Splits a length in minutes into hours and minutes for the time labels. */
object TimeRules {
    private const val MINUTES_PER_HOUR = 60

    /** (hours, minutes); hours is 0 below an hour. */
    fun split(totalMinutes: Int): Pair<Int, Int> {
        val safe = totalMinutes.coerceAtLeast(0)
        return safe / MINUTES_PER_HOUR to safe % MINUTES_PER_HOUR
    }

    /** "m:ss" or "h:mm:ss" for the player's elapsed time. */
    fun clock(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = safe % 3600 / 60
        val seconds = safe % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}
