package cx.aswin.boxlore.feature.widgets

import androidx.annotation.ColorRes

/**
 * Widget chrome color **resource** roles ([R.color.widget_*] → `system_neutral*` /
 * `system_accent*` on API 31+). Pass these to [WidgetRemoteViewsColors] so accents
 * re-resolve; do not bake ARGB ints into RemoteViews on API 31+.
 */
internal object WidgetPalette {
    @ColorRes val surface: Int = R.color.widget_surface

    @ColorRes val onSurface: Int = R.color.widget_on_surface

    @ColorRes val onSurfaceVariant: Int = R.color.widget_on_surface_variant

    @ColorRes val primary: Int = R.color.widget_primary

    @ColorRes val onPrimary: Int = R.color.widget_on_primary

    @ColorRes val secondaryContainer: Int = R.color.widget_secondary_container

    @ColorRes val onSecondaryContainer: Int = R.color.widget_on_secondary_container

    @ColorRes val primaryContainer: Int = R.color.widget_primary_container

    @ColorRes val onPrimaryContainer: Int = R.color.widget_on_primary_container
}
