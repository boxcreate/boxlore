package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListenerIdAdoptionTest {
    @Test
    fun onlyIdsNothingElseIdentifiesAreHeld() {
        val references =
            listOf("-1", "-2", "-3", "-4", "5001", "briefing_daily_1", "0", "x").map { LocalCatalogListenerReference(it, null, null, null) }
        val showAliases = listOf(alias("-9", show = SHOW, key = null, state = EpisodeAliasState.UNRESOLVED))
        val aliasesById =
            mapOf(
                "-2" to alias("-2", show = OTHER_SHOW, key = null, state = EpisodeAliasState.UNRESOLVED),
                "-3" to alias("-3", show = SHOW, key = "g3"),
                "-4" to alias("-4", show = SHOW, key = null, state = EpisodeAliasState.UNRESOLVED),
                "-9" to showAliases.single(),
            )

        val evidence = ListenerEvidence.of(SHOW, references, showAliases, catalogHeldIds = setOf("-1"), aliasesById = aliasesById)

        // -1 has a catalog row, -2 belongs to another show, -3 already has a key; Briefs and non-IDs never count.
        assertEquals(setOf("-4", "5001", "-9"), evidence.heldIds)
        assertEquals(listOf("-4", "5001"), evidence.references.map { it.episodeId })
        assertSame(ListenerEvidence.NONE, ListenerEvidence.of(SHOW, emptyList(), emptyList(), emptySet(), emptyMap()))
    }

    @Test
    fun anOwnMintWinsOverAListenerMatchAndEachIdIsKeptOnce() {
        val evidence =
            ListenerEvidence(
                heldIds = setOf("-5", "5001"),
                references = listOf(LocalCatalogListenerReference("-5", AUDIO_B, null, null), LocalCatalogListenerReference("5001", AUDIO_A, null, null)),
            )
        val candidates = listOf(candidate(0, mints = listOf("-5"), audio = AUDIO_A), candidate(1, mints = listOf("-5"), audio = AUDIO_B))

        val adopted = ListenerIdAdoption.adopt(candidates, evidence, claimed = emptySet())

        assertEquals(ListenerIdAdoption.Adopted("-5", ListenerIdAdoption.Kind.OWN_MINT), adopted[0])
        // -5 is taken, so its audio match to item 1 no longer counts; 5001 matches item 0, which already kept -5.
        assertEquals(null, adopted[1])
    }

    @Test
    fun claimedAndClosedPositionsNeverTakeAnId() {
        val evidence = ListenerEvidence(setOf("-5", "5001"), listOf(LocalCatalogListenerReference("5001", AUDIO_A, null, null)))
        assertTrue(ListenerIdAdoption.adopt(listOf(candidate(0, mints = listOf("-5"))), evidence, claimed = setOf("-5")).isEmpty())
        assertTrue(ListenerIdAdoption.adopt(listOf(candidate(0, open = false, audio = AUDIO_A)), evidence, claimed = emptySet()).isEmpty())
    }

    @Test
    fun aTitleMatchNeedsADateWithinTwoDays() {
        val far = ListenerEvidence(setOf("5001"), listOf(LocalCatalogListenerReference("5001", null, " Ep ", 10L + 3L * 24L * 3_600L)))
        assertTrue(ListenerIdAdoption.adopt(listOf(candidate(0, title = "Ep")), far, emptySet()).isEmpty())
        val near = ListenerEvidence(setOf("5001"), listOf(LocalCatalogListenerReference("5001", null, " Ep ", 10L + 3_600L)))
        assertEquals("5001", ListenerIdAdoption.adopt(listOf(candidate(0, title = "Ep")), near, emptySet())[0]?.episodeId)
        val undated = ListenerEvidence(setOf("5001"), listOf(LocalCatalogListenerReference("5001", null, "Ep", null)))
        assertTrue(ListenerIdAdoption.adopt(listOf(candidate(0, title = "Ep")), undated, emptySet()).isEmpty())
    }

    @Test
    fun anIdWhoseRowsPointAtTwoItemsKeepsNothing() {
        val evidence =
            ListenerEvidence(
                setOf("5001"),
                listOf(LocalCatalogListenerReference("5001", AUDIO_A, null, null), LocalCatalogListenerReference("5001", AUDIO_B, null, null)),
            )
        assertTrue(ListenerIdAdoption.adopt(listOf(candidate(0, audio = AUDIO_A), candidate(1, audio = AUDIO_B)), evidence, emptySet()).isEmpty())
    }

    private fun candidate(position: Int, open: Boolean = true, mints: List<String> = emptyList(), audio: String = "https://cdn.example/$position.mp3", title: String = "Ep $position",) =
        ListenerIdAdoption.Candidate(position, open, mints, setOf(audio), title, publishedDate = 10L)

    private fun alias(id: String, show: String, key: String?, state: String = EpisodeAliasState.TRUSTED) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = show,
        catalogKey = key,
        recipeId = null,
        state = state,
        owner = false,
        provenance = EpisodeAliasProvenance.UPGRADE_LISTENER_ROW,
        createdAt = 0L,
    )

    private companion object {
        const val SHOW = "100"
        const val OTHER_SHOW = "200"
        const val AUDIO_A = "https://cdn.example/a.mp3"
        const val AUDIO_B = "https://cdn.example/b.mp3"
    }
}
