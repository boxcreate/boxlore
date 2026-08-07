package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

/**
 * Applies [R.color.widget_*] roles to RemoteViews via [RemoteViews.setColor] so the
 * host stores a color **resource** and can re-resolve Material You / system accent
 * colors on theme changes (instead of baking a stale ARGB int).
 *
 * Module minSdk is 31, so [RemoteViews.setColor] is always available.
 */
internal object WidgetRemoteViewsColors {
    fun setColorFilter(
        views: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int,
        @Suppress("UNUSED_PARAMETER") context: Context,
    ) {
        views.setColor(viewId, "setColorFilter", colorRes)
    }

    fun setTextColor(
        views: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int,
        @Suppress("UNUSED_PARAMETER") context: Context,
    ) {
        views.setColor(viewId, "setTextColor", colorRes)
    }

    fun resolve(
        context: Context,
        @ColorRes colorRes: Int,
    ): Int = ContextCompat.getColor(context, colorRes)
}
