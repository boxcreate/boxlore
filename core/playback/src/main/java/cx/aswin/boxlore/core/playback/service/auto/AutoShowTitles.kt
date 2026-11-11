package cx.aswin.boxlore.core.playback.service.auto

/**
 * Fits a show's name on the two lines of a car tile.
 *
 * Android Auto draws a tile's title on one line and cuts it with an ellipsis; only the second line
 * is left for more text. The title keeps the whole name, because the car also uses it as the
 * heading of the show's own screen. When the name is too wide for the first line, the second line
 * carries on from the word where it is cut, so "The Rest Is Hi…" is followed by "History".
 */
internal object AutoShowTitles {
    /**
     * Width of a tile's first line, in the units of [widthOf]. Set from a 1280 by 720 car screen,
     * where "Darknet Diaries" is shown whole and "The Desi Crime Podcast" is cut to "The Desi Crim…".
     */
    private const val LINE_WIDTH = 7.15
    private const val ELLIPSIS_WIDTH = 0.6
    private const val DEFAULT_LOWER = 0.56
    private const val DEFAULT_UPPER = 0.68
    private const val DEFAULT_OTHER = 0.55

    /** Approximate letter widths of the car's font, as a fraction of its size. */
    private val WIDTHS: Map<Char, Double> = buildMap {
        "il".forEach { put(it, 0.25) }
        "j.,'’:;!|".forEach { put(it, 0.25) }
        put(' ', 0.25)
        put('I', 0.27)
        "ft".forEach { put(it, 0.34) }
        put('r', 0.37)
        "cksvxyz".forEach { put(it, 0.52) }
        put('w', 0.78)
        put('m', 0.88)
        "MW".forEach { put(it, 0.9) }
    }

    /** The second line for [name]: the words from the one the first line is cut in, or null when the name fits. */
    fun secondLine(name: String): String? {
        val text = name.trim().replace(Regex("\\s+"), " ")
        if (widthOf(text) <= LINE_WIDTH) return null
        // The first character the car hides: everything up to it, plus the ellipsis, still fits.
        var width = 0.0
        val firstHidden = text.indices.firstOrNull { index ->
            width += widthOf(text[index])
            width + ELLIPSIS_WIDTH > LINE_WIDTH
        } ?: return null
        val wordStart = text.lastIndexOf(' ', firstHidden) + 1
        // A cut inside the first word leaves nothing shorter to carry over.
        return text.substring(wordStart).takeIf { wordStart > 0 }
    }

    private fun widthOf(text: String): Double = text.sumOf(::widthOf)

    private fun widthOf(char: Char): Double = WIDTHS[char] ?: when {
        char.isLowerCase() -> DEFAULT_LOWER
        char.isUpperCase() -> DEFAULT_UPPER
        else -> DEFAULT_OTHER
    }
}
