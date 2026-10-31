package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo

/**
 * A day's richer listening totals for one episode (Room v41), summed from its sessions so they
 * outlive them: the longest single session, where the listening was heard, how much came from Lore
 * and the Smart Queue, how much was of episodes released within [NEW_RELEASE_WINDOW_MS] before it,
 * and (Room v42) how much was of episodes whose release date is known, so new releases are a share of
 * that time alone. A daily summary keeps them, the cloud sync carries them, and a backup saves them;
 * 0 means not known (listening recorded before v41, or a summary from an older build; for
 * [releaseKnownMs], a summary from before v42).
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
    /** Listening of episodes with a known release date (Room v42); [newReleaseMs] is part of it once known. */
    @ColumnInfo(defaultValue = "0") val releaseKnownMs: Long = 0L,
) {
    /** These totals with [session] added in. */
    fun plus(session: ListeningSessionEntity): ListeningDayTotals {
        val ms = session.consumedMs
        fun heard(output: String) = if (session.output == output) ms else 0L
        fun from(source: String) = if (session.source == source) ms else 0L
        val sessionKnownMs = if (isReleaseKnown(session)) ms else 0L
        return ListeningDayTotals(
            longestSessionMs = maxOf(longestSessionMs, ms),
            speakerMs = speakerMs + heard(ListeningSessionValues.OUTPUT_SPEAKER),
            headphonesMs = headphonesMs + heard(ListeningSessionValues.OUTPUT_HEADPHONES),
            bluetoothMs = bluetoothMs + heard(ListeningSessionValues.OUTPUT_BLUETOOTH),
            carMs = carMs + heard(ListeningSessionValues.OUTPUT_CAR),
            castMs = castMs + heard(ListeningSessionValues.OUTPUT_CAST),
            loreMs = loreMs + from(ListeningSessionValues.SOURCE_LORE),
            smartQueueMs = smartQueueMs + from(ListeningSessionValues.SOURCE_SMART_QUEUE),
            newReleaseMs = newReleaseOf(newReleaseMs, releaseKnownMs, if (isNewRelease(session)) ms else 0L, sessionKnownMs),
            releaseKnownMs = releaseKnownMs + sessionKnownMs,
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
        newReleaseMs = newReleaseOf(newReleaseMs, releaseKnownMs, other.newReleaseMs, other.releaseKnownMs),
        releaseKnownMs = releaseKnownMs + other.releaseKnownMs,
    )

    /**
     * These totals of some listening, with what [other] (totals of the same listening, from a backup or
     * the cloud) knows better: all of them where these hold none, else the release dates (Room v42)
     * where [other] knows them for more of it (a copy that summed part of the day before v42 knows less).
     */
    fun fillingIn(other: ListeningDayTotals): ListeningDayTotals = when {
        this == ListeningDayTotals() -> other
        other.releaseKnownMs > releaseKnownMs -> copy(newReleaseMs = other.newReleaseMs, releaseKnownMs = other.releaseKnownMs)
        else -> this
    }

    companion object {
        /**
         * The longest sitting of two parts of a day and episode: the longer known one, or 0 (not known)
         * when a part whose longest isn't known holds more listening than that, so could hide a longer one.
         */
        fun longestOf(aLongestMs: Long, aConsumedMs: Long, bLongestMs: Long, bConsumedMs: Long): Long {
            val known = maxOf(aLongestMs, bLongestMs)
            val hidden = (aLongestMs == 0L && aConsumedMs > known) || (bLongestMs == 0L && bConsumedMs > known)
            return if (hidden) 0L else known
        }

        /**
         * The new-release time of two parts of a day and episode added up so it stays part of the time
         * with a known release date: once either part knows that time, a part that doesn't (a summary
         * from before Room v42) counts none of its new releases, or new would outweigh what is known.
         */
        fun newReleaseOf(aNewMs: Long, aKnownMs: Long, bNewMs: Long, bKnownMs: Long): Long {
            if (aKnownMs == 0L && bKnownMs == 0L) return aNewMs + bNewMs
            return (if (aKnownMs > 0L) aNewMs else 0L) + (if (bKnownMs > 0L) bNewMs else 0L)
        }

        /** An episode counts as a new release for a week after it came out. */
        const val NEW_RELEASE_WINDOW_MS = 7L * 24 * 60 * 60 * 1_000

        /** Whether [session]'s episode has a known release date (Room v40 sessions store it when the app knew it). */
        fun isReleaseKnown(session: ListeningSessionEntity): Boolean = (session.episodePublishedAt ?: 0L) > 0L

        /** Whether [session] played its episode within a week of its release (a future date counts as new). */
        fun isNewRelease(session: ListeningSessionEntity): Boolean {
            val released = session.episodePublishedAt?.takeIf { it > 0L } ?: return false
            return session.startedAt - released <= NEW_RELEASE_WINDOW_MS
        }
    }
}
