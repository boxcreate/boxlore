package cx.aswin.boxlore.tv.ui.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The player's colours as ARGB values, all from one hue taken from the cover. [focus] marks the
 * control the remote is on; [primary] is the Play button and the played part of the seek bar.
 */
data class PlayerPalette(
    val backgroundTop: Int,
    val backgroundBottom: Int,
    val glow: Int,
    val container: Int,
    val onContainer: Int,
    val track: Int,
    val primary: Int,
    val onPrimary: Int,
    val title: Int,
    val soft: Int,
    val focus: Int,
    val onFocus: Int,
    val panel: Int,
)

/**
 * How the player takes its colours from the cover, as the phone player does: the cover's main hue,
 * each role at a fixed lightness so text keeps its contrast whatever the cover is. Covers with no
 * clear hue (black, white, grey) use boxlore's iris.
 */
object PlayerColorRules {
    /** boxlore's iris, for covers without a clear colour. */
    const val IRIS: Int = 0xFF9C8CFF.toInt()

    /**
     * The cover's main colour from a small sample of its pixels, or null when it has none. Like the
     * phone's picker, it skips white margins, black lettering and tiny bright details.
     */
    fun seed(pixels: IntArray): Int? {
        val buckets = HashMap<Int, Bucket>()
        pixels.forEach { argb ->
            if (argb ushr ALPHA_SHIFT >= MIN_ALPHA) {
                val r = argb shr RED_SHIFT and CHANNEL
                val g = argb shr GREEN_SHIFT and CHANNEL
                val b = argb and CHANNEL
                val key = (r shr BUCKET_SHIFT shl BUCKET_RED) or (g shr BUCKET_SHIFT shl BUCKET_GREEN) or (b shr BUCKET_SHIFT)
                buckets.getOrPut(key) { Bucket() }.add(r, g, b)
            }
        }
        val total = buckets.values.sumOf { it.count }.coerceAtLeast(1)
        return buckets.values
            .filter { it.count.toDouble() / total >= MIN_SHARE }
            .map { it to it.mean() }
            .filter { (_, argb) -> chroma(argb) >= MIN_CHROMA && lightness(argb) in MIN_LIGHTNESS..MAX_LIGHTNESS }
            .maxByOrNull { (bucket, argb) -> bucket.count.toDouble() / total * SHARE_WEIGHT + chroma(argb) * CHROMA_WEIGHT }
            ?.second
    }

    /** The player's colours for a cover's [seed]; null gives boxlore's iris. */
    fun palette(seed: Int?): PlayerPalette {
        val (hue, saturation) = hueAndSaturation(seed ?: IRIS)
        val s = { cap: Float -> min(saturation, cap) }
        return PlayerPalette(
            backgroundTop = hsl(hue, s(0.5f), 0.2f),
            backgroundBottom = hsl(hue, s(0.4f), 0.08f),
            glow = hsl(hue, s(0.6f), 0.32f),
            container = hsl(hue, s(0.32f), 0.26f),
            onContainer = hsl(hue, s(0.3f), 0.92f),
            track = hsl(hue, s(0.28f), 0.36f),
            primary = hsl(hue, saturation.coerceIn(0.5f, 0.85f), 0.78f),
            onPrimary = hsl(hue, s(0.6f), 0.14f),
            title = hsl(hue, s(0.15f), 0.95f),
            soft = hsl(hue, s(0.35f), 0.8f),
            focus = hsl(hue, s(0.2f), 0.96f),
            onFocus = hsl(hue, s(0.6f), 0.12f),
            panel = hsl(hue, s(0.3f), 0.15f),
        )
    }

    /** WCAG contrast between two opaque colours, from 1 to 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + CONTRAST_OFFSET) / (min(la, lb) + CONTRAST_OFFSET)
    }

    private class Bucket {
        var count = 0
        private var r = 0L
        private var g = 0L
        private var b = 0L

        fun add(red: Int, green: Int, blue: Int) {
            count++
            r += red
            g += green
            b += blue
        }

        fun mean(): Int = rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun channels(argb: Int) = Triple(argb shr RED_SHIFT and CHANNEL, argb shr GREEN_SHIFT and CHANNEL, argb and CHANNEL)

    private fun chroma(argb: Int): Float {
        val (r, g, b) = channels(argb)
        return (maxOf(r, g, b) - minOf(r, g, b)) / CHANNEL.toFloat()
    }

    private fun lightness(argb: Int): Float {
        val (r, g, b) = channels(argb)
        return (maxOf(r, g, b) + minOf(r, g, b)) / (2f * CHANNEL)
    }

    private fun hueAndSaturation(argb: Int): Pair<Float, Float> {
        val (ri, gi, bi) = channels(argb)
        val r = ri / CHANNEL.toFloat()
        val g = gi / CHANNEL.toFloat()
        val b = bi / CHANNEL.toFloat()
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        val delta = high - low
        if (delta == 0f) return 0f to 0f
        val light = (high + low) / 2f
        val saturation = delta / (1f - abs(2f * light - 1f))
        val hue =
            when (high) {
                r -> ((g - b) / delta).mod(SECTORS)
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            } * DEGREES_PER_SECTOR
        return hue to saturation.coerceIn(0f, 1f)
    }

    private fun hsl(hue: Float, saturation: Float, light: Float): Int {
        val c = (1f - abs(2f * light - 1f)) * saturation
        val x = c * (1f - abs((hue / DEGREES_PER_SECTOR).mod(2f) - 1f))
        val m = light - c / 2f
        val (r, g, b) =
            when ((hue / DEGREES_PER_SECTOR).toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
        val channel = { v: Float -> ((v + m) * CHANNEL).toInt().coerceIn(0, CHANNEL) }
        return rgb(channel(r), channel(g), channel(b))
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = (OPAQUE shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b

    private fun luminance(argb: Int): Double {
        val (r, g, b) = channels(argb)
        val linear = { v: Int ->
            val c = v / CHANNEL.toDouble()
            if (c <= SRGB_KNEE) c / SRGB_LINEAR_SLOPE else Math.pow((c + SRGB_OFFSET) / (1 + SRGB_OFFSET), SRGB_GAMMA)
        }
        return RED_WEIGHT * linear(r) + GREEN_WEIGHT * linear(g) + BLUE_WEIGHT * linear(b)
    }

    private const val CHANNEL = 0xFF
    private const val OPAQUE = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val MIN_ALPHA = 128

    // Pixels group into 8 levels per channel before the main colour is chosen.
    private const val BUCKET_SHIFT = 5
    private const val BUCKET_RED = 6
    private const val BUCKET_GREEN = 3

    private const val MIN_SHARE = 0.01
    private const val MIN_CHROMA = 0.12f
    private const val MIN_LIGHTNESS = 0.1f
    private const val MAX_LIGHTNESS = 0.9f
    private const val SHARE_WEIGHT = 70.0
    private const val CHROMA_WEIGHT = 20.0

    private const val SECTORS = 6f
    private const val DEGREES_PER_SECTOR = 60f

    private const val CONTRAST_OFFSET = 0.05
    private const val SRGB_KNEE = 0.04045
    private const val SRGB_LINEAR_SLOPE = 12.92
    private const val SRGB_OFFSET = 0.055
    private const val SRGB_GAMMA = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
}
