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
    /** False only on the shortest slots, where the pitch gives way so the name and art still show whole. */
    val showHook: Boolean = true,
    /** True only for a slot smaller than any card with the whole name: no art, no pitch, and the name ends in "…". */
    val titleCut: Boolean = false,
    /** False only on a slot too short for the tag and a line of the name: the name keeps the room. */
    val showTag: Boolean = true,
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
    private val CompactTightSizes = listOf(15f, 14f, 13f, 12f)

    /** Only for a name that would otherwise be cut, alone on the card. */
    private val LastResortSizes = listOf(11f, 10f)

    /** [truncated] is set when the name needs more than [MaxTitleLines] lines even at the smallest size. */
    data class TitleFit(val sizeSp: Float, val box: PrismTextBox, val publisher: Boolean, val truncated: Boolean = false)

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
        return TitleFit(small.last(), PrismTextBox(MaxTitleLines, box.height * MaxTitleLines / box.lines.coerceAtLeast(1)), false, truncated = true)
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
            if (hugged.art < px.dp(MinStackedArt) || hugged.titleCut) return@repeat
            plan = hugged
        }
        // A name the stacked card would cut goes to the compact card, which can give it the full width.
        return if (plan.art >= px.dp(MinStackedArt) && !plan.titleCut) plan else compact(title, hook, maxWidth, maxHeightPx, px, measure)
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
        return PrismPlan(false, width, art, fit.sizeSp, fit.box.lines, publisher, frame + art + words, titleCut = fit.truncated)
    }

    /**
     * Art beside the tag and name, the pitch underneath. The name is never cut while anything else
     * can give way: when it would need a fourth line, the art shrinks and then goes to give it room,
     * and then the name steps down to 12sp. When the card is too tall, the art shrinks, then the
     * pitch steps down a size or two, and then the art goes; the pitch is never cut. On slots
     * shorter still the pitch is left out, so the card is never taller than its slot. Only a card
     * smaller than any whole name (no art, no pitch) ends the name in "…".
     */
    private fun compact(title: String, hook: String, width: Int, maxHeight: Int, px: Px, measure: PrismTextMeasure): PrismPlan {
        val card = CompactCard(title, width, maxHeight, px, measure)
        for (tight in listOf(false, true)) {
            for (hookSp in CompactHookSizes) {
                card.withPitch(hook, hookSp, tight)?.let { return it }
            }
        }
        return card.bare()
    }

    /** The compact card's sizes for one name in one slot, and its plans with and without the pitch. */
    private class CompactCard(val title: String, val width: Int, val maxHeight: Int, val px: Px, val measure: PrismTextMeasure) {
        val inner = width - px.dp((Border + Padding) * 2)
        val tag = max(px.dp(TagMinHeight), px.sp(TagLineSp) + px.dp(8f))
        val frame = px.dp((Border + Padding) * 2)

        // The steps down from the largest art end on the smallest even when the step skips past it, then no art.
        private val arts = (generateSequence(CompactMaxArt) { (it - CompactArtStep).takeIf { next -> next >= CompactMinArt } }.toList() + CompactMinArt).distinct() + 0f

        fun nameFit(artPx: Int, tight: Boolean): TitleFit {
            val nameWidth = inner - artPx - (if (artPx > 0) px.dp(CompactGap) else 0)
            return if (tight) fitTightTitle(title, nameWidth, measure) else fitTitle(title, nameWidth, compact = true, measure)
        }

        /**
         * The first art size, largest first, whose plan with the pitch at [hookSp] fits; a cut name
         * is never kept (a smaller art gives it the room). The art only goes once the pitch is at
         * its smallest, unless the name needs the room.
         */
        fun withPitch(hook: String, hookSp: Float, tight: Boolean): PrismPlan? {
            val hookHeight = px.dp(HookGap) + hookBox(hook, hookSp, inner, px, measure)
            val lastPitch = hookSp == CompactHookSizes.last()
            for (art in arts) {
                val artPx = px.dp(art)
                val fit = nameFit(artPx, tight)
                val plan = PrismPlan(true, width, artPx, fit.sizeSp, fit.box.lines, false, frame + row(artPx, fit.box.height) + hookHeight, hookSp)
                val tooTallAtSmallestArt = art == CompactMinArt && !lastPitch && plan.height > maxHeight
                when {
                    !fit.truncated && plan.height <= maxHeight -> return plan
                    tooTallAtSmallestArt && !fit.truncated -> return null
                }
            }
            return null
        }

        /**
         * Without the pitch: the art at its smallest, then none, then the name alone a size or two
         * smaller; a slot smaller than any card with the whole name cuts it to the lines it has.
         */
        fun bare(): PrismPlan {
            for (art in listOf(CompactMinArt, 0f)) {
                val artPx = px.dp(art)
                val fit = nameFit(artPx, tight = true)
                val plan = PrismPlan(true, width, artPx, fit.sizeSp, fit.box.lines, false, frame + row(artPx, fit.box.height), CompactHookSizes.last(), showHook = false)
                if (plan.height <= maxHeight) return plan
            }
            for (size in LastResortSizes) {
                val box = measure.title(title, size, inner)
                val plan = PrismPlan(true, width, 0, size, box.lines, false, frame + row(0, box.height), CompactHookSizes.last(), showHook = false)
                if (plan.height <= maxHeight) return plan
            }
            val fit = nameFit(0, tight = true)
            val lineHeight = fit.box.height / fit.box.lines.coerceAtLeast(1)
            // The tag gives way too when it leaves no room for a line of the name.
            val showTag = frame + tag + px.dp(TagGap) + lineHeight <= maxHeight
            val chrome = frame + if (showTag) tag + px.dp(TagGap) else 0
            val lines = ((maxHeight - chrome) / lineHeight.coerceAtLeast(1)).coerceIn(1, fit.box.lines)
            return PrismPlan(true, width, 0, fit.sizeSp, lines, false, chrome + lineHeight * lines, CompactHookSizes.last(), showHook = false, titleCut = lines < fit.box.lines, showTag = showTag)
        }

        private fun row(artPx: Int, nameHeight: Int): Int = max(artPx, tag + px.dp(TagGap) + nameHeight)
    }

    /**
     * The name down to 12sp for the shortest slots: in at most three lines while a size allows it,
     * then whole at 12sp in as many lines as it needs.
     */
    private fun fitTightTitle(text: String, widthPx: Int, measure: PrismTextMeasure): TitleFit {
        for (size in CompactTightSizes) {
            val box = measure.title(text, size, widthPx)
            if (box.lines <= MaxTitleLines) return TitleFit(size, box, false)
        }
        return TitleFit(CompactTightSizes.last(), measure.title(text, CompactTightSizes.last(), widthPx), false)
    }

    private fun hookBox(hook: String, sizeSp: Float, inner: Int, px: Px, measure: PrismTextMeasure): Int =
        measure.hook(hook, sizeSp, inner - px.dp((HookPaddingH + HookBorder) * 2)).height + px.dp((HookPaddingV + HookBorder) * 2)

    private class Px(val density: Float, val fontScale: Float) {
        fun dp(value: Float): Int = (value * density).roundToInt()
        fun sp(value: Float): Int = (value * density * fontScale).roundToInt()
    }
}
