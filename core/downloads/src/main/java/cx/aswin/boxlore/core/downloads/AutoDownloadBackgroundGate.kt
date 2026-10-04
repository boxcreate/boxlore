package cx.aswin.boxlore.core.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class BackgroundCheckDeviceState(
    val connected: Boolean = false,
    val unmetered: Boolean = false,
    val batteryNotLow: Boolean = false,
    val charging: Boolean = false,
)

/** Unknown consent, connectivity or battery state never authorizes background feed access. */
internal class AutoDownloadBackgroundGate(
    private val settings: Flow<AutoDownloadBackgroundSettings>,
    private val readDevice: () -> BackgroundCheckDeviceState,
    private val deviceChanges: Flow<BackgroundCheckDeviceState>,
) {
    suspend fun allowed(): Boolean = permits(settings.first(), readDevice())

    /** Cancel in-flight work on revocation as well as checking at admission. */
    suspend fun <T> runGuarded(block: suspend () -> T): T? {
        val initial = settings.first()
        if (!permits(initial, readDevice())) return null
        return coroutineScope {
            val action = async { if (allowed()) block() else null }
            val watcher = launch {
                combine(settings, deviceChanges) { policy, device -> policy != initial || !permits(policy, device) }.first { it }
                action.cancel(PolicyRevoked())
            }
            try {
                action.await()
            } catch (_: PolicyRevoked) {
                null
            } finally {
                watcher.cancel()
            }
        }
    }

    private class PolicyRevoked : CancellationException("Background auto-download policy no longer permits work")

    companion object {
        fun permits(settings: AutoDownloadBackgroundSettings, device: BackgroundCheckDeviceState): Boolean =
            settings.enabled &&
                device.connected &&
                device.batteryNotLow &&
                (!settings.wifiOnly || device.unmetered) &&
            (!settings.chargingOnly || device.charging)

        fun create(context: Context, preferences: UserPreferencesRepository, forTransfer: Boolean = false): AutoDownloadBackgroundGate {
            val reader = { readDeviceState(context) }
            val policy = if (forTransfer) {
                combine(preferences.autoDownloadBackgroundSettingsStream, preferences.autoDownloadWifiOnlyStream) { background, wifi ->
                    background.copy(wifiOnly = background.wifiOnly || wifi)
                }
            } else {
                preferences.autoDownloadBackgroundSettingsStream
            }
            return AutoDownloadBackgroundGate(policy, reader, deviceChanges(context, reader))
        }

        private fun readDeviceState(context: Context): BackgroundCheckDeviceState = try {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            BackgroundCheckDeviceState(
                connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                unmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
                batteryNotLow = level >= 0 && scale > 0 && level.toLong() * 100 > scale.toLong() * 15,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        } catch (_: Exception) {
            BackgroundCheckDeviceState()
        }

        private fun deviceChanges(context: Context, read: () -> BackgroundCheckDeviceState): Flow<BackgroundCheckDeviceState> = callbackFlow {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val network = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(read())
                }
                override fun onLost(network: Network) {
                    trySend(read())
                }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    trySend(read())
                }
            }
            val battery = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(read())
                }
            }
            var networkRegistered = false
            var batteryRegistered = false
            try {
                manager?.registerDefaultNetworkCallback(network)
                networkRegistered = manager != null
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                if (Build.VERSION.SDK_INT >= 33) {
                    context.registerReceiver(battery, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(battery, filter)
                }
                batteryRegistered = true
                trySend(read())
            } catch (_: Exception) {
                trySend(BackgroundCheckDeviceState())
            }
            awaitClose {
                if (networkRegistered) manager?.unregisterNetworkCallback(network)
                if (batteryRegistered) context.unregisterReceiver(battery)
            }
        }
    }
}
