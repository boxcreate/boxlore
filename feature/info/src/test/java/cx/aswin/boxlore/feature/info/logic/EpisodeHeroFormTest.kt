package cx.aswin.boxlore.feature.info.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeHeroFormTest {
    @Test
    fun `phones, the cover screen and narrow detail panes keep the stacked phone layout`() {
        listOf(360f, 412f, 475f, 473f, 520f, 599f).forEach { width ->
            assertEquals(EpisodeHeroForm.Stacked, episodeHeroForm(width, isShortPane = false), "at ${width}dp")
        }
    }

    @Test
    fun `a page 600dp or wider puts the cover beside the title`() {
        assertEquals(EpisodeHeroForm.Stacked, episodeHeroForm(599f, isShortPane = false))
        assertEquals(EpisodeHeroForm.TwoColumn, episodeHeroForm(600f, isShortPane = false))
        assertEquals(EpisodeHeroForm.TwoColumn, episodeHeroForm(720f, isShortPane = false))
    }

    @Test
    fun `a short page gets the small cover beside the title at any width`() {
        listOf(412f, 650f, 915f, 1_280f).forEach { width ->
            assertEquals(EpisodeHeroForm.Row, episodeHeroForm(width, isShortPane = true), "at ${width}dp")
        }
    }

    @Test
    fun `the two-column cover grows with the page between its limits`() {
        assertEquals(200f, episodeHeroArtSizeDp(EpisodeHeroForm.Stacked, 412f))
        assertEquals(120f, episodeHeroArtSizeDp(EpisodeHeroForm.Row, 915f))
        assertEquals(240f, episodeHeroArtSizeDp(EpisodeHeroForm.TwoColumn, 600f))
        assertEquals(280f, episodeHeroArtSizeDp(EpisodeHeroForm.TwoColumn, 1_000f))
    }
}
