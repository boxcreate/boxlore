package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** `recipe_id` beside `episode_id` (P4 PR 18, D11): filled from what is known, never guessed, never blocking. */
class EpisodeRecipeIdsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit
    private val asked = mutableListOf<Set<String>>()
    private var pending: ((Map<String, Long>) -> Unit)? = null

    @BeforeEach
    fun setUp() {
        EpisodeRecipeIds.reset()
        restore = AnalyticsEmit.installRecordingSink(recorder)
        EpisodeRecipeIds.install { ids, deliver ->
            asked += ids
            pending = deliver
        }
    }

    @AfterEach
    fun tearDown() {
        EpisodeRecipeIds.reset()
        restore()
    }

    private fun emit(event: String, properties: Map<String, Any>) = EpisodeRecipeIds.withRecipeIds(event, properties)

    @Test
    fun everyEpisodeEventCarriesTheRecipeIdOfARecipeRangeId() {
        val recipeId = (1L shl 52) + 42L
        for (event in AnalyticsGlossary.EPISODE_ID_EVENTS) {
            val out = emit(event, mapOf("episode_id" to recipeId.toString()))
            assertEquals(recipeId.toString(), out[AnalyticsGlossary.RECIPE_ID_PROPERTY], event)
        }
        assertTrue(asked.isEmpty(), "a recipe-range ID needs no lookup")
    }

    @Test
    fun anImpressionAsksAndTheTapAndPlayThatFollowCarryTheSameValue() {
        val impression = emit("home_surface_impression", mapOf("episode_ids" to listOf("1234", "-55")))
        assertFalse(impression.containsKey(AnalyticsGlossary.RECIPE_IDS_PROPERTY), "never held back for a lookup")
        assertEquals(listOf(setOf("1234", "-55")), asked)
        pending!!(mapOf("1234" to (1L shl 52) + 7L))

        val tap = emit("home_surface_tapped", mapOf("episode_id" to "1234"))
        val play = emit("playback_started", mapOf("episode_id" to "1234"))
        assertEquals(((1L shl 52) + 7L).toString(), tap[AnalyticsGlossary.RECIPE_ID_PROPERTY])
        assertEquals(tap[AnalyticsGlossary.RECIPE_ID_PROPERTY], play[AnalyticsGlossary.RECIPE_ID_PROPERTY])
        assertEquals(listOf(((1L shl 52) + 7L).toString()), emit("home_surface_impression", mapOf("episode_ids" to listOf("1234", "-55")))[AnalyticsGlossary.RECIPE_IDS_PROPERTY])
    }

    @Test
    fun aMissIsNotAskedAgainSoonAndAValueOutsideTheRangeIsIgnored() {
        emit("playback_started", mapOf("episode_id" to "99"))
        pending!!(mapOf("99" to 12345L))
        val again = emit("playback_started", mapOf("episode_id" to "99"))
        assertFalse(again.containsKey(AnalyticsGlossary.RECIPE_ID_PROPERTY))
        assertEquals(1, asked.size)
    }

    @Test
    fun aCallerProvidedValueIsKeptAndRememberedOnesAreUsed() {
        val provided = emit("playback_started", mapOf("episode_id" to "5", AnalyticsGlossary.RECIPE_ID_PROPERTY to "x"))
        assertEquals("x", provided[AnalyticsGlossary.RECIPE_ID_PROPERTY])
        EpisodeRecipeIds.remember(mapOf("6" to (1L shl 52) + 1L))
        assertEquals(((1L shl 52) + 1L).toString(), emit("playback_paused", mapOf("episode_id" to "6"))[AnalyticsGlossary.RECIPE_ID_PROPERTY])
    }

    @Test
    fun aFailingLookupNeverBreaksAnEvent() {
        EpisodeRecipeIds.install { _, _ -> throw IllegalStateException("db closed") }
        val out = emit("playback_started", mapOf("episode_id" to "8"))
        assertEquals("8", out["episode_id"])
        assertFalse(out.containsKey(AnalyticsGlossary.RECIPE_ID_PROPERTY))
    }

    @Test
    fun theUpgradeEventCarriesCountsOnly() {
        AnalyticsHelper.trackOwnIdsUpgradeCompleted(cx.aswin.boxlore.core.model.UpgradeRepairReport(12, 1, 3, 11, 2, 7_560_000L))
        val (name, properties) = recorder.single()
        assertEquals("own_ids_upgrade_completed", name)
        assertEquals("gave_up", properties["outcome"])
        assertEquals(12, properties["shows_count"])
        assertEquals(1, properties["shows_gave_up_count"])
        assertEquals(3, properties["addresses_swapped_count"])
        assertEquals(11, properties["notifications_reregistered_count"])
        assertEquals(2, properties["runs_count"])
        assertEquals(2.1, properties["elapsed_hours"])
    }
}
