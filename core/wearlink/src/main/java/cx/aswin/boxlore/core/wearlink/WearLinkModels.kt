package cx.aswin.boxlore.core.wearlink

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import kotlinx.serialization.Serializable

/**
 * The names both apps use on the watch link. They are stored by the system on both devices, so
 * they never change; a new shape gets a new path.
 */
object WearLinkProtocol {
    const val VERSION = 1

    /** Items the phone publishes. */
    const val PHONE_PREFIX = "/boxlore/phone"
    const val PATH_PHONE_LIBRARY = "$PHONE_PREFIX/library"
    const val PATH_PHONE_NOW_PLAYING = "$PHONE_PREFIX/now-playing"
    const val PATH_PHONE_SETTINGS = "$PHONE_PREFIX/settings"

    /** Items the watch publishes. */
    const val WATCH_PREFIX = "/boxlore/watch"
    const val PATH_WATCH_CHANGES = "$WATCH_PREFIX/changes"
    const val PATH_WATCH_SETTINGS = "$WATCH_PREFIX/settings"

    /** Whether the watch's own player is playing, so the two devices never play at once unnoticed. */
    const val PATH_WATCH_NOW_PLAYING = "$WATCH_PREFIX/now-playing"

    /** A player command from the watch to the phone. */
    const val PATH_COMMAND = "/boxlore/command"

    /** A [WearLinkCommand] from the phone to the watch: pause, or play this episode here. */
    const val PATH_WATCH_COMMAND = "/boxlore/watch-command"

    /** The watch asking the phone for a show's episodes. */
    const val PATH_EPISODES = "/boxlore/episodes"

    /** Declared in each app's `wear.xml`, so one side can find the other. */
    const val CAPABILITY_PHONE = "boxlore_phone"
    const val CAPABILITY_WATCH = "boxlore_watch"
}

/** A followed show with what the watch needs to draw and open it. */
@Serializable
data class WearLinkShow(
    val podcastId: String,
    val title: String,
    val author: String = "",
    val imageUrl: String? = null,
    val feedUrl: String? = null,
)

/** One episode, with the identity fields that let either side find its own copy. */
@Serializable
data class WearLinkEpisode(
    val episodeId: String,
    val podcastId: String,
    val title: String,
    val podcastName: String = "",
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val durationMs: Long = 0L,
    val publishedAt: Long = 0L,
    val recipeId: Long? = null,
    val catalogKey: String? = null,
)

/**
 * What the phone tells the watch: every followed show and the most recently played episodes, in
 * the same row shape the phone syncs with the cloud.
 *
 * @property acked for each watch ([WatchChanges.watchId]), the newest [WatchChanges.revision] the
 *   phone has merged, so that watch can stop sending those changes.
 * @property showsTruncated true when [shows] had to be cut to fit one item; the watch then keeps
 *   shows it already has instead of treating the missing ones as unfollowed.
 */
@Serializable
data class PhoneLibrary(
    val version: Int = WearLinkProtocol.VERSION,
    val shows: List<WearLinkShow> = emptyList(),
    val history: List<ListeningHistorySyncDto> = emptyList(),
    val acked: Map<String, Long> = emptyMap(),
    val showsTruncated: Boolean = false,
    val sentAt: Long = 0L,
    /** The newest unplayed episode of each followed show, newest first. */
    val newEpisodes: List<WearLinkEpisode> = emptyList(),
    /** The phone's Daily Mix, in playing order. */
    val dailyMix: List<WearLinkEpisode> = emptyList(),
)

/** A follow or unfollow made on the watch; [show] lets the phone list a show it has never seen. */
@Serializable
data class WatchSubscriptionChange(
    val revision: Long,
    val change: UserSubscriptionSyncDto,
    val show: WearLinkShow? = null,
)

/** Listening done on the watch, as one history row. */
@Serializable
data class WatchHistoryChange(
    val revision: Long,
    val row: ListeningHistorySyncDto,
)

/**
 * What the watch tells the phone: the changes the phone has not confirmed yet, and how much the
 * watch is storing. [revision] grows with every change on the watch; [watchId] is made once per
 * install of the watch app, so a watch that starts over is never mistaken for its old self.
 */
@Serializable
data class WatchChanges(
    val version: Int = WearLinkProtocol.VERSION,
    val watchId: String = "",
    val revision: Long = 0L,
    val subscriptions: List<WatchSubscriptionChange> = emptyList(),
    val history: List<WatchHistoryChange> = emptyList(),
    val storedBytes: Long = 0L,
    val downloads: Int = 0,
    val sentAt: Long = 0L,
    /** Every episode the watch stores or is downloading, so the phone can show which ones it has. */
    val stored: List<WatchStoredEpisode> = emptyList(),
)

/** One episode's audio on the watch; [done] is false while it still downloads. */
@Serializable
data class WatchStoredEpisode(val episodeId: String, val audioUrl: String? = null, val done: Boolean = false)

/** The phone's player as of [at]: enough for the watch to show it and to work out the position since. */
@Serializable
data class PhoneNowPlaying(
    val episode: WearLinkEpisode? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val at: Long = 0L,
    /** The episodes queued after [episode] on the phone, in order, at most [UP_NEXT_LIMIT]. */
    val upNext: List<WearLinkEpisode> = emptyList(),
) {
    /** Where the phone's player is at [now], assuming it kept playing at [speed] since [at]. */
    fun positionAt(now: Long): Long {
        if (!isPlaying || at <= 0L) return positionMs
        val moved = ((now - at).coerceAtLeast(0L) * speed).toLong()
        val end = episode?.durationMs?.takeIf { it > 0L } ?: Long.MAX_VALUE
        return (positionMs + moved).coerceAtMost(end)
    }

    companion object {
        /** A watch shows a short list; a long queue is cut here so the message stays small. */
        const val UP_NEXT_LIMIT = 20
    }
}

/** The watch's own player as of [at]. The phone only needs to know whether it is playing, and what. */
@Serializable
data class WatchNowPlaying(val isPlaying: Boolean = false, val episodeId: String? = null, val at: Long = 0L)

/** The watch settings either side can change; the newer [changedAt] wins. */
@Serializable
data class WearLinkSettings(
    val showsLayout: String = LAYOUT_HONEYCOMB,
    val storageLimitMb: Int = DEFAULT_STORAGE_LIMIT_MB,
    val changedAt: Long = 0L,
) {
    companion object {
        const val LAYOUT_HONEYCOMB = "Honeycomb"
        const val LAYOUT_LIST = "List"
        const val DEFAULT_STORAGE_LIMIT_MB = 500

        /** The limits a listener can pick, in megabytes. */
        val STORAGE_LIMIT_CHOICES_MB: List<Int> = listOf(250, 500, 1000, 2000)
    }
}

/** A player command sent from the watch to the phone. */
@Serializable
data class WearLinkCommand(
    val action: String,
    val positionMs: Long? = null,
    val speed: Float? = null,
    val episode: WearLinkEpisode? = null,
    /** With [PLAY_EPISODE]: what was queued after the episode on the watch, to queue after it on the phone. */
    val upNext: List<WearLinkEpisode> = emptyList(),
) {
    companion object {
        const val TOGGLE = "toggle"
        const val PAUSE = "pause"
        const val SEEK_BACK = "seek_back"
        const val SEEK_FORWARD = "seek_forward"
        const val SET_SPEED = "set_speed"

        /** Phone to watch: store [episode]'s audio on the watch, or remove it again. */
        const val DOWNLOAD_EPISODE = "download_episode"
        const val REMOVE_DOWNLOAD = "remove_download"

        /** Start [episode] on the phone at [positionMs], with [upNext] queued after it. */
        const val PLAY_EPISODE = "play_episode"
    }
}

@Serializable
data class EpisodeListRequest(
    val podcastId: String,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 25
    }
}

/** The phone's saved episodes of a show, newest first; empty when it has none saved. */
@Serializable
data class EpisodeListReply(
    val podcastId: String,
    val episodes: List<WearLinkEpisode> = emptyList(),
)
