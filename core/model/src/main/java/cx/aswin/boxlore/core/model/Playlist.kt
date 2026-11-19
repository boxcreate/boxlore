package cx.aswin.boxlore.core.model

/**
 * A listener's playlist: a set of shows played as one list. Its episodes are worked out when it
 * plays (the shows' unfinished episodes in [order]), so it never runs out while the shows publish.
 * [coverUrls] are the artwork of its first few shows, in the order they were added.
 */
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val showCount: Int,
    val order: PlaylistOrder = PlaylistOrder.NEWEST,
    val coverUrls: List<String> = emptyList(),
)

/** Which end of the release dates a playlist plays from. [stored] is the value kept on the phone and in backups. */
enum class PlaylistOrder(val stored: String) {
    NEWEST("newest"),
    OLDEST("oldest"),
    ;

    companion object {
        /** Unknown or missing values read as [NEWEST]. */
        fun fromStored(value: String?): PlaylistOrder = entries.firstOrNull { it.stored == value } ?: NEWEST
    }
}

/**
 * One show in a playlist, with what the phone knows of it. [isFollowed] false or [isHidden] true
 * means the show stays in the playlist but its episodes are skipped.
 */
data class PlaylistShow(
    val playlistId: String,
    val podcastId: String,
    val position: Int,
    val addedAt: Long,
    val title: String,
    val imageUrl: String?,
    val isFollowed: Boolean,
    val isHidden: Boolean = false,
) {
    val isPlayable: Boolean get() = isFollowed && !isHidden
}

/** The longest playlist name, in characters. */
const val PLAYLIST_NAME_MAX_LENGTH = 60

/** A playlist name as stored: trimmed, inner runs of whitespace folded, at most [PLAYLIST_NAME_MAX_LENGTH] characters. */
fun normalizePlaylistName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").take(PLAYLIST_NAME_MAX_LENGTH).trim()
