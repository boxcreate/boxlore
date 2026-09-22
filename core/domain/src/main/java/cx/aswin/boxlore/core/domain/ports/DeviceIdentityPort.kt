package cx.aswin.boxlore.core.domain.ports

/**
 * Narrow device-identity seam providing a stable identifier for this device/installation.
 *
 * Used for cross-device sync attribution (e.g. queue and subscription mutations).
 */
fun interface DeviceIdentityPort {
    fun getDeviceId(): String
}
