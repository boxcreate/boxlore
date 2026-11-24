package cx.aswin.boxlore.feature.player.v2

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.player.R
import java.util.Locale

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
    }.sortedBy { it.name.lowercase(Locale.ROOT) }

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
    isConnected: Boolean,
    isConnecting: Boolean,
    description: String?,
): UiText = when {
    isConnected -> UiText.res(R.string.player_cast_connected)
    isConnecting -> UiText.res(R.string.player_cast_connecting)
    !description.isNullOrBlank() -> UiText.raw(description)
    else -> UiText.res(R.string.player_cast_ready)
}

/** The Cast device this phone has a connected session with. */
internal data class ConnectedCastDevice(val id: String, val name: String?)

/** Where the device the listener picked in the Cast sheet stands. */
internal enum class CastPickProgress { CONNECTING, CONNECTED, FAILED }

/**
 * Follows the Cast session, not MediaRouter's selection: with media transfer on (every Android
 * version boxlore runs on), MediaRouter selects a separate group route for the device, so the row
 * the listener picked never reports itself selected. A pick made while casting to another device
 * ([deviceIdAtPick]) waits until the session is on a different device.
 */
internal fun castPickProgress(
    deviceIdAtPick: String?,
    connected: ConnectedCastDevice?,
    startFailed: Boolean,
): CastPickProgress = when {
    connected != null && connected.id != deviceIdAtPick -> CastPickProgress.CONNECTED
    startFailed -> CastPickProgress.FAILED
    else -> CastPickProgress.CONNECTING
}

/**
 * Whether a device row is the one this phone casts to: by the row's Cast device ID, or by name
 * when the row carries no Cast device.
 */
internal fun isConnectedCastRoute(
    routeDeviceId: String?,
    routeName: String,
    connected: ConnectedCastDevice?,
): Boolean = when {
    connected == null -> false
    routeDeviceId != null -> routeDeviceId == connected.id
    else -> routeName == connected.name
}

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
