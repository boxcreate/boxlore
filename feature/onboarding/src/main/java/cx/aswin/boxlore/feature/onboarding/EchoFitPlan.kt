package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
    val fit = planner.plan(base)
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

    fun plan(base: EchoLayoutMetrics): EchoFit {
        if (!sized) return EchoFit(EchoFitLevel.Today, base, questionHeight = questionHeight(base, width).dp, answersMinHeight = null)
        // Today's tier, then each shrink step in order.
        val steps = shrinkSteps(base)
        (listOf(EchoFitLevel.Today to base) + steps.map { EchoFitLevel.Shrunk to it }).forEach { (level, metrics) ->
            accept(level, metrics, columns)?.let { return it }
        }
        val shrunk = steps.lastOrNull() ?: base
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

    /** The fit for [metrics] in one column, or null when it overflows. Labels too long for their icons drop them. */
    private fun accept(level: EchoFitLevel, metrics: EchoLayoutMetrics, columns: Int): EchoFit? {
        val question = questionHeight(metrics, width)
        val minAnswers = answersMinHeight(metrics, columns, inline = true)
        if (columnHeight(metrics, width, question) + minAnswers > height) return null
        val icons = labelsFit(width, columns, showIcons = true, metrics)
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
        if (width >= SideBySideMinWidth && width > height) {
            return besideAnswers(metrics.copy(showEcho = fontScale < EchoLayout.TightFontScale, avatarWidth = base.avatarWidth, bubbleLines = base.bubbleLines))
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

    private fun besideAnswers(metrics: EchoLayoutMetrics): EchoFit? {
        val start = startColumnWidth(width)
        val end = width - start - SideBySideGap
        // Topics in two columns, or four (then three) when two would be too tall; later choices in one
        // column, or two when one would be. More columns only where their labels still fit.
        val choices = if (topics) listOf(EchoLayout.TopicColumns, WideTopicColumns, WideTopicColumns - 1) else listOf(1, 2)
        val columns = choices.firstOrNull { columns ->
            answersMinHeight(metrics, columns, inline = false) <= height && (columns == choices.first() || labelsFit(end, columns, showIcons = false, metrics))
        } ?: return null
        val minAnswers = answersMinHeight(metrics, columns, inline = false)
        // Echo keeps the largest size its column has room for, or folds away.
        val option = headerOptions(metrics).firstOrNull { columnHeight(it, start, questionHeight(it, start)) <= height }
            ?: return null
        return EchoFit(
            EchoFitLevel.Stacked,
            option,
            showIcons = false,
            columns = columns,
            sideBySide = true,
            somethingElseInFooter = true,
            questionHeight = questionHeight(option, start).dp,
            answersMinHeight = minAnswers.dp,
            growRows = rows(columns),
        )
    }

    /** Echo folds away and the question takes its smallest size; the answers keep their 48dp rows. */
    private fun lastResort(shrunk: EchoLayoutMetrics): EchoFit {
        val metrics = shrunk.copy(showEcho = false, tileHeight = TouchFloor, tileStacked = false, rowHeight = TouchFloor, questionMax = shrunk.questionMin)
        val side = width >= SideBySideMinWidth && width > height
        val questionWidth = if (side) startColumnWidth(width) else width
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
    private fun labelsFit(slotWidth: Float, columns: Int, showIcons: Boolean, metrics: EchoLayoutMetrics): Boolean {
        val tile = (slotWidth - EchoLayout.GroupGap.value * (columns - 1)) / columns
        val stacked = topics && metrics.tileStacked
        val room = when {
            !showIcons -> tile - EchoAnswerTextOnlyPadding.value * 2
            stacked -> tile - EchoAnswerStackedPadding.value * 2
            else -> tile - EchoAnswerRowStart.value - EchoAnswerRowEnd.value - EchoAnswerRowSpacing.value - metrics.badgeSize.value
        }
        return measure.labelsFit(room, maxLines = if (topics) 1 else 2)
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

        /** A body at least this wide (a window of about 450dp) and wider than tall can stand Echo beside the answers. */
        private const val SideBySideMinWidth = 400f

        /** Until the last resort the question keeps at least this share of its tier's largest size. */
        private const val QuestionFloorShare = 0.75f
    }
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
