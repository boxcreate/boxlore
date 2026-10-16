package cx.aswin.boxlore.session

import java.io.IOException

/**
 * Turns an App Check failure into a short category for `app_check_status.reason` and
 * `api_session_status`. Only SDK error text is read; it never holds user data. Categories:
 * `play_integrity_<code>` (Play Integrity error codes such as -1 or -9), `http_<status>` (the
 * App Check exchange answered with an error), `too_many_attempts`, `network` or `other`.
 */
internal object AppCheckFailureReason {
    private val integrityCode = Regex("""Integrity API error \((-?\d+)\)""")
    private val leadingIntegrityCode = Regex("""^(-\d+):""")
    private val httpCode = Regex("""code:\s*(\d{3})""")

    fun of(error: Throwable?): String {
        val chain = generateSequence(error) { it.cause }.take(MAX_CAUSES).toList()
        val text = chain.joinToString(" | ") { it.message.orEmpty() }
        integrityCode.find(text)?.let { return "play_integrity_${it.groupValues[1]}" }
        chain.firstNotNullOfOrNull { leadingIntegrityCode.find(it.message.orEmpty().trim()) }?.let {
            return "play_integrity_${it.groupValues[1]}"
        }
        val http = httpCode.find(text)
        return when {
            text.contains("too many attempts", ignoreCase = true) -> "too_many_attempts"
            http != null -> "http_${http.groupValues[1]}"
            chain.any { it is IOException } -> "network"
            else -> "other"
        }
    }

    private const val MAX_CAUSES = 6
}
