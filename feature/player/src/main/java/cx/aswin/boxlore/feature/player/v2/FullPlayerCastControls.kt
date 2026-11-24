package cx.aswin.boxlore.feature.player.v2

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.cast.Cast
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.feature.player.R

@OptIn(UnstableApi::class)
@Composable
internal fun BoxLoreCastRouteButton(
    enabled: Boolean,
    isCasting: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    activeTint: Color = MaterialTheme.colorScheme.primary,
) {
    val hosted = LocalCastPicker.current
    val picker = hosted ?: rememberCastPickerState()
    val effectiveTint =
        resolveCastButtonTint(
            enabled = enabled,
            isCasting = isCasting,
            tint = tint,
            activeTint = activeTint,
        )
    Box(
        modifier =
        modifier
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = picker.onCastClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            contentDescription = stringResource(if (isCasting) R.string.player_change_cast_device else R.string.player_cast),
            tint = effectiveTint,
            modifier = Modifier.size(20.dp),
        )
    }
    // Without a host (outside the full player), the button shows its own sheet.
    if (hosted == null) CastPickerSheetHost(picker, isCasting)
}

@Composable
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
internal fun CastRoutePickerSheet(
    isCasting: Boolean,
    onDismiss: () -> Unit,
) {
    TrackScreenView("player_cast")
    val context = LocalContext.current
    val mediaRouter = remember(context) { MediaRouter.getInstance(context) }
    val routeSelector = remember(context) { resolveCastRouteSelector(context) }
    val routes = rememberAvailableCastRoutes(mediaRouter, routeSelector)
    val castSession = rememberCastSessionWatch(context, isCasting)
    val pick = rememberCastPickState()
    followCastPick(pick = pick, session = castSession, onConnected = onDismiss)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        CastRoutePickerContent(
            isCasting = isCasting,
            routes = routes,
            connectedDevice = castSession.connected,
            connectingRouteId = pick.routeId,
            connectingRouteName = pick.routeName,
            onStopCasting = {
                Cast.getSingletonInstance(context).endCurrentSession(true)
                onDismiss()
            },
            onRouteSelected = { route ->
                if (isConnectedCastRoute(route.castDeviceId(), route.name, castSession.connected)) {
                    // Already casting there: nothing to connect.
                    onDismiss()
                } else {
                    castSession.startFailed = false
                    pick.start(route.id, route.name, fromDeviceId = castSession.connected?.id)
                    route.select()
                }
            },
        )
    }
}

@Composable
private fun rememberAvailableCastRoutes(
    mediaRouter: MediaRouter,
    routeSelector: MediaRouteSelector,
): List<MediaRouter.RouteInfo> {
    var routes by remember(mediaRouter, routeSelector) {
        mutableStateOf(availableCastRoutes(mediaRouter, routeSelector))
    }
    DisposableEffect(mediaRouter, routeSelector) {
        fun refreshRoutes() {
            routes = availableCastRoutes(mediaRouter, routeSelector)
        }

        val callback =
            object : MediaRouter.Callback() {
                override fun onRouteAdded(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteRemoved(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteChanged(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refreshRoutes()

                override fun onRouteSelected(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                    reason: Int,
                ) = refreshRoutes()

                override fun onRouteUnselected(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                    reason: Int,
                ) = refreshRoutes()
            }
        mediaRouter.addCallback(
            routeSelector,
            callback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
        )
        refreshRoutes()
        onDispose { mediaRouter.removeCallback(callback) }
    }
    return routes
}

@Composable
private fun CastRoutePickerContent(
    isCasting: Boolean,
    routes: List<MediaRouter.RouteInfo>,
    connectedDevice: ConnectedCastDevice?,
    connectingRouteId: String?,
    connectingRouteName: String?,
    onStopCasting: () -> Unit,
    onRouteSelected: (MediaRouter.RouteInfo) -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        CastPickerHeader()
        Spacer(modifier = Modifier.height(18.dp))
        if (connectingRouteName != null) {
            CastConnectingIndicator(deviceName = connectingRouteName)
            Spacer(modifier = Modifier.height(18.dp))
        }
        Text(
            text = stringResource(R.string.player_cast_available_devices).uppercase(currentLocale()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isCasting) {
            CastRouteRow(
                title = stringResource(R.string.player_cast_this_phone),
                subtitle = stringResource(R.string.player_cast_move_back_here),
                selected = false,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                    )
                },
                onClick = onStopCasting,
            )
        }
        routes.forEach { route ->
            val isPending = route.id == connectingRouteId
            val isConnected = isConnectedCastRoute(route.castDeviceId(), route.name, connectedDevice)
            CastRouteRow(
                title = route.name,
                subtitle =
                castRouteSubtitle(
                    isConnected = isConnected,
                    isConnecting =
                    isPending ||
                        route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
                    description = route.description,
                ).asString(),
                selected = isConnected,
                enabled = connectingRouteId == null,
                connecting = isPending && !isConnected,
                icon = {
                    Icon(
                        imageVector = if (isConnected) Icons.Rounded.CastConnected else Icons.Rounded.Tv,
                        contentDescription = null,
                    )
                },
                onClick = { onRouteSelected(route) },
            )
        }
        if (routes.isEmpty()) CastRouteEmptyState()
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.player_cast_same_wifi),
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CastPickerHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Rounded.Cast,
                contentDescription = null,
                modifier = Modifier.padding(12.dp).size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.player_cast_choose_where),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CastRouteRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    connecting: Boolean = false,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Column(
            modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (connecting) {
            Text(
                text = stringResource(R.string.player_cast_connecting),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        } else if (selected) {
            Text(
                text = stringResource(R.string.player_playing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CastConnectingIndicator(deviceName: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxLoreLoader.CircularWavy(
                size = 28.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            )
            Text(
                text = stringResource(R.string.player_cast_connecting_to, deviceName),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CastRouteEmptyState() {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BoxLoreLoader.CircularWavy(
            size = 28.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
            text = stringResource(R.string.player_cast_looking),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.player_cast_awake),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
