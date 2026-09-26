package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.graphics.Color
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
                "Quantum Beacon",
            ),
            titles,
        )

        val impacts = SUPPORT_TIER_CARDS.map { it.powerImpact }
        assertEquals(
            listOf(
                "Powers the boxlore servers for 3 hours",
                "Powers the boxlore servers for 8 hours",
                "Powers the boxlore servers for 1 full day",
                "Powers the boxlore servers for 3 full days",
                "Powers the boxlore servers for 1 full week",
            ),
            impacts,
        )
    }

    @Test
    fun supportTierCards_hasExactlyOneFeaturedTier() {
        val featured = SUPPORT_TIER_CARDS.filter { it.isFeatured }
        assertEquals(1, featured.size)
        assertEquals("Quantum Beacon", featured.first().title)
    }

    @Test
    fun supportTierCards_containsGameCodenames() {
        val codenames = SUPPORT_TIER_CARDS.map { "${it.codenameLine1} ${it.codenameLine2}" }
        assertEquals(
            listOf(
                "UNIT BLUE",
                "GRID HAWK",
                "VAULT PRIME",
                "STACK ZERO",
                "ORBIT X",
            ),
            codenames,
        )
    }

    @Test
    fun supportTierCards_costsAndIconsAreValid() {
        SUPPORT_TIER_CARDS.forEach { tier ->
            assertTrue("Cost should start with USD dollar sign", tier.cost.startsWith("$"))
            assertTrue("Power impact should not be blank", tier.powerImpact.isNotBlank())
            assertTrue("Short duration should not be blank", tier.shortDuration.isNotBlank())
            assertTrue("Energy segments must be between 1 and 5", tier.energySegments in 1..5)
            assertTrue("Icon resource must be non-zero", tier.iconRes != 0)
        }

        val segments = SUPPORT_TIER_CARDS.map { it.energySegments }
        assertEquals(listOf(1, 2, 3, 4, 5), segments)
    }

    @Test
    fun supportButtonContentColor_contrastsCorrectly() {
        val tier1Color = SUPPORT_TIER_CARDS[0].auraColor // Blue
        val tier2Color = SUPPORT_TIER_CARDS[1].auraColor // Green
        val tier3Color = SUPPORT_TIER_CARDS[2].auraColor // Yellow
        val tier4Color = SUPPORT_TIER_CARDS[3].auraColor // Purple
        val tier5Color = SUPPORT_TIER_CARDS[4].auraColor // Red

        assertEquals(Color.White, getSupportButtonContentColor(tier1Color))
        assertEquals(Color.Black, getSupportButtonContentColor(tier2Color))
        assertEquals(Color.Black, getSupportButtonContentColor(tier3Color))
        assertEquals(Color.White, getSupportButtonContentColor(tier4Color))
        assertEquals(Color.White, getSupportButtonContentColor(tier5Color))
    }

    @Test
    fun supportMissionText_underFiveHoursOrNull_returnsGeneralOpenPledgeCopy() {
        val expected = "Software that helps people learn, listen, and explore should simply exist without a catch. " +
            "No ads, no paywalls. Backing a power cell helps fund our servers and keep boxlore open for everyone."

        assertEquals(expected, getSupportMissionText(null))
        assertEquals(expected, getSupportMissionText(0L))
        assertEquals(expected, getSupportMissionText(1L))
        assertEquals(expected, getSupportMissionText(4L))
    }

    @Test
    fun supportMissionText_fiveHoursOrMore_returnsCompanionCopyHighlightingListeningTime() {
        val text5h = getSupportMissionText(5L)
        val text42h = getSupportMissionText(42L)

        assertTrue(text5h.contains("across your 5 hours of listening"))
        assertTrue(text5h.contains("Software that helps people learn, listen, and explore should simply exist without a catch."))
        assertTrue(text5h.contains("backing a power cell helps keep the app free and ad-free for everyone."))

        assertTrue(text42h.contains("across your 42 hours of listening"))
        assertTrue(text42h.contains("backing a power cell helps keep the app free and ad-free for everyone."))
    }

    @Test
    fun supportMissionText_neverMentionsZeroTracking() {
        listOf(null, 0L, 3L, 5L, 20L).forEach { hours ->
            val copy = getSupportMissionText(hours)
            assertTrue("Copy should never falsely claim zero tracking", !copy.contains("tracking", ignoreCase = true))
        }
    }
}
