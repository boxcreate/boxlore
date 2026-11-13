package cx.aswin.boxlore.wear.ui.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** The player's colours as ARGB ints, each foreground chosen to be readable on its ground. */
internal data class PlayerColors(
    /** The play button and the progress ring. */
    val accent: Int,
    val onAccent: Int,
    /** The seek buttons. */
    val container: Int,
    val onContainer: Int,
    /** The small buttons and the unplayed part of the ring. */
    val quiet: Int,
    /** Secondary text on the black ground. */
    val detail: Int,
)

/**
 * Builds the player's colours from one colour taken from the episode artwork, the way the phone
 * player follows each show. Only the hue is kept; lightness is fixed per role so text stays
 * readable whatever the artwork is. Grey artwork falls back to boxlore's own colours.
 */
internal object PlayerPalette {
    val Brand =
        PlayerColors(
            accent = 0xFF9C8CFF.toInt(),
            onAccent = 0xFF21164B.toInt(),
            container = 0xFF3F347B.toInt(),
            onContainer = 0xFFECE7FF.toInt(),
            quiet = 0xFF272832.toInt(),
            detail = 0xFFC8C5D3.toInt(),
        )

    private const val MIN_SEED_SATURATION = 0.12f

    fun fromSeed(seedArgb: Int?): PlayerColors {
        if (seedArgb == null) return Brand
        val (hue, saturation) = hueAndSaturation(seedArgb)
        if (saturation < MIN_SEED_SATURATION) return Brand
        return PlayerColors(
            accent = hsl(hue, 0.9f, 0.83f),
            onAccent = hsl(hue, 0.7f, 0.2f),
            container = hsl(hue, 0.46f, 0.3f),
            onContainer = hsl(hue, 0.9f, 0.93f),
            quiet = hsl(hue, 0.26f, 0.18f),
            detail = hsl(hue, 0.5f, 0.82f),
        )
    }

    /** WCAG contrast ratio between two opaque colours. */
    fun contrast(first: Int, second: Int): Float {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
    }

    private fun hueAndSaturation(argb: Int): Pair<Float, Float> {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val delta = high - low
        if (delta == 0f) return 0f to 0f
        val lightness = (high + low) / 2f
        val saturation = delta / (1f - abs(2f * lightness - 1f))
        val hue =
            when (high) {
                r -> ((g - b) / delta).mod(6f)
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            } * 60f
        return hue to saturation
    }

    private fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val x = chroma * (1f - abs((hue / 60f).mod(2f) - 1f))
        val (r, g, b) =
            when ((hue / 60f).toInt().mod(6)) {
                0 -> Triple(chroma, x, 0f)
                1 -> Triple(x, chroma, 0f)
                2 -> Triple(0f, chroma, x)
                3 -> Triple(0f, x, chroma)
                4 -> Triple(x, 0f, chroma)
                else -> Triple(chroma, 0f, x)
            }
        val lift = lightness - chroma / 2f
        return (0xFF shl 24) or (channel(r + lift) shl 16) or (channel(g + lift) shl 8) or channel(b + lift)
    }

    private fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun luminance(argb: Int): Float {
        fun linear(channel: Int): Float {
            val c = channel / 255f
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * linear(argb shr 16 and 0xFF) + 0.7152f * linear(argb shr 8 and 0xFF) + 0.0722f * linear(argb and 0xFF)
    }
}
