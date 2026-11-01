package cx.aswin.boxlore.core.analytics

/**
 * Keeps links and signed tokens out of analytics text. Playback errors carry messages from deep in
 * the player, such as a playlist line or an HTTP failure, that can hold an episode's signed media URL.
 */
internal object AnalyticsRedaction {
    private val LINK = Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s"'<>]+""")
    private val SECRET = Regex("""(?i)\b(access_token|token|signature|sig|key|auth|policy|expires)=[^\s&"'<>]+""")
    private const val MAX_CAUSES = 8

    fun text(value: String?): String? = value?.replace(LINK, "<url>")?.replace(SECRET, "\$1=<redacted>")

    /**
     * A copy of [error] and its causes with every message redacted, for error tracking. Each copy
     * names the original type in its message and keeps its stack trace; [error] itself is untouched.
     */
    fun throwable(error: Throwable): Throwable = copy(error, depth = 0)

    private fun copy(error: Throwable, depth: Int,): Throwable {
        val cause = error.cause?.takeIf { it !== error && depth < MAX_CAUSES }?.let { copy(it, depth + 1) }
        return RedactedException(error.javaClass.name, text(error.message), cause).apply { stackTrace = error.stackTrace }
    }
}

/** An exception reported to error tracking in place of one of [type], its message redacted ([AnalyticsRedaction]). */
internal class RedactedException(val type: String, message: String?, cause: Throwable?,) : Exception(listOfNotNull(type, message).joinToString(": "), cause)
