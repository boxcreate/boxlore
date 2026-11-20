package cx.aswin.boxlore.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Show page list storage. Internal storage helper; the repository remains the public entry point.
 */
internal class ShowPagePreferences(
    private val dataStore: DataStore<Preferences>,
) {
    /** Episode rows on show pages show only the title, details line and play button. Off by default. */
    val episodeTitlesOnlyStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { it[Keys.EPISODE_TITLES_ONLY] ?: false }
            .distinctUntilChanged()

    suspend fun setEpisodeTitlesOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.EPISODE_TITLES_ONLY] = enabled }
    }
}
