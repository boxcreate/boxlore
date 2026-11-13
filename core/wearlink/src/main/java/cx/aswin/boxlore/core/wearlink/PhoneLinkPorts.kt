package cx.aswin.boxlore.core.wearlink

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** The phone's library as the watch link reads and changes it. The app implements it over its repositories. */
interface PhoneLibraryPort {
    /** Emits whenever the list of followed shows changes. */
    val showsChanged: Flow<Unit>

    /** Every followed show and the [historyLimit] most recently played history rows. */
    suspend fun library(historyLimit: Int): PhoneLibraryRows

    /** Merges follows, unfollows and listening done on the watch into the phone's library. */
    suspend fun apply(subscriptions: List<UserSubscriptionSyncDto>, history: List<ListeningHistorySyncDto>)

    /** The newest [limit] episodes of a show the phone has saved; empty when it has none. */
    suspend fun episodes(podcastId: String, limit: Int): List<WearLinkEpisode>
}

class PhoneLibraryRows(val shows: List<WearLinkShow>, val history: List<ListeningHistorySyncDto>)

/** The phone's player as the watch link shows and controls it. */
interface PhonePlayerPort {
    val state: Flow<PhonePlayerState>

    suspend fun togglePlayPause()

    suspend fun pause()

    suspend fun seekBack()

    suspend fun seekForward()

    suspend fun setSpeed(speed: Float)

    /**
     * Starts [episode] at [positionMs] and queues [upNext] after it: the listener moved playback
     * from the watch to the phone.
     */
    suspend fun play(episode: WearLinkEpisode, positionMs: Long, upNext: List<WearLinkEpisode> = emptyList())
}

data class PhonePlayerState(
    val episode: WearLinkEpisode? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    /** The episodes queued after [episode], in order. */
    val upNext: List<WearLinkEpisode> = emptyList(),
)

/** What the phone last heard about the watch. */
data class WatchStatus(val storedBytes: Long = 0L, val downloads: Int = 0, val seenAt: Long = 0L)

/** What the phone's Wear OS settings page shows. */
data class WearLinkStatus(
    /** Whether boxlore on a watch has ever linked with this phone. */
    val watchKnown: Boolean = false,
    /** Whether that watch app can be reached right now. */
    val reachable: Boolean = false,
    val watch: WatchStatus = WatchStatus(),
)

/** What the phone remembers about the watch link between launches. */
interface PhoneLinkStore {
    var watchKnown: Boolean
    var settings: WearLinkSettings
    var watchStatus: WatchStatus

    /** For each watch, the newest revision of its changes the phone has merged. */
    fun acked(): Map<String, Long>

    fun setAcked(watchId: String, revision: Long)

    class InMemory : PhoneLinkStore {
        private val confirmed = LinkedHashMap<String, Long>()
        override var watchKnown = false
        override var settings = WearLinkSettings()
        override var watchStatus = WatchStatus()

        override fun acked(): Map<String, Long> = confirmed.toMap()

        override fun setAcked(watchId: String, revision: Long) {
            confirmed[watchId] = revision
        }
    }
}

/** [PhoneLinkStore] in the preferences file `boxlore_wear_link`. */
class SharedPreferencesPhoneLinkStore(private val prefs: SharedPreferences) : PhoneLinkStore {
    override var watchKnown: Boolean
        get() = prefs.getBoolean(KEY_WATCH_KNOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_WATCH_KNOWN, value) }

    override var settings: WearLinkSettings
        get() =
            WearLinkSettings(
                showsLayout = prefs.getString(KEY_SHOWS_LAYOUT, null) ?: WearLinkSettings.LAYOUT_HONEYCOMB,
                storageLimitMb = prefs.getInt(KEY_STORAGE_LIMIT_MB, WearLinkSettings.DEFAULT_STORAGE_LIMIT_MB),
                changedAt = prefs.getLong(KEY_SETTINGS_CHANGED_AT, 0L),
            )
        set(value) =
            prefs.edit {
                putString(KEY_SHOWS_LAYOUT, value.showsLayout)
                putInt(KEY_STORAGE_LIMIT_MB, value.storageLimitMb)
                putLong(KEY_SETTINGS_CHANGED_AT, value.changedAt)
            }

    override var watchStatus: WatchStatus
        get() = WatchStatus(prefs.getLong(KEY_WATCH_STORED_BYTES, 0L), prefs.getInt(KEY_WATCH_DOWNLOADS, 0), prefs.getLong(KEY_WATCH_SEEN_AT, 0L))
        set(value) =
            prefs.edit {
                putLong(KEY_WATCH_STORED_BYTES, value.storedBytes)
                putInt(KEY_WATCH_DOWNLOADS, value.downloads)
                putLong(KEY_WATCH_SEEN_AT, value.seenAt)
            }

    override fun acked(): Map<String, Long> =
        prefs.getString(KEY_ACKED, null)
            ?.let { saved -> runCatching { WearLinkCodec.json.decodeFromString(ACKED, saved) }.getOrNull() }
            .orEmpty()

    override fun setAcked(watchId: String, revision: Long) {
        // The newest watches are kept; a phone rarely sees more than one.
        val kept = (acked() - watchId).entries.toList().takeLast(MAX_WATCHES - 1).associate { it.toPair() } + (watchId to revision)
        prefs.edit { putString(KEY_ACKED, WearLinkCodec.json.encodeToString(ACKED, kept)) }
    }

    companion object {
        /** Stable across releases: renaming any of these makes the phone forget its watch. */
        const val FILE_NAME = "boxlore_wear_link"
        const val KEY_WATCH_KNOWN = "watch_known"
        const val KEY_SHOWS_LAYOUT = "shows_layout"
        const val KEY_STORAGE_LIMIT_MB = "storage_limit_mb"
        const val KEY_SETTINGS_CHANGED_AT = "settings_changed_at"
        const val KEY_WATCH_STORED_BYTES = "watch_stored_bytes"
        const val KEY_WATCH_DOWNLOADS = "watch_downloads"
        const val KEY_WATCH_SEEN_AT = "watch_seen_at"
        const val KEY_ACKED = "acked_revisions"
        private const val MAX_WATCHES = 4
        private val ACKED = MapSerializer(String.serializer(), Long.serializer())

        fun create(context: Context): SharedPreferencesPhoneLinkStore =
            SharedPreferencesPhoneLinkStore(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    }
}
