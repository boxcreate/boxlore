package cx.aswin.boxlore.core.database

/**
 * When an episode was first played and first finished by listening (`listening_history.firstPlayedAt`
 * and `finishedAt`, Room v40).
 *
 * - `null`: not yet (or not known for a row another device or a backup brought without the date).
 * - [BEFORE_RECORDING] (0): it happened before boxlore recorded these dates. Every row stored before
 *   v40 reads 0 for `firstPlayedAt`, and finished rows read 0 for `finishedAt`.
 * - Otherwise: epoch milliseconds.
 *
 * The earliest known value wins, and 0 counts as earlier than any recorded date: a play or finish
 * before recording began happened before every date recorded since.
 */
object ListeningDates {
    const val BEFORE_RECORDING = 0L

    /** The earlier of two values, where `null` means none and 0 is earlier than any date. */
    fun earliest(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }
}

/** The stored values a whole-row history write must keep ([ListeningHistoryDao.upsert]). */
data class StoredListeningDates(val episodeId: String, val firstPlayedAt: Long?, val finishedAt: Long?,)

/**
 * This row as it should be written over [stored]: each listening date keeps the earliest of the two.
 * A finish is dated only by playback (its own writes date it); a write from sync, a backup or a mark
 * never infers one from a completion it carries.
 */
fun ListeningHistoryEntity.keepingListeningDatesOf(stored: StoredListeningDates?): ListeningHistoryEntity {
    if (stored == null) return this
    val first = ListeningDates.earliest(stored.firstPlayedAt, firstPlayedAt)
    val finished = ListeningDates.earliest(stored.finishedAt, finishedAt)
    return if (first == firstPlayedAt && finished == finishedAt) this else copy(firstPlayedAt = first, finishedAt = finished)
}
