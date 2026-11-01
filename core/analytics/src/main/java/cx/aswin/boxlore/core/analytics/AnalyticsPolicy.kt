package cx.aswin.boxlore.core.analytics

import kotlin.random.Random

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
 *   `docs/analytics/event_glossary.csv` mirrors them (`AnalyticsBudgetTest`). [ALWAYS_SENT] events
 *   ignore the switch.
 * - Remote switch: the [FLAG_KEY] flag payload overrides levels and the sample share with no release,
 *   for example `{"sample_percent": 10, "events": {"playback_seeked": "off"}, "versions": {"0.0.30":
 *   {"events": {"playback_seeked": "always"}}}}`. A `versions` entry applies on top for that app version
 *   only; a flag variant targeted at one person changes events for that person alone. A payload that
 *   isn't an object, or a missing flag, means the defaults.
 * - Sampling: an install is in the sample when its bucket (0–99, picked once and kept in the analytics
 *   prefs, so a PostHog reset doesn't move it) is below the sample percent. Sampled events carry
 *   [SAMPLE_PERCENT_PROPERTY], the share they were picked at, so charts can scale them back up.
 *
 * [AnalyticsEmit] checks app events here; [AnalyticsBeforeSend] checks every event PostHog captures,
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

    /**
     * PostHog identity events, always sent whatever the switch says: the SDK updates its identity
     * and person-property state before the event is checked, so dropping one leaves PostHog behind.
     */
    val ALWAYS_SENT: Set<String> = setOf("\$identify", "\$create_alias", "\$set", "\$groupidentify")

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

    /**
     * [admit] for an event PostHog is capturing, which [AnalyticsEmit] may already have admitted
     * while it waited for PostHog setup. One that carries [SAMPLE_PERCENT_PROPERTY] keeps that
     * decision and its share even if the switch changed since: charts weigh it by the share it was
     * picked at, and re-tagging it would miscount. It is dropped only if the event is now off.
     */
    fun admitCaptured(event: String, properties: Map<String, Any>,): Map<String, Any>? =
        when {
            SAMPLE_PERCENT_PROPERTY !in properties -> admit(event)
            levelFor(event) == AnalyticsLevel.Off -> null
            else -> emptyMap()
        }

    /** Where this install keeps its bucket and the last switch: SharedPreferences in the app. */
    interface Store {
        /** The saved bucket, or null when none was saved. */
        fun bucket(): Int?

        fun saveBucket(bucket: Int)

        /** The last switch as [encode] wrote it, or null. */
        fun rules(): String?

        fun saveRules(encoded: String)
    }

    /**
     * Starts from this install's bucket (picked at random on first use, then kept) and the switch
     * remembered from the last launch, if that switch was for this [appVersion].
     */
    fun start(store: Store, appVersion: String, random: () -> Int = { Random.nextInt(BUCKETS) },) {
        val saved = store.bucket()?.takeIf { it in 0 until BUCKETS }
        val bucket = saved ?: random().coerceIn(0, BUCKETS - 1).also(store::saveBucket)
        restore(bucket, store.rules(), appVersion)
    }

    /** Applies a flag [payload] for [appVersion] and remembers it in [store] for the next launch. */
    fun refresh(payload: Any?, appVersion: String, store: Store,) {
        store.saveRules(applyPayload(payload, appVersion))
    }

    /** Starts from [bucket] and the [stored] switch; one saved by another app version is dropped. */
    fun restore(bucket: Int, stored: String?, appVersion: String,) {
        this.bucket = bucket.coerceIn(0, BUCKETS - 1)
        rules = decode(stored, appVersion) ?: Rules()
    }

    /** Applies a flag [payload] for [appVersion]; returns the encoded rules to remember. */
    fun applyPayload(payload: Any?, appVersion: String,): String {
        val next = parse(payload, appVersion) ?: Rules()
        rules = next
        return encode(next, appVersion)
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

    /**
     * `<app version>|<percent>|<event>=<level>,…`. The version keeps a version-specific switch from
     * applying after an update; versions and event names never contain `|`, `,` or `=`.
     */
    internal fun encode(rules: Rules, appVersion: String,): String =
        appVersion + "|" + rules.samplePercent + "|" + rules.overrides.entries.joinToString(",") { "${it.key}=${it.value.wire}" }

    internal fun decode(stored: String?, appVersion: String,): Rules? {
        val parts = stored?.split('|', limit = 3)?.takeIf { it.size == 3 && it[0] == appVersion } ?: return null
        val percent = parts[1].toIntOrNull()?.coerceIn(0, BUCKETS) ?: return null
        val overrides =
            parts[2]
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

    private fun levelFor(event: String, rules: Rules,): AnalyticsLevel =
        if (event in ALWAYS_SENT) AnalyticsLevel.Always else rules.overrides[event] ?: DEFAULT_LEVELS[event] ?: AnalyticsLevel.Always
}
