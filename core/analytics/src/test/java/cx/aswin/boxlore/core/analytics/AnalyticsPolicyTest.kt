package cx.aswin.boxlore.core.analytics

import com.posthog.PostHogEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Event levels: defaults, the `analytics-policy-v1` payload (base, per-version, bad input), the
 * remembered switch, sampling by bucket, and both places that apply them: [AnalyticsEmit] for app
 * events and [AnalyticsBeforeSend] for everything PostHog sends.
 */
class AnalyticsPolicyTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restoreSink: () -> Unit

    @BeforeEach
    fun setUp() {
        AnalyticsPolicy.resetForTest()
        restoreSink = AnalyticsEmit.installRecordingSink(recorder, applyLevels = true)
    }

    @AfterEach
    fun tearDown() {
        restoreSink()
        AnalyticsPolicy.resetForTest()
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
        AnalyticsPolicy.restore(bucket = 9, stored = null)
        assertEquals(mapOf(AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY to 10), AnalyticsPolicy.admit("playback_seeked"))

        AnalyticsPolicy.restore(bucket = 10, stored = null)
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

        AnalyticsPolicy.restore(bucket = 30, stored = AnalyticsPolicy.applyPayload(payload, appVersion = "0.0.30"))
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
        AnalyticsPolicy.restore(bucket = 4, stored = stored)
        assertEquals(AnalyticsLevel.Always, AnalyticsPolicy.levelFor("Application Backgrounded"))
        assertEquals(mapOf(AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY to 5), AnalyticsPolicy.admit("playback_seeked"))

        AnalyticsPolicy.restore(bucket = 4, stored = "garbage")
        assertEquals(AnalyticsLevel.Off, AnalyticsPolicy.levelFor("Application Backgrounded"))
        assertNull(AnalyticsPolicy.decode("x|y"))
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
    fun `before send tags every event, drops the ones that are off, and keeps existing tags`() {
        val tags: Map<String, Any> = mapOf("is_internal" to true, "app_environment" to "debug")
        val beforeSend = AnalyticsBeforeSend(tags)

        val installed = beforeSend.run(PostHogEvent(event = "Application Installed", distinctId = "d1", properties = mutableMapOf<String, Any>("version" to "0.0.30")))
        assertEquals(mapOf("version" to "0.0.30", "is_internal" to true, "app_environment" to "debug"), installed?.properties)

        assertNull(beforeSend.run(PostHogEvent(event = "Application Backgrounded", distinctId = "d1")))

        val tagged = PostHogEvent(event = "search_performed", distinctId = "d1", properties = tags.toMutableMap())
        assertSame(tagged, beforeSend.run(tagged), "an event that already carries the tags passes unchanged")

        val sampled = beforeSend.run(PostHogEvent(event = "playback_seeked", distinctId = "d1", properties = tags.toMutableMap()))
        assertEquals(10, sampled?.properties?.get(AnalyticsPolicy.SAMPLE_PERCENT_PROPERTY))
    }
}
