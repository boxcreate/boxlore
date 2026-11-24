package cx.aswin.boxlore.feature.player.v2

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.delay

/** How long the sheet waits for a picked device before letting the listener pick again. */
private const val CAST_CONNECT_TIMEOUT_MS = 30_000L

/** A short beat on "Connected" before the sheet closes. */
private const val CAST_CONNECTED_DISMISS_DELAY_MS = 250L

/** The phone's Cast session as the device sheet sees it, kept current while the sheet is open. */
@Stable
internal class CastSessionWatch(initial: ConnectedCastDevice?) {
    var connected by mutableStateOf(initial)

    /** A session failed to start since the listener last picked a device. */
    var startFailed by mutableStateOf(false)
}

@Composable
internal fun rememberCastSessionWatch(context: Context, isCasting: Boolean): CastSessionWatch {
    val watch = remember(context) { CastSessionWatch(connectedCastDevice(context)) }
    // The player's route follows the same session; read it again so the sheet never lags the player.
    LaunchedEffect(watch, isCasting) { watch.connected = connectedCastDevice(context) }
    DisposableEffect(context, watch) {
        val sessionManager = runCatching { CastContext.getSharedInstance(context).sessionManager }.getOrNull()
        val listener = CastSessionWatchListener(watch)
        sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        onDispose { sessionManager?.removeSessionManagerListener(listener, CastSession::class.java) }
    }
    return watch
}

/**
 * The device the listener picked in the sheet, kept in saved state so a fold, unfold, resize or
 * recreation keeps the pick and the device it started from.
 */
@Stable
internal class CastPickState(routeId: String?, routeName: String?, fromDeviceId: String?) {
    var routeId by mutableStateOf(routeId)
        private set
    var routeName by mutableStateOf(routeName)
        private set

    /** The device the phone was casting to when the listener picked; null when it wasn't casting. */
    var fromDeviceId by mutableStateOf(fromDeviceId)
        private set

    fun start(routeId: String, routeName: String?, fromDeviceId: String?) {
        this.routeId = routeId
        this.routeName = routeName
        this.fromDeviceId = fromDeviceId
    }

    fun clear() {
        routeId = null
        routeName = null
    }

    companion object {
        val Saver =
            listSaver<CastPickState, String?>(
                save = { listOf(it.routeId, it.routeName, it.fromDeviceId) },
                restore = { CastPickState(it[0], it[1], it[2]) },
            )
    }
}

@Composable
internal fun rememberCastPickState(): CastPickState = rememberSaveable(saver = CastPickState.Saver) { CastPickState(null, null, null) }

/**
 * Closes the sheet once [pick] connects, calling the current [onConnected] once; lets the listener
 * pick again when the session fails to start or the pick times out.
 */
@Composable
internal fun followCastPick(
    pick: CastPickState,
    session: CastSessionWatch,
    onConnected: () -> Unit,
) {
    val currentOnConnected by rememberUpdatedState(onConnected)
    val progress =
        pick.routeId?.let {
            castPickProgress(
                deviceIdAtPick = pick.fromDeviceId,
                connected = session.connected,
                startFailed = session.startFailed,
            )
        }
    LaunchedEffect(pick, progress) {
        when (progress) {
            CastPickProgress.CONNECTED -> {
                delay(CAST_CONNECTED_DISMISS_DELAY_MS)
                currentOnConnected()
            }
            CastPickProgress.FAILED -> pick.clear()
            CastPickProgress.CONNECTING -> {
                delay(CAST_CONNECT_TIMEOUT_MS)
                pick.clear()
            }
            null -> Unit
        }
    }
}

/** The Cast device behind a MediaRouter row, or null when the row carries none. */
internal fun MediaRouter.RouteInfo.castDeviceId(): String? = runCatching { CastDevice.getFromBundle(extras)?.deviceId }.getOrNull()

private fun connectedCastDevice(context: Context): ConnectedCastDevice? = runCatching {
    CastContext
        .getSharedInstance(context)
        .sessionManager
        .currentCastSession
        ?.connectedDevice()
}.getOrNull()

private fun CastSession.connectedDevice(): ConnectedCastDevice? = castDevice
    ?.takeIf { isConnected }
    ?.let { device -> ConnectedCastDevice(id = device.deviceId, name = device.friendlyName) }

private class CastSessionWatchListener(private val watch: CastSessionWatch) : SessionManagerListener<CastSession> {
    override fun onSessionStarted(session: CastSession, sessionId: String) {
        watch.connected = session.connectedDevice()
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        watch.connected = session.connectedDevice()
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
        watch.startFailed = true
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        watch.connected = null
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
        watch.connected = null
    }

    override fun onSessionStarting(session: CastSession) = Unit

    override fun onSessionEnding(session: CastSession) = Unit

    override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

    override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
}
