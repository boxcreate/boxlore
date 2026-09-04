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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.cast.Cast
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun BoxLoreCastRouteButton(
    enabled: Boolean,
    isCasting: Boolean,
    modifier: Modifier = Modifier,
) {
    var showRoutePicker by rememberSaveable { mutableStateOf(false) }
    IconButton(
        onClick = { showRoutePicker = true },
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            contentDescription = if (isCasting) "Change Cast device" else "Cast",
        )
    }
    if (showRoutePicker) {
        CastRoutePickerSheet(
            isCasting = isCasting,
            onDismiss = { showRoutePicker = false },
        )
    }
}

@Composable
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
internal fun CastRoutePickerSheet(
    isCasting: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val mediaRouter = remember(context) { MediaRouter.getInstance(context) }
    val routeSelector = remember(context) { resolveCastRouteSelector(context) }
    val routes = rememberAvailableCastRoutes(mediaRouter, routeSelector)
    var connectingRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    var connectingRouteName by rememberSaveable { mutableStateOf<String?>(null) }
    var hasConnectedCastSession by remember { mutableStateOf(false) }

    val connectionComplete =
        routes.any { route ->
            isCastConnectionComplete(
                pendingRouteId = connectingRouteId,
                routeId = route.id,
                isSelected = route.isSelected,
                hasActiveCastSession = isCasting || hasConnectedCastSession,
            )
        }
    dismissConnectedCastRoute(
        connectionComplete = connectionComplete,
        onDismiss = onDismiss,
    )
    monitorPendingCastConnection(
        context = context,
        connectingRouteId = connectingRouteId,
        onConnectionChanged = { hasConnectedCastSession = it },
        onTimeout = {
            connectingRouteId = null
            connectingRouteName = null
        },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        CastRoutePickerContent(
            isCasting = isCasting,
            routes = routes,
            connectingRouteId = connectingRouteId,
            connectingRouteName = connectingRouteName,
            onStopCasting = {
                Cast.getSingletonInstance(context).endCurrentSession(true)
                onDismiss()
            },
            onRouteSelected = { route ->
                connectingRouteId = route.id
                connectingRouteName = route.name
                route.select()
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
private fun dismissConnectedCastRoute(
    connectionComplete: Boolean,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(connectionComplete) {
        if (connectionComplete) {
            delay(250L)
            onDismiss()
        }
    }
}

@Composable
private fun monitorPendingCastConnection(
    context: android.content.Context,
    connectingRouteId: String?,
    onConnectionChanged: (Boolean) -> Unit,
    onTimeout: () -> Unit,
) {
    LaunchedEffect(connectingRouteId) {
        if (connectingRouteId == null) {
            onConnectionChanged(false)
            return@LaunchedEffect
        }
        val connected = awaitActiveCastSession(context)
        onConnectionChanged(connected)
        if (!connected) onTimeout()
    }
}

private suspend fun awaitActiveCastSession(context: android.content.Context): Boolean {
    repeat(60) {
        val connected =
            runCatching {
                CastContext
                    .getSharedInstance(context)
                    .sessionManager
                    .currentCastSession
                    ?.isConnected == true
            }.getOrDefault(false)
        if (connected) return true
        delay(250L)
    }
    return false
}

@Composable
private fun CastRoutePickerContent(
    isCasting: Boolean,
    routes: List<MediaRouter.RouteInfo>,
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
            text = "AVAILABLE DEVICES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isCasting) {
            CastRouteRow(
                title = "This phone",
                subtitle = "Move playback back here",
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
            CastRouteRow(
                title = route.name,
                subtitle =
                castRouteSubtitle(
                    isSelected = route.isSelected,
                    isConnecting =
                    isPending ||
                        route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
                    description = route.description,
                ),
                selected = route.isSelected,
                enabled = connectingRouteId == null,
                connecting = isPending,
                icon = {
                    Icon(
                        imageVector = if (route.isSelected) Icons.Rounded.CastConnected else Icons.Rounded.Tv,
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
            text = "Devices must be on the same Wi‑Fi network.",
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
            text = "Choose where to listen",
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
                text = "Connecting…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        } else if (selected) {
            Text(
                text = "Playing",
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
                text = "Connecting to $deviceName…",
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
            text = "Looking for nearby devices…",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Make sure your Cast device is awake.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
