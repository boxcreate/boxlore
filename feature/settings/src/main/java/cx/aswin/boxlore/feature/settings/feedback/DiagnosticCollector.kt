package cx.aswin.boxlore.feature.settings.feedback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.util.Locale

/**
 * Diagnostic information about the device and runtime environment.
 */
data class DiagnosticInfo(
    val appVersion: String,
    val buildCode: Long,
    val packageName: String,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val networkType: String,
    val audioRoute: String,
    val locale: String,
) {
    /**
     * Compact summary designed to fit comfortably inside the in-app feedback body (~120 chars).
     */
    fun toCondensedSummary(): String = "App: v$appVersion ($buildCode) | OS: Android $androidRelease (API $sdkInt) | Device: $manufacturer $model | Net: $networkType | Audio: $audioRoute"

    /**
     * Full GitHub / Sharesheet Markdown report.
     */
    fun toMarkdownReport(sanitizedLogcat: String? = null): String = buildString {
            append("### System Diagnostics\n")
            append("- **App**: boxlore `$appVersion` (build `$buildCode`)\n")
            append("- **Package**: `$packageName`\n")
            append("- **Android**: `$androidRelease` (API `$sdkInt`)\n")
            append("- **Device**: `$manufacturer $model`\n")
            append("- **Network**: `$networkType`\n")
            append("- **Audio Route**: `$audioRoute`\n")
            append("- **Locale**: `$locale`\n\n")

            if (!sanitizedLogcat.isNullOrBlank()) {
                append("<details>\n<summary>Sanitized App Logs</summary>\n\n")
                append("```\n")
                append(sanitizedLogcat.trim())
                append("\n```\n</details>\n")
            }
        }
}

object DiagnosticCollector {

    /**
     * Extracts non-sensitive diagnostic parameters from the Android environment.
     */
    fun collect(context: Context): DiagnosticInfo {
        val pm = context.packageManager
        val pkgName = context.packageName
        val packageInfo = try {
            pm.getPackageInfo(pkgName, 0)
        } catch (_: Exception) {
            null
        }

        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

        val networkType = determineNetworkType(context)
        val audioRoute = determineAudioRoute(context)
        val locale = Locale.getDefault().toLanguageTag()

        return DiagnosticInfo(
            appVersion = versionName,
            buildCode = versionCode,
            packageName = pkgName,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            networkType = networkType,
            audioRoute = audioRoute,
            locale = locale,
        )
    }

    private fun determineNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"
        val network = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "Offline"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
    }

    private fun determineAudioRoute(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "Default"

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeDeviceTypes = devices.map { it.type }

        return when {
            activeDeviceTypes.contains(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) ||
                activeDeviceTypes.contains(AudioDeviceInfo.TYPE_BLUETOOTH_SCO) ||
                activeDeviceTypes.contains(AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                activeDeviceTypes.contains(AudioDeviceInfo.TYPE_BLE_SPEAKER) -> "Bluetooth"

            activeDeviceTypes.contains(AudioDeviceInfo.TYPE_WIRED_HEADPHONES) ||
                activeDeviceTypes.contains(AudioDeviceInfo.TYPE_WIRED_HEADSET) ||
                activeDeviceTypes.contains(AudioDeviceInfo.TYPE_USB_HEADSET) -> "Headphones"

            activeDeviceTypes.contains(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) -> "Speaker"
            else -> "Default"
        }
    }
}
