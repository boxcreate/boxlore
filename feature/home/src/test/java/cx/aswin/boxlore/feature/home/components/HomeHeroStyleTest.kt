package cx.aswin.boxlore.feature.home.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeHeroStyleTest {
    @Test fun `attached cover rounds only the inner corner and retains more than 99 percent of the cover`() {
        val shape = HomeHeroStyle.AttachedArtworkShape
        val ltr = (shape.createOutline(Size(188f, 188f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded).roundRect
        val rtl = (shape.createOutline(Size(188f, 188f), LayoutDirection.Rtl, Density(1f)) as Outline.Rounded).roundRect
        assertEquals(24f, ltr.bottomLeftCornerRadius.x)
        assertEquals(0f, ltr.bottomRightCornerRadius.x)
        assertEquals(0f, ltr.topLeftCornerRadius.x)
        assertEquals(0f, ltr.topRightCornerRadius.x)
        assertEquals(ltr.bottomLeftCornerRadius, rtl.bottomRightCornerRadius)
        assertTrue(1.0 - (1.0 - kotlin.math.PI / 4) * 24 * 24 / (188 * 188) > 0.99)
    }

    @Test fun `tablets show Cover for Marquee and keep every other style`() {
        assertEquals(cx.aswin.boxlore.core.prefs.HeroCardStyle.COVER, homeHeroStyle(cx.aswin.boxlore.core.prefs.HeroCardStyle.MARQUEE, tablet = true))
        assertEquals(cx.aswin.boxlore.core.prefs.HeroCardStyle.MARQUEE, homeHeroStyle(cx.aswin.boxlore.core.prefs.HeroCardStyle.MARQUEE, tablet = false))
        listOf(
            cx.aswin.boxlore.core.prefs.HeroCardStyle.BLOCK,
            cx.aswin.boxlore.core.prefs.HeroCardStyle.COVER,
            cx.aswin.boxlore.core.prefs.HeroCardStyle.TIDE,
            cx.aswin.boxlore.core.prefs.HeroCardStyle.DUOTONE,
            cx.aswin.boxlore.core.prefs.HeroCardStyle.HALO,
        ).forEach { style -> assertEquals(style, homeHeroStyle(style, tablet = true), style) }
        assertEquals(cx.aswin.boxlore.core.prefs.HeroCardStyle.BLOCK, homeHeroStyle("unknown", tablet = true))
    }

    @Test fun `light and dark canvas use paired theme roles`() {
        val light = lightColorScheme()
        val dark = darkColorScheme()
        assertEquals(light.primary, HomeHeroStyle.container(light))
        assertEquals(light.onPrimary, HomeHeroStyle.foreground(light))
        assertEquals(dark.primaryContainer, HomeHeroStyle.container(dark))
        assertEquals(dark.onPrimaryContainer, HomeHeroStyle.foreground(dark))
    }

    @Test fun `backdrop and solo cover retain the episode show fallback source order`() {
        val episode = Episode("ep", "Episode", "", "", imageUrl = "episode-cover")
        val show = Podcast("show", "Show", "", "show-cover", latestEpisode = episode, fallbackImageUrl = "fallback-cover")
        assertEquals("episode-cover", heroArtworkUrl(show))
        assertEquals("show-cover", heroArtworkUrl(show.copy(latestEpisode = episode.copy(imageUrl = ""))))
        assertEquals("fallback-cover", heroArtworkUrl(show.copy(latestEpisode = null, imageUrl = "")))
        assertNull(heroArtworkUrl(show.copy(latestEpisode = null, imageUrl = "", fallbackImageUrl = null)))
    }

    @Test fun `progress and time labels tolerate missing invalid and completed sessions`() {
        val podcast = Podcast("show", "Show", "", "", latestEpisode = Episode("ep", "Episode", "", "", duration = 3000), resumeProgress = 0.5f)
        assertEquals(25, HomeHeroStyle.minutes(podcast, resume = true))
        assertEquals(50, HomeHeroStyle.minutes(podcast, resume = false))
        val longEpisode = podcast.copy(latestEpisode = podcast.latestEpisode!!.copy(duration = 10560))
        assertEquals(88, HomeHeroStyle.minutes(longEpisode, resume = true))
        assertEquals(176, HomeHeroStyle.minutes(longEpisode, resume = false))
        assertEquals(1, HomeHeroStyle.minutes(podcast.copy(resumeProgress = 1f), resume = true))
        assertNull(HomeHeroStyle.minutes(podcast.copy(latestEpisode = null), resume = true))
        assertNull(HomeHeroStyle.minutes(podcast.copy(latestEpisode = podcast.latestEpisode!!.copy(duration = 0)), resume = false))
        assertEquals(0f, HomeHeroStyle.progress(podcast.copy(resumeProgress = Float.NaN)))
        assertEquals(0f, HomeHeroStyle.progress(podcast.copy(resumeProgress = -1f)))
        assertEquals(1f, HomeHeroStyle.progress(podcast.copy(resumeProgress = 2f)))
    }

    @Test fun `spotlight artwork prefers show identity and falls back to episode art only without show art`() {
        val episode = Episode("ep", "Episode", "", "", imageUrl = "episode-cover")
        val show = Podcast("show", "Show", "", "show-cover", latestEpisode = episode, fallbackImageUrl = "fallback-cover")
        assertEquals("show-cover", heroArtworkUrl(show, showFirst = true))
        assertEquals("fallback-cover", heroArtworkUrl(show.copy(imageUrl = " "), showFirst = true))
        assertEquals("episode-cover", heroArtworkUrl(show.copy(imageUrl = "", fallbackImageUrl = null), showFirst = true))
        assertNull(heroArtworkUrl(show.copy(imageUrl = "", fallbackImageUrl = null, latestEpisode = null), showFirst = true))
        assertEquals("show-cover", show.imageUrl)
        assertEquals("episode-cover", show.latestEpisode?.imageUrl)
    }
}
