package cx.aswin.boxlore.feature.settings.feedback

import android.content.Context
import android.os.Process
import androidx.annotation.WorkerThread
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Collects and sanitizes in-process logcat entries for diagnostic feedback.
 *
 * Scrubbing ensures that no bearer tokens, API keys, passwords, email addresses,
 * private API URLs, or internal header names leak into feedback or diagnostic dumps.
 */
object LogcatCollector {

    private const val SCRUBBED_API_URL = "https://api.boxlore.app"
    private const val SCRUBBED_API_HOST = "api.boxlore.app"
    private const val SCRUBBED_APP_KEY_LABEL = "public key"

    private val AUTH_HEADER_PATTERN = Pattern.compile(
        """(?i)(["']?authorization["']?\s*:\s*)?(Bearer|Basic)\s+([a-z0-9\-_./+=]{4,})""",
    )

    private val FIREBASE_API_KEY_PATTERN = Pattern.compile(
        """AIza[0-9A-Za-z\-_]{35}""",
    )

    private val URL_QUERY_PARAM_PATTERN = Pattern.compile(
        """([?&](?:token|key|auth|api[-_]?key|secret)=)[^&\s]+""",
        Pattern.CASE_INSENSITIVE,
    )

    private val X_APP_KEY_VALUE_PATTERN = Pattern.compile(
        """(?i)["']?[a-z0-9_-]+(?:app|public)[-_ ]key["']?\s*[:=]\s*(?:\[REDACTED\]|["']?[^\s"']+)""",
    )

    private val X_APP_KEY_NAME_PATTERN = Pattern.compile(
        """(?i)\b(?:x[-_]app[-_]key|boxlore[-_]public[-_]key|boxcast[-_]public[-_]key|x\s+app\s+key)\b""",
    )

    private val SENSITIVE_KEY_VALUE_PATTERN = Pattern.compile(
        """(?i)(["']?[a-z0-9_]*(?:auth|token|secret|password|key)["']?\s*[:=]\s*["']?)([^"'\s,;&?\[]+)(["']?)""",
    )

    private val EMAIL_ADDRESS_PATTERN = Pattern.compile(
        """\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\b""",
    )

    private val API_URL_PATTERN = Pattern.compile(
        """(?i)https?://[a-z0-9.-]*aswin\.cx(:[0-9]+)?(/[^\s"']*)?""",
    )

    private val ASWIN_HOST_PATTERN = Pattern.compile(
        """(?i)\b[a-z0-9.-]*aswin\.cx(:[0-9]+)?\b""",
    )

    private val API_CONFIG_URL_PATTERN = Pattern.compile(
        """(?i)(["']?(?:api|base)[-_a-z]*url["']?\s*[:=]\s*["']?)https?://[^"'\s]+(["']?)""",
    )

    /**
     * Captures the most recent logcat entries for the current application process.
     * Must be called on a background thread.
     */
    @WorkerThread
    fun collectSanitizedLogcat(context: Context? = null, maxLines: Int = 150): String = try {
        val process = startLogcatProcess(maxLines)
        val rawLog = readProcessOutput(process)

        if (rawLog.isBlank()) {
            "[No logcat entries recorded for current session]"
        } else {
            val (apiBaseUrl, publicKey) = readApiConfig(context)
            sanitizeLogcatOutput(rawLog, apiBaseUrl, publicKey)
        }
    } catch (e: Exception) {
        "[Logcat collection unavailable: ${e.javaClass.simpleName}]"
    }

    private fun startLogcatProcess(maxLines: Int): java.lang.Process {
        val pid = Process.myPid()
        return try {
            ProcessBuilder("logcat", "--pid=$pid", "-d", "-v", "time", "-t", maxLines.toString())
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            ProcessBuilder("logcat", "-d", "-v", "time", "-t", maxLines.toString())
                .redirectErrorStream(true)
                .start()
        }
    }

    private fun readProcessOutput(process: java.lang.Process): String {
        val output = StringBuilder()
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    output.append(line).append('\n')
                }
            }
            process.waitFor()
        } finally {
            process.destroy()
        }
        return output.toString()
    }

    private fun readApiConfig(context: Context?): Pair<String?, String?> {
        if (context == null) return null to null
        val prefs = context.getSharedPreferences("boxlore_api_config", Context.MODE_PRIVATE)
        return prefs.getString("base_url", null) to prefs.getString("public_key", null)
    }

    /**
     * Sanitizes sensitive information from raw logcat text.
     */
    fun sanitizeLogcatOutput(
        raw: String,
        knownApiBaseUrl: String? = null,
        knownPublicKey: String? = null,
    ): String {
        if (raw.isBlank()) return raw

        var sanitized = raw

        if (!knownPublicKey.isNullOrBlank() && knownPublicKey.length >= 4) {
            sanitized = sanitized.replace(knownPublicKey, "[REDACTED]")
        }
        if (!knownApiBaseUrl.isNullOrBlank() && knownApiBaseUrl.startsWith("http")) {
            sanitized = sanitized.replace(knownApiBaseUrl, SCRUBBED_API_URL)
        }

        sanitized = URL_QUERY_PARAM_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]")
        sanitized = AUTH_HEADER_PATTERN.matcher(sanitized).replaceAll("$1$2 [REDACTED]")
        sanitized = FIREBASE_API_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_API_KEY]")
        sanitized = X_APP_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$SCRUBBED_APP_KEY_LABEL: [REDACTED]")
        sanitized = X_APP_KEY_NAME_PATTERN.matcher(sanitized).replaceAll(SCRUBBED_APP_KEY_LABEL)
        sanitized = SENSITIVE_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]$3")
        sanitized = EMAIL_ADDRESS_PATTERN.matcher(sanitized).replaceAll("[REDACTED_EMAIL]")
        sanitized = API_CONFIG_URL_PATTERN.matcher(sanitized).replaceAll("$1$SCRUBBED_API_URL$2")
        sanitized = API_URL_PATTERN.matcher(sanitized).replaceAll("$SCRUBBED_API_URL$2")
        sanitized = ASWIN_HOST_PATTERN.matcher(sanitized).replaceAll(SCRUBBED_API_HOST)

        return sanitized
    }
}
