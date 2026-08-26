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
 */
internal class WidgetChrome private constructor(
    private val context: Context,
    private val followAppTheme: Boolean,
    private val appColors: BoxLoreChromeColors?,
) {
    val usesAppTheme: Boolean = followAppTheme

    fun setColorFilter(
        views: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int,
    ) {
        if (followAppTheme) {
            views.setInt(viewId, "setColorFilter", argb(colorRes))
        } else {
            views.setColor(viewId, "setColorFilter", colorRes)
        }
    }

    fun setTextColor(
        views: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int,
    ) {
        if (followAppTheme) {
            views.setTextColor(viewId, argb(colorRes))
        } else {
            views.setColor(viewId, "setTextColor", colorRes)
        }
    }

    @ColorInt
    fun argb(
        @ColorRes colorRes: Int,
    ): Int {
        val colors = appColors
        if (colors != null) {
            return when (colorRes) {
                R.color.widget_surface -> colors.surface
                R.color.widget_on_surface -> colors.onSurface
                R.color.widget_on_surface_variant -> colors.onSurfaceVariant
                R.color.widget_primary -> colors.primary
                R.color.widget_on_primary -> colors.onPrimary
                R.color.widget_primary_container -> colors.primaryContainer
                R.color.widget_on_primary_container -> colors.onPrimaryContainer
                R.color.widget_secondary_container -> colors.secondaryContainer
                R.color.widget_on_secondary_container -> colors.onSecondaryContainer
                else -> ContextCompat.getColor(context, colorRes)
            }
        }
        return ContextCompat.getColor(context, colorRes)
    }

    companion object {
        fun resolve(context: Context): WidgetChrome {
            val app = context.applicationContext
            if (!WidgetAppearance.followsAppTheme(WidgetAppearance.cached(app))) {
                return WidgetChrome(app, followAppTheme = false, appColors = null)
            }
            val prefs =
                app.getSharedPreferences(FontRoundnessAxis.THEME_FAST_CACHE, Context.MODE_PRIVATE)
            val themeConfig = prefs.getString("theme_config", "system") ?: "system"
            val surfaceStyle = prefs.getString("surface_style", "classic_dynamic") ?: "classic_dynamic"
            val themeBrand = prefs.getString("theme_brand", "violet") ?: "violet"
            val dynamicColor = prefs.getBoolean("use_dynamic_color", false)
            val colors =
                resolveBoxLoreChromeColors(
                    context = app,
                    darkTheme = darkThemeFromConfig(app, themeConfig),
                    dynamicColor = dynamicColor,
                    themeBrand = themeBrand,
                    surfaceStyle = surfaceStyle,
                )
            return WidgetChrome(app, followAppTheme = true, appColors = colors)
        }
    }
}
