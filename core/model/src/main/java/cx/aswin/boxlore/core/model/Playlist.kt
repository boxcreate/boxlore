package cx.aswin.boxlore.core.model

/**
 * A listener's playlist: a set of shows played as one list. Its episodes are worked out when it
 * plays (the shows' unheard episodes, mixed by [mix], at most [perShowLimit] from each show; null
 * means no limit), so it never runs out while the shows publish. [iconKey] is a genre icon key, null
 * for none. [coverUrls] are the artwork of its first few shows, in the order they were added.
 */
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val showCount: Int,
    val mix: PlaylistMix = PlaylistMix.TAKE_TURNS,
    val perShowLimit: Int? = PLAYLIST_DEFAULT_PER_SHOW_LIMIT,
    val iconKey: String? = null,
    val coverUrls: List<String> = emptyList(),
)

/** How a playlist mixes its shows' episodes. [stored] is the value kept on the phone and in backups. */
enum class PlaylistMix(val stored: String) {
    /**
     * Each show's newest episodes of the last [PLAYLIST_TAKE_TURNS_WINDOW_DAYS] days, shows taking
     * turns in playlist order: every show's newest, then every show's second newest, and so on.
     */
    TAKE_TURNS("take_turns"),

    /** Every show's newest episodes, merged by release date, newest first. */
    NEWEST_FIRST("newest_first"),

    /** Show by show in playlist order, each show's episodes oldest first. */
    ONE_SHOW_AT_A_TIME("one_show"),
    ;

    companion object {
        /** Unknown or missing values read as [TAKE_TURNS]. */
        fun fromStored(value: String?): PlaylistMix = entries.firstOrNull { it.stored == value } ?: TAKE_TURNS
    }
}

/** A new playlist's episodes per show. */
const val PLAYLIST_DEFAULT_PER_SHOW_LIMIT = 3

/** The episodes-per-show choices a listener picks from; null is no limit. */
val PLAYLIST_PER_SHOW_CHOICES: List<Int?> = listOf(1, 2, 3, 5, null)

/** How far back [PlaylistMix.TAKE_TURNS] reaches, in days. */
const val PLAYLIST_TAKE_TURNS_WINDOW_DAYS = 30

/** A per-show limit as kept: positive, or null for no limit. */
fun normalizePlaylistPerShowLimit(limit: Int?): Int? = limit?.takeIf { it > 0 }

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
