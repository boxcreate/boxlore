package cx.aswin.boxlore.feature.settings.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogcatCollectorTest {

    @Test
    fun `sanitizeLogcatOutput scrubs sensitive bearer tokens`() {
        val raw = "12-10 10:20:30.123 1000 1000 D OkHttp: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz"
        val sanitized = LogcatCollector.sanitizeLogcatOutput(raw)

        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz"))
        assertTrue(sanitized.contains("Bearer [REDACTED]"))
    }

    @Test
    fun `sanitizeLogcatOutput scrubs Firebase API keys`() {
        val fakeKey = "AIza" + "SyD1234567890abcdefghijklmnopqrstuv"
        val raw = "Loaded Firebase options with key $fakeKey"
        val sanitized = LogcatCollector.sanitizeLogcatOutput(raw)

        assertFalse(sanitized.contains(fakeKey))
        assertTrue(sanitized.contains("[REDACTED_API_KEY]"))
    }

    @Test
    fun `sanitizeLogcatOutput scrubs key-value credentials`() {
        val fakeApiKey = "mock" + "_api_key_12345678"
        val fakeToken = "mock" + "_token_12345678"
        val raw = """{"api_key": "$fakeApiKey", "token": "$fakeToken"}"""
        val sanitized = LogcatCollector.sanitizeLogcatOutput(raw)

        assertFalse(sanitized.contains(fakeApiKey))
        assertFalse(sanitized.contains(fakeToken))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun `sanitizeLogcatOutput scrubs sensitive query parameters`() {
        val raw = "GET https://api.boxlore.cx/query?token=xyz123456&category=news HTTP/1.1"
        val sanitized = LogcatCollector.sanitizeLogcatOutput(raw)

        assertFalse(sanitized.contains("token=xyz123456"))
        assertTrue(sanitized.contains("token=[REDACTED]"))
        assertTrue(sanitized.contains("category=news"))
    }

    @Test
    fun `sanitizeLogcatOutput scrubs email addresses`() {
        val raw = "User signed in as user.test@example.com from device"
        val sanitized = LogcatCollector.sanitizeLogcatOutput(raw)

        assertFalse(sanitized.contains("user.test@example.com"))
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun `sanitizeLogcatOutput preserves non-sensitive content`() {
        val raw = "12-10 10:20:30.123 1000 1000 I PlaybackService: State changed to STATE_PLAYING position=12400"
        val sanitized = LogcatCollector.sanitizeLogcatOutput(raw)

        assertEquals(raw, sanitized)
    }

    @Test
    fun `sanitizeLogcatOutput handles empty text safely`() {
        assertEquals("", LogcatCollector.sanitizeLogcatOutput(""))
    }
}
