package cx.aswin.boxlore.feature.widgets

import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes

/**
 * Applies [R.color.widget_*] roles through [WidgetChrome] so App-theme widgets bake
 * Appearance ARGB while System widgets keep resource-backed Material You colors.
 */
internal object WidgetRemoteViewsColors {
    fun setColorFilter(
        views: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int,
        chrome: WidgetChrome,
    ) {
        chrome.setColorFilter(views, viewId, colorRes)
    }

    fun setTextColor(
        views: RemoteViews,
        viewId: Int,
        @ColorRes colorRes: Int,
        chrome: WidgetChrome,
    ) {
        chrome.setTextColor(views, viewId, colorRes)
    }

    @ColorInt
    fun resolve(
        chrome: WidgetChrome,
        @ColorRes colorRes: Int,
    ): Int = chrome.argb(colorRes)
}
