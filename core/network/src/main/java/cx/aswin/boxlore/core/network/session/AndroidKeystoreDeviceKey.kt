package cx.aswin.boxlore.core.network.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * The session's device key: a non-exportable EC P-256 key in the Android Keystore, in StrongBox
 * when the phone has one and in the TEE otherwise. Keystore keys never leave the phone and are not
 * restored from backups, so a session copied to another phone stops renewing.
 */
class AndroidKeystoreDeviceKey(private val alias: String = DEFAULT_ALIAS) : DeviceKeySigner {
    private val lock = Any()

    override fun publicKey(): String? = synchronized(lock) {
        runCatching {
            val keyStore = keyStore()
            val certificate = keyStore.getCertificate(alias) ?: run {
                generate()
                keyStore().getCertificate(alias)
            }
            encoder.encodeToString(certificate.publicKey.encoded)
        }.onFailure { Log.w(TAG, "Device key unavailable", it) }.getOrNull()
    }

    override fun sign(message: String): String? = runCatching {
        val key = keyStore().getKey(alias, null) as? PrivateKey ?: return null
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(key)
            update(message.toByteArray(Charsets.UTF_8))
        }
        encoder.encodeToString(signature.sign())
    }.onFailure { Log.w(TAG, "Device key could not sign", it) }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun generate() {
        // StrongBox is optional hardware; phones without it (or with a broken one) use the TEE.
        val strongBox = runCatching { generate(strongBox = true) }
        if (strongBox.isFailure) generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).apply { initialize(spec) }.generateKeyPair()
    }

    companion object {
        /** Keystore alias; keep it stable so existing sessions keep renewing after updates. */
        const val DEFAULT_ALIAS = "boxlore_api_session_device_key"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TAG = "ApiSessionDeviceKey"
        private val encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
