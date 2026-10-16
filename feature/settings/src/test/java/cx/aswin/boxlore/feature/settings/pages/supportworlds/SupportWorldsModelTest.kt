package cx.aswin.boxlore.feature.settings.pages.supportworlds

import cx.aswin.boxlore.feature.settings.pages.ControlSupportPageStyle
import cx.aswin.boxlore.feature.settings.pages.SUPPORT_TIER_CARDS
import cx.aswin.boxlore.feature.settings.pages.getSupportMissionText
import cx.aswin.boxlore.feature.settings.pages.supportCodenameFontSize
import cx.aswin.boxlore.feature.settings.pages.supportWorldPageStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportWorldsModelTest {
    @Test
    fun `flag variants map to worlds case-insensitively and unknown values do not`() {
        assertEquals(SupportWorld.Orbit, SupportWorld.fromVariant("orbit"))
        assertEquals(SupportWorld.Sky, SupportWorld.fromVariant("  SKY "))
        assertEquals(SupportWorld.Control, SupportWorld.fromVariant("control"))
        assertNull(SupportWorld.fromVariant(null))
        assertNull(SupportWorld.fromVariant(""))
        assertNull(SupportWorld.fromVariant("galaxy"))
    }

    @Test
    fun `missing or unknown flags fall back to today's page and are never counted as exposures`() {
        assertEquals(SupportWorldAssignment(SupportWorld.Control, SupportWorldSource.Fallback), resolveSupportWorld(null, null))
        assertEquals(SupportWorldAssignment(SupportWorld.Control, SupportWorldSource.Fallback), resolveSupportWorld("galaxy", null))
        assertEquals(SupportWorldAssignment(SupportWorld.Abyss, SupportWorldSource.Experiment), resolveSupportWorld("abyss", null))
        assertEquals(SupportWorldAssignment(SupportWorld.Control, SupportWorldSource.Experiment), resolveSupportWorld("control", null))
    }

    @Test
    fun `a debug override wins over the experiment`() {
        assertEquals(
            SupportWorldAssignment(SupportWorld.Neon, SupportWorldSource.DebugOverride),
            resolveSupportWorld("orbit", SupportWorld.Neon),
        )
    }

    @Test
    fun `every world sells the same five offers as today's page, in the same order`() {
        assertEquals(SUPPORT_TIER_CARDS.map { it.cost }, SUPPORT_OFFERS.map { it.priceLabel })
        SUPPORT_OFFERS.forEachIndexed { index, offer ->
            assertEquals(index, offer.index)
            assertEquals(offer.priceLabel, "$" + "%.2f".format(offer.amountUsd))
            assertNull("Play Billing products are attached later", offer.productId)
        }
        assertEquals(2, SUPPORT_DEFAULT_OFFER_INDEX)
        assertFalse(SUPPORT_PAYMENTS_LIVE)
    }

    @Test
    fun `only the control has no skin and every skin covers all five offers`() {
        assertNull(supportWorldSkin(SupportWorld.Control))
        SupportWorld.entries.filter { it != SupportWorld.Control }.forEach { world ->
            val maybeSkin = supportWorldSkin(world)
            assertNotNull(maybeSkin)
            val skin = maybeSkin!!
            assertEquals(world, skin.world)
            assertEquals(5, skin.tierNames.size)
            assertEquals(5, skin.tierArt.size)
            assertTrue(skin.tierArt.all { it != 0 })
            assertEquals(5, skin.tierArt.toSet().size)
            assertTrue(skin.ctaVerb.isNotBlank())
        }
    }

    @Test
    fun `every payments-off notice names the price and says nothing was charged`() {
        val notices = SupportWorld.entries.mapNotNull { supportWorldSkin(it) }.map { it.notice.body("$6.99") } +
            supportControlNoticeContent(art = 1, accent = SUPPORT_TIER_CARDS[3].auraColor, priceLabel = "$6.99").body
        assertEquals(6, notices.size)
        notices.forEach { body ->
            assertTrue(body, body.contains("$6.99"))
            assertTrue(body, body.contains("nothing was charged"))
        }
    }

    @Test
    fun `every world is today's page with only names, art and words swapped`() {
        assertEquals(SUPPORT_TIER_CARDS, ControlSupportPageStyle.tiers)
        assertNull(ControlSupportPageStyle.skin)
        SupportWorld.entries.mapNotNull { supportWorldSkin(it) }.forEach { skin ->
            val style = supportWorldPageStyle(skin)
            assertEquals(skin.world, style.world)
            assertEquals(skin.tierNames, style.tiers.map { it.title })
            assertEquals(skin.tierArt, style.tiers.map { it.iconRes })
            assertEquals(skin.tierNames.map { it.uppercase() }, style.tiers.map { "${it.codenameLine1} ${it.codenameLine2}" })
            style.tiers.zip(SUPPORT_TIER_CARDS).forEach { (world, control) ->
                assertEquals(control.cost, world.cost)
                assertEquals(control.shortDuration, world.shortDuration)
                assertEquals(control.powerImpact, world.powerImpact)
                assertEquals(control.energySegments, world.energySegments)
                assertEquals(control.auraColor, world.auraColor)
                assertEquals(control.isFeatured, world.isFeatured)
            }
        }
    }

    @Test
    fun `a codename shrinks only when its measured width would overflow the stage`() {
        assertEquals(52f, supportCodenameFontSize(fullSize = 52f, measuredWidth = 300f, availableWidth = 340f))
        assertEquals(52f, supportCodenameFontSize(fullSize = 52f, measuredWidth = 340f, availableWidth = 340f))
        assertEquals(26f, supportCodenameFontSize(fullSize = 52f, measuredWidth = 680f, availableWidth = 340f), 0.001f)
        assertEquals(42f, supportCodenameFontSize(fullSize = 42f, measuredWidth = 0f, availableWidth = 340f))
    }

    @Test
    fun `mission copy keeps today's wording and only swaps what a contribution backs`() {
        assertEquals(
            "Software that helps people learn, listen, and explore should simply exist without a catch. " +
                "No ads, no paywalls. Every snack helps fund our servers and keep boxlore open for everyone.",
            getSupportMissionText(4L, "every snack"),
        )
        assertTrue(getSupportMissionText(42L, "every island").contains("42 hours of listening, every island helps keep"))
        assertEquals(getSupportMissionText(4L), getSupportMissionText(4L, "backing a power cell"))
        listOf(null, 0L, 5L, 120L).forEach { hours ->
            val copy = getSupportMissionText(hours, "every relic")
            assertFalse(copy.contains("tracking", ignoreCase = true))
            assertFalse(copy.contains("privacy", ignoreCase = true))
        }
    }
}
