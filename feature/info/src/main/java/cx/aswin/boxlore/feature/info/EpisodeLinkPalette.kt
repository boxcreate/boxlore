package cx.aswin.boxlore.feature.info

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

internal data class EpisodeLinkPalette(val container: Color, val icon: Color)

private val platformColors = mapOf(
    R.drawable.ic_link_instagram to Color(0xFFFF0069),
    R.drawable.ic_link_facebook to Color(0xFF0866FF),
    R.drawable.ic_link_linkedin to Color(0xFF0A66C2),
    R.drawable.ic_link_bluesky to Color(0xFF1185FE),
    R.drawable.ic_link_youtube to Color(0xFFFF0000),
    R.drawable.ic_link_tiktok to Color(0xFF25F4EE),
    R.drawable.ic_link_twitch to Color(0xFF9146FF),
    R.drawable.ic_link_reddit to Color(0xFFFF4500),
    R.drawable.ic_link_discord to Color(0xFF5865F2),
    R.drawable.ic_link_spotify to Color(0xFF1ED760),
    R.drawable.ic_link_applepodcasts to Color(0xFF9933CC),
    R.drawable.ic_link_kofi to Color(0xFFFF6433),
    R.drawable.ic_link_buymeacoffee to Color(0xFFFFDD00),
)

internal fun episodeLinkPalette(icon: Int, surface: Color, onSurface: Color): EpisodeLinkPalette {
    // Monochrome brands use the theme foreground so black logos also work in dark mode.
    val seed = platformColors[icon] ?: onSurface
    val container = lerp(surface, seed, 0.10f)
    return EpisodeLinkPalette(container, readablePlatformTint(seed, container, onSurface))
}

private fun readablePlatformTint(seed: Color, container: Color, foreground: Color): Color {
    for (step in 0..10) {
        val tint = lerp(seed, foreground, step / 10f)
        if (linkColorContrast(tint, container) >= 3f) return tint
    }
    return foreground
}

private fun linkColorContrast(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) / (min(firstLuminance, secondLuminance) + 0.05f)
}
