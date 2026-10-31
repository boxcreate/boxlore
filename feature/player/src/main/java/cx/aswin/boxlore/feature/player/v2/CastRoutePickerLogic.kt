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

/** Android 17 (API 37): finding speakers on the local network needs this runtime permission. */
internal const val LOCAL_NETWORK_PERMISSION_SDK = 37
internal const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** What tapping Cast does. */
internal enum class CastButtonAction { SHOW_SHEET, REQUEST_LOCAL_NETWORK, SHOW_SYSTEM_PICKER }

/**
 * boxlore's Cast sheet finds speakers itself, which on Android 17 needs local network access;
 * without it, ask once per tap.
 */
internal fun castButtonAction(sdkInt: Int, hasLocalNetworkAccess: Boolean): CastButtonAction = when {
    sdkInt < LOCAL_NETWORK_PERMISSION_SDK || hasLocalNetworkAccess -> CastButtonAction.SHOW_SHEET
    else -> CastButtonAction.REQUEST_LOCAL_NETWORK
}

/** Declined (now or before): Android's own output picker still lists Cast speakers, with no permission. */
internal fun castActionAfterLocalNetworkRequest(granted: Boolean): CastButtonAction =
    if (granted) CastButtonAction.SHOW_SHEET else CastButtonAction.SHOW_SYSTEM_PICKER
