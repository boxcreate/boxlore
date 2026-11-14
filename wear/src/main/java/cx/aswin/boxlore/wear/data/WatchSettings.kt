package cx.aswin.boxlore.wear.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cx.aswin.boxlore.core.wearlink.WearLinkSettings
import cx.aswin.boxlore.wear.model.ShowsLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The listener's choices for the watch. They can be changed here or in the phone app's Wear OS
 * settings; whichever side changed them last wins.
 */
class WatchSettings(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val layout = MutableStateFlow(readLayout())
    private val limit = MutableStateFlow(readLimit())
    private val shared = MutableStateFlow(snapshot(prefs.getLong(KEY_CHANGED_AT, 0L)))

    val showsLayout: StateFlow<ShowsLayout> = layout.asStateFlow()

    /** The most space downloads may take, in megabytes: one of [StoragePolicy.LIMIT_CHOICES_MB]. */
    val storageLimitMb: StateFlow<Int> = limit.asStateFlow()

    /** The choices as the phone link carries them, with when they last changed. */
    val linkSettings: StateFlow<WearLinkSettings> = shared.asStateFlow()

    fun setShowsLayout(value: ShowsLayout) = save(value, limit.value, clock())

    fun setStorageLimitMb(value: Int) {
        if (value in StoragePolicy.LIMIT_CHOICES_MB) save(layout.value, value, clock())
    }

    /** Takes the phone's choices when they are newer than the watch's. Returns whether it did. */
    fun applyRemote(remote: WearLinkSettings): Boolean {
        if (remote.changedAt <= shared.value.changedAt) return false
        val newLayout = ShowsLayout.entries.firstOrNull { it.name == remote.showsLayout } ?: layout.value
        val newLimit = remote.storageLimitMb.takeIf { it in StoragePolicy.LIMIT_CHOICES_MB } ?: limit.value
        save(newLayout, newLimit, remote.changedAt)
        return true
    }

    private fun save(newLayout: ShowsLayout, newLimit: Int, changedAt: Long) {
        prefs.edit {
            putString(KEY_SHOWS_LAYOUT, newLayout.name)
            putInt(KEY_STORAGE_LIMIT_MB, newLimit)
            putLong(KEY_CHANGED_AT, changedAt)
        }
        layout.value = newLayout
        limit.value = newLimit
        shared.value = snapshot(changedAt)
    }

    private fun snapshot(changedAt: Long) = WearLinkSettings(layout.value.name, limit.value, changedAt)

    private fun readLimit(): Int =
        prefs.getInt(KEY_STORAGE_LIMIT_MB, StoragePolicy.DEFAULT_LIMIT_MB)
            .takeIf { it in StoragePolicy.LIMIT_CHOICES_MB } ?: StoragePolicy.DEFAULT_LIMIT_MB

    private fun readLayout(): ShowsLayout =
        prefs.getString(KEY_SHOWS_LAYOUT, null)
            ?.let { saved -> ShowsLayout.entries.firstOrNull { it.name == saved } }
            ?: ShowsLayout.Honeycomb

    companion object {
        /** Stable across releases: renaming any of these loses the listener's choice. */
        const val FILE_NAME = "boxlore_watch_settings"
        const val KEY_SHOWS_LAYOUT = "shows_layout"
        const val KEY_STORAGE_LIMIT_MB = "storage_limit_mb"
        const val KEY_CHANGED_AT = "changed_at"

        fun create(context: Context): WatchSettings =
            WatchSettings(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    }
}
