package cx.aswin.boxlore.feature.info

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeLinkPaletteTest {
    @Test
    fun `known logos remain readable on solid tinted bubbles in light and dark themes`() {
        val themes = listOf(Color(0xFFF3F1F6) to Color(0xFF1B1A20), Color(0xFF222027) to Color(0xFFE8E1E8))
        val hosts = listOf("instagram.com", "x.com", "threads.com", "facebook.com", "linkedin.com", "bsky.app", "youtube.com", "tiktok.com", "twitch.tv", "reddit.com", "discord.gg", "open.spotify.com", "podcasts.apple.com", "patreon.com", "ko-fi.com", "buymeacoffee.com")
        themes.forEach { (surface, foreground) ->
            hosts.forEach { host ->
                val palette = episodeLinkPalette(episodeLinkBrandIcon(host)!!, surface, foreground)
                assertEquals(1f, palette.container.alpha, host)
                assertTrue(contrast(palette.icon, palette.container) >= 3f, "Logo contrast: $host")
                assertTrue(contrast(foreground, palette.container) >= 4.5f, "Text contrast: $host")
            }
        }
    }

    @Test
    fun `TikTok uses a cyan accent rather than the monochrome fallback in both themes`() {
        val themes = listOf(Color(0xFFF3F1F6) to Color(0xFF1B1A20), Color(0xFF222027) to Color(0xFFE8E1E8))
        themes.forEach { (surface, foreground) ->
            val palette = episodeLinkPalette(R.drawable.ic_link_tiktok, surface, foreground)
            val neutral = episodeLinkPalette(R.drawable.ic_link_x, surface, foreground)
            assertNotEquals(neutral.container, palette.container)
            assertTrue(palette.icon.green - palette.icon.red > 0.2f, "TikTok icon keeps its cyan hue")
            assertTrue(palette.icon.blue - palette.icon.red > 0.2f, "TikTok icon keeps its cyan hue")
            assertEquals(1f, palette.container.alpha)
            assertTrue(contrast(palette.icon, palette.container) >= 3f)
            assertTrue(contrast(foreground, palette.container) >= 4.5f)
        }
    }

    @Test
    fun `X and Threads retain readable monochrome logos in both themes`() {
        val themes = listOf(Color(0xFFF3F1F6) to Color(0xFF1B1A20), Color(0xFF222027) to Color(0xFFE8E1E8))
        themes.forEach { (surface, foreground) ->
            listOf(R.drawable.ic_link_x, R.drawable.ic_link_threads).forEach { icon ->
                val palette = episodeLinkPalette(icon, surface, foreground)
                assertEquals(foreground, palette.icon)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
    }
}
