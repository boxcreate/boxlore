package cx.aswin.boxlore.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.RemoteViews
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import cx.aswin.boxlore.feature.widgets.HomeScreenWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The home screen widget with this [key], drawn by its own renderer with sample content. The widget
 * is laid out at its own [size] and that picture is scaled to fit the modifier's bounds, so it looks
 * the same at every window width. It is drawn again when [look] changes: pass everything that changes
 * how a widget looks (its colours, the lettering, the Your Shows cover style). It is a picture:
 * nothing in it can be tapped, and screen readers skip it.
 */
@Composable
internal fun HomeScreenWidgetPreview(key: String, size: DpSize, look: Any, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Light and dark, and the font scale, change what the widget's own layout resolves to.
    val configuration = LocalConfiguration.current
    val views by produceState<RemoteViews?>(initialValue = null, key, look, configuration) {
        value = withContext(Dispatchers.Default) { HomeScreenWidgets.preview(context, key) }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = widgetPictureScale(size, maxWidth, maxHeight)
        AndroidView(
            factory = ::WidgetPictureHost,
            update = { host -> host.show(views) },
            modifier =
            Modifier.requiredSize(size).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
    }
}

/** How much a widget laid out at [size] is scaled to fit the space it has. It is shrunk to fit, never enlarged. */
internal fun widgetPictureScale(size: DpSize, maxWidth: Dp, maxHeight: Dp): Float = minOf(1f, maxWidth / size.width, maxHeight / size.height)

/**
 * Holds one widget's views the way a home screen does: a list or grid a widget carries with it is
 * only filled inside a widget host. Touches never reach the widget, so its buttons cannot control
 * playback from Settings.
 */
@SuppressLint("ViewConstructor")
private class WidgetPictureHost(context: Context) : AppWidgetHostView(context) {
    private var shown: RemoteViews? = null

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun show(views: RemoteViews?) {
        if (views === shown) return
        shown = views
        removeAllViews()
        if (views == null) return
        // A preview that cannot be drawn leaves its frame empty; the widget's name still stands beside it.
        runCatching { addView(views.apply(context, this), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
