package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo

/**
 * A day's richer listening totals for one episode (Room v41), summed from its sessions so they
 * outlive them: the longest single session, where the listening was heard, how much came from Lore
 * and the Smart Queue, and how much was of episodes released within [NEW_RELEASE_WINDOW_MS] before
 * it. A daily summary keeps them, the cloud sync carries them, and a backup saves them; 0 means not
 * known (listening recorded before v41, or a summary from an older build).
 */
data class ListeningDayTotals(
    @ColumnInfo(defaultValue = "0") val longestSessionMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val speakerMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val headphonesMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val bluetoothMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val carMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val castMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val loreMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val smartQueueMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val newReleaseMs: Long = 0L,
) {
    /** These totals with [session] added in. */
    fun plus(session: ListeningSessionEntity): ListeningDayTotals {
        val ms = session.consumedMs
        fun heard(output: String) = if (session.output == output) ms else 0L
        fun from(source: String) = if (session.source == source) ms else 0L
        return ListeningDayTotals(
            longestSessionMs = maxOf(longestSessionMs, ms),
            speakerMs = speakerMs + heard(ListeningSessionValues.OUTPUT_SPEAKER),
            headphonesMs = headphonesMs + heard(ListeningSessionValues.OUTPUT_HEADPHONES),
            bluetoothMs = bluetoothMs + heard(ListeningSessionValues.OUTPUT_BLUETOOTH),
            carMs = carMs + heard(ListeningSessionValues.OUTPUT_CAR),
            castMs = castMs + heard(ListeningSessionValues.OUTPUT_CAST),
            loreMs = loreMs + from(ListeningSessionValues.SOURCE_LORE),
            smartQueueMs = smartQueueMs + from(ListeningSessionValues.SOURCE_SMART_QUEUE),
            newReleaseMs = newReleaseMs + if (isNewRelease(session)) ms else 0L,
        )
    }

    /** Two parts of one day and episode added up (the longest session is the longer of the two). */
    operator fun plus(other: ListeningDayTotals) = ListeningDayTotals(
        longestSessionMs = maxOf(longestSessionMs, other.longestSessionMs),
        speakerMs = speakerMs + other.speakerMs,
        headphonesMs = headphonesMs + other.headphonesMs,
        bluetoothMs = bluetoothMs + other.bluetoothMs,
        carMs = carMs + other.carMs,
        castMs = castMs + other.castMs,
        loreMs = loreMs + other.loreMs,
        smartQueueMs = smartQueueMs + other.smartQueueMs,
        newReleaseMs = newReleaseMs + other.newReleaseMs,
    )

    companion object {
        /** An episode counts as a new release for a week after it came out. */
        const val NEW_RELEASE_WINDOW_MS = 7L * 24 * 60 * 60 * 1_000

        /** Whether [session] played its episode within a week of its release (a future date counts as new). */
        fun isNewRelease(session: ListeningSessionEntity): Boolean {
            val released = session.episodePublishedAt?.takeIf { it > 0L } ?: return false
            return session.startedAt - released <= NEW_RELEASE_WINDOW_MS
        }
    }
}
