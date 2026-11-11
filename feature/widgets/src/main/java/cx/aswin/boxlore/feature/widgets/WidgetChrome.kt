package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreChromeColors
import cx.aswin.boxlore.core.designsystem.theme.darkThemeFromConfig
import cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreChromeColors
import cx.aswin.boxlore.core.prefs.FontRoundnessAxis
import cx.aswin.boxlore.core.prefs.WidgetAppearance

/**
 * Resolves widget Material roles from either the in-app Appearance theme (default)
 * or system Material You resources.
 *
 * Every colour reaches the launcher as a tint, never baked into a bitmap, in one of three forms:
 * - System appearance: a colour resource, which the launcher resolves with its own light/dark mode
 *   and wallpaper palette.
 * - App theme with a fixed Light or Dark display mode: one ARGB value.
 * - App theme whose display mode follows the system: a light and a dark ARGB value, so the launcher
 *   switches with the phone even while the app's process is not running.
 */
internal class WidgetChrome private constructor(
    private val context: Context,
    private val followAppTheme: Boolean,
    private val appColors: BoxLoreChromeColors?,
    private val dayNight: DayNight?,
) {
    /** The app theme's roles for the phone's light and dark modes. */
    internal class DayNight(val day: BoxLoreChromeColors, val night: BoxLoreChromeColors)

    val usesAppTheme: Boolean = followAppTheme

    /** True when the launcher, not this process, picks between the light and dark app colours. */
    val followsSystemDayNight: Boolean = dayNight != null

    fun setColorFilter(
        views: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int,
    ) = applyColor(views, viewId, METHOD_COLOR_FILTER, colorRes)

    fun setTextColor(
        views: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int,
    ) = applyColor(views, viewId, METHOD_TEXT_COLOR, colorRes)

    private fun applyColor(
        views: RemoteViews,
        viewId: Int,
        method: String,
        @ColorRes colorRes: Int,
    ) {
        val pair = dayNight
        when {
            !followAppTheme -> views.setColor(viewId, method, colorRes)
            pair != null ->
                views.setColorInt(
                    viewId,
                    method,
                    pair.day.role(colorRes) ?: argb(colorRes),
                    pair.night.role(colorRes) ?: argb(colorRes),
                )
            else -> views.setInt(viewId, method, argb(colorRes))
        }
    }

    @ColorInt
    fun argb(
        @ColorRes colorRes: Int,
    ): Int = appColors?.role(colorRes) ?: ContextCompat.getColor(context, colorRes)

    companion object {
        private const val METHOD_COLOR_FILTER = "setColorFilter"
        private const val METHOD_TEXT_COLOR = "setTextColor"
        private const val THEME_CONFIG_LIGHT = "light"
        private const val THEME_CONFIG_DARK = "dark"

        fun resolve(context: Context): WidgetChrome {
            val app = context.applicationContext
            if (!WidgetAppearance.followsAppTheme(WidgetAppearance.cached(app))) {
                return WidgetChrome(app, followAppTheme = false, appColors = null, dayNight = null)
            }
            val prefs =
                app.getSharedPreferences(FontRoundnessAxis.THEME_FAST_CACHE, Context.MODE_PRIVATE)
            val themeConfig = prefs.getString("theme_config", "system") ?: "system"
            val surfaceStyle = prefs.getString("surface_style", "classic_dynamic") ?: "classic_dynamic"
            val themeBrand = prefs.getString("theme_brand", "violet") ?: "violet"
            val dynamicColor = prefs.getBoolean("use_dynamic_color", false)

            fun colors(dark: Boolean): BoxLoreChromeColors = resolveBoxLoreChromeColors(
                context = app,
                darkTheme = dark,
                dynamicColor = dynamicColor,
                themeBrand = themeBrand,
                surfaceStyle = surfaceStyle,
            )

            val followsSystem = themeConfig != THEME_CONFIG_LIGHT && themeConfig != THEME_CONFIG_DARK
            return WidgetChrome(
                context = app,
                followAppTheme = true,
                appColors = colors(darkThemeFromConfig(app, themeConfig)),
                dayNight = if (followsSystem) DayNight(day = colors(false), night = colors(true)) else null,
            )
        }
    }
}

/** Each widget colour resource and the app-theme role that stands in for it. */
private val APP_THEME_ROLES: Map<Int, (BoxLoreChromeColors) -> Int> =
    mapOf(
        R.color.widget_surface to BoxLoreChromeColors::surface,
        R.color.widget_on_surface to BoxLoreChromeColors::onSurface,
        R.color.widget_on_surface_variant to BoxLoreChromeColors::onSurfaceVariant,
        R.color.widget_primary to BoxLoreChromeColors::primary,
        R.color.widget_on_primary to BoxLoreChromeColors::onPrimary,
        R.color.widget_primary_container to BoxLoreChromeColors::primaryContainer,
        R.color.widget_on_primary_container to BoxLoreChromeColors::onPrimaryContainer,
        R.color.widget_secondary_container to BoxLoreChromeColors::secondaryContainer,
        R.color.widget_on_secondary_container to BoxLoreChromeColors::onSecondaryContainer,
        R.color.widget_tertiary to BoxLoreChromeColors::tertiary,
        R.color.widget_on_tertiary to BoxLoreChromeColors::onTertiary,
        R.color.widget_tertiary_container to BoxLoreChromeColors::tertiaryContainer,
        R.color.widget_on_tertiary_container to BoxLoreChromeColors::onTertiaryContainer,
    )

@ColorInt
private fun BoxLoreChromeColors.role(
    @ColorRes colorRes: Int,
): Int? = APP_THEME_ROLES[colorRes]?.invoke(this)
