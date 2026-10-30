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
data class StoredListeningDates(
    val episodeId: String,
    val firstPlayedAt: Long?,
    val finishedAt: Long?,
    val isCompleted: Boolean,
    val isManualCompletion: Boolean,
    val isBulkCompletion: Boolean,
) {
    val finishedByListening: Boolean get() = isCompleted && !isManualCompletion && !isBulkCompletion
}

/**
 * This row as it should be written over [stored]: each listening date keeps the earliest of the two,
 * and a write that finishes a stored, not yet finished episode by listening dates the finish with its
 * play time when no finish is known. A new row keeps its own values.
 */
fun ListeningHistoryEntity.keepingListeningDatesOf(stored: StoredListeningDates?): ListeningHistoryEntity {
    if (stored == null) return this
    val first = ListeningDates.earliest(stored.firstPlayedAt, firstPlayedAt)
    var finished = ListeningDates.earliest(stored.finishedAt, finishedAt)
    val finishesNow = isCompleted && !isManualCompletion && !isBulkCompletion && !stored.finishedByListening
    if (finished == null && finishesNow && lastPlayedAt > 0L) finished = lastPlayedAt
    return if (first == firstPlayedAt && finished == finishedAt) this else copy(firstPlayedAt = first, finishedAt = finished)
}
