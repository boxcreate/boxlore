package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.SupportWorldContext

/** The four Support page events, behind a seam so sessions are testable without the SDK. */
internal interface SupportAnalyticsSink {
    fun pageViewed(assignment: SupportWorldAssignment, entryPoint: String, localHour: Int, weekday: Int)

    fun tierSelected(
        assignment: SupportWorldAssignment,
        offer: SupportOffer,
        tierName: String,
        codename: String,
        isLoreInspect: Boolean,
    )

    fun payTapped(assignment: SupportWorldAssignment, offer: SupportOffer, tierName: String, codename: String)

    fun pageLeft(assignment: SupportWorldAssignment, dwellMs: Long, tiersViewed: Int, payTapped: Boolean)
}

internal fun SupportWorldAssignment.analyticsContext(): SupportWorldContext =
    SupportWorldContext(supportWorld = world.variantKey, assignmentSource = source.analyticsValue)

internal object AnalyticsHelperSupportSink : SupportAnalyticsSink {
    override fun pageViewed(assignment: SupportWorldAssignment, entryPoint: String, localHour: Int, weekday: Int) {
        AnalyticsHelper.trackSupportPageViewed(
            world = assignment.analyticsContext(),
            entryPoint = entryPoint,
            localHour = localHour,
            weekday = weekday,
        )
    }

    override fun tierSelected(
        assignment: SupportWorldAssignment,
        offer: SupportOffer,
        tierName: String,
        codename: String,
        isLoreInspect: Boolean,
    ) {
        AnalyticsHelper.trackSupportTierToggled(
            tierTitle = tierName,
            codename = codename,
            amount = offer.priceLabel,
            isLoreInspect = isLoreInspect,
            world = assignment.analyticsContext(),
            tierIndex = offer.index,
        )
    }

    override fun payTapped(assignment: SupportWorldAssignment, offer: SupportOffer, tierName: String, codename: String) {
        AnalyticsHelper.trackSupportDonateClicked(
            tierTitle = tierName,
            codename = codename,
            amount = offer.priceLabel,
            world = assignment.analyticsContext(),
            tierIndex = offer.index,
            amountUsd = offer.amountUsd,
            paymentsLive = SUPPORT_PAYMENTS_LIVE,
        )
    }

    override fun pageLeft(assignment: SupportWorldAssignment, dwellMs: Long, tiersViewed: Int, payTapped: Boolean) {
        AnalyticsHelper.trackSupportPageLeft(
            world = assignment.analyticsContext(),
            dwellMs = dwellMs,
            tiersViewed = tiersViewed,
            payTapped = payTapped,
        )
    }
}

/**
 * One visit to the Support page. It is saved across configuration changes, so rotating, resizing
 * or folding never counts a second view, and [end] only reports a real exit once.
 */
internal class SupportPageSession(
    val assignment: SupportWorldAssignment,
    private val sink: SupportAnalyticsSink,
    private val elapsedMs: () -> Long,
    private var startedAtMs: Long = NOT_STARTED,
    seenTiers: Set<Int> = emptySet(),
    private var payTapped: Boolean = false,
    private var ended: Boolean = false,
) {
    private val seenTiers = seenTiers.toMutableSet()

    val hasStarted: Boolean get() = startedAtMs != NOT_STARTED

    fun start(entryPoint: String, localHour: Int, weekday: Int, initialOffer: Int = SUPPORT_DEFAULT_OFFER_INDEX) {
        if (hasStarted) return
        startedAtMs = elapsedMs()
        seenTiers += initialOffer
        sink.pageViewed(assignment, entryPoint, localHour, weekday)
    }

    fun tierSelected(offer: SupportOffer, tierName: String, codename: String, isLoreInspect: Boolean = false) {
        if (ended) return
        seenTiers += offer.index
        sink.tierSelected(assignment, offer, tierName, codename, isLoreInspect)
    }

    fun payTapped(offer: SupportOffer, tierName: String, codename: String) {
        if (ended) return
        payTapped = true
        sink.payTapped(assignment, offer, tierName, codename)
    }

    fun end() {
        if (ended || !hasStarted) return
        ended = true
        val dwell = (elapsedMs() - startedAtMs).coerceAtLeast(0L)
        sink.pageLeft(assignment, dwell, seenTiers.size, payTapped)
    }

    companion object {
        private const val NOT_STARTED = -1L

        fun saver(sink: SupportAnalyticsSink, elapsedMs: () -> Long): Saver<SupportPageSession, Any> = listSaver(
            save = { session ->
                listOf(
                    session.assignment.world.variantKey,
                    session.assignment.source.name,
                    session.startedAtMs,
                    session.seenTiers.toList(),
                    session.payTapped,
                    session.ended,
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                SupportPageSession(
                    assignment = SupportWorldAssignment(
                        world = SupportWorld.fromVariant(saved[0] as String) ?: SupportWorld.Control,
                        source = SupportWorldSource.valueOf(saved[1] as String),
                    ),
                    sink = sink,
                    elapsedMs = elapsedMs,
                    startedAtMs = saved[2] as Long,
                    seenTiers = (saved[3] as List<Int>).toSet(),
                    payTapped = saved[4] as Boolean,
                    ended = saved[5] as Boolean,
                )
            },
        )
    }
}
