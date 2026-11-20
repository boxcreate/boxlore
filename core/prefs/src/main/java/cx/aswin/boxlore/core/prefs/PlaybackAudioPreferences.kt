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

/** The app's trim silence setting; shares the repository's one DataStore. */
internal class PlaybackAudioPreferences(private val store: DataStore<Preferences>) {
    /** Off unless the listener turns it on. */
    val trimSilenceEnabled: Flow<Boolean> = store.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[Keys.TRIM_SILENCE_ENABLED] ?: false
        }.distinctUntilChanged()

    suspend fun setTrimSilenceEnabled(enabled: Boolean) {
        store.edit { preferences -> preferences[Keys.TRIM_SILENCE_ENABLED] = enabled }
    }
}
