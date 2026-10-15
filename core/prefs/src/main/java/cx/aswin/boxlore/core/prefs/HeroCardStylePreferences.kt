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

/** Home hero card style ([HeroCardStyle]); the fast cache lets Home draw the right cards on its first frame. */
internal class HeroCardStylePreferences(private val store: DataStore<Preferences>, private val cache: SharedPreferences) {
    val cachedStyle: String
        get() = HeroCardStyle.sanitize(cache.getString(HeroCardStyle.PREF_KEY, null))

    val style = store.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            val style = preferences[Keys.HERO_CARD_STYLE]?.let(HeroCardStyle::sanitize) ?: cachedStyle
            cache.edit().putString(HeroCardStyle.PREF_KEY, style).apply()
            style
        }.distinctUntilChanged()

    suspend fun setStyle(style: String) {
        val sanitized = HeroCardStyle.sanitize(style)
        cache.edit().putString(HeroCardStyle.PREF_KEY, sanitized).apply()
        store.edit { it[Keys.HERO_CARD_STYLE] = sanitized }
    }

    fun hydrate(preferences: MutablePreferences) {
        preferences[Keys.HERO_CARD_STYLE] = preferences[Keys.HERO_CARD_STYLE] ?: cachedStyle
    }
}
