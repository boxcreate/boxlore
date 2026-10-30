package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per show the listener has played, followed or not (Room v40), so insights know a show's
 * name, art and genre and when it was first and last played without following it.
 *
 * The v40 upgrade fills it from listening history with [firstPlayedAt] = [ListeningDates.BEFORE_RECORDING];
 * later plays add and update rows ([PlayedShowDao.recordPlay]). [isDirty] marks rows the next cloud
 * sync push sends.
 */
@Entity(
    tableName = "played_shows",
    indices = [Index(value = ["isDirty"])],
)
data class PlayedShowEntity(
    @PrimaryKey
    val podcastId: String,
    val podcastName: String,
    val imageUrl: String?,
    /** The show's genre when known; null when unknown (never the "Podcast" placeholder). */
    val genre: String?,
    /** First play: 0 when it was before boxlore recorded it ([ListeningDates]). */
    val firstPlayedAt: Long,
    val lastPlayedAt: Long,
    val isDirty: Boolean = true,
) {
    /**
     * The values of this row merged with [other] for the same show: the earliest first play, the
     * latest last play, and name, art and genre from the later play when it has them. Keeps this
     * row's [isDirty]; callers decide whether the merge needs a push.
     */
    fun mergedWith(other: PlayedShowEntity): PlayedShowEntity {
        val (older, newer) = if (other.lastPlayedAt > lastPlayedAt) this to other else other to this
        return copy(
            podcastName = newer.podcastName.ifBlank { older.podcastName },
            imageUrl = newer.imageUrl?.takeIf { it.isNotBlank() } ?: older.imageUrl,
            genre = newer.genre ?: older.genre,
            firstPlayedAt = minOf(firstPlayedAt, other.firstPlayedAt),
            lastPlayedAt = maxOf(lastPlayedAt, other.lastPlayedAt),
        )
    }

    /** Whether this row holds the same show values as [other], ignoring [isDirty]. */
    fun sameValuesAs(other: PlayedShowEntity): Boolean = copy(isDirty = other.isDirty) == other

    companion object {
        /** A show's genre as stored: blank and the generic "Podcast" placeholder mean unknown. */
        fun storedGenre(genre: String?): String? = genre?.trim()?.takeIf { it.isNotEmpty() && !it.equals(PLACEHOLDER_GENRE, ignoreCase = true) }

        private const val PLACEHOLDER_GENRE = "Podcast"
    }
}
