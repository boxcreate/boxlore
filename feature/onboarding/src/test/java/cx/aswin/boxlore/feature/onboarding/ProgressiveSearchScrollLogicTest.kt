package cx.aswin.boxlore.feature.onboarding

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
        val catalog =
            local.copy(topResultId = "meili-best")
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
        val after = before.copy(hasAlsoFoundSection = true)
        assertTrue(ProgressiveSearchScrollLogic.shouldPinToTop(before, after))
    }

    @Test
    fun pinsWhenQueryChanges() {
        val previous =
            ProgressiveSearchScrollLogic.Snapshot(
                query = "joe",
                topResultId = "1",
                hasAlsoFoundSection = false,
            )
        val current = previous.copy(query = "joe rogan")
        assertTrue(ProgressiveSearchScrollLogic.shouldPinToTop(previous, current))
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
