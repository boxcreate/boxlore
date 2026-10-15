package cx.aswin.boxlore.core.prefs

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Home hero card style ([HeroCardStyle]) and the Cover shade ([HeroCoverShade]); the fast cache
 * lets Home draw the right cards on its first frame.
 */
internal class HeroCardStylePreferences(private val store: DataStore<Preferences>, private val cache: SharedPreferences) {
    val cachedStyle: String
        get() = HeroCardStyle.sanitize(cache.getString(HeroCardStyle.PREF_KEY, null))

    val cachedCoverShade: String
        get() = HeroCoverShade.sanitize(cache.getString(HeroCoverShade.PREF_KEY, null))

    private val preferences = store.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    val style: Flow<String> = preferences.map { preferences ->
        val style = preferences[Keys.HERO_CARD_STYLE]?.let(HeroCardStyle::sanitize) ?: cachedStyle
        cache.edit().putString(HeroCardStyle.PREF_KEY, style).apply()
        style
    }.distinctUntilChanged()

    val coverShade: Flow<String> = preferences.map { preferences ->
        val shade = preferences[Keys.HERO_COVER_SHADE]?.let(HeroCoverShade::sanitize) ?: cachedCoverShade
        cache.edit().putString(HeroCoverShade.PREF_KEY, shade).apply()
        shade
    }.distinctUntilChanged()

    suspend fun setStyle(style: String) {
        val sanitized = HeroCardStyle.sanitize(style)
        cache.edit().putString(HeroCardStyle.PREF_KEY, sanitized).apply()
        store.edit { it[Keys.HERO_CARD_STYLE] = sanitized }
    }

    suspend fun setCoverShade(shade: String) {
        val sanitized = HeroCoverShade.sanitize(shade)
        cache.edit().putString(HeroCoverShade.PREF_KEY, sanitized).apply()
        store.edit { it[Keys.HERO_COVER_SHADE] = sanitized }
    }

    fun hydrate(preferences: MutablePreferences) {
        preferences[Keys.HERO_CARD_STYLE] = preferences[Keys.HERO_CARD_STYLE] ?: cachedStyle
        preferences[Keys.HERO_COVER_SHADE] = preferences[Keys.HERO_COVER_SHADE] ?: cachedCoverShade
    }
}
