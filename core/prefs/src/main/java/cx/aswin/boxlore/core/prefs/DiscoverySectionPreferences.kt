package cx.aswin.boxlore.core.prefs

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Stores [DiscoverySections]. The Brief reuses `briefing_dismissed_forever`, so the card's
 * "hide forever" and the switch stay one choice; the other sections have their own keys.
 * Picked for you is mirrored in [cache] so Explore opens on the right tab before DataStore loads.
 */
internal class DiscoverySectionPreferences(private val store: DataStore<Preferences>, private val cache: SharedPreferences) {
    val cachedPickedForYou: Boolean
        get() = cache.getBoolean(PICKED_FOR_YOU.name, true)

    val sections: Flow<DiscoverySections> = store.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            val pickedForYou = preferences[PICKED_FOR_YOU] ?: cachedPickedForYou
            cache.edit().putBoolean(PICKED_FOR_YOU.name, pickedForYou).apply()
            DiscoverySections(
                dailyBrief = preferences[Keys.BRIEFING_DISMISSED_FOREVER] != true,
                pickedForYou = pickedForYou,
                becauseYouLike = preferences[BECAUSE_YOU_LIKE] ?: true,
                curatedRows = preferences[CURATED_ROWS] ?: true,
                moreLikeThis = preferences[MORE_LIKE_THIS] ?: true,
            )
        }.distinctUntilChanged()

    suspend fun set(section: DiscoverySection, on: Boolean) {
        store.edit { preferences ->
            when (section) {
                DiscoverySection.DAILY_BRIEF -> {
                    preferences[Keys.BRIEFING_DISMISSED_FOREVER] = !on
                    // Turning the Brief back on also undoes today's dismissal, so it shows at once.
                    if (on) preferences.remove(Keys.BRIEFING_DISMISSED_DATE)
                }
                DiscoverySection.PICKED_FOR_YOU -> preferences[PICKED_FOR_YOU] = on
                DiscoverySection.BECAUSE_YOU_LIKE -> preferences[BECAUSE_YOU_LIKE] = on
                DiscoverySection.CURATED_ROWS -> preferences[CURATED_ROWS] = on
                DiscoverySection.MORE_LIKE_THIS -> preferences[MORE_LIKE_THIS] = on
            }
        }
        // Mirror only after DataStore commits, so a failed write cannot leave Explore on the wrong tab.
        if (section == DiscoverySection.PICKED_FOR_YOU) cache.edit().putBoolean(PICKED_FOR_YOU.name, on).apply()
    }

    private companion object {
        val PICKED_FOR_YOU = booleanPreferencesKey("discovery_picked_for_you")
        val BECAUSE_YOU_LIKE = booleanPreferencesKey("discovery_because_you_like")
        val CURATED_ROWS = booleanPreferencesKey("discovery_curated_rows")
        val MORE_LIKE_THIS = booleanPreferencesKey("discovery_more_like_this")
    }
}
