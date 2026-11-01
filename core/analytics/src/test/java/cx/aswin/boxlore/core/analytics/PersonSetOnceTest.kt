package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Set-once person properties go through the SDK's set-once slot, never nested under `$set`. */
class PersonSetOnceTest {
    private val set = mutableListOf<Map<String, Any>>()
    private val setOnce = mutableListOf<Map<String, Any>>()
    private lateinit var previousSet: (Map<String, Any>) -> Unit
    private lateinit var previousSetOnce: (Map<String, Any>) -> Unit

    @BeforeEach
    fun setUp() {
        previousSet = AnalyticsEmit.personSetSink
        previousSetOnce = AnalyticsEmit.personSetOnceSink
        AnalyticsEmit.personSetSink = { set += it }
        AnalyticsEmit.personSetOnceSink = { setOnce += it }
    }

    @AfterEach
    fun tearDown() {
        AnalyticsEmit.personSetSink = previousSet
        AnalyticsEmit.personSetOnceSink = previousSetOnce
    }

    @Test
    fun `install channel is set once as its own person properties`() {
        // Regression: it was sent as {"$set_once": {...}} under $set, so PostHog stored one property
        // literally named "$set_once" and install_channel never existed on the person.
        AnalyticsHelper.trackInstallChannelAttributed(installChannel = "share", referrerRaw = "type=podcast&id=1")

        assertTrue(set.isEmpty(), "got \$set $set")
        assertEquals(listOf(mapOf("install_channel" to "share", "referrer_raw" to "type=podcast&id=1")), setOnce)
    }

    @Test
    fun `a later import keeps the first setup route`() {
        AnalyticsHelper.trackOnboardingSearchDone(
            entryPoint = "welcome_screen",
            totalSubscribedCount = 3,
            subscribedPodcastsList = listOf("p1", "p2", "p3"),
            searchesPerformed = 1,
            timeSpentOnSearchSeconds = 4f,
            totalOnboardingTimeSeconds = 9f,
        )
        AnalyticsHelper.setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed = 40)

        assertEquals("import", set.last()["onboarding_method"])
        assertEquals(
            mapOf("first_onboarding_method" to "search", "first_user_intent" to "targeted_listener", "initial_podcasts_subscribed" to 3),
            setOnce.first(),
        )
        set.forEach { assertFalse("initial_podcasts_subscribed" in it, "initial count belongs to set-once: $it") }
    }
}
