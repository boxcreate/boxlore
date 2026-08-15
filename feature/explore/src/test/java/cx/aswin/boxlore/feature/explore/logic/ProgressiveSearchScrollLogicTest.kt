package cx.aswin.boxlore.feature.explore.logic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressiveSearchScrollLogicTest {
    @Test
    fun pinsWhenLocalHitsAreReplacedByCatalogTop() {
        val local =
            ProgressiveSearchScrollLogic.Snapshot(
                query = "joe",
                topResultId = "local-alpha",
                hasAlsoFoundSection = false,
            )
        val catalog = local.copy(topResultId = "meili-best")
        assertTrue(ProgressiveSearchScrollLogic.shouldPinToTop(local, catalog))
    }

    @Test
    fun pinsWhenMatchesHeaderAppears() {
        val before =
            ProgressiveSearchScrollLogic.Snapshot(
                query = "joe",
                topResultId = "meili-best",
                hasAlsoFoundSection = false,
            )
        assertTrue(
            ProgressiveSearchScrollLogic.shouldPinToTop(
                before,
                before.copy(hasAlsoFoundSection = true),
            ),
        )
    }

    @Test
    fun pinsWhenQueryChanges() {
        val previous =
            ProgressiveSearchScrollLogic.Snapshot(
                query = "joe",
                topResultId = "1",
                hasAlsoFoundSection = false,
            )
        assertTrue(ProgressiveSearchScrollLogic.shouldPinToTop(previous, previous.copy(query = "joe rogan")))
    }

    @Test
    fun doesNotPinWhenAlsoFoundGrowsButTopStays() {
        val previous =
            ProgressiveSearchScrollLogic.Snapshot(
                query = "joe",
                topResultId = "meili-best",
                hasAlsoFoundSection = true,
            )
        assertFalse(ProgressiveSearchScrollLogic.shouldPinToTop(previous, previous))
    }

    @Test
    fun doesNotPinBlankQuery() {
        assertFalse(
            ProgressiveSearchScrollLogic.shouldPinToTop(
                previous = null,
                current =
                    ProgressiveSearchScrollLogic.Snapshot(
                        query = "",
                        topResultId = "1",
                        hasAlsoFoundSection = false,
                    ),
            ),
        )
    }
}
