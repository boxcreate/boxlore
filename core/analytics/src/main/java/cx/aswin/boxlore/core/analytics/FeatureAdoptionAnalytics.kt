package cx.aswin.boxlore.core.analytics

import android.content.Context
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** How much this install uses playlists, hidden shows, titles-only lists, trim silence and per-show speed. */
data class FeatureAdoption(
    val playlistsCount: Int,
    val hiddenShowsCount: Int,
    val episodeTitlesOnly: Boolean,
    val trimSilence: Boolean,
    val showsWithOwnSpeed: Int,
) {
    internal fun properties(): Map<String, Any> =
        linkedMapOf(
            "playlists_count" to playlistsCount,
            "hidden_shows_count" to hiddenShowsCount,
            "episode_titles_only" to episodeTitlesOnly,
            "trim_silence" to trimSilence,
            "shows_with_own_speed" to showsWithOwnSpeed,
        )
}

/**
 * Feature-adoption person properties. Each `$set` is a capture, so a value is sent only when it
 * differs from the last one sent, kept across launches in [Store]; changes within [DEBOUNCE_MS] of
 * each other go out as one.
 */
object FeatureAdoptionAnalytics {
    /** How long values must stay unchanged before they are compared and sent. */
    const val DEBOUNCE_MS = 5_000L

    /** The last properties sent, as [encode] wrote them: SharedPreferences in the app. */
    interface Store {
        fun lastSent(): String?

        fun saveSent(encoded: String)
    }

    /** The properties last sent or already on the person, for an identity reset. */
    @Volatile
    private var current: Map<String, Any>? = null

    fun store(context: Context): Store = AnalyticsLocalState.featureAdoptionStore(context)

    /** Follows [adoption] until cancelled, sending each settled value that differs from the last one sent. */
    @OptIn(FlowPreview::class)
    suspend fun sync(adoption: Flow<FeatureAdoption>, store: Store, debounceMs: Long = DEBOUNCE_MS,) {
        adoption.distinctUntilChanged().debounce(debounceMs).collect { offer(it, store) }
    }

    internal fun offer(adoption: FeatureAdoption, store: Store) {
        val properties = adoption.properties()
        val encoded = encode(properties)
        current = properties
        if (encoded == store.lastSent()) return
        AnalyticsEmit.personSet(properties)
        store.saveSent(encoded)
    }

    /** An identity reset starts a new anonymous person, so it gets the current values again. */
    fun resendAfterIdentityReset() {
        current?.let(AnalyticsEmit::personSet)
    }

    internal fun encode(properties: Map<String, Any>): String = properties.entries.joinToString(";") { "${it.key}=${it.value}" }

    /** Test helper: forget the values seen in this process. */
    internal fun resetForTest() {
        current = null
    }
}
