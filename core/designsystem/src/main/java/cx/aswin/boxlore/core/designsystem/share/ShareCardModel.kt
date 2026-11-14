package cx.aswin.boxlore.core.designsystem.share

import com.materialkolor.hct.Hct
import cx.aswin.boxlore.core.designsystem.theme.artworkHueForDarkTone
import cx.aswin.boxlore.core.model.ShareTarget

/** boxlore indigo: the card's colour when the artwork has no colour of its own (black-and-white covers). */
internal const val ShareCardFallbackSeed = 0xFF573DF5.toInt()

/**
 * The player card's colours, all from one tonal family of the artwork's seed: a deep ground, a
 * tinted card, a bright accent for the play button and the boxlore bar, and light text.
 */
internal data class ShareCardColors(
    val ground: Int,
    val card: Int,
    val track: Int,
    val accent: Int,
    val onAccent: Int,
    val thumb: Int,
    val title: Int,
    val subtitle: Int,
)

private const val CardChroma = 48.0
private const val AccentChroma = 40.0

/** [seed] is the artwork's colour ([cx.aswin.boxlore.core.designsystem.theme.selectArtworkSeed]); null falls back to boxlore indigo. */
internal fun shareCardColors(seed: Int?): ShareCardColors {
    val hct = Hct.fromInt(seed ?: ShareCardFallbackSeed)
    val chroma = hct.chroma.coerceAtLeast(CardChroma)
    // Yellow and olive hues turn muddy when dark; the shared helper leans them towards amber.
    val darkHue = artworkHueForDarkTone(hct.hue)
    fun dark(tone: Double) = Hct.from(darkHue, chroma, tone).toInt()
    fun light(tone: Double) = Hct.from(hct.hue, chroma.coerceAtMost(AccentChroma), tone).toInt()
    return ShareCardColors(
        ground = dark(10.0),
        card = dark(20.0),
        track = dark(40.0),
        accent = light(80.0),
        onAccent = dark(15.0),
        thumb = light(92.0),
        title = light(97.0),
        subtitle = light(82.0),
    )
}

/** A rectangle in card pixels; plain numbers so layouts can be checked without Android graphics. */
internal data class ShareBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
}

/**
 * Where everything sits on one card size. The card's top corners sweep ([cardTopRadius]) and meet
 * the boxlore bar with small corners; the bar's bottom corners sweep again.
 */
internal data class ShareCardLayout(
    val width: Int,
    val height: Int,
    val card: ShareBox,
    val cardTopRadius: Float,
    val joinRadius: Float,
    val artwork: ShareBox,
    val artworkRadius: Float,
    val text: ShareBox,
    /** True when the text sits beside the artwork and is centred vertically in [text]. */
    val textBeside: Boolean,
    val titleSizes: List<Float>,
    val titleMaxLines: Int,
    val subtitleSize: Float,
    val momentSize: Float,
    val slider: ShareBox,
    val play: ShareBox,
    val bar: ShareBox,
    val barBottomRadius: Float,
    val labelSize: Float,
    val logoWidth: Float,
)

/** Instagram draws its reply bar and buttons over the bottom of a story; nothing important goes below this. */
internal const val StorySafeBottom = 1_600f

internal fun shareCardLayout(target: ShareTarget): ShareCardLayout = when (target) {
    ShareTarget.MESSAGE -> ShareCardLayout(
        width = 1_200,
        height = 1_200,
        card = ShareBox(56f, 56f, 1_088f, 904f),
        cardTopRadius = 84f,
        joinRadius = 28f,
        artwork = ShareBox(112f, 112f, 560f, 560f),
        artworkRadius = 56f,
        text = ShareBox(720f, 112f, 368f, 560f),
        textBeside = true,
        titleSizes = listOf(56f, 50f, 46f, 42f, 40f),
        titleMaxLines = 6,
        subtitleSize = 32f,
        momentSize = 30f,
        slider = ShareBox(112f, 784f, 720f, 64f),
        play = ShareBox(880f, 716f, 200f, 200f),
        bar = ShareBox(56f, 976f, 1_088f, 168f),
        barBottomRadius = 84f,
        labelSize = 40f,
        logoWidth = 340f,
    )
    ShareTarget.INSTAGRAM_STORY -> ShareCardLayout(
        width = 1_080,
        height = 1_920,
        card = ShareBox(60f, 160f, 960f, 1_260f),
        cardTopRadius = 104f,
        joinRadius = 32f,
        artwork = ShareBox(200f, 236f, 680f, 680f),
        artworkRadius = 60f,
        text = ShareBox(150f, 952f, 780f, 264f),
        textBeside = false,
        titleSizes = listOf(62f, 56f, 52f, 48f),
        titleMaxLines = 2,
        subtitleSize = 38f,
        momentSize = 32f,
        slider = ShareBox(150f, 1_283f, 570f, 64f),
        play = ShareBox(760f, 1_230f, 170f, 170f),
        bar = ShareBox(60f, 1_436f, 960f, 164f),
        barBottomRadius = 82f,
        labelSize = 42f,
        logoWidth = 350f,
    )
}

/** The text on a card: the title, who it is by, and an optional start time or clip. */
data class ShareCardText(val title: String, val subtitle: String, val moment: String? = null)

/**
 * A title size that fits: the largest of [sizes] whose text fits ([fits] gets the size and the
 * lines it may use). Past the smallest size the title is cut with an ellipsis at that size.
 */
internal fun fitTitleSize(sizes: List<Float>, fits: (Float) -> Boolean): Float = sizes.firstOrNull(fits) ?: sizes.last()

/** What part of an episode a share points at. */
sealed interface ShareMoment {
    data object Whole : ShareMoment
    data class StartsAt(val ms: Long) : ShareMoment
    data class Clip(val startMs: Long, val endMs: Long) : ShareMoment
}

/** A link to the show can't start at a time or play a clip, so it shares the whole thing. */
fun shareMoment(opensShow: Boolean, timestampMs: Long?, startMs: Long?, endMs: Long?): ShareMoment = when {
    opensShow -> ShareMoment.Whole
    startMs != null && endMs != null && endMs > startMs -> ShareMoment.Clip(startMs, endMs)
    timestampMs != null && timestampMs > 0 -> ShareMoment.StartsAt(timestampMs)
    else -> ShareMoment.Whole
}

/** Where the slider's thumb sits when nothing says otherwise: early, so the wavy played part shows. */
internal const val DefaultShareProgress = 0.38f

/** The slider's thumb: the shared position within the episode when both are known, else [DefaultShareProgress]. */
internal fun shareProgress(moment: ShareMoment, durationSeconds: Int): Float {
    val durationMs = durationSeconds * 1_000L
    val atMs = when (moment) {
        is ShareMoment.StartsAt -> moment.ms
        is ShareMoment.Clip -> moment.startMs
        ShareMoment.Whole -> null
    }
    if (atMs == null || durationMs <= 0L) return DefaultShareProgress
    return (atMs.toFloat() / durationMs).coerceIn(0.06f, 0.94f)
}

/** 1:05:09 or 4:07. */
fun formatShareTime(ms: Long): String {
    val totalSecs = (ms / 1_000).coerceAtLeast(0)
    val hours = totalSecs / 3_600
    val minutes = (totalSecs % 3_600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/**
 * The message sent with the card: the name, who it is by, the start time when there is one, and
 * the link. No sales line: the card and the link's preview already say boxlore.
 */
fun shareMessage(title: String, subtitle: String, moment: String?, url: String): String {
    val name = if (subtitle.isBlank()) title else "$title · $subtitle"
    return listOfNotNull(name, moment, url).joinToString("\n")
}

/**
 * How big the card preview in the share sheet is, from the window's height. Short windows (a phone
 * on its side, a small split-screen pane) leave it out, so the share buttons stay on screen.
 */
fun sharePreviewSizeDp(windowHeightDp: Float): Int = when {
    windowHeightDp >= 760f -> 240
    windowHeightDp >= 600f -> 176
    else -> 0
}
