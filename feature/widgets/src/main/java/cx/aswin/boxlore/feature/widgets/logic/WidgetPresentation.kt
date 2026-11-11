package cx.aswin.boxlore.feature.widgets.logic

import kotlin.math.ceil

/**
 * What a playback widget shows at the size the launcher reports: how many title lines, whether there
 * is room for the time left and the show name, and which shape of layout to use.
 *
 * The layouts scale themselves to the space they really get and keep their own proportions, so
 * nothing here can clip a widget: a late or wrong report only makes these choices a little too
 * generous or too cautious. The shape limits below repeat the ones the layouts' frames enforce.
 */
object WidgetPresentation {
    /** What sits under the episode title. The show name goes first when space runs out. */
    enum class Meta {
        TIME_AND_SHOW,
        TIME,
        NONE,
    }

    /** Now Playing · Full Controls. */
    data class FullControls(
        /** The taller shape: a large info block over the controls. */
        val tall: Boolean,
        val titleLines: Int,
        val meta: Meta,
    )

    fun fullControls(size: WidgetSize): FullControls {
        val tall = size.heightDp >= FULL_TALL_FROM && size.widthDp >= FULL_TALL_MIN_WIDTH
        // The frame stops growing once it is this tall for its width; the rest of the cell stays empty.
        val content = minOf(size.heightDp.toFloat(), size.widthDp * if (tall) FULL_TALL_MAX_SHAPE else FULL_MAX_SHAPE)
        val infoRow = if (tall) (content - ROW_GAP) * TALL_INFO_SHARE else (content - ROW_GAP) / 2f
        return FullControls(
            tall = tall,
            titleLines =
            when {
                tall -> TALL_TITLE_LINES
                infoRow >= TWO_LINE_ROW -> 2
                else -> 1
            },
            meta =
            when {
                infoRow >= SHOW_NAME_ROW -> Meta.TIME_AND_SHOW
                infoRow >= TIME_ROW -> Meta.TIME
                else -> Meta.NONE
            },
        )
    }

    /** Now Playing · Compact. */
    data class Bar(
        val titleLines: Int,
        /** The artwork already names the show, so the second line is the time left, when there is room. */
        val showTimeLeft: Boolean,
    )

    fun bar(size: WidgetSize): Bar {
        // The pill fills the cell until it is a quarter as tall as it is wide, then stays that slim.
        val pill = minOf(size.heightDp.toFloat(), size.widthDp * BAR_MAX_SHAPE)
        return Bar(
            titleLines = if (pill >= BAR_TWO_LINE_PILL) 2 else 1,
            showTimeLeft = pill >= BAR_TIME_PILL,
        )
    }

    /**
     * The sizes at which Full Controls changes what it shows, smallest first: the time left appears,
     * then the show name, then a second title line, then the taller shape. Each is the smallest
     * widget whose frame has room for that step, and each fits inside the next.
     */
    val FULL_CONTROLS_STEPS: List<WidgetSize> =
        listOf(
            WidgetSize(0, 0),
            frameWith(infoRow = TIME_ROW),
            frameWith(infoRow = SHOW_NAME_ROW),
            frameWith(infoRow = TWO_LINE_ROW),
            WidgetSize(FULL_TALL_MIN_WIDTH, FULL_TALL_FROM),
        )

    /** The sizes at which Compact changes what it shows: a second title line, then the time left. */
    val BAR_STEPS: List<WidgetSize> = listOf(WidgetSize(0, 0), pillOf(BAR_TWO_LINE_PILL), pillOf(BAR_TIME_PILL))

    /**
     * The step a launcher draws for a widget really [drawn] this big: the largest one that fits, as
     * `RemoteViews` chooses among sized layouts. The first step fits everything.
     */
    fun stepFor(steps: List<WidgetSize>, drawn: WidgetSize): WidgetSize =
        steps.lastOrNull { it.widthDp <= drawn.widthDp && it.heightDp <= drawn.heightDp } ?: steps.first()

    /** The smallest regular Full Controls widget whose info row is [infoRow] tall. */
    private fun frameWith(infoRow: Float): WidgetSize {
        val frame = infoRow * 2 + ROW_GAP
        return WidgetSize(ceil(frame / FULL_MAX_SHAPE).toInt(), ceil(frame).toInt())
    }

    /** The smallest Compact widget whose pill is [pill] tall. */
    private fun pillOf(pill: Float): WidgetSize = WidgetSize(ceil(pill / BAR_MAX_SHAPE).toInt(), ceil(pill).toInt())

    private const val FULL_TALL_FROM = 236

    // As wide as the widest regular step, so every step fits inside the next.
    private const val FULL_TALL_MIN_WIDTH = 267
    private const val FULL_MAX_SHAPE = 0.6f
    private const val FULL_TALL_MAX_SHAPE = 0.8f
    private const val ROW_GAP = 8f
    private const val TALL_INFO_SHARE = 21f / 31f
    private const val TALL_TITLE_LINES = 3
    private const val TWO_LINE_ROW = 76f
    private const val SHOW_NAME_ROW = 60f
    private const val TIME_ROW = 46f

    private const val BAR_MAX_SHAPE = 0.25f
    private const val BAR_TWO_LINE_PILL = 60f
    private const val BAR_TIME_PILL = 68f
}
