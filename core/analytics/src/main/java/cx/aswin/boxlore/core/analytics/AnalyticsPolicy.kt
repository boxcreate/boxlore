package cx.aswin.boxlore.core.analytics

/** How much of an event this install sends; see [AnalyticsPolicy]. */
enum class AnalyticsLevel(val wire: String) {
    /** Every install sends it. */
    Always("always"),

    /** Only installs in the sample (a fixed share, picked once per install) send it. */
    Sampled("sampled"),

    /** Nobody sends it until the remote switch turns it back on. */
    Off("off"),
    ;

    companion object {
        fun fromWire(value: Any?): AnalyticsLevel? {
            val wire = value?.toString()?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.wire == wire }
        }
    }
}

/**
 * Which events this install sends.
 *
 * - Defaults: [DEFAULT_LEVELS]; anything unlisted is [AnalyticsLevel.Always]. The `level` column of
 *   `docs/analytics/event_glossary.csv` mirrors them (`AnalyticsBudgetTest`).
 * - Remote switch: the [FLAG_KEY] flag payload overrides levels and the sample share with no release,
 *   for example `{"sample_percent": 10, "events": {"playback_seeked": "off"}, "versions": {"0.0.30":
 *   {"events": {"playback_seeked": "always"}}}}`. A `versions` entry applies on top for that app version
 *   only; a flag variant targeted at one person changes events for that person alone. A payload that
 *   isn't an object, or a missing flag, means the defaults.
 * - Sampling: an install is in the sample when its bucket (0–99, picked once and kept in the analytics
 *   prefs, so a PostHog reset doesn't move it) is below the sample percent. Sampled events carry
 *   [SAMPLE_PERCENT_PROPERTY] so charts can scale them back up.
 *
 * [AnalyticsEmit] checks app events here; [AnalyticsBeforeSend] checks every event PostHog sends,
 * SDK lifecycle events included. State is an immutable snapshot, so any thread can read it.
 */
object AnalyticsPolicy {
    const val FLAG_KEY = "analytics-policy-v1"
    const val SAMPLE_PERCENT_PROPERTY = "sample_percent"
    const val DEFAULT_SAMPLE_PERCENT = 10
    const val BUCKETS = 100

    /** Events that aren't always on. SDK lifecycle events use their PostHog names. */
    val DEFAULT_LEVELS: Map<String, AnalyticsLevel> =
        mapOf(
            "playback_heartbeat" to AnalyticsLevel.Off,
            "adaptive_ranking_status" to AnalyticsLevel.Off,
            "android_auto_browse" to AnalyticsLevel.Off,
            "session_restore_prompt" to AnalyticsLevel.Off,
            "Application Backgrounded" to AnalyticsLevel.Off,
            "playback_seeked" to AnalyticsLevel.Sampled,
        )

    /** Remote overrides on top of [DEFAULT_LEVELS], and the share of installs in the sample. */
    data class Rules(val samplePercent: Int = DEFAULT_SAMPLE_PERCENT, val overrides: Map<String, AnalyticsLevel> = emptyMap(),)

    @Volatile
    private var rules = Rules()

    @Volatile
    private var bucket = 0

    fun levelFor(event: String): AnalyticsLevel = levelFor(event, rules)

    /**
     * Extra properties to send with [event], or null when this install doesn't send it: off, or
     * sampled while outside the sample.
     */
    fun admit(event: String): Map<String, Any>? {
        val current = rules
        return when (levelFor(event, current)) {
            AnalyticsLevel.Always -> emptyMap()
            AnalyticsLevel.Off -> null
            AnalyticsLevel.Sampled -> if (bucket < current.samplePercent) mapOf(SAMPLE_PERCENT_PROPERTY to current.samplePercent) else null
        }
    }

    /** Starts from this install's [bucket] and the switch remembered from the last launch. */
    fun restore(bucket: Int, stored: String?,) {
        this.bucket = bucket.coerceIn(0, BUCKETS - 1)
        rules = decode(stored) ?: Rules()
    }

    /** Applies a flag [payload] for [appVersion]; returns the encoded rules to remember. */
    fun applyPayload(payload: Any?, appVersion: String,): String {
        val next = parse(payload, appVersion) ?: Rules()
        rules = next
        return encode(next)
    }

    /** The rules a flag payload sets for [appVersion], or null when it isn't a policy object. */
    internal fun parse(payload: Any?, appVersion: String,): Rules? {
        val root = payload as? Map<*, *> ?: return null
        val base = section(root, Rules())
        val forVersion = (root["versions"] as? Map<*, *>)?.get(appVersion) as? Map<*, *>
        return if (forVersion == null) base else section(forVersion, base)
    }

    private fun section(values: Map<*, *>, base: Rules,): Rules {
        val percent = (values["sample_percent"] as? Number)?.toInt()?.coerceIn(0, BUCKETS) ?: base.samplePercent
        val events =
            (values["events"] as? Map<*, *>)
                .orEmpty()
                .mapNotNull { (name, level) -> (name as? String)?.let { key -> AnalyticsLevel.fromWire(level)?.let { key to it } } }
                .toMap()
        return Rules(percent, base.overrides + events)
    }

    /** `<percent>|<event>=<level>,…`; event names never contain `|`, `,` or `=`. */
    internal fun encode(rules: Rules): String =
        rules.samplePercent.toString() + "|" + rules.overrides.entries.joinToString(",") { "${it.key}=${it.value.wire}" }

    internal fun decode(stored: String?): Rules? {
        val parts = stored?.split('|', limit = 2)?.takeIf { it.size == 2 } ?: return null
        val percent = parts[0].toIntOrNull()?.coerceIn(0, BUCKETS) ?: return null
        val overrides =
            parts[1]
                .split(',')
                .mapNotNull { entry ->
                    val (name, level) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                    AnalyticsLevel.fromWire(level)?.let { name to it }
                }.toMap()
        return Rules(percent, overrides)
    }

    /** Test hook: defaults, bucket 0. */
    internal fun resetForTest() {
        rules = Rules()
        bucket = 0
    }

    private fun levelFor(event: String, rules: Rules,): AnalyticsLevel = rules.overrides[event] ?: DEFAULT_LEVELS[event] ?: AnalyticsLevel.Always
}
