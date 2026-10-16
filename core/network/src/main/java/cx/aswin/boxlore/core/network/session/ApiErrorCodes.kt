package cx.aswin.boxlore.core.network.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Error codes the API shield sends as `{"error": "<code>"}`. */
object ApiErrorCodes {
    const val SESSION_EXPIRED = "session_expired"
    const val SESSION_INVALID = "session_invalid"
    const val SESSION_REVOKED = "session_revoked"
    const val SESSION_REQUIRED = "session_required"
    const val VERIFICATION_REQUIRED = "verification_required"
    const val DAILY_AI_LIMIT = "daily_ai_limit"

    /** The session itself was refused: get a new one once, then retry. */
    val SESSION_REJECTIONS = setOf(SESSION_EXPIRED, SESSION_INVALID, SESSION_REVOKED)

    /** Answers about the API session, never about a Firebase sign-in. */
    val SHIELD_CODES = SESSION_REJECTIONS + setOf(SESSION_REQUIRED, VERIFICATION_REQUIRED, DAILY_AI_LIMIT)

    /** AI routes refused for this session even after any step-up: onboarding uses its non-AI path. */
    val AI_UNAVAILABLE = setOf(VERIFICATION_REQUIRED, DAILY_AI_LIMIT)

    private const val MAX_PEEK_BYTES = 2_048L
    private val json = Json { ignoreUnknownKeys = true }

    /** The `error` field of a small JSON body, or null for anything else. */
    fun parse(body: String?): String? {
        if (body.isNullOrBlank() || body.length > MAX_PEEK_BYTES) return null
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        return (element["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    /** Reads the code of a 401 or 429 without consuming the body. */
    fun of(response: okhttp3.Response): String? {
        if (response.code != 401 && response.code != 429) return null
        return parse(runCatching { response.peekBody(MAX_PEEK_BYTES).string() }.getOrNull())
    }

    /** Reads the code of a failed Retrofit response; consumes its error body. */
    fun of(response: retrofit2.Response<*>): String? {
        if (response.isSuccessful) return null
        return parse(runCatching { response.errorBody()?.string() }.getOrNull())
    }
}
