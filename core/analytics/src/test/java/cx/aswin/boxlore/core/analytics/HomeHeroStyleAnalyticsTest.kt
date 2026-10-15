package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeHeroStyleAnalyticsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        AppearanceAnalyticsTracks.resetSyncedPersonPropertiesForTest()
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
        AppearanceAnalyticsTracks.resetSyncedPersonPropertiesForTest()
    }

    @Test
    fun `stored style keys report the names listeners see and Block takes no colours`() {
        assertEquals("block", AppearanceAnalyticsTracks.heroStyleName(HeroCardStyle.BLOCK))
        assertEquals("cover", AppearanceAnalyticsTracks.heroStyleName(HeroCardStyle.COVER))
        assertEquals("cover", AppearanceAnalyticsTracks.heroStyleName(null))
        assertEquals("marquee", AppearanceAnalyticsTracks.heroStyleName(HeroCardStyle.MARQUEE))
        assertEquals("none", AppearanceAnalyticsTracks.heroColorsName(HeroCardStyle.BLOCK, HeroCoverShade.THEME))
        assertEquals("block", AppearanceAnalyticsTracks.heroStyleCombo(HeroCardStyle.BLOCK, HeroCoverShade.THEME))
        assertEquals("duotone_theme", AppearanceAnalyticsTracks.heroStyleCombo(HeroCardStyle.DUOTONE, HeroCoverShade.THEME))
        assertEquals("cover_artwork", AppearanceAnalyticsTracks.heroStyleCombo(null, null))
    }

    @Test
    fun `picking a style reports the full look it makes`() {
        AnalyticsHelper.trackHomeHeroStyleSelected(HeroCardStyle.COVER, HeroCoverShade.DARK, HeroCardStyle.HALO, HeroCoverShade.DARK)

        assertEquals(
            listOf(
                "home_hero_style_selected" to
                    mapOf(
                        "hero_card_style" to "halo",
                        "hero_colors" to "dark",
                        "hero_style_combo" to "halo_dark",
                        "changed" to "style",
                        "previous_hero_style_combo" to "cover_dark",
                    ),
            ),
            recorder,
        )
    }

    @Test
    fun `picking colours reports them against the current style`() {
        AnalyticsHelper.trackHomeHeroStyleSelected(HeroCardStyle.TIDE, HeroCoverShade.ARTWORK, HeroCardStyle.TIDE, HeroCoverShade.THEME)

        val props = recorder.single().second
        assertEquals("tide_theme", props["hero_style_combo"])
        assertEquals("colors", props["changed"])
        assertEquals("tide_artwork", props["previous_hero_style_combo"])
    }

    @Test
    fun `re-picking the current look sends nothing`() {
        AnalyticsHelper.trackHomeHeroStyleSelected(HeroCardStyle.DUOTONE, HeroCoverShade.ARTWORK, HeroCardStyle.DUOTONE, HeroCoverShade.ARTWORK)
        AnalyticsHelper.trackHomeHeroStyleSelected(null, null, HeroCardStyle.COVER, HeroCoverShade.ARTWORK)

        assertTrue(recorder.isEmpty(), "Got $recorder")
    }

    @Test
    fun `person properties follow the look once per change, not once per window change`() {
        repeat(3) { AnalyticsHelper.syncHomeHeroStylePersonProperties(HeroCardStyle.COVER, HeroCoverShade.ARTWORK) }
        AnalyticsHelper.syncHomeHeroStylePersonProperties(HeroCardStyle.BLOCK, HeroCoverShade.ARTWORK)
        // Block ignores colours, so a colour change under it is the same look.
        AnalyticsHelper.syncHomeHeroStylePersonProperties(HeroCardStyle.BLOCK, HeroCoverShade.THEME)
        AnalyticsHelper.syncHomeHeroStylePersonProperties(HeroCardStyle.COVER, HeroCoverShade.ARTWORK)

        assertEquals(listOf(AnalyticsGlossary.PERSON_SET_EVENT), recorder.map { it.first }.distinct())
        assertEquals(listOf("cover_artwork", "block", "cover_artwork"), recorder.map { it.second["home_hero_style_combo"] })
        assertEquals(
            mapOf("home_hero_card_style" to "block", "home_hero_colors" to "none", "home_hero_style_combo" to "block"),
            recorder[1].second,
        )
    }

    @Test
    fun `an identity reset resends the current look to the new person`() {
        AppearanceAnalyticsTracks.resendHomeHeroStyleAfterIdentityReset()
        assertTrue(recorder.isEmpty(), "Nothing to resend before the first sync")

        AnalyticsHelper.syncHomeHeroStylePersonProperties(HeroCardStyle.MARQUEE, HeroCoverShade.THEME)
        AppearanceAnalyticsTracks.resendHomeHeroStyleAfterIdentityReset()

        assertEquals(2, recorder.size)
        assertEquals(recorder[0], recorder[1])
        assertEquals("marquee_theme", recorder[1].second["home_hero_style_combo"])
    }
}
