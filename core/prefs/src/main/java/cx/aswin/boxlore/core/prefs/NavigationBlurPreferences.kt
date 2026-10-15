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

/** How strongly the page blurs behind the floating navigation bar ([NavigationBlurLevel]); soft by default. */
internal class NavigationBlurPreferences(private val store: DataStore<Preferences>, private val cache: SharedPreferences) {
    val cachedLevel: String
        get() = NavigationBlurLevel.sanitize(cache.getString(NavigationBlurLevel.PREF_KEY, null))

    val level = store.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            val level = preferences[Keys.NAVIGATION_BLUR]?.let(NavigationBlurLevel::sanitize) ?: cachedLevel
            cache.edit().putString(NavigationBlurLevel.PREF_KEY, level).apply()
            level
        }.distinctUntilChanged()

    suspend fun setLevel(level: String) {
        val sanitized = NavigationBlurLevel.sanitize(level)
        cache.edit().putString(NavigationBlurLevel.PREF_KEY, sanitized).apply()
        store.edit { it[Keys.NAVIGATION_BLUR] = sanitized }
    }

    fun hydrate(preferences: MutablePreferences) {
        preferences[Keys.NAVIGATION_BLUR] = preferences[Keys.NAVIGATION_BLUR] ?: cachedLevel
    }
}
