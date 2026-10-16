package cx.aswin.boxlore.feature.onboarding

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A measured block of text: its line count and height in px. */
internal data class PrismTextBox(val lines: Int, val height: Int)

/** The room the card is given, in px, and the screen's density and text scale. */
internal data class PrismRoom(val widthPx: Int, val heightPx: Int, val density: Float, val fontScale: Float)

/** Measures the card's words; the card backs it with a TextMeasurer, tests with a stand-in. */
internal interface PrismTextMeasure {
    /** The name in the card's display capitals at [sizeSp], wrapped to [widthPx]. */
    fun title(text: String, sizeSp: Float, widthPx: Int): PrismTextBox

    /** The pitch at [sizeSp], wrapped to [widthPx]. */
    fun hook(text: String, sizeSp: Float, widthPx: Int): PrismTextBox
}

/**
 * How one Prism card is laid out, in px: stacked (tilted art on top, name, pitch) or, when the art
 * would get too small, compact (art beside the tag and name, pitch underneath).
 */
internal data class PrismPlan(
    val compact: Boolean,
    val width: Int,
    val art: Int,
    val titleSp: Float,
    val titleLines: Int,
    val showPublisher: Boolean,
    val height: Int,
    val hookSp: Float = EchoPrismLayout.HookSp,
)

/**
 * Sizes the Prism card from the room it is given. The words are measured first and always shown
 * whole: the name keeps its size while it fits in two lines, then steps down, and only past that
 * takes three lines (ending in "…" only for an extreme name); the pitch is never cut. The square
 * art takes the height that is left, and the card narrows to hug it, so there is no empty foil at
 * its sides. The publisher only shows beside a short name. All lengths below are in dp.
 */
internal object EchoPrismLayout {
    const val Border = 3f
    const val Padding = 16f
    const val MaxWidth = 420f
    const val MinWidth = 280f
    const val MinStackedArt = 150f

    /** Room above and below the art for its 2° tilt, and at each side. */
    const val TiltClearance = 4f
    const val SideClearance = 9f
    const val TitleGap = 10f
    const val PublisherGap = 6f
    const val PublisherLineSp = 14f
    const val HookGap = 12f
    const val HookPaddingH = 14f
    const val HookPaddingV = 10f
    const val HookBorder = 1f
    const val HookSp = 14f

    /** Only the tightest slots (tiny windows, very large text) set the pitch smaller, never below this. */
    private val CompactHookSizes = listOf(14f, 13f, 12f, 11f)

    /** Foil left either side of the art once the card hugs it. */
    const val SnugExtra = 44f
    const val CompactGap = 14f
    const val CompactMaxArt = 124f
    const val CompactMinArt = 72f
    const val CompactArtStep = 8f
    const val TagMinHeight = 24f
    const val TagGap = 8f
    const val TagLineSp = 14f

    /** The name may show the publisher beside it only at this size or above (or on one line). */
    const val PublisherMinSp = 22f
    const val MaxTitleLines = 3
    private const val HugPasses = 3

    private val StackedSizes = listOf(24f, 23f, 22f, 21f, 20f, 19f, 18f)
    private val StackedSmallSizes = listOf(18f, 17f, 16f)
    private val CompactSizes = listOf(18f, 17f, 16f, 15f)
    private val CompactSmallSizes = listOf(15f, 14f, 13f)

    data class TitleFit(val sizeSp: Float, val box: PrismTextBox, val publisher: Boolean)

    fun fitTitle(text: String, widthPx: Int, compact: Boolean, measure: PrismTextMeasure): TitleFit {
        for (size in if (compact) CompactSizes else StackedSizes) {
            val box = measure.title(text, size, widthPx)
            if (box.lines <= 2) return TitleFit(size, box, !compact && (box.lines == 1 || size >= PublisherMinSp))
        }
        val small = if (compact) CompactSmallSizes else StackedSmallSizes
        for (size in small) {
            val box = measure.title(text, size, widthPx)
            if (box.lines <= MaxTitleLines) return TitleFit(size, box, false)
        }
        val box = measure.title(text, small.last(), widthPx)
        return TitleFit(small.last(), PrismTextBox(MaxTitleLines, box.height * MaxTitleLines / box.lines.coerceAtLeast(1)), false)
    }

    /**
     * The [room]'s density converts dp to px and its text scale sizes the publisher and tag lines;
     * the measured name and pitch already include it.
     */
    fun plan(title: String, hook: String, hasPublisher: Boolean, room: PrismRoom, measure: PrismTextMeasure): PrismPlan {
        val px = Px(room.density, room.fontScale)
        val maxWidthPx = room.widthPx
        val maxHeightPx = room.heightPx
        val maxWidth = min(maxWidthPx, px.dp(MaxWidth))
        var plan = stacked(title, hook, hasPublisher, maxWidth, maxHeightPx, px, measure)
        // Narrowing the card can wrap the words onto more lines and shrink the art again, so it
        // hugs a few times; it stops before the art would drop below the stacked minimum.
        repeat(HugPasses) {
            val snug = max(min(maxWidth, px.dp(MinWidth)), plan.art + px.dp(SideClearance * 2 + (Border + Padding) * 2 + SnugExtra))
            if (snug >= plan.width) return@repeat
            val hugged = stacked(title, hook, hasPublisher, snug, maxHeightPx, px, measure)
            if (hugged.art < px.dp(MinStackedArt)) return@repeat
            plan = hugged
        }
        return if (plan.art >= px.dp(MinStackedArt)) plan else compact(title, hook, maxWidth, maxHeightPx, px, measure)
    }

    private fun stacked(title: String, hook: String, hasPublisher: Boolean, width: Int, maxHeight: Int, px: Px, measure: PrismTextMeasure): PrismPlan {
        val inner = width - px.dp((Border + Padding) * 2)
        val fit = fitTitle(title, inner, compact = false, measure)
        val publisher = hasPublisher && fit.publisher
        val words = px.dp(TiltClearance + TitleGap) + fit.box.height +
            (if (publisher) px.dp(PublisherGap) + px.sp(PublisherLineSp) else 0) +
            px.dp(HookGap) + hookBox(hook, HookSp, inner, px, measure)
        val frame = px.dp((Border + Padding) * 2 + TiltClearance)
        val art = min(inner - px.dp(SideClearance * 2), maxHeight - frame - words)
        return PrismPlan(false, width, art, fit.sizeSp, fit.box.lines, publisher, frame + art + words)
    }

    /**
     * Art beside the tag and name, the pitch underneath. When even that is too tall, the art
     * shrinks, then the pitch steps down a size or two, and as a last resort the art goes; the
     * pitch is never cut.
     */
    private fun compact(title: String, hook: String, width: Int, maxHeight: Int, px: Px, measure: PrismTextMeasure): PrismPlan {
        val inner = width - px.dp((Border + Padding) * 2)
        val tag = max(px.dp(TagMinHeight), px.sp(TagLineSp) + px.dp(8f))
        val arts = generateSequence(CompactMaxArt) { (it - CompactArtStep).takeIf { next -> next >= CompactMinArt } }.toList() + 0f
        var plan: PrismPlan? = null
        for (hookSp in CompactHookSizes) {
            val hookHeight = px.dp(HookGap) + hookBox(hook, hookSp, inner, px, measure)
            for (art in arts) {
                val artPx = px.dp(art)
                val gap = if (artPx > 0) px.dp(CompactGap) else 0
                val fit = fitTitle(title, inner - artPx - gap, compact = true, measure)
                val row = max(artPx, tag + px.dp(TagGap) + fit.box.height)
                plan = PrismPlan(true, width, artPx, fit.sizeSp, fit.box.lines, false, px.dp((Border + Padding) * 2) + row + hookHeight, hookSp)
                if (plan.height <= maxHeight) return plan
                // Dropping the art is the last resort, only once the pitch is at its smallest.
                if (art == CompactMinArt && hookSp != CompactHookSizes.last()) break
            }
        }
        return plan!!
    }

    private fun hookBox(hook: String, sizeSp: Float, inner: Int, px: Px, measure: PrismTextMeasure): Int =
        measure.hook(hook, sizeSp, inner - px.dp((HookPaddingH + HookBorder) * 2)).height + px.dp((HookPaddingV + HookBorder) * 2)

    private class Px(val density: Float, val fontScale: Float) {
        fun dp(value: Float): Int = (value * density).roundToInt()
        fun sp(value: Float): Int = (value * density * fontScale).roundToInt()
    }
}
