package cx.aswin.boxlore.feature.settings.dialogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportDevelopmentSheetTest {

    @Test
    fun supportTiers_containsExpectedCountAndOrder() {
        assertEquals(5, SUPPORT_TIERS.size)

        val durations = SUPPORT_TIERS.map { it.duration }
        assertEquals(listOf("3 Hours", "8 Hours", "1 Day", "3 Days", "1 Week"), durations)

        val titles = SUPPORT_TIERS.map { it.title }
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
    }

    @Test
    fun supportTiers_costsAreNonEmptyAndFormatted() {
        SUPPORT_TIERS.forEach { tier ->
            assertTrue("Cost should contain INR symbol", tier.cost.contains("\u20B9"))
            assertTrue("Cost should contain USD dollar symbol", tier.cost.contains("$"))
            assertTrue("Title should not be blank", tier.title.isNotBlank())
            assertTrue("Duration should not be blank", tier.duration.isNotBlank())
        }
    }
}
