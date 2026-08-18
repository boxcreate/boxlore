package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.ranking.database.PreferenceFacetEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShowFacetMigrationLogicTest {
    @Test
    fun `migration preserves and merges learned show evidence`() {
        val old =
            PreferenceFacetEntity(
                facetType = PreferenceFacetType.SHOW.name,
                facetKey = "rss:old",
                positiveEvidence = 3.0,
                negativeEvidence = 1.0,
                updatedAt = 100L,
            )
        val existingTarget =
            PreferenceFacetEntity(
                facetType = PreferenceFacetType.SHOW.name,
                facetKey = "42",
                positiveEvidence = 2.0,
                negativeEvidence = 4.0,
                updatedAt = 200L,
            )

        val merged = ShowFacetMigrationLogic.merge(old, existingTarget, "42")

        assertEquals("42", merged.facetKey)
        assertEquals(5.0, merged.positiveEvidence)
        assertEquals(5.0, merged.negativeEvidence)
        assertEquals(200L, merged.updatedAt)
    }

    @Test
    fun `migration retargets evidence when the target facet is absent`() {
        val old =
            PreferenceFacetEntity(
                facetType = PreferenceFacetType.SHOW.name,
                facetKey = "rss:old",
                positiveEvidence = 3.0,
                negativeEvidence = 1.0,
                updatedAt = 100L,
            )

        val merged = ShowFacetMigrationLogic.merge(old, null, "42")

        assertEquals("42", merged.facetKey)
        assertEquals(3.0, merged.positiveEvidence)
        assertEquals(1.0, merged.negativeEvidence)
        assertEquals(100L, merged.updatedAt)
    }
}
