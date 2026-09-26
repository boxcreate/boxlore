package cx.aswin.boxlore.feature.settings.feedback

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticCollectorTest {

    private val sampleInfo = DiagnosticInfo(
        appVersion = "1.2.3",
        buildCode = 42L,
        packageName = "cx.aswin.boxlore",
        androidRelease = "15",
        sdkInt = 35,
        manufacturer = "Google",
        model = "Pixel 9",
        networkType = "Wi-Fi",
        audioRoute = "Bluetooth",
        locale = "en-US",
    )

    @Test
    fun `toCondensedSummary formats compact diagnostics correctly`() {
        val summary = sampleInfo.toCondensedSummary()

        assertTrue(summary.contains("App: v1.2.3 (42)"))
        assertTrue(summary.contains("Android 15 (API 35)"))
        assertTrue(summary.contains("Google Pixel 9"))
        assertTrue(summary.contains("Net: Wi-Fi"))
        assertTrue(summary.contains("Audio: Bluetooth"))
        assertTrue(summary.length < 150)
    }

    @Test
    fun `toMarkdownReport includes headers and fields`() {
        val report = sampleInfo.toMarkdownReport(sanitizedLogcat = "Sample logcat content")

        assertTrue(report.contains("### System Diagnostics"))
        assertTrue(report.contains("- **App**: boxlore `1.2.3` (build `42`)"))
        assertTrue(report.contains("- **Package**: `cx.aswin.boxlore`"))
        assertTrue(report.contains("- **Android**: `15` (API `35`)"))
        assertTrue(report.contains("- **Device**: `Google Pixel 9`"))
        assertTrue(report.contains("- **Network**: `Wi-Fi`"))
        assertTrue(report.contains("- **Audio Route**: `Bluetooth`"))
        assertTrue(report.contains("- **Locale**: `en-US`"))
        assertTrue(report.contains("<details>"))
        assertTrue(report.contains("<summary>Sanitized App Logs</summary>"))
        assertTrue(report.contains("Sample logcat content"))
    }

    @Test
    fun `toMarkdownReport without logs omits details block`() {
        val report = sampleInfo.toMarkdownReport(sanitizedLogcat = null)

        assertTrue(report.contains("### System Diagnostics"))
        assertFalse(report.contains("<details>"))
    }
}
