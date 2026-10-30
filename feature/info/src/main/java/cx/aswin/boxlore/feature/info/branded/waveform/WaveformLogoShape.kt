package cx.aswin.boxlore.feature.info.branded.waveform

import androidx.compose.ui.geometry.Offset

/** Where a logo bar sits against the red block: clear of it, under it, or over it. */
internal enum class LogoGroup { Plain, UnderBlock, OverBlock }

/** One bar of the logo in the design's 369 × 120dp box: its left edge, top and height, all in dp. */
internal data class LogoBar(
    val x: Float,
    val y: Float,
    val height: Float,
    val group: LogoGroup,
    /** Which of the four beats it breathes on while an episode plays. */
    val phase: Int,
    /** When it bursts out in the logo intro: the centre first, then outwards. */
    val introDelay: Int,
)

/**
 * The Waveform logo traced from the show's official cover as real shapes, so every bar can move.
 * The centre is exact; small bars extend it to both edges.
 */
internal object WaveformLogoShape {
    const val WIDTH = 369f
    const val HEIGHT = 120f
    const val BAR_WIDTH = 3f
    private const val STEP = 7f
    private const val EDGE_BARS = 12
    private const val EDGE_DELAY_STEP = 9
    private const val MAX_DELAY = 700

    val Bars = listOf(
    LogoBar(4f, 58f, 4f, LogoGroup.Plain, phase = 0, introDelay = 350),
    LogoBar(11f, 56.5f, 7f, LogoGroup.Plain, phase = 2, introDelay = 340),
    LogoBar(18f, 57.5f, 5f, LogoGroup.Plain, phase = 1, introDelay = 332),
    LogoBar(25f, 55.5f, 9f, LogoGroup.Plain, phase = 3, introDelay = 322),
    LogoBar(32f, 57f, 6f, LogoGroup.Plain, phase = 0, introDelay = 314),
    LogoBar(39f, 58f, 4f, LogoGroup.Plain, phase = 2, introDelay = 304),
    LogoBar(46f, 56f, 8f, LogoGroup.Plain, phase = 1, introDelay = 296),
    LogoBar(53f, 57.5f, 5f, LogoGroup.Plain, phase = 3, introDelay = 286),
    LogoBar(60f, 56.5f, 7f, LogoGroup.Plain, phase = 0, introDelay = 278),
    LogoBar(67f, 57.5f, 5f, LogoGroup.Plain, phase = 2, introDelay = 268),
    LogoBar(74f, 57f, 6f, LogoGroup.Plain, phase = 1, introDelay = 260),
    LogoBar(81f, 55.9f, 8.2f, LogoGroup.Plain, phase = 3, introDelay = 250),
    LogoBar(88f, 53.9f, 12.2f, LogoGroup.Plain, phase = 0, introDelay = 242),
    LogoBar(95f, 45.5f, 29f, LogoGroup.Plain, phase = 2, introDelay = 232),
    LogoBar(102f, 53.9f, 12.2f, LogoGroup.Plain, phase = 1, introDelay = 224),
    LogoBar(109f, 55.9f, 8.2f, LogoGroup.Plain, phase = 3, introDelay = 214),
    LogoBar(116f, 53.9f, 12.2f, LogoGroup.Plain, phase = 0, introDelay = 206),
    LogoBar(123f, 45.5f, 29f, LogoGroup.Plain, phase = 2, introDelay = 196),
    LogoBar(130f, 35.8f, 48.4f, LogoGroup.Plain, phase = 1, introDelay = 188),
    LogoBar(137f, 38.6f, 42.8f, LogoGroup.Plain, phase = 3, introDelay = 178),
    LogoBar(228f, 38.6f, 42.8f, LogoGroup.Plain, phase = 0, introDelay = 178),
    LogoBar(235f, 35.8f, 48.4f, LogoGroup.Plain, phase = 2, introDelay = 188),
    LogoBar(242f, 45.5f, 29f, LogoGroup.Plain, phase = 1, introDelay = 196),
    LogoBar(249f, 53.9f, 12.2f, LogoGroup.Plain, phase = 3, introDelay = 206),
    LogoBar(256f, 55.9f, 8.2f, LogoGroup.Plain, phase = 0, introDelay = 214),
    LogoBar(263f, 53.9f, 12.2f, LogoGroup.Plain, phase = 2, introDelay = 224),
    LogoBar(270f, 45.5f, 29f, LogoGroup.Plain, phase = 1, introDelay = 232),
    LogoBar(277f, 53.9f, 12.2f, LogoGroup.Plain, phase = 3, introDelay = 242),
    LogoBar(284f, 55.9f, 8.2f, LogoGroup.Plain, phase = 0, introDelay = 250),
    LogoBar(291f, 57f, 6f, LogoGroup.Plain, phase = 2, introDelay = 260),
    LogoBar(298f, 57.5f, 5f, LogoGroup.Plain, phase = 1, introDelay = 268),
    LogoBar(305f, 56.5f, 7f, LogoGroup.Plain, phase = 3, introDelay = 278),
    LogoBar(312f, 57.5f, 5f, LogoGroup.Plain, phase = 0, introDelay = 286),
    LogoBar(319f, 56f, 8f, LogoGroup.Plain, phase = 2, introDelay = 296),
    LogoBar(326f, 58f, 4f, LogoGroup.Plain, phase = 1, introDelay = 304),
    LogoBar(333f, 57f, 6f, LogoGroup.Plain, phase = 3, introDelay = 314),
    LogoBar(340f, 55.5f, 9f, LogoGroup.Plain, phase = 0, introDelay = 322),
    LogoBar(347f, 57.5f, 5f, LogoGroup.Plain, phase = 2, introDelay = 332),
    LogoBar(354f, 56.5f, 7f, LogoGroup.Plain, phase = 1, introDelay = 340),
    LogoBar(361f, 58f, 4f, LogoGroup.Plain, phase = 3, introDelay = 350),
    LogoBar(186f, 37.1f, 45.8f, LogoGroup.UnderBlock, phase = 1, introDelay = 124),
    LogoBar(193f, 33.4f, 53.2f, LogoGroup.UnderBlock, phase = 3, introDelay = 134),
    LogoBar(200f, 29.5f, 61f, LogoGroup.UnderBlock, phase = 0, introDelay = 142),
    LogoBar(207f, 26f, 68f, LogoGroup.UnderBlock, phase = 2, introDelay = 152),
    LogoBar(214f, 22.2f, 75.6f, LogoGroup.UnderBlock, phase = 1, introDelay = 160),
    LogoBar(221f, 18.5f, 83f, LogoGroup.UnderBlock, phase = 3, introDelay = 170),
    LogoBar(144f, 18.8f, 82.4f, LogoGroup.OverBlock, phase = 0, introDelay = 170),
    LogoBar(151f, 22.4f, 75.2f, LogoGroup.OverBlock, phase = 2, introDelay = 160),
    LogoBar(158f, 25.9f, 68.2f, LogoGroup.OverBlock, phase = 1, introDelay = 152),
    LogoBar(165f, 29.7f, 60.6f, LogoGroup.OverBlock, phase = 3, introDelay = 142),
    LogoBar(172f, 33.3f, 53.4f, LogoGroup.OverBlock, phase = 0, introDelay = 134),
    LogoBar(179f, 36.7f, 46.6f, LogoGroup.OverBlock, phase = 2, introDelay = 124)
    )

    /** The slanted red block. */
    val Block = listOf(Offset(144f, 60f), Offset(224f, 17.5f), Offset(224f, 59.6f), Offset(144f, 102.1f))

    /** The block slices in from the middle of its left edge. */
    val BlockOrigin = Offset(144f, 59.8f)

    /** Bars drawn over the block show only above its lower edge's line. */
    val OverClip = listOf(Offset(0f, 0f), Offset(WIDTH, 0f), Offset(WIDTH, 179.3f), Offset(144f, 60f), Offset(0f, 60f))

    /** Bars drawn under the block show only below its upper edge's line. */
    val UnderClip = listOf(Offset(0f, 136.5f), Offset(WIDTH, -59.5f), Offset(WIDTH, HEIGHT), Offset(0f, HEIGHT))

    /**
     * The logo for a window [widthDp] wide, centred. Wider than the design, the small edge bars
     * carry on to both edges and burst out a little later the further out they are.
     */
    fun barsFor(widthDp: Float): List<LogoBar> {
        val extra = (widthDp - WIDTH) / 2f
        if (extra < STEP) return Bars
        val plain = Bars.filter { it.group == LogoGroup.Plain }
        val left = plain.take(EDGE_BARS)
        val right = plain.takeLast(EDGE_BARS).reversed()
        val count = (extra / STEP).toInt() + 1
        val added = (1..count).flatMap { k ->
            val delay = (left.first().introDelay + k * EDGE_DELAY_STEP).coerceAtMost(MAX_DELAY)
            listOf(
                left[(k - 1) % EDGE_BARS].copy(x = left.first().x - k * STEP, introDelay = delay),
                right[(k - 1) % EDGE_BARS].copy(x = right.first().x + k * STEP, introDelay = delay),
            )
        }
        return added + Bars
    }
}
