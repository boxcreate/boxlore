package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.compose.runtime.saveable.SaverScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class RecordingSupportSink : SupportAnalyticsSink {
    val events = mutableListOf<String>()
    var lastLeft: Triple<Long, Int, Boolean>? = null
    val selections = mutableListOf<Pair<Int, Boolean>>()
    val pays = mutableListOf<SupportOffer>()
    val views = mutableListOf<Pair<SupportWorldAssignment, String>>()

    override fun pageViewed(assignment: SupportWorldAssignment, entryPoint: String, localHour: Int, weekday: Int) {
        events += "view:${assignment.world.variantKey}:${assignment.source.analyticsValue}"
        views += assignment to entryPoint
    }

    override fun tierSelected(
        assignment: SupportWorldAssignment,
        offer: SupportOffer,
        tierName: String,
        codename: String,
        isLoreInspect: Boolean,
    ) {
        events += "tier:${offer.index}"
        selections += offer.index to isLoreInspect
    }

    override fun payTapped(assignment: SupportWorldAssignment, offer: SupportOffer, tierName: String, codename: String) {
        events += "pay:${offer.index}"
        pays += offer
    }

    override fun pageLeft(assignment: SupportWorldAssignment, dwellMs: Long, tiersViewed: Int, payTapped: Boolean) {
        events += "left:${assignment.world.variantKey}"
        lastLeft = Triple(dwellMs, tiersViewed, payTapped)
    }

    fun count(prefix: String) = events.count { it.startsWith(prefix) }
}

class SupportPageSessionTest {
    private val orbit = SupportWorldAssignment(SupportWorld.Orbit, SupportWorldSource.Experiment)

    @Test
    fun `a visit reports one view, its selections and pay tap, then one exit with dwell`() {
        var now = 1_000L
        val sink = RecordingSupportSink()
        val session = SupportPageSession(orbit, sink, { now })

        session.start("settings", localHour = 21, weekday = 5)
        session.start("settings", localHour = 21, weekday = 5)
        session.tierSelected(SUPPORT_OFFERS[3], "Ring Station", "TIER 4 RING STATION")
        session.tierSelected(SUPPORT_OFFERS[2], "Cargo Shuttle", "TIER 3 CARGO SHUTTLE")
        session.payTapped(SUPPORT_OFFERS[2], "Cargo Shuttle", "TIER 3 CARGO SHUTTLE")
        now = 7_500L
        session.end()
        session.end()

        assertEquals(listOf("view:orbit:experiment", "tier:3", "tier:2", "pay:2", "left:orbit"), sink.events)
        assertEquals(Triple(6_500L, 2, true), sink.lastLeft)
    }

    @Test
    fun `nothing is reported before a visit starts or after it ends`() {
        val sink = RecordingSupportSink()
        val session = SupportPageSession(orbit, sink, { 0L })
        session.end()
        session.start("settings", 9, 1)
        session.end()
        session.tierSelected(SUPPORT_OFFERS[0], "Scout Probe", "TIER 1 SCOUT PROBE")
        session.payTapped(SUPPORT_OFFERS[0], "Scout Probe", "TIER 1 SCOUT PROBE")
        assertEquals(listOf("view:orbit:experiment", "left:orbit"), sink.events)
        assertEquals(Triple(0L, 1, false), sink.lastLeft)
    }

    @Test
    fun `a restored visit never reports a second view and keeps its dwell start and tiers`() {
        var now = 2_000L
        val sink = RecordingSupportSink()
        val saver = SupportPageSession.saver(sink) { now }
        val session = SupportPageSession(orbit, sink, { now })
        session.start("settings", 10, 3)
        session.tierSelected(SUPPORT_OFFERS[4], "Star Cruiser", "TIER 5 STAR CRUISER")

        val saved = with(saver) { SaverScope { true }.save(session) }!!
        val restored = saver.restore(saved)!!
        restored.start("settings", 10, 3)
        now = 5_000L
        restored.end()

        assertEquals(orbit, restored.assignment)
        assertEquals(1, sink.count("view"))
        assertEquals(Triple(3_000L, 2, false), sink.lastLeft)
    }

    @Test
    fun `the production sink sends world, assignment and price properties`() {
        val captured = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsHelper.installRecordingSink(captured)
        try {
            val session = SupportPageSession(orbit, AnalyticsHelperSupportSink, { 0L })
            session.start("settings", localHour = 22, weekday = 6)
            session.tierSelected(SUPPORT_OFFERS[1], "Relay Satellite", "TIER 2 RELAY SATELLITE")
            session.payTapped(SUPPORT_OFFERS[1], "Relay Satellite", "TIER 2 RELAY SATELLITE")
            session.end()
        } finally {
            restore()
        }

        val viewed = captured.first { it.first == "support_page_viewed" }.second
        assertEquals("orbit", viewed["support_world"])
        assertEquals("experiment", viewed["assignment_source"])
        assertEquals("settings", viewed["entry_point"])
        assertEquals(22, viewed["local_hour"])
        assertEquals(6, viewed["weekday"])

        val toggled = captured.first { it.first == "support_tier_toggled" }.second
        assertEquals(1, toggled["tier_index"])
        assertEquals("$0.99", toggled["amount"])

        val donate = captured.first { it.first == "support_donate_clicked" }.second
        assertEquals("orbit", donate["support_world"])
        assertEquals(0.99, donate["amount_usd"])
        assertEquals(false, donate["payments_live"])
        assertEquals("Relay Satellite", donate["tier_title"])

        val left = captured.first { it.first == "support_page_left" }.second
        assertEquals(2, left["tiers_viewed"])
        assertEquals(true, left["pay_tapped"])
        assertEquals(1, captured.count { it.first == "support_page_viewed" })
        assertTrue(captured.any { it.first == "settings_interaction" && it.second["action"] == "support_page_viewed" })
    }
}
