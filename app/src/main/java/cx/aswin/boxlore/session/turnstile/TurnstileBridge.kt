package cx.aswin.boxlore.session.turnstile

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What the step-up page (`public/auth/turnstile.html` on the API host) reports through its bridge. */
internal sealed interface TurnstileBridgeEvent {
    data class Token(val value: String) : TurnstileBridgeEvent

    /** [code] is Turnstile's error code (or `render` / `script` from the page itself). */
    data class Error(val code: String) : TurnstileBridgeEvent

    data object Interactive : TurnstileBridgeEvent

    data object InteractionDone : TurnstileBridgeEvent

    data object Expired : TurnstileBridgeEvent

    data object ChallengeTimeout : TurnstileBridgeEvent

    data object Unsupported : TurnstileBridgeEvent

    data object Ready : TurnstileBridgeEvent

    data object Unknown : TurnstileBridgeEvent
}

internal object TurnstileBridge {
    /** The JavaScript object the page posts to: `BoxloreTurnstile.postMessage(json)`. */
    const val BRIDGE_NAME = "BoxloreTurnstile"
    const val PAGE_PATH = "auth/turnstile"
    private const val CHALLENGE_HOST = "challenges.cloudflare.com"
    private const val MAX_MESSAGE_LENGTH = 4_096
    private const val MAX_CODE_LENGTH = 24
    private val json = Json { ignoreUnknownKeys = true }

    /** The step-up page on the API host. Static assets drop `.html`, so the clean path is used. */
    fun pageUrl(apiBaseUrl: String, dark: Boolean): String = apiBaseUrl.trimEnd('/') + "/" + PAGE_PATH + "?theme=" + if (dark) "dark" else "light"

    /** The page's own origin; only it may post to the bridge. */
    fun origin(pageUrl: String): String? = runCatching {
        val uri = URI(pageUrl)
        val port = if (uri.port == -1) "" else ":${uri.port}"
        "${uri.scheme}://${uri.host}$port"
    }.getOrNull()

    /** Top-level navigation stays on the API host, Turnstile's own, or the blank frames Turnstile uses. */
    fun allowsNavigation(url: String, apiHost: String?): Boolean {
        if (url == "about:blank" || url == "about:srcdoc") return true
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https") return false
        return uri.host == apiHost || uri.host == CHALLENGE_HOST
    }

    fun parse(message: String?): TurnstileBridgeEvent {
        if (message.isNullOrBlank() || message.length > MAX_MESSAGE_LENGTH) return TurnstileBridgeEvent.Unknown
        val body = runCatching { json.parseToJsonElement(message) }.getOrNull() as? JsonObject ?: return TurnstileBridgeEvent.Unknown
        val value = (body["value"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when ((body["type"] as? JsonPrimitive)?.content) {
            "token" -> value?.takeIf(String::isNotBlank)?.let(TurnstileBridgeEvent::Token) ?: TurnstileBridgeEvent.Error("empty_token")
            "error" -> TurnstileBridgeEvent.Error(sanitizeCode(value))
            "interactive" -> TurnstileBridgeEvent.Interactive
            "interactive_done" -> TurnstileBridgeEvent.InteractionDone
            "expired" -> TurnstileBridgeEvent.Expired
            "timeout" -> TurnstileBridgeEvent.ChallengeTimeout
            "unsupported" -> TurnstileBridgeEvent.Unsupported
            "ready" -> TurnstileBridgeEvent.Ready
            else -> TurnstileBridgeEvent.Unknown
        }
    }

    private fun sanitizeCode(value: String?): String = value.orEmpty().filter { it.isLetterOrDigit() || it == '_' }.take(MAX_CODE_LENGTH).ifEmpty { "unknown" }
}
