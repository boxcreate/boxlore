package cx.aswin.boxlore.core.analytics

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The glossary's `level` and `monthly_per_install` columns. Every event has a level that matches
 * [AnalyticsPolicy.DEFAULT_LEVELS], and the estimated monthly events per active install stay under
 * [MONTHLY_BUDGET_PER_INSTALL]. PostHog's free plan is 1M events a month, so 250 per install
 * covers about 4,000 active installs.
 *
 * `monthly_per_install` is each event's count over active installs from PostHog (30 days to
 * 1 Nov 2026, test phones left out). A new event needs an estimate; turning a heavy event on, or
 * raising its estimate, has to fit the budget. SDK-backed rows (`app_open`, `app_background`) use the
 * PostHog event named in `glossary_emission_coverage.csv`. The budget covers glossary events only; SDK
 * traffic outside the glossary (identity, flag, survey and exception events) isn't counted.
 */
class AnalyticsBudgetTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: File(".").canonicalFile.let { dir ->
                generateSequence(dir) { it.parentFile }
                    .firstOrNull { File(it, "docs/analytics/event_glossary.csv").isFile }
                    ?: dir
            }

    private data class Row(val event: String, val level: String, val monthlyPerInstall: Double?)

    private val rows: List<Row> by lazy {
        val lines = File(projectRoot, "docs/analytics/event_glossary.csv").readLines().filter { it.isNotBlank() }
        val header = lines.first().split(',')
        val levelColumn = header.indexOf("level")
        val estimateColumn = header.indexOf("monthly_per_install")
        lines.drop(1).map { line ->
            val cols = line.split(',')
            Row(cols[0].trim(), cols.getOrElse(levelColumn) { "" }.trim(), cols.getOrNull(estimateColumn)?.trim()?.toDoubleOrNull())
        }
    }

    /** Glossary rows backed by an SDK event, mapped to that PostHog event name. */
    private val sdkNames: Map<String, String> by lazy {
        File(projectRoot, "docs/analytics/glossary_emission_coverage.csv")
            .readLines()
            .drop(1)
            .mapNotNull { line ->
                val cols = line.split(',')
                cols.getOrNull(1)?.takeIf { it.startsWith(SDK_BACKED) }?.let { cols[0].trim() to it.removePrefix(SDK_BACKED).trim() }
            }.toMap()
    }

    private fun policyName(event: String) = sdkNames[event] ?: event

    /** The default level in code, independent of any remote switch another test applied. */
    private fun defaultLevel(event: String) = AnalyticsPolicy.DEFAULT_LEVELS[policyName(event)] ?: AnalyticsLevel.Always

    @Test
    fun `every glossary event has a valid level and a monthly estimate`() {
        val invalid = rows.filter { AnalyticsLevel.fromWire(it.level) == null || it.monthlyPerInstall == null || it.monthlyPerInstall < 0 }
        assertTrue(invalid.isEmpty(), "Rows need level always/sampled/off and monthly_per_install >= 0: ${invalid.map { it.event }}")
    }

    @Test
    fun `glossary levels match the defaults in AnalyticsPolicy`() {
        val mismatched =
            rows.filter { AnalyticsLevel.fromWire(it.level) != defaultLevel(it.event) }
                .map { "${it.event}: csv=${it.level} code=${defaultLevel(it.event).wire}" }
        assertTrue(mismatched.isEmpty(), "event_glossary.csv level and AnalyticsPolicy.DEFAULT_LEVELS disagree: $mismatched")

        val known = rows.map { policyName(it.event) }.toSet()
        val unknown = AnalyticsPolicy.DEFAULT_LEVELS.keys - known
        assertEquals(emptySet<String>(), unknown, "DEFAULT_LEVELS names events the glossary doesn't have")
    }

    @Test
    fun `estimated monthly events per active install stay within the budget`() {
        val samplePart = AnalyticsPolicy.DEFAULT_SAMPLE_PERCENT / 100.0
        val total =
            rows.sumOf { row ->
                val estimate = row.monthlyPerInstall ?: 0.0
                when (defaultLevel(row.event)) {
                    AnalyticsLevel.Always -> estimate
                    AnalyticsLevel.Sampled -> estimate * samplePart
                    AnalyticsLevel.Off -> 0.0
                }
            }
        assertTrue(
            total <= MONTHLY_BUDGET_PER_INSTALL,
            "Estimated %.1f events a month per active install; the budget is %.0f. Sample or switch off an event, or fold it into another.".format(total, MONTHLY_BUDGET_PER_INSTALL),
        )
    }

    private companion object {
        const val SDK_BACKED = "sdk_backed:"

        /** About 212 with the listening record and page views (screen_viewed is an estimate). */
        const val MONTHLY_BUDGET_PER_INSTALL = 225.0
    }
}
