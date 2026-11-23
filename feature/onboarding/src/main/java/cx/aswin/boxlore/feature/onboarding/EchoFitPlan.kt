package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/** What fills the answer slot: the opening topic grid, rows of later choices, or something with its own height. */
internal enum class EchoAnswerKind { Topics, Rows, Other }

/** The answers a turn shows: [count] of [kind]. */
internal data class EchoAnswerShape(val kind: EchoAnswerKind, val count: Int)

/**
 * The text heights and widths the fit order needs. The screen measures them with the rendered styles
 * ([rememberEchoFitMeasure]); tests use fixed numbers.
 */
internal interface EchoFitMeasure {
    /**
     * The question area's height at [widthDp] wide: the question at the largest size from [maxSp] down
     * to [minSp] that keeps it to three lines (or at [minSp]), plus anything shown under it.
     */
    fun questionHeight(widthDp: Float, maxSp: Float, minSp: Float): Float

    /** Echo's speech bubble, at most [widthDp] wide, holding up to [lines] lines; 0 when Echo says nothing. */
    fun bubbleHeight(widthDp: Float, lines: Int): Float

    /** Whether every answer label fits [widthDp] in [maxLines] at the 12sp floor. */
    fun labelsFit(widthDp: Float, maxLines: Int): Boolean

    /** The question's type size at [widthDp] wide: the largest from [maxSp] down to [minSp] within three lines. */
    fun questionSize(widthDp: Float, maxSp: Float, minSp: Float): Float = maxSp

    /** Whether what Echo says fits its bubble at [widthDp] whole, in [lines] lines. */
    fun bubbleFits(widthDp: Float, lines: Int): Boolean = true
}

/**
 * The steps Echo's screen takes, in order, to fit without scrolling. [Today] is the tier layout as it
 * was; each later step is taken only when every earlier one would overflow.
 */
internal enum class EchoFitLevel { Today, Shrunk, NoIcons, Stacked, LastResort }

/**
 * How Echo's body fits: the sizes to draw with, whether answers keep their icons, how many topic
 * columns there are, whether Echo and the question stand beside the answers, and whether Something
 * else waits in the footer. [questionHeight] is the height the question gets before anything else.
 */
internal data class EchoFit(
    val level: EchoFitLevel,
    val metrics: EchoLayoutMetrics,
    val showIcons: Boolean = true,
    val columns: Int = EchoLayout.TopicColumns,
    val sideBySide: Boolean = false,
    val somethingElseInFooter: Boolean = false,
    val questionHeight: Dp = 0.dp,
    /** The answers' height before they grow into spare room; null when the slot keeps its own height. */
    val answersMinHeight: Dp? = null,
    /** Rows that may each grow a little into spare room. */
    val growRows: Int = EchoLayout.AnswerRows,
    /** Beside the answers, the share of the width Echo and the question take. */
    val startShare: Float = SideBySideStartShare,
    /** Lines a topic label may take: two only where one would be cut beside Echo. */
    val labelLines: Int = 1,
    val overflow: Boolean = false,
) {
    val somethingElseInline: Boolean get() = !somethingElseInFooter
}

/**
 * Echo's screen never scrolls. The question gets its full height first and the answers take the rest.
 * When they would overflow, the screen steps down in this order and stops at the first step that fits:
 * today's tier layout; shorter answers, then a smaller Echo, a shorter bubble and tighter gaps (never
 * under Compact's sizes or the 48dp touch target); answers without icons; then the answers as one
 * stack, beside Echo and the question on wide, short windows, with Something else moved into the
 * footer; and last, Echo folds away and the question takes its smallest size. Labels that can't fit
 * their tile at 12sp with an icon drop the icons first.
 *
 * [body] is the room Echo, the question and the answers share (before the keyboard), [tierHeight]
 * the height the tier table reads. The keyboard only folds Echo away; it never changes the step.
 */
internal fun echoFitPlan(
    body: DpSize,
    tierHeight: Dp,
    fontScale: Float,
    keyboardOpen: Boolean,
    answers: EchoAnswerShape,
    measure: EchoFitMeasure,
): EchoFit {
    val planner = EchoFitPlanner(body.width.value, body.height.value, fontScale, answers, measure)
    val base = EchoLayout.metrics(tierHeight, fontScale, keyboardOpen = false)
    // Larger text drops a tier, which also cuts Echo's bubble to two lines; a third comes back when it fits.
    val droppedForText = EchoLayout.tier(tierHeight, fontScale) != EchoLayout.tier(tierHeight, 1f)
    val fit = planner.plan(base, roomierBubble = droppedForText && fontScale >= EchoLayout.LargeFontScale)
    return if (keyboardOpen) fit.copy(metrics = fit.metrics.copy(showEcho = false)) else fit
}

private class EchoFitPlanner(
    private val width: Float,
    private val height: Float,
    private val fontScale: Float,
    private val answers: EchoAnswerShape,
    private val measure: EchoFitMeasure,
) {
    private val topics = answers.kind == EchoAnswerKind.Topics
    private val sized = answers.kind != EchoAnswerKind.Other

    /** Topics stand two to a row, later choices one. */
    private val columns = if (topics) EchoLayout.TopicColumns else 1

    fun plan(base: EchoLayoutMetrics, roomierBubble: Boolean = false): EchoFit {
        if (!sized) return EchoFit(EchoFitLevel.Today, base, questionHeight = questionHeight(base, width).dp, answersMinHeight = null)
        if (roomierBubble && base.showEcho && base.bubbleLines < RoomyBubbleLines) {
            // Today's layout or shorter answers, with a three-line bubble, before anything else.
            val roomy = base.copy(bubbleLines = RoomyBubbleLines)
            (listOf(EchoFitLevel.Today to roomy) + echoAnswerSteps(topics, roomy).map { EchoFitLevel.Shrunk to it }).forEach { (level, metrics) ->
                accept(level, metrics)?.let { return it }
            }
        }
        // Today's tier, then each shrink step in order.
        val steps = shrinkSteps(base)
        val shrunk = steps.lastOrNull() ?: base
        (listOf(EchoFitLevel.Today to base) + steps.map { EchoFitLevel.Shrunk to it }).forEach { (level, metrics) ->
            accept(level, metrics, shrunk)?.let { return it }
        }
        if (topics && !labelsFit(width, columns, showIcons = false, shrunk)) {
            stackedColumn(shrunk)?.let { return it }
        }
        return stacked(base, shrunk) ?: lastResort(shrunk)
    }

    /**
     * Shorter answers through the smaller tiers' tiles, then the smaller tiers' Echo, then a two- and
     * one-line bubble, then the 48dp floor with Compact's gaps.
     */
    private fun shrinkSteps(base: EchoLayoutMetrics): List<EchoLayoutMetrics> {
        val steps = mutableListOf<EchoLayoutMetrics>()
        var current = base
        fun step(next: EchoLayoutMetrics) {
            if (next != current) {
                steps += next
                current = next
            }
        }
        if (topics) {
            TileSteps.filter { it.height < current.tileHeight }.forEach { tile ->
                step(current.copy(tileHeight = tile.height, tileStacked = tile.stacked, badgeSize = tile.badge))
            }
        } else {
            RowSteps.filter { it.height < current.rowHeight }.forEach { row -> step(current.copy(rowHeight = row.height, badgeSize = row.badge)) }
        }
        if (current.showEcho) {
            AvatarSteps.filter { it < current.avatarWidth }.forEach { avatar -> step(current.copy(avatarWidth = avatar)) }
            (current.bubbleLines - 1 downTo 1).forEach { lines -> step(current.copy(bubbleLines = lines)) }
        }
        step(current.copy(tileHeight = minOf(current.tileHeight, TouchFloor), tileStacked = false, rowHeight = minOf(current.rowHeight, TouchFloor), gap = minOf(current.gap, CompactGap)))
        return steps
    }

    /**
     * The fit for [metrics] in today's columns, or null when it overflows. Labels too long for their
     * icons drop them; topic labels too long for two columns even without icons stand one to a row
     * instead (at this step, else at the last), and only where that doesn't fit do they shrink to 12sp.
     */
    private fun accept(level: EchoFitLevel, metrics: EchoLayoutMetrics, shrunk: EchoLayoutMetrics = metrics): EchoFit? {
        val question = questionHeight(metrics, width)
        val minAnswers = answersMinHeight(metrics, columns, inline = true)
        if (columnHeight(metrics, width, question) + minAnswers > height) return null
        val icons = labelsFit(width, columns, showIcons = true, metrics)
        if (!icons && topics && !labelsFit(width, columns, showIcons = false, metrics)) {
            (stackedColumn(metrics) ?: stackedColumn(shrunk))?.let { return it }
        }
        return EchoFit(
            level = if (icons) level else EchoFitLevel.NoIcons,
            metrics = metrics,
            showIcons = icons,
            columns = columns,
            questionHeight = question.dp,
            answersMinHeight = minAnswers.dp,
            growRows = rows(columns),
        )
    }

    /** Topics one to a row, for labels too long for two columns even without icons, when that fits. */
    private fun stackedColumn(shrunk: EchoLayoutMetrics): EchoFit? {
        val metrics = shrunk.copy(tileHeight = TouchFloor, tileStacked = false)
        return headerOptions(metrics).firstNotNullOfOrNull { option ->
            val question = questionHeight(option, width)
            val minAnswers = answersMinHeight(option, columns = 1, inline = false)
            if (columnHeight(option, width, question) + minAnswers > height) return@firstNotNullOfOrNull null
            EchoFit(EchoFitLevel.Stacked, option, showIcons = false, columns = 1, somethingElseInFooter = true, questionHeight = question.dp, answersMinHeight = minAnswers.dp, growRows = rows(1))
        }
    }

    /**
     * The answers as one stack without icons, Something else in the footer. Wide, short bodies put Echo
     * and the question in a start column beside the answers; narrower ones keep one column.
     */
    private fun stacked(base: EchoLayoutMetrics, shrunk: EchoLayoutMetrics): EchoFit? {
        val metrics = shrunk.copy(tileHeight = TouchFloor, tileStacked = false, rowHeight = TouchFloor)
        // Beside the answers Echo no longer competes with them for height: it starts again from today's
        // size, and comes back on short windows when its column has room (very large text still leaves it out).
        // Echo stays folded away wherever its tier already hides it, so it never comes and goes with the text.
        if (width >= SideBySideMinWidth && width > height) {
            return besideAnswers(metrics.copy(showEcho = base.showEcho, avatarWidth = base.avatarWidth, bubbleLines = base.bubbleLines))
        }
        return headerOptions(metrics).firstNotNullOfOrNull { option ->
            val question = questionHeight(option, width)
            val minAnswers = answersMinHeight(option, columns, inline = false)
            if (columnHeight(option, width, question) + minAnswers > height) return@firstNotNullOfOrNull null
            EchoFit(
                EchoFitLevel.Stacked,
                option,
                showIcons = false,
                somethingElseInFooter = true,
                columns = columns,
                questionHeight = question.dp,
                answersMinHeight = minAnswers.dp,
                growRows = rows(columns),
            )
        }
    }

    /**
     * Echo and the question in a start column beside the answers. Topics stand in two columns, or four
     * (then three) when two would be too tall; later choices in one column, or two when one would be.
     * Every arrangement must fit its labels: when they don't, the start column narrows, and as a last
     * resort topic labels take two lines. Echo shows only with everything it says, or folds away.
     */
    private fun besideAnswers(metrics: EchoLayoutMetrics): EchoFit? {
        val choices = if (topics) listOf(EchoLayout.TopicColumns, WideTopicColumns, WideTopicColumns - 1) else listOf(1, 2)
        val tries = StartShares.map { it to 1 } + if (topics) StartShares.map { it to 2 } else emptyList()
        tries.forEach { (share, labelLines) ->
            val start = (width - SideBySideGap) * share
            val end = width - start - SideBySideGap
            val columns = choices.firstOrNull { columns ->
                answersMinHeight(metrics, columns, inline = false) <= height && labelsFit(end, columns, showIcons = false, metrics, labelLines)
            } ?: return@forEach
            // Echo keeps the largest size its column has room for, or folds away.
            val option = headerOptions(metrics)
                .filter { !it.showEcho || (it.avatarWidth.value <= start && measure.bubbleFits(start, it.bubbleLines)) }
                .firstOrNull { columnHeight(it, start, questionHeight(it, start)) <= height } ?: return@forEach
            // The question keeps to three lines, however tall its column.
            val questionMax = measure.questionSize(start, option.questionMax.value, option.questionMin.value).coerceAtMost(option.questionMax.value)
            return EchoFit(
                EchoFitLevel.Stacked,
                option.copy(questionMax = questionMax.sp),
                showIcons = false,
                columns = columns,
                sideBySide = true,
                somethingElseInFooter = true,
                questionHeight = questionHeight(option, start).dp,
                answersMinHeight = answersMinHeight(metrics, columns, inline = false).dp,
                growRows = rows(columns),
                startShare = share,
                labelLines = labelLines,
            )
        }
        return null
    }

    /** Echo folds away and the question takes its smallest size; the answers keep their 48dp rows. */
    private fun lastResort(shrunk: EchoLayoutMetrics): EchoFit {
        val metrics = shrunk.copy(showEcho = false, tileHeight = TouchFloor, tileStacked = false, rowHeight = TouchFloor, questionMax = shrunk.questionMin)
        val side = width >= SideBySideMinWidth && width > height
        // Beside the answers, long topic labels get the narrowest start column and two lines.
        val share = if (side && topics && !labelsFit(width - startColumnWidth(width) - SideBySideGap, columns, showIcons = false, metrics)) StartShares.last() else SideBySideStartShare
        val questionWidth = if (side) (width - SideBySideGap) * share else width
        val end = width - questionWidth - SideBySideGap
        val labelLines = if (side && topics && !labelsFit(end, columns, showIcons = false, metrics)) 2 else 1
        val question = questionHeight(metrics, questionWidth)
        val minAnswers = answersMinHeight(metrics, columns, inline = false)
        val total = if (side) maxOf(question, minAnswers) else question + metrics.gap.value + minAnswers
        return EchoFit(
            EchoFitLevel.LastResort,
            metrics,
            showIcons = false,
            columns = columns,
            sideBySide = side,
            somethingElseInFooter = true,
            questionHeight = question.dp,
            answersMinHeight = minAnswers.dp,
            growRows = rows(columns),
            startShare = share,
            labelLines = labelLines,
            overflow = total > height,
        )
    }

    /** [metrics] with Echo, then each smaller Echo and bubble, then Echo folded away. */
    private fun headerOptions(metrics: EchoLayoutMetrics): List<EchoLayoutMetrics> {
        if (!metrics.showEcho) return listOf(metrics)
        val withEcho = AvatarSteps.filter { it <= metrics.avatarWidth }.flatMap { avatar ->
            (metrics.bubbleLines downTo 1).map { lines -> metrics.copy(avatarWidth = avatar, bubbleLines = lines) }
        }
        return withEcho + metrics.copy(showEcho = false)
    }

    /** Echo with its bubble, the gap under it, the question and the gap before the answers. */
    private fun columnHeight(metrics: EchoLayoutMetrics, columnWidth: Float, question: Float): Float {
        val header = if (metrics.showEcho) {
            metrics.avatarWidth.value * EchoFigureGeometry.TotalShare + measure.bubbleHeight(columnWidth, metrics.bubbleLines)
        } else {
            0f
        }
        return header + (if (header > 0f) metrics.gap.value else 0f) + question + metrics.gap.value
    }

    /**
     * The height the question needs whole: at its comfortable floor ([QuestionFloorShare] of its
     * largest size, never under its smallest), within three lines. It grows back to its largest size
     * wherever there is room.
     */
    private fun questionHeight(metrics: EchoLayoutMetrics, columnWidth: Float): Float {
        val floor = maxOf(metrics.questionMin.value, metrics.questionMax.value * QuestionFloorShare)
        return measure.questionHeight(columnWidth, floor, metrics.questionMin.value)
    }

    /** Rows the answers take; one column of later choices keeps room for four, as it always has. */
    private fun rows(columns: Int): Int = when {
        topics || columns > 1 -> ceil(answers.count / columns.toFloat()).toInt()
        else -> maxOf(answers.count, EchoLayout.AnswerRows)
    }

    private fun answersMinHeight(metrics: EchoLayoutMetrics, columns: Int, inline: Boolean): Float {
        val rows = rows(columns)
        val row = if (topics) metrics.tileHeight else metrics.rowHeight
        return row.value * rows + EchoLayout.GroupGap.value * (rows - 1) + if (inline) EchoLayout.SomethingElseHeight.value else 0f
    }

    /** Whether every label fits its tile at 12sp in a [slotWidth] slot of [columns], with or without icons. */
    private fun labelsFit(slotWidth: Float, columns: Int, showIcons: Boolean, metrics: EchoLayoutMetrics, topicLines: Int = 1): Boolean {
        val tile = (slotWidth - EchoLayout.GroupGap.value * (columns - 1)) / columns
        val stacked = topics && metrics.tileStacked
        val room = when {
            !showIcons -> tile - EchoAnswerTextOnlyPadding.value * 2
            stacked -> tile - EchoAnswerStackedPadding.value * 2
            else -> tile - EchoAnswerRowStart.value - EchoAnswerRowEnd.value - EchoAnswerRowSpacing.value - metrics.badgeSize.value
        }
        return measure.labelsFit(room, maxLines = if (topics) topicLines else 2)
    }

    private fun startColumnWidth(total: Float): Float = (total - SideBySideGap) * SideBySideStartShare

    private data class TileStep(val height: Dp, val stacked: Boolean, val badge: Dp)

    private data class RowStep(val height: Dp, val badge: Dp)

    companion object {
        /** The tiers' tiles, tallest first (Spacious, Roomy, Regular, Compact). */
        private val TileSteps = listOf(TileStep(96.dp, true, 48.dp), TileStep(80.dp, true, 42.dp), TileStep(58.dp, false, 36.dp), TileStep(50.dp, false, 32.dp))
        private val RowSteps = listOf(RowStep(72.dp, 48.dp), RowStep(64.dp, 42.dp), RowStep(58.dp, 36.dp), RowStep(50.dp, 32.dp))
        private val AvatarSteps = listOf(168.dp, 132.dp, 112.dp, 84.dp)
        private val TouchFloor = 48.dp
        private val CompactGap = 8.dp
        private const val WideTopicColumns = 4
        private const val RoomyBubbleLines = 3

        /** Beside the answers, the start column narrows through these shares before labels take two lines. */
        private val StartShares = listOf(SideBySideStartShare, 0.35f, 0.3f)

        /** A body at least this wide (a window of about 450dp) and wider than tall can stand Echo beside the answers. */
        private const val SideBySideMinWidth = 400f

        /** Until the last resort the question keeps at least this share of its tier's largest size. */
        private const val QuestionFloorShare = 0.75f
    }
}

/** The answers' own shrink steps: the smaller tiers' tiles for [topics], or rows. */
private fun echoAnswerSteps(topics: Boolean, base: EchoLayoutMetrics): List<EchoLayoutMetrics> = if (topics) {
    listOf(80.dp to (true to 42.dp), 58.dp to (false to 36.dp), 50.dp to (false to 32.dp))
        .filter { it.first < base.tileHeight }.map { (height, rest) -> base.copy(tileHeight = height, tileStacked = rest.first, badgeSize = rest.second) }
} else {
    listOf(64.dp to 42.dp, 58.dp to 36.dp, 50.dp to 32.dp).filter { it.first < base.rowHeight }.map { (height, badge) -> base.copy(rowHeight = height, badgeSize = badge) }
}

/** Beside the answers, Echo and the question take this share of the width. */
internal const val SideBySideStartShare = 0.4f
internal const val SideBySideGap = 24f

/** Answer content padding, shared by the planner and the tiles it plans for. */
internal val EchoAnswerRowStart = 10.dp
internal val EchoAnswerRowEnd = 14.dp
internal val EchoAnswerRowSpacing = 12.dp
internal val EchoAnswerStackedPadding = 14.dp
internal val EchoAnswerTextOnlyPadding = 16.dp
