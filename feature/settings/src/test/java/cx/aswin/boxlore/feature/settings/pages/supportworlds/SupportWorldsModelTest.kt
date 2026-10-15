package cx.aswin.boxlore.feature.settings.pages.supportworlds

import cx.aswin.boxlore.feature.settings.pages.SUPPORT_TIER_CARDS
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
        assertEquals(5, SUPPORT_TIER_TONES.size)
        assertEquals(5, SUPPORT_RARITIES.size)
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
            supportControlNoticeContent(art = 1, accent = SUPPORT_TIER_TONES[3].color, priceLabel = "$6.99").body
        assertEquals(6, notices.size)
        notices.forEach { body ->
            assertTrue(body, body.contains("$6.99"))
            assertTrue(body, body.contains("nothing was charged"))
        }
    }

    @Test
    fun `split codenames fit the narrowest stage and never exceed the display size`() {
        SupportWorld.entries.mapNotNull { supportWorldSkin(it) }
            .filter { it.codename == SupportCodenameStyle.SplitName }
            .forEach { skin ->
                skin.tierNames.flatMap { it.uppercase().split(" ") }.forEach { word ->
                    val size = codenameSizeDp(word.length, stageWidthDp = 360f, widthFactor = skin.displayWidthFactor)
                    assertTrue("$word is too large", size <= 66f)
                    assertTrue("$word overflows", size * word.length * skin.displayWidthFactor <= 360f - 28f + 0.01f)
                }
            }
    }

    @Test
    fun `mission copy mirrors today's page and never makes privacy claims`() {
        assertEquals(
            "No ads, no paywalls. Every snack helps fund the servers and keeps boxlore open for everyone.",
            supportWorldMissionText("snack", 4L),
        )
        assertTrue(supportWorldMissionText("island", 42L).contains("across your 42 hours of listening"))
        listOf(null, 0L, 5L, 120L).forEach { hours ->
            val copy = supportWorldMissionText("relic", hours)
            assertFalse(copy.contains("tracking", ignoreCase = true))
            assertFalse(copy.contains("privacy", ignoreCase = true))
        }
    }

    @Test
    fun `neon sign flickers only on its off-beats`() {
        assertEquals(0.62f, neonFlickerAlpha(0f))
        assertEquals(0.08f, neonFlickerAlpha(0.2f))
        assertEquals(0.08f, neonFlickerAlpha(0.55f))
        assertEquals(0.62f, neonFlickerAlpha(0.9f))
    }
}
