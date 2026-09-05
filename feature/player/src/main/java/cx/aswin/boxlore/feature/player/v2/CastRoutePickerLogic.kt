package cx.aswin.boxlore.feature.player.v2

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext

internal fun availableCastRoutes(
    mediaRouter: MediaRouter,
    selector: MediaRouteSelector,
): List<MediaRouter.RouteInfo> = mediaRouter.routes
    .filter { route ->
        shouldShowCastRoute(
            isEnabled = route.isEnabled,
            isDefault = route.isDefault,
            isBluetooth = route.isBluetooth,
            matchesSelector = route.matchesSelector(selector),
        )
    }.sortedBy { it.name.lowercase() }

@Suppress("DEPRECATION")
internal fun resolveCastRouteSelector(context: Context): MediaRouteSelector = runCatching {
    CastContext
        .getSharedInstance(context)
        .mergedSelector
}.getOrNull()
    ?.takeUnless(MediaRouteSelector::isEmpty)
    ?: MediaRouteSelector
        .Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
            ),
        ).build()

internal fun shouldShowCastRoute(
    isEnabled: Boolean,
    isDefault: Boolean,
    isBluetooth: Boolean,
    matchesSelector: Boolean,
): Boolean = isEnabled && !isDefault && !isBluetooth && matchesSelector

internal fun castRouteSubtitle(
    isSelected: Boolean,
    isConnecting: Boolean,
    description: String?,
): String = when {
    isSelected -> "Connected"
    isConnecting -> "Connecting…"
    !description.isNullOrBlank() -> description
    else -> "Ready to cast"
}

internal fun isCastConnectionComplete(
    pendingRouteId: String?,
    routeId: String,
    isSelected: Boolean,
    hasActiveCastSession: Boolean,
): Boolean = pendingRouteId == routeId && isSelected && hasActiveCastSession

internal fun resolveCastButtonTint(
    enabled: Boolean,
    isCasting: Boolean,
    tint: Color,
    activeTint: Color,
): Color = when {
    !enabled -> tint.copy(alpha = 0.38f)
    isCasting -> activeTint
    else -> tint
}
