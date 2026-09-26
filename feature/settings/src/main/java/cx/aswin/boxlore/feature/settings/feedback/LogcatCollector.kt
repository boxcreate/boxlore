package cx.aswin.boxlore.feature.settings.feedback

import android.os.Process
import androidx.annotation.WorkerThread
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Collects and sanitizes in-process logcat entries for diagnostic feedback.
 *
 * Scrubbing ensures that no bearer tokens, API keys, passwords, email addresses,
 * or query parameters leak into feedback or diagnostic dumps.
 */
object LogcatCollector {

    private val BEARER_AUTH_PATTERN = Pattern.compile(
        """(?i)(["']?authorization["']?\s*:\s*)?Bearer\s+([A-Za-z0-9\-_./+=]{6,})""",
    )

    private val FIREBASE_API_KEY_PATTERN = Pattern.compile(
        """AIza[0-9A-Za-z\-_]{35}""",
    )

    private val URL_QUERY_PARAM_PATTERN = Pattern.compile(
        """([?&](?:token|key|auth|api[-_]?key|secret)=)[^&\s]+""",
        Pattern.CASE_INSENSITIVE,
    )

    private val SENSITIVE_KEY_VALUE_PATTERN = Pattern.compile(
        """(?i)(["']?(?:authorization|token|secret|api[-_]?key|password|app[-_]?key|x[-_]app[-_]key)["']?\s*[:=]\s*["']?)(?!\s*(?:Bearer|\[REDACTED))([^"'\s,;&?]{6,})(["']?)""",
    )

    private val EMAIL_ADDRESS_PATTERN = Pattern.compile(
        """\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\b""",
    )

    /**
     * Captures the most recent logcat entries for the current application process.
     * Must be called on a background thread.
     */
    @WorkerThread
    fun collectSanitizedLogcat(maxLines: Int = 150): String = try {
            val pid = Process.myPid()
            val process = try {
                ProcessBuilder("logcat", "--pid=$pid", "-d", "-v", "time", "-t", maxLines.toString())
                    .redirectErrorStream(true)
                    .start()
            } catch (_: Exception) {
                // Fallback without --pid if platform logcat doesn't support the flag
                ProcessBuilder("logcat", "-d", "-v", "time", "-t", maxLines.toString())
                    .redirectErrorStream(true)
                    .start()
            }

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    output.append(it).append('\n')
                }
            }
            process.waitFor()

            val rawLog = output.toString()
            if (rawLog.isBlank()) {
                "[No logcat entries recorded for current session]"
            } else {
                sanitizeLogcatOutput(rawLog)
            }
        } catch (e: Exception) {
            "[Logcat collection unavailable: ${e.message ?: e.javaClass.simpleName}]"
        }

    /**
     * Sanitizes sensitive information from raw logcat text.
     */
    fun sanitizeLogcatOutput(raw: String): String {
        if (raw.isBlank()) return raw

        var sanitized = raw
        sanitized = URL_QUERY_PARAM_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]")
        sanitized = BEARER_AUTH_PATTERN.matcher(sanitized).replaceAll("$1Bearer [REDACTED]")
        sanitized = FIREBASE_API_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_API_KEY]")
        sanitized = SENSITIVE_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]$3")
        sanitized = EMAIL_ADDRESS_PATTERN.matcher(sanitized).replaceAll("[REDACTED_EMAIL]")

        return sanitized
    }
}
