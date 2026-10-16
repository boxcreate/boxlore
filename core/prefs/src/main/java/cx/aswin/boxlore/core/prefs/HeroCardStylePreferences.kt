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
 * Home hero card style ([HeroCardStyle]), its colours ([HeroCoverShade]) and Tide's progress ring
 * ([HeroTideProgress]); the fast cache
 * lets Home draw the right cards on its first frame. Only a picked value is cached or copied into
 * DataStore, so listeners who never picked keep following the current default.
 */
internal class HeroCardStylePreferences(private val store: DataStore<Preferences>, private val cache: SharedPreferences) {
    val cachedStyle: String
        get() = HeroCardStyle.sanitize(cache.getString(HeroCardStyle.PREF_KEY, null))

    /** The picked colours, or [HeroCoverShade.UNSET] before any pick, so each style can apply its own default. */
    val cachedCoverShade: String
        get() = cache.getString(HeroCoverShade.PREF_KEY, null)?.let(HeroCoverShade::sanitize) ?: HeroCoverShade.UNSET

    val cachedTideProgress: String
        get() = HeroTideProgress.sanitize(cache.getString(HeroTideProgress.PREF_KEY, null))

    private val preferences = store.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    val style: Flow<String> = preferences.map { preferences ->
        val picked = preferences[Keys.HERO_CARD_STYLE]?.let(HeroCardStyle::sanitize)
        picked?.let { cache.edit().putString(HeroCardStyle.PREF_KEY, it).apply() }
        picked ?: cachedStyle
    }.distinctUntilChanged()

    val coverShade: Flow<String> = preferences.map { preferences ->
        val picked = preferences[Keys.HERO_COVER_SHADE]?.let(HeroCoverShade::sanitize)
        picked?.let { cache.edit().putString(HeroCoverShade.PREF_KEY, it).apply() }
        picked ?: cachedCoverShade
    }.distinctUntilChanged()

    val tideProgress: Flow<String> = preferences.map { preferences ->
        val picked = preferences[Keys.HERO_TIDE_PROGRESS]?.let(HeroTideProgress::sanitize)
        picked?.let { cache.edit().putString(HeroTideProgress.PREF_KEY, it).apply() }
        picked ?: cachedTideProgress
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

    suspend fun setTideProgress(shape: String) {
        val sanitized = HeroTideProgress.sanitize(shape)
        cache.edit().putString(HeroTideProgress.PREF_KEY, sanitized).apply()
        store.edit { it[Keys.HERO_TIDE_PROGRESS] = sanitized }
    }

    /** Restores a cached pick (Google Backup restores the cache without DataStore); an unpicked default stays unsaved. */
    fun hydrate(preferences: MutablePreferences) {
        cache.getString(HeroCardStyle.PREF_KEY, null)?.let { cached ->
            preferences[Keys.HERO_CARD_STYLE] = preferences[Keys.HERO_CARD_STYLE] ?: HeroCardStyle.sanitize(cached)
        }
        cache.getString(HeroCoverShade.PREF_KEY, null)?.let { cached ->
            preferences[Keys.HERO_COVER_SHADE] = preferences[Keys.HERO_COVER_SHADE] ?: HeroCoverShade.sanitize(cached)
        }
        cache.getString(HeroTideProgress.PREF_KEY, null)?.let { cached ->
            preferences[Keys.HERO_TIDE_PROGRESS] = preferences[Keys.HERO_TIDE_PROGRESS] ?: HeroTideProgress.sanitize(cached)
        }
    }
}
