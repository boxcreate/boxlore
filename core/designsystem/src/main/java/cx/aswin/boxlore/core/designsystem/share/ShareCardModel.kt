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
 * the boxlore bar with small corners; the bar's bottom corners sweep again. The words start at the
 * top of [text], the title's capitals level with the artwork's top edge.
 */
internal data class ShareCardLayout(
    val width: Int,
    val height: Int,
    val card: ShareBox,
    val cardTopRadius: Float,
    val joinRadius: Float,
    val artwork: ShareBox,
    val artworkRadius: Float,
    /** The words: title, who it is by, the start time and the details line, top to bottom. */
    val text: ShareBox,
    val titleSizes: List<Float>,
    val titleMaxLines: Int,
    val subtitleSize: Float,
    val momentSize: Float,
    val detailsSize: Float,
    /** The square's narrow column lets the details line wrap once rather than cut the date. */
    val detailsMaxLines: Int,
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
    // Square: the words top the column beside the artwork and the play button closes it, level
    // with the artwork's bottom edge; the slider runs the full width underneath.
    ShareTarget.MESSAGE -> ShareCardLayout(
        width = 1_200,
        height = 1_200,
        card = ShareBox(56f, 56f, 1_088f, 904f),
        cardTopRadius = 84f,
        joinRadius = 28f,
        artwork = ShareBox(112f, 112f, 600f, 600f),
        artworkRadius = 56f,
        text = ShareBox(760f, 112f, 328f, 376f),
        titleSizes = listOf(56f, 50f, 46f, 42f, 40f),
        titleMaxLines = 4,
        subtitleSize = 32f,
        momentSize = 34f,
        detailsSize = 32f,
        detailsMaxLines = 2,
        slider = ShareBox(112f, 772f, 976f, 64f),
        play = ShareBox(888f, 512f, 200f, 200f),
        bar = ShareBox(56f, 976f, 1_088f, 168f),
        barBottomRadius = 84f,
        labelSize = 40f,
        logoWidth = 340f,
    )
    // Story: one column from the artwork down to the play button, all above Instagram's controls.
    ShareTarget.INSTAGRAM_STORY -> ShareCardLayout(
        width = 1_080,
        height = 1_920,
        card = ShareBox(60f, 120f, 960f, 1_356f),
        cardTopRadius = 104f,
        joinRadius = 32f,
        artwork = ShareBox(180f, 210f, 720f, 720f),
        artworkRadius = 64f,
        text = ShareBox(180f, 970f, 720f, 290f),
        titleSizes = listOf(62f, 56f, 52f, 48f),
        titleMaxLines = 2,
        subtitleSize = 38f,
        momentSize = 36f,
        detailsSize = 34f,
        detailsMaxLines = 1,
        slider = ShareBox(180f, 1_313f, 540f, 64f),
        play = ShareBox(750f, 1_270f, 150f, 150f),
        bar = ShareBox(60f, 1_488f, 960f, 112f),
        barBottomRadius = 82f,
        labelSize = 38f,
        logoWidth = 300f,
    )
}

/**
 * The text on a card: the title, who it is by, an optional start time or clip, and an optional
 * details line ([shareEpisodeDetails], [sharePodcastDetails]).
 */
data class ShareCardText(val title: String, val subtitle: String, val moment: String? = null, val details: String? = null)

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
 * How big the card preview in the share sheet is, from the height the sheet or panel can give its
 * content. The card shows only when the start-time switch and every share action still fit with it;
 * otherwise the sheet shows the name row, so Copy link and Instagram Story stay on screen on a
 * sideways phone, a foldable's cover screen or the lower half of a half-open fold.
 */
fun sharePreviewSizeDp(availableHeightDp: Float): Int = when {
    availableHeightDp >= SHARE_SHEET_WITHOUT_CARD_DP + LARGE_SHARE_PREVIEW_DP -> LARGE_SHARE_PREVIEW_DP
    availableHeightDp >= SHARE_SHEET_WITHOUT_CARD_DP + SMALL_SHARE_PREVIEW_DP -> SMALL_SHARE_PREVIEW_DP
    else -> 0
}

private const val LARGE_SHARE_PREVIEW_DP = 240
private const val SMALL_SHARE_PREVIEW_DP = 176

/** The share sheet's own height without the card: its heading, the start-time switch and the actions. */
private const val SHARE_SHEET_WITHOUT_CARD_DP = 440f
