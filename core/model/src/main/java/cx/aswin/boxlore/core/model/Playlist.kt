package cx.aswin.boxlore.core.model

/**
 * A listener's playlist: episodes in an order the listener chose. [coverUrls] are the artwork of its
 * first few episodes, in playlist order.
 */
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val episodeCount: Int,
    val coverUrls: List<String> = emptyList(),
) {
    val isFull: Boolean get() = episodeCount >= PLAYLIST_EPISODE_LIMIT
}

/** One episode in a playlist. [itemId] identifies this row (the same episode can sit in other playlists). */
data class PlaylistEpisode(
    val itemId: Long,
    val playlistId: String,
    val position: Int,
    val addedAt: Long,
    val episode: Episode,
)

/** The most episodes one playlist holds. */
const val PLAYLIST_EPISODE_LIMIT = 500

/** The longest playlist name, in characters. */
const val PLAYLIST_NAME_MAX_LENGTH = 60

/** A playlist name as stored: trimmed, inner runs of whitespace folded, at most [PLAYLIST_NAME_MAX_LENGTH] characters. */
fun normalizePlaylistName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").take(PLAYLIST_NAME_MAX_LENGTH).trim()
