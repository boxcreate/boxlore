package cx.aswin.boxlore.core.network.session

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How the API trusted the install when it issued a session, best first. */
enum class ApiAttestation(val wire: String, val rank: Int) {
    AppCheck("appcheck", 2),
    Challenge("challenge", 1),
    None("none", 0),
    ;

    companion object {
        /** Unknown kinds are treated as the least trusted, so the app never over-reaches. */
        fun fromWire(value: String?): ApiAttestation = entries.firstOrNull { it.wire == value } ?: None
    }
}

/**
 * A `bls1.<kid>.<payload>.<sig>` session from `POST /auth/session` (or `X-Session-Renew`) plus the
 * claims the app reads from its payload. Times are the API's epoch seconds. The app never verifies
 * the signature; the API does that on every request.
 */
data class ApiSessionToken(
    val raw: String,
    val sid: String,
    val attestation: ApiAttestation,
    val issuedAt: Long,
    val expiresAt: Long,
    val attestedUntil: Long,
    val hasDeviceKey: Boolean,
) {
    /** A limited session reaches cheap routes only; AI routes need a step-up. */
    val limited: Boolean get() = attestation == ApiAttestation.None

    /** Renews silently past [expiresAt] (with a device-key proof) until [attestedUntil]. */
    val renewable: Boolean get() = hasDeviceKey && attestedUntil > expiresAt

    override fun toString(): String = "ApiSessionToken(att=${attestation.wire}, exp=$expiresAt, attExp=$attestedUntil)"

    companion object {
        private const val PREFIX = "bls1"
        private const val MAX_LENGTH = 2048
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String?): ApiSessionToken? {
            if (raw.isNullOrBlank() || raw.length > MAX_LENGTH) return null
            val parts = raw.split('.')
            if (parts.size != 4 || parts[0] != PREFIX) return null
            val claims = runCatching {
                json.decodeFromString(SessionClaims.serializer(), String(Base64.getUrlDecoder().decode(parts[2]), Charsets.UTF_8))
            }.getOrNull() ?: return null
            if (claims.sid.isBlank() || claims.exp <= 0L) return null
            return ApiSessionToken(
                raw = raw,
                sid = claims.sid,
                attestation = ApiAttestation.fromWire(claims.att),
                issuedAt = claims.iat,
                expiresAt = claims.exp,
                attestedUntil = maxOf(claims.attExp, claims.exp),
                hasDeviceKey = !claims.dpk.isNullOrEmpty(),
            )
        }
    }
}

@Serializable
private data class SessionClaims(
    val sid: String = "",
    val att: String? = null,
    val iat: Long = 0L,
    val exp: Long = 0L,
    val attExp: Long = 0L,
    val dpk: String? = null,
)
