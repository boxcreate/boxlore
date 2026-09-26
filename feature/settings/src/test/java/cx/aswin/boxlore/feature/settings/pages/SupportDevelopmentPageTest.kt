package cx.aswin.boxlore.feature.settings.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportDevelopmentPageTest {

    @Test
    fun supportTierCards_containsAllFiveTiersInOrder() {
        assertEquals(5, SUPPORT_TIER_CARDS.size)

        val titles = SUPPORT_TIER_CARDS.map { it.title }
        assertEquals(
            listOf(
                "Micro Energy Cell",
                "Field Battery Pack",
                "Power Station",
                "Server Tower",
                "Orbital Quantum Beacon",
            ),
            titles,
        )

        val impacts = SUPPORT_TIER_CARDS.map { it.powerImpact }
        assertEquals(
            listOf(
                "Powers 3 Hours",
                "Powers 8 Hours",
                "Powers 1 Full Day",
                "Powers 3 Full Days",
                "Powers 1 Full Week",
            ),
            impacts,
        )
    }

    @Test
    fun supportTierCards_hasExactlyOneFeaturedTier() {
        val featured = SUPPORT_TIER_CARDS.filter { it.isFeatured }
        assertEquals(1, featured.size)
        assertEquals("Orbital Quantum Beacon", featured.first().title)
    }

    @Test
    fun supportTierCards_costsAndIconsAreValid() {
        SUPPORT_TIER_CARDS.forEach { tier ->
            assertTrue("Cost should start with USD dollar sign", tier.cost.startsWith("$"))
            assertTrue("Description should not be blank", tier.description.isNotBlank())
            assertTrue("Scope should not be blank", tier.scope.isNotBlank())
            assertTrue("Short duration should not be blank", tier.shortDuration.isNotBlank())
            assertTrue("Energy segments must be between 1 and 5", tier.energySegments in 1..5)
            assertTrue("Icon resource must be non-zero", tier.iconRes != 0)
        }

        val segments = SUPPORT_TIER_CARDS.map { it.energySegments }
        assertEquals(listOf(1, 2, 3, 4, 5), segments)
    }

    @Test
    fun supportTierCards_technicalBreakdownsArePopulated() {
        SUPPORT_TIER_CARDS.forEach { tier ->
            assertTrue("Technical breakdown must not be empty", tier.technicalBreakdown.isNotEmpty())
            tier.technicalBreakdown.forEach { item ->
                assertTrue("Breakdown item must not be blank", item.isNotBlank())
            }
        }
    }
}
