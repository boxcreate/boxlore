package cx.aswin.boxlore.core.designsystem.echo

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Recolours Echo's accent parts (ear cups, boots, emblem, accent props: the blue-violet range of the
 * frames) to follow [accent], in place. Hue comes from the theme; the tone is calmed (saturation capped,
 * a deeper tone than the theme colour) and each pixel keeps its own shading, so the parts still read as
 * 3D. White, black, the glass and props in other colours are untouched.
 */
fun tintEchoAccents(pixels: IntArray, accent: Int, darkTheme: Boolean) {
    val target = FloatArray(3).also { rgbToHsl(accent shr 16 and 0xFF, accent shr 8 and 0xFF, accent and 0xFF, it) }
    val hue = target[0]
    val saturation = min(target[1], EchoAccentMaxSaturation)
    val tone = if (darkTheme) EchoAccentToneDark else EchoAccentToneLight
    val hsl = FloatArray(3)
    for (i in pixels.indices) {
        if (pixels[i] ushr 24 != 0) pixels[i] = tintedAccentPixel(pixels[i], hue, saturation, tone, hsl)
    }
}

/** One visible pixel, moved toward the theme hue by how much it belongs to the accent range; others come back unchanged. */
private fun tintedAccentPixel(argb: Int, hue: Float, saturation: Float, tone: Float, hsl: FloatArray): Int {
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    val k = echoAccentWeight(r, g, b)
    if (k <= 0f) return argb
    rgbToHsl(r, g, b, hsl)
    val lightness = (tone + (hsl[2] - EchoAccentBaseTone) * EchoShadingKeep).coerceIn(0.04f, 0.96f)
    val out = hslToRgb(hue, saturation, lightness)
    val nr = (r + (out shr 16 and 0xFF) * k - r * k).toInt().coerceIn(0, 255)
    val ng = (g + (out shr 8 and 0xFF) * k - g * k).toInt().coerceIn(0, 255)
    val nb = (b + (out and 0xFF) * k - b * k).toInt().coerceIn(0, 255)
    return (argb and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
}

/** Colour of the face's lit dots: the theme hue, light and softly saturated, like light behind glass. */
fun echoFaceColor(accent: Int): Int {
    val hsl = FloatArray(3).also { rgbToHsl(accent shr 16 and 0xFF, accent shr 8 and 0xFF, accent and 0xFF, it) }
    return hslToRgb(hsl[0], min(hsl[1], 0.75f), 0.80f) or (0xFF shl 24)
}

/** 0..1 membership of Echo's blue-violet accent range (HSV hue around 247°, enough saturation and value). */
fun echoAccentWeight(r: Int, g: Int, b: Int): Float {
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    if (hi == 0) return 0f
    val s = (hi - lo).toFloat() / hi
    val v = hi / 255f
    if (s < 0.235f || v < 0.157f) return 0f
    val hue = hueDegrees(r, g, b, hi, lo)
    val d = abs(hue - EchoAccentHue).let { min(it, 360f - it) }
    if (d > 31f) return 0f
    return min(1f, (31f - d) / 11.3f) * min(1f, (s - 0.235f) / 0.157f)
}

private fun hueDegrees(r: Int, g: Int, b: Int, hi: Int, lo: Int): Float {
    val c = (hi - lo).toFloat()
    if (c == 0f) return 0f
    val h = when (hi) {
        r -> ((g - b) / c).mod(6f)
        g -> (b - r) / c + 2f
        else -> (r - g) / c + 4f
    }
    return h * 60f
}

private fun rgbToHsl(r: Int, g: Int, b: Int, out: FloatArray) {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val hi = max(rf, max(gf, bf))
    val lo = min(rf, min(gf, bf))
    val l = (hi + lo) / 2f
    val d = hi - lo
    out[2] = l
    if (d == 0f) {
        out[0] = 0f
        out[1] = 0f
        return
    }
    out[1] = if (l > 0.5f) d / (2f - hi - lo) else d / (hi + lo)
    out[0] = hueDegrees(r, g, b, max(r, max(g, b)), min(r, min(g, b)))
}

private fun hslToRgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
    val m = l - c / 2f
    val (r, g, b) = when ((h.mod(360f) / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

private const val EchoAccentHue = 247f
private const val EchoAccentBaseTone = 0.52f
private const val EchoShadingKeep = 0.9f
private const val EchoAccentMaxSaturation = 0.38f
private const val EchoAccentToneLight = 0.44f
private const val EchoAccentToneDark = 0.56f
