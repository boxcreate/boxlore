package cx.aswin.boxlore.core.analytics

import com.posthog.PostHogEvent
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Event levels: defaults, the `analytics-policy-v1` payload (base, per-version, bad input), the
 * remembered bucket and switch, sampling by bucket, and both places that apply them: [AnalyticsEmit]
 * for app events and [AnalyticsBeforeSend] for everything PostHog sends.
 */
class AnalyticsPolicyTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restoreSink: () -> Unit

    @BeforeEach
    fun setUp() {
        AnalyticsPolicy.resetForTest()
        AnalyticsHelper.resetPolicyForTest()
        restoreSink = AnalyticsEmit.installRecordingSink(recorder, applyLevels = true)
    }

    @AfterEach
    fun tearDown() {
        restoreSink()
        AnalyticsPolicy.resetForTest()
        AnalyticsHelper.resetPolicyForTest()
    }

    @Test
    fun `defaults keep most events on, turn the dropped ones off and sample seeks`() {
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_started"))
        listOf("playback_heartbeat", "adaptive_ranking_status", "android_auto_browse", "session_restore_prompt", "Application Backgrounded")
            .forEach { assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor(it), it) }
        assertEquals(AnalyticsLevel.Sampled, AnalyticsPolicy.levelFor("playback_seeked"))
        assertEquals(emptyMap<String, Any>(), AnalyticsPolicy.admit("playback_started"))
        assertNull(AnalyticsPolicy.admit("playback_heartbeat"))
    }

    @Test
    fun `sampled events go out only from installs inside the sample, tagged with its size`() {
        AnalyticsPolicy.restore(bucket = 9, stored = null, appVersion = VERSION)
        assertEquals(mapOf(AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY to 10), AnalyticsPolicy.admit("playback_seeked"))

        AnalyticsPolicy.restore(bucket = 10, stored = null, appVersion = VERSION)
        assertNull(AnalyticsPolicy.admit("playback_seeked"))
    }

    @Test
    fun `the payload overrides levels and the sample, and a version entry applies on top for that version`() {
        val payload =
            mapOf(
                "sample_percent" to 25.0,
                "events" to mapOf("playback_heartbeat" to "always", "playback_started" to "off", "search_performed" to "nonsense"),
                "versions" to mapOf("0.0.30" to mapOf("events" to mapOf("playback_started" to "sampled"), "sample_percent" to 50)),
            )

        AnalyticsPolicy.applyPayload(payload, appVersion = "0.0.29")
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_heartbeat"))
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("playback_started"))
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("search_performed"), "an unknown level is ignored")

        AnalyticsPolicy.restore(bucket = 30, stored = AnalyticsPolicy.applyPayload(payload, appVersion = "0.0.30"), appVersion = "0.0.30")
        assertEquals(AnalyticsLevel.Sampled, AnalyticsPolicy.levelFor("playback_started"))
        assertEquals(mapOf(AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY to 50), AnalyticsPolicy.admit("playback_started"))
    }

    @Test
    fun `a missing flag or a payload that isn't an object means the defaults`() {
        AnalyticsPolicy.applyPayload(mapOf("events" to mapOf("playback_heartbeat" to "always")), appVersion = "0.0.29")
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_heartbeat"))

        AnalyticsPolicy.applyPayload(null, appVersion = "0.0.29")
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("playback_heartbeat"))

        AnalyticsPolicy.applyPayload("""{"events":{}}""", appVersion = "0.0.29")
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("playback_heartbeat"))
    }

    @Test
    fun `the remembered switch survives a restart, and a damaged one falls back to the defaults`() {
        val stored =
            AnalyticsPolicy.applyPayload(
                mapOf("sample_percent" to 5, "events" to mapOf("Application Backgrounded" to "always")),
                appVersion = "0.0.29",
            )
        AnalyticsPolicy.resetForTest()
        AnalyticsPolicy.restore(bucket = 4, stored = stored, appVersion = "0.0.29")
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("Application Backgrounded"))
        assertEquals(mapOf(AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY to 5), AnalyticsPolicy.admit("playback_seeked"))

        AnalyticsPolicy.restore(bucket = 4, stored = "garbage", appVersion = "0.0.29")
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("Application Backgrounded"))
        assertNull(AnalyticsPolicy.decode("x|y", appVersion = "x"))
        assertNull(AnalyticsPolicy.decode("0.0.29|ten|", appVersion = "0.0.29"))
    }

    @Test
    fun `a switch remembered by another app version is dropped after an update`() {
        val payload = mapOf("versions" to mapOf("0.0.30" to mapOf("events" to mapOf("playback_heartbeat" to "always"))))
        val stored = AnalyticsPolicy.applyPayload(payload, appVersion = "0.0.30")
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_heartbeat"))

        AnalyticsPolicy.restore(bucket = 0, stored = stored, appVersion = "0.0.31")
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("playback_heartbeat"))

        AnalyticsPolicy.restore(bucket = 0, stored = stored, appVersion = "0.0.30")
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_heartbeat"))
    }

    @Test
    fun `start keeps a saved bucket and picks and saves one only when none is usable`() {
        val kept = FakeStore(bucket = 42)
        AnalyticsPolicy.start(kept, VERSION) { error("a saved bucket is reused, not picked again") }
        assertEquals(42, kept.bucket)
        assertEquals(emptyList<Int>(), kept.savedBuckets)

        listOf(null, -1, AnalyticsPolicy.BUCKETS, 250).forEach { saved ->
            val store = FakeStore(bucket = saved)
            AnalyticsPolicy.start(store, VERSION) { 7 }
            assertEquals(listOf(7), store.savedBuckets, "saved bucket $saved")
            assertEquals(mapOf(AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY to 10), AnalyticsPolicy.admit("playback_seeked"))
        }

        val outOfRangePick = FakeStore(bucket = null)
        AnalyticsPolicy.start(outOfRangePick, VERSION) { 500 }
        assertEquals(listOf(AnalyticsPolicy.BUCKETS - 1), outOfRangePick.savedBuckets)
        assertNull(AnalyticsPolicy.admit("playback_seeked"), "bucket 99 is outside a 10% sample")
    }

    @Test
    fun `refresh remembers the switch, and the next start restores it only for the same version`() {
        val store = FakeStore(bucket = 3)
        AnalyticsPolicy.start(store, VERSION)
        AnalyticsPolicy.refresh(mapOf("events" to mapOf("playback_heartbeat" to "always")), VERSION, store)
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_heartbeat"))

        AnalyticsPolicy.resetForTest()
        AnalyticsPolicy.start(store, VERSION)
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_heartbeat"))

        AnalyticsPolicy.start(store, "0.0.31")
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("playback_heartbeat"))

        AnalyticsPolicy.refresh(null, VERSION, store)
        AnalyticsPolicy.start(store, VERSION)
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("playback_heartbeat"), "a missing flag is remembered as the defaults")
    }

    @Test
    fun `app events that are off never reach PostHog, and sampled ones carry the sample size`() {
        AnalyticsEmit.event("playback_heartbeat", mapOf("episode_id" to "e1"))
        AnalyticsEmit.event("playback_seeked", mapOf("episode_id" to "e1"))
        AnalyticsEmit.event("search_performed", mapOf("results_count" to 3))

        assertEquals(listOf("playback_seeked", "search_performed"), recorder.map { it.first })
        assertEquals(10, recorder.first().second[AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY])
        assertNull(recorder.last().second[AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY])
    }

    @Test
    fun `identity events stay on whatever the switch says`() {
        val off = AnalyticsPolicy.ALWAYS_SENT.associateWith { "off" }
        AnalyticsPolicy.applyPayload(mapOf("events" to off), VERSION)

        AnalyticsPolicy.ALWAYS_SENT.forEach { assertEquals(emptyMap<String, Any>(), AnalyticsPolicy.admit(it), it) }
        val set = PostHogEvent(event = "\$set", distinctId = "d1")
        assertSame(set, AnalyticsBeforeSend(emptyMap()).run(set))
    }

    @Test
    fun `an explicit flag reload applies and remembers the switch, then runs the caller's callback`() {
        var payload: Any? = mapOf("events" to mapOf("playback_heartbeat" to "always"))
        AnalyticsHelper.policyPayload = { payload }
        val calls = mutableListOf<String>()

        AnalyticsHelper.flagsReloaded { calls += "caller" }.loaded()
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("playback_heartbeat"), "nothing applies before initPolicy")
        assertEquals(listOf("caller"), calls)

        val store = FakeStore(bucket = 3)
        AnalyticsHelper.startPolicy(store, VERSION)
        AnalyticsHelper.flagsReloaded { calls += "caller" }.loaded()
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_heartbeat"))
        assertEquals(listOf("caller", "caller"), calls)

        AnalyticsPolicy.resetForTest()
        AnalyticsPolicy.start(store, VERSION)
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("playback_heartbeat"), "the reloaded switch is remembered")

        payload = null
        AnalyticsHelper.refreshAnalyticsPolicy()
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("playback_heartbeat"))
    }

    @Test
    fun `flag reloads go through AnalyticsHelper so the switch follows them`() {
        val root =
            System.getProperty("boxlore.projectRoot")?.let(::File)?.takeIf { it.isDirectory }
                ?: generateSequence(File(".").canonicalFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val direct =
            root
                .walkTopDown()
                .onEnter { it.name != "build" && !it.name.startsWith(".") }
                .filter { it.extension == "kt" && "/src/main/" in it.path && it.name != "AnalyticsHelper.kt" }
                .filter { file -> DIRECT_RELOAD.containsMatchIn(file.readText()) }
                .map { it.relativeTo(root).path }
                .toList()
        assertEquals(emptyList<String>(), direct, "use AnalyticsHelper.reloadFeatureFlags instead")
    }

    @Test
    fun `before send tags every event, drops the ones that are off, and replaces tags another build left`() {
        val tags: Map<String, Any> = mapOf("is_internal" to true, "app_environment" to "debug")
        val beforeSend = AnalyticsBeforeSend(tags)

        val installed = beforeSend.run(PostHogEvent(event = "Application Installed", distinctId = "d1", properties = mutableMapOf<String, Any>("version" to "0.0.30")))
        assertEquals(mapOf("version" to "0.0.30", "is_internal" to true, "app_environment" to "debug"), installed?.properties)

        assertNull(beforeSend.run(PostHogEvent(event = "Application Backgrounded", distinctId = "d1")))

        val tagged = PostHogEvent(event = "search_performed", distinctId = "d1", properties = tags.toMutableMap())
        assertSame(tagged, beforeSend.run(tagged), "an event that already carries the tags passes unchanged")

        val release: Map<String, Any> = mapOf("is_internal" to false, "app_environment" to "production")
        val updated = PostHogEvent(event = "Application Updated", distinctId = "d1", properties = release.toMutableMap())
        assertEquals(tags, beforeSend.run(updated)?.properties, "a test build installed over a release one is tagged as the test build")
        val fromTest = PostHogEvent(event = "Application Updated", distinctId = "d1", properties = tags.toMutableMap())
        assertEquals(release, AnalyticsBeforeSend(release).run(fromTest)?.properties, "and a release over a test build as the release")

        val sampled = beforeSend.run(PostHogEvent(event = "playback_seeked", distinctId = "d1", properties = tags.toMutableMap()))
        assertEquals(10, sampled?.properties?.get(AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY))
    }

    @Test
    fun `a queued sampled event keeps the share it was picked at when the switch changes before capture`() {
        val tags: Map<String, Any> = mapOf("is_internal" to false, "app_environment" to "production")
        val beforeSend = AnalyticsBeforeSend(tags)
        AnalyticsPolicy.restore(bucket = 7, stored = null, appVersion = VERSION)
        AnalyticsEmit.event("playback_seeked", mapOf("episode_id" to "e1"))
        val emitted = recorder.single().second + tags
        val queued = { PostHogEvent(event = "playback_seeked", distinctId = "d1", properties = emitted.toMutableMap()) }

        listOf(
            mapOf("sample_percent" to 50),
            mapOf("events" to mapOf("playback_seeked" to "always")),
            mapOf("sample_percent" to 5),
        ).forEach { payload ->
            AnalyticsPolicy.applyPayload(payload, VERSION)
            val sent = beforeSend.run(queued())
            assertEquals(emitted, sent?.properties, "after $payload")
            assertEquals(sent?.properties, beforeSend.run(requireNotNull(sent))?.properties, "PostHog's second pass agrees")
        }

        AnalyticsPolicy.applyPayload(mapOf("events" to mapOf("playback_seeked" to "off")), VERSION)
        assertNull(beforeSend.run(queued()), "an event switched off is still dropped")
    }

    private class FakeStore(var bucket: Int?) : AnalyticsPolicy.Store {
        val savedBuckets = mutableListOf<Int>()
        private var rules: String? = null

        override fun bucket(): Int? = bucket

        override fun saveBucket(bucket: Int) {
            savedBuckets += bucket
            this.bucket = bucket
        }

        override fun rules(): String? = rules

        override fun saveRules(encoded: String) {
            rules = encoded
        }
    }

    private companion object {
        const val VERSION = "0.0.30"

        /** A call, not a mention in a comment. */
        val DIRECT_RELOAD = Regex("""PostHog\.reloadFeatureFlags\s*[({]""")
    }
}
