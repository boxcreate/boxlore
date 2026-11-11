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

/**
 * How the Your Shows widget draws covers ([WidgetShowsArtwork]); whole covers by default. Mirrored in
 * the theme fast-cache so the widget's list factory can read it without DataStore.
 */
internal class WidgetShowsArtworkPreferences(private val store: DataStore<Preferences>, private val cache: SharedPreferences) {
    val cachedStyle: String
        get() = WidgetShowsArtwork.sanitize(cache.getString(WidgetShowsArtwork.PREF_KEY, null))

    val style = store.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            val style = preferences[Keys.WIDGET_SHOWS_ARTWORK]?.let(WidgetShowsArtwork::sanitize) ?: cachedStyle
            cache.edit().putString(WidgetShowsArtwork.PREF_KEY, style).apply()
            style
        }.distinctUntilChanged()

    suspend fun setStyle(style: String) {
        val sanitized = WidgetShowsArtwork.sanitize(style)
        // The cache is written first: the widget refresh this triggers reads it straight away.
        cache.edit().putString(WidgetShowsArtwork.PREF_KEY, sanitized).apply()
        store.edit { it[Keys.WIDGET_SHOWS_ARTWORK] = sanitized }
    }

    fun hydrate(preferences: MutablePreferences) {
        preferences[Keys.WIDGET_SHOWS_ARTWORK] = preferences[Keys.WIDGET_SHOWS_ARTWORK] ?: cachedStyle
    }
}
