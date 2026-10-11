package cx.aswin.boxlore.core.prefs

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Internal appearance storage; repository callers keep one DataStore and fast cache. */
internal class FullPlayerPreferences(private val store: DataStore<Preferences>, private val cache: SharedPreferences) {
    val cachedStyle: String
        get() = FullPlayerStyle.sanitize(cache.getString(FullPlayerStyle.PREF_KEY, null))

    val style = store.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            val style = preferences[Keys.FULL_PLAYER_STYLE]?.let(FullPlayerStyle::sanitize) ?: cachedStyle
            cache.edit().putString(FullPlayerStyle.PREF_KEY, style).apply()
            style
        }.distinctUntilChanged()

    suspend fun setStyle(style: String) {
        val sanitized = FullPlayerStyle.sanitize(style)
        store.edit { it[Keys.FULL_PLAYER_STYLE] = sanitized }
        cache.edit().putString(FullPlayerStyle.PREF_KEY, sanitized).apply()
    }

    fun hydrate(preferences: MutablePreferences) {
        preferences[Keys.FULL_PLAYER_STYLE] = preferences[Keys.FULL_PLAYER_STYLE] ?: cachedStyle
    }
}
