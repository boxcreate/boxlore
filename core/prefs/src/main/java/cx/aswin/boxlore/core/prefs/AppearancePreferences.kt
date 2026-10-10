package cx.aswin.boxlore.core.prefs

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Internal storage helper; the repository remains the public entry point. */
internal class AppearancePreferences(
    private val dataStore: DataStore<Preferences>,
    private val syncPrefs: SharedPreferences,
) {
    val cachedThemeConfig: String
        get() = syncPrefs.getString("theme_config", null) ?: "system"

    val cachedSurfaceStyle: String
        get() = syncPrefs.getString("surface_style", null) ?: "classic_dynamic"

    /** Lettering roundness preset key: `crisp` | `soft` | `round` (default). */
    val cachedFontRoundness: String
        get() = FontRoundnessAxis.sanitizeKey(syncPrefs.getString(FontRoundnessAxis.PREF_KEY, null))

    val cachedNavigationStyle: String
        get() = sanitizeNavigationStyle(syncPrefs.getString("navigation_style", null))

    /** Cold-start destination: `home` | `subscriptions` | `downloads` (default home). */
    val cachedOpenAppTo: String
        get() = sanitizeOpenAppTo(syncPrefs.getString("open_app_to", null))

    /** Explore landing tab: `for_you` (default) or `top`. */
    val cachedExploreDefaultTab: String
        get() = ExploreDefaultTab.sanitize(syncPrefs.getString(ExploreDefaultTab.PREF_KEY, null))

    /** Subscriptions landing tab: `shows` (default) or `new_episodes`. */
    val cachedSubscriptionsDefaultTab: String
        get() = SubscriptionsDefaultTab.sanitize(syncPrefs.getString(SubscriptionsDefaultTab.PREF_KEY, null))

    /** Subscriptions tab layout: `top` (default) or `floating`. */
    val cachedSubscriptionsTabStyle: String
        get() = SubscriptionsTabStyle.sanitize(syncPrefs.getString(SubscriptionsTabStyle.PREF_KEY, null))

    val cachedThemeBrand: String
        get() = syncPrefs.getString("theme_brand", null) ?: "violet"

    val cachedUseDynamicColor: Boolean
        get() = syncPrefs.getBoolean("use_dynamic_color", false)

    val cachedArtworkColorsEnabled: Boolean
        get() = syncPrefs.getBoolean("artwork_colors_enabled", true)

    val cachedCustomTheme: ThemeSelection?
        get() = syncPrefs.getString("custom_theme_brand", null)?.let { brand ->
            ThemeSelection(brand, syncPrefs.getString("custom_theme_surface", "standard") ?: "standard", syncPrefs.getBoolean("custom_theme_dynamic", false))
        }

    val artworkColorsEnabledStream: Flow<Boolean> = dataStore.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }.map { preferences ->
        preferences[Keys.ARTWORK_COLORS]?.also { syncPrefs.edit().putBoolean("artwork_colors_enabled", it).apply() } ?: cachedArtworkColorsEnabled
    }.distinctUntilChanged()

    suspend fun setArtworkColorsEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("artwork_colors_enabled", enabled).apply()
        dataStore.edit { it[Keys.ARTWORK_COLORS] = enabled }
    }

    val customThemeStream: Flow<ThemeSelection?> = dataStore.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }.map { preferences ->
        preferences[Keys.CUSTOM_THEME_BRAND]?.let { brand ->
            ThemeSelection(brand, preferences[Keys.CUSTOM_THEME_SURFACE] ?: "standard", preferences[Keys.CUSTOM_THEME_DYNAMIC] ?: false).also {
                syncPrefs.edit().putString("custom_theme_brand", it.brand).putString("custom_theme_surface", it.surfaceStyle).putBoolean("custom_theme_dynamic", it.wallpaperColors).apply()
            }
        } ?: cachedCustomTheme
    }.distinctUntilChanged()

    /** Apply all color inputs together, optionally remembering the current or newly created Custom theme. */
    suspend fun setThemeSelection(selection: ThemeSelection, customThemeToRemember: ThemeSelection? = null) {
        val cache = syncPrefs.edit().putString("theme_brand", selection.brand)
            .putString("surface_style", selection.surfaceStyle).putBoolean("use_dynamic_color", selection.wallpaperColors)
        customThemeToRemember?.let {
            cache.putString("custom_theme_brand", it.brand).putString("custom_theme_surface", it.surfaceStyle)
                .putBoolean("custom_theme_dynamic", it.wallpaperColors)
        }
        cache.apply()
        dataStore.edit { preferences ->
            preferences[Keys.THEME_BRAND] = selection.brand
            preferences[Keys.SURFACE_STYLE] = selection.surfaceStyle
            preferences[Keys.USE_DYNAMIC_COLOR] = selection.wallpaperColors
            customThemeToRemember?.let {
                preferences[Keys.CUSTOM_THEME_BRAND] = it.brand
                preferences[Keys.CUSTOM_THEME_SURFACE] = it.surfaceStyle
                preferences[Keys.CUSTOM_THEME_DYNAMIC] = it.wallpaperColors
            }
        }
    }

    /** Widget chrome: `app` (default, match Appearance) or `system` (launcher Material You). */
    val cachedWidgetAppearance: String
        get() = WidgetAppearance.sanitize(syncPrefs.getString(WidgetAppearance.PREF_KEY, null))

    // THEME PREFERENCES
    val themeConfigStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.THEME_CONFIG]
                if (stored != null) {
                    syncPrefs.edit().putString("theme_config", stored).apply()
                    stored
                } else {
                    cachedThemeConfig
                }
            }.distinctUntilChanged()

    suspend fun setThemeConfig(themeConfig: String) {
        syncPrefs.edit().putString("theme_config", themeConfig).apply()
        dataStore.edit { preferences ->
            preferences[Keys.THEME_CONFIG] = themeConfig
        }
    }

    val useDynamicColorStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.USE_DYNAMIC_COLOR]
                if (stored != null) {
                    syncPrefs.edit().putBoolean("use_dynamic_color", stored).apply()
                    stored
                } else {
                    cachedUseDynamicColor
                }
            }.distinctUntilChanged()

    suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        syncPrefs.edit().putBoolean("use_dynamic_color", useDynamicColor).apply()
        dataStore.edit { preferences ->
            preferences[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }

    val themeBrandStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.THEME_BRAND]
                if (stored != null) {
                    syncPrefs.edit().putString("theme_brand", stored).apply()
                    stored
                } else {
                    cachedThemeBrand
                }
            }.distinctUntilChanged()

    suspend fun setThemeBrand(themeBrand: String) {
        syncPrefs.edit().putString("theme_brand", themeBrand).apply()
        dataStore.edit { preferences ->
            preferences[Keys.THEME_BRAND] = themeBrand
        }
    }

    val surfaceStyleStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.SURFACE_STYLE]
                if (stored != null) {
                    syncPrefs.edit().putString("surface_style", stored).apply()
                    stored
                } else {
                    cachedSurfaceStyle
                }
            }.distinctUntilChanged()

    suspend fun setSurfaceStyle(surfaceStyle: String) {
        syncPrefs.edit().putString("surface_style", surfaceStyle).apply()
        dataStore.edit { preferences ->
            preferences[Keys.SURFACE_STYLE] = surfaceStyle
        }
    }

    val fontRoundnessStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.FONT_ROUNDNESS]
                if (stored != null) {
                    val roundness = FontRoundnessAxis.sanitizeKey(stored)
                    syncPrefs.edit().putString(FontRoundnessAxis.PREF_KEY, roundness).apply()
                    roundness
                } else {
                    cachedFontRoundness
                }
            }.distinctUntilChanged()

    suspend fun setFontRoundness(fontRoundness: String) {
        val sanitized = FontRoundnessAxis.sanitizeKey(fontRoundness)
        syncPrefs.edit().putString(FontRoundnessAxis.PREF_KEY, sanitized).apply()
        dataStore.edit { preferences ->
            preferences[Keys.FONT_ROUNDNESS] = sanitized
        }
    }

    /** Navigation presentation key: `floating` (default) or `classic`. */
    val navigationStyleStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.NAVIGATION_STYLE]
                if (stored != null) {
                    val navigationStyle = sanitizeNavigationStyle(stored)
                    syncPrefs.edit().putString("navigation_style", navigationStyle).apply()
                    navigationStyle
                } else {
                    cachedNavigationStyle
                }
            }.distinctUntilChanged()

    suspend fun setNavigationStyle(navigationStyle: String) {
        val sanitized = sanitizeNavigationStyle(navigationStyle)
        syncPrefs.edit().putString("navigation_style", sanitized).apply()
        dataStore.edit { preferences ->
            preferences[Keys.NAVIGATION_STYLE] = sanitized
        }
    }

    /** Cold-start landing: `home` (default) or `subscriptions`. Mirrored in theme fast-cache for launch. */
    val openAppToStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.OPEN_APP_TO]
                if (stored != null) {
                    val openAppTo = sanitizeOpenAppTo(stored)
                    syncPrefs.edit().putString("open_app_to", openAppTo).apply()
                    openAppTo
                } else {
                    cachedOpenAppTo
                }
            }.distinctUntilChanged()

    suspend fun setOpenAppTo(openAppTo: String) {
        val sanitized = sanitizeOpenAppTo(openAppTo)
        syncPrefs.edit().putString("open_app_to", sanitized).apply()
        dataStore.edit { preferences ->
            preferences[Keys.OPEN_APP_TO] = sanitized
        }
    }

    val exploreDefaultTabStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.EXPLORE_DEFAULT_TAB]
                if (stored != null) {
                    val tab = ExploreDefaultTab.sanitize(stored)
                    syncPrefs.edit().putString(ExploreDefaultTab.PREF_KEY, tab).apply()
                    tab
                } else {
                    cachedExploreDefaultTab
                }
            }.distinctUntilChanged()

    suspend fun setExploreDefaultTab(tab: String) {
        val sanitized = ExploreDefaultTab.sanitize(tab)
        syncPrefs.edit().putString(ExploreDefaultTab.PREF_KEY, sanitized).apply()
        dataStore.edit { preferences ->
            preferences[Keys.EXPLORE_DEFAULT_TAB] = sanitized
        }
    }

    val subscriptionsDefaultTabStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.SUBSCRIPTIONS_DEFAULT_TAB]
                if (stored != null) {
                    val tab = SubscriptionsDefaultTab.sanitize(stored)
                    syncPrefs.edit().putString(SubscriptionsDefaultTab.PREF_KEY, tab).apply()
                    tab
                } else {
                    cachedSubscriptionsDefaultTab
                }
            }.distinctUntilChanged()

    suspend fun setSubscriptionsDefaultTab(tab: String) {
        val sanitized = SubscriptionsDefaultTab.sanitize(tab)
        syncPrefs.edit().putString(SubscriptionsDefaultTab.PREF_KEY, sanitized).apply()
        dataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTIONS_DEFAULT_TAB] = sanitized
        }
    }

    val subscriptionsTabStyleStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.SUBSCRIPTIONS_TAB_STYLE]
                if (stored != null) {
                    val style = SubscriptionsTabStyle.sanitize(stored)
                    syncPrefs.edit().putString(SubscriptionsTabStyle.PREF_KEY, style).apply()
                    style
                } else {
                    cachedSubscriptionsTabStyle
                }
            }.distinctUntilChanged()

    suspend fun setSubscriptionsTabStyle(style: String) {
        val sanitized = SubscriptionsTabStyle.sanitize(style)
        syncPrefs.edit().putString(SubscriptionsTabStyle.PREF_KEY, sanitized).apply()
        dataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTIONS_TAB_STYLE] = sanitized
        }
    }

    /**
     * After Google Backup, SharedPreferences fast-cache can restore while DataStore does not.
     * Copy cache values into missing DataStore keys so UI streams and workers stay aligned.
     */
    suspend fun hydrateMissingDataStoreFromFastCache() {
        dataStore.edit { preferences ->
            hydrateArtworkThemePreferences(preferences)
            if (preferences[Keys.THEME_CONFIG] == null) {
                preferences[Keys.THEME_CONFIG] = cachedThemeConfig
            }
            if (preferences[Keys.USE_DYNAMIC_COLOR] == null) {
                preferences[Keys.USE_DYNAMIC_COLOR] = cachedUseDynamicColor
            }
            if (preferences[Keys.THEME_BRAND] == null) {
                preferences[Keys.THEME_BRAND] = cachedThemeBrand
            }
            if (preferences[Keys.SURFACE_STYLE] == null) {
                preferences[Keys.SURFACE_STYLE] = cachedSurfaceStyle
            }
            if (preferences[Keys.FONT_ROUNDNESS] == null) {
                preferences[Keys.FONT_ROUNDNESS] = cachedFontRoundness
            }
            if (preferences[Keys.NAVIGATION_STYLE] == null) {
                preferences[Keys.NAVIGATION_STYLE] = cachedNavigationStyle
            }
            if (preferences[Keys.OPEN_APP_TO] == null) {
                preferences[Keys.OPEN_APP_TO] = cachedOpenAppTo
            }
            if (preferences[Keys.EXPLORE_DEFAULT_TAB] == null) {
                preferences[Keys.EXPLORE_DEFAULT_TAB] = cachedExploreDefaultTab
            }
            if (preferences[Keys.SUBSCRIPTIONS_DEFAULT_TAB] == null) {
                preferences[Keys.SUBSCRIPTIONS_DEFAULT_TAB] = cachedSubscriptionsDefaultTab
            }
            if (preferences[Keys.SUBSCRIPTIONS_TAB_STYLE] == null) {
                preferences[Keys.SUBSCRIPTIONS_TAB_STYLE] = cachedSubscriptionsTabStyle
            }
            if (preferences[Keys.WIDGET_APPEARANCE] == null) {
                preferences[Keys.WIDGET_APPEARANCE] = cachedWidgetAppearance
            }
        }
    }

    /**
     * Widget chrome source: [WidgetAppearance.APP] (default) or [WidgetAppearance.SYSTEM].
     * Mirrored in theme fast-cache so RemoteViews can read it without DataStore.
     */
    val widgetAppearanceStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val stored = preferences[Keys.WIDGET_APPEARANCE]
                if (stored != null) {
                    val appearance = WidgetAppearance.sanitize(stored)
                    syncPrefs.edit().putString(WidgetAppearance.PREF_KEY, appearance).apply()
                    appearance
                } else {
                    cachedWidgetAppearance
                }
            }.distinctUntilChanged()

    suspend fun setWidgetAppearance(appearance: String) {
        val sanitized = WidgetAppearance.sanitize(appearance)
        syncPrefs.edit().putString(WidgetAppearance.PREF_KEY, sanitized).apply()
        dataStore.edit { preferences ->
            preferences[Keys.WIDGET_APPEARANCE] = sanitized
        }
    }
}

private fun AppearancePreferences.hydrateArtworkThemePreferences(preferences: androidx.datastore.preferences.core.MutablePreferences) {
    if (preferences[Keys.ARTWORK_COLORS] == null) preferences[Keys.ARTWORK_COLORS] = cachedArtworkColorsEnabled
    cachedCustomTheme?.let { custom ->
        if (preferences[Keys.CUSTOM_THEME_BRAND] == null) {
            preferences[Keys.CUSTOM_THEME_BRAND] = custom.brand
            preferences[Keys.CUSTOM_THEME_SURFACE] = custom.surfaceStyle
            preferences[Keys.CUSTOM_THEME_DYNAMIC] = custom.wallpaperColors
        }
    }
}
