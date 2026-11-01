package cx.aswin.boxlore.core.network.session

/**
 * Signs one request with the device key, for routes that hold this install's private data
 * (support): `<unix seconds>.<signature>` over [message] for the session in the request, on the
 * API's clock ([serverNow], unix seconds). [ApiSessionManager.requestProofs] is the app's one.
 */
class RequestProofSigner(private val deviceKey: DeviceKeySigner, private val serverNow: () -> Long) {
    /** Null when the token can't be read, has no device key, or the key can't sign; the API then refuses the route. */
    fun proof(rawToken: String, method: String, path: String): String? {
        val token = ApiSessionToken.parse(rawToken)?.takeIf { it.hasDeviceKey } ?: return null
        val now = serverNow()
        val signature = runCatching { deviceKey.sign(message(token.sid, method, path, now)) }.getOrNull() ?: return null
        return "$now.$signature"
    }

    companion object {
        /** The string the device key signs: `bls-request.<sid>.<METHOD>.<path>.<unix seconds>`. */
        fun message(sid: String, method: String, path: String, unixSeconds: Long): String =
            "bls-request.$sid.${method.uppercase()}.$path.$unixSeconds"
    }
}
