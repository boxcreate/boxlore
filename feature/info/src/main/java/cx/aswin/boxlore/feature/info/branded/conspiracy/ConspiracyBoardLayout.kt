package cx.aswin.boxlore.feature.info.branded.conspiracy

import androidx.compose.ui.geometry.Offset

/**
 * The evidence board's layout, in dp on the design's 369dp-wide board whose top is the top of the
 * screen under a 43dp status bar. The board's middle stays centred on wider windows, while the clues
 * peeking in from the sides keep to the window's own edges, as if the board carries on past it.
 */
internal object ConspiracyBoardLayout {
    const val WIDTH = 369f

    /** The design's status bar. The header starts under the real one; the board draws itself up behind it. */
    const val STATUS_BAR = 43f

    /** Where the board's paper ends, and where it has faded fully into the page. */
    const val PAPER_BOTTOM = 326f
    const val BOTTOM = 322f
    const val FADE_TOP = 282f

    /** The top of the board darkens (or brightens) to here, so the top bar's icons stay clear. */
    const val TOP_FADE = 100f

    enum class Anchor { CENTRE, LEFT, RIGHT }

    /** One pinned piece: its box before it is turned [rotation] degrees about its centre, and when it drops in. */
    data class Piece(val x: Float, val y: Float, val width: Float, val height: Float, val rotation: Float, val anchor: Anchor, val dropAt: Float)

    val Scrap = Piece(70f, 112f, 228f, 78f, -2f, Anchor.CENTRE, 0f)
    val Clipping = Piece(8f, 200f, 110f, 90f, -4f, Anchor.CENTRE, 90f)
    val IndexCard = Piece(252f, 196f, 110f, 74f, 4f, Anchor.CENTRE, 170f)
    val Sticky = Piece(140f, 238f, 86f, 62f, 3f, Anchor.CENTRE, 250f)

    /** The three clues at the edges: an index card off the left, a clipping and a card off the right. */
    val Peeks = listOf(
        Piece(-62f, 118f, 112f, 76f, -7f, Anchor.LEFT, 170f),
        Piece(310f, 106f, 118f, 86f, 6f, Anchor.RIGHT, 90f),
        Piece(334f, 258f, 100f, 60f, -5f, Anchor.RIGHT, 170f),
    )

    /** What a pin holds; a pin shows only when its piece does. */
    enum class Holds { SCRAP, CLIPPING, INDEX_CARD, STICKY, LEFT_PEEK, RIGHT_PEEK }

    /** A pushpin pressed in at [at], [pushAt] milliseconds after the board opens. */
    data class Pin(val at: Offset, val anchor: Anchor, val holds: Holds, val pushAt: Float)

    val Pins = listOf(
        Pin(Offset(184f, 114f), Anchor.CENTRE, Holds.SCRAP, 180f),
        Pin(Offset(82f, 186f), Anchor.CENTRE, Holds.SCRAP, 240f),
        Pin(Offset(290f, 180f), Anchor.CENTRE, Holds.SCRAP, 300f),
        Pin(Offset(63f, 203f), Anchor.CENTRE, Holds.CLIPPING, 360f),
        Pin(Offset(307f, 199f), Anchor.CENTRE, Holds.INDEX_CARD, 420f),
        Pin(Offset(183f, 241f), Anchor.CENTRE, Holds.STICKY, 480f),
        Pin(Offset(184f, 190f), Anchor.CENTRE, Holds.SCRAP, 540f),
        Pin(Offset(22f, 123f), Anchor.LEFT, Holds.LEFT_PEEK, 120f),
        Pin(Offset(338f, 113f), Anchor.RIGHT, Holds.RIGHT_PEEK, 150f),
    )

    /** A length of yarn, as SVG path data on the board, drawn on from its start [drawAt] milliseconds after opening. */
    data class Yarn(val path: String, val anchor: Anchor, val needs: Holds, val drawAt: Float)

    /**
     * The yarn runs only over empty board: an X under the wordmark, each lower corner of the scrap to
     * the clue on the far side, a drop from the scrap to the sticky note, and the edge clues' threads
     * running off the screen.
     */
    val Yarns = listOf(
        Yarn(sag(Offset(82f, 186f), Offset(307f, 199f), 24f), Anchor.CENTRE, Holds.INDEX_CARD, 520f),
        Yarn(sag(Offset(290f, 180f), Offset(63f, 203f), 14f), Anchor.CENTRE, Holds.CLIPPING, 620f),
        Yarn("M184 190 C 181 207, 186 224, 183 241", Anchor.CENTRE, Holds.STICKY, 720f),
        Yarn("M22 123 C 8 102, -14 86, -40 78", Anchor.LEFT, Holds.LEFT_PEEK, 820f),
        Yarn("M338 113 C 356 96, 380 90, 410 96", Anchor.RIGHT, Holds.RIGHT_PEEK, 920f),
    )

    /** The red marker loop round the newest clue, drawn last. */
    const val MARKER = "M 10 214 C 24 194, 116 192, 128 214 C 138 244, 124 298, 64 302 C 14 304, 0 274, 3 244 C 5 230, 12 220, 22 214"
    const val MARKER_AT = 1_050f
    const val MARKER_MILLIS = 560f

    const val DROP_MILLIS = 320f
    const val PUSH_MILLIS = 240f
    const val YARN_MILLIS = 520f

    /** Once drawn, the yarn sways down 1.2dp and back, twice, then rests. */
    const val SWAY_AT = 1_400f
    const val SWAY_MILLIS = 3_600f
    const val SWAYS = 2
    const val END = SWAY_AT + SWAY_MILLIS * SWAYS

    /**
     * A hanging thread from [a] to [b]: it dips [k] below the lower end, a little lopsided, never a
     * perfect curve.
     */
    fun sag(a: Offset, b: Offset, k: Float): String {
        val dx = b.x - a.x
        val low = maxOf(a.y, b.y)
        return "M${a.x} ${a.y} C ${a.x + dx * 0.22f} ${low + k * 1.05f}, ${a.x + dx * 0.74f} ${low + k * 0.9f}, ${b.x} ${b.y}"
    }

    /** How far right [anchor] moves on a window [width] dp wide. */
    fun shift(anchor: Anchor, width: Float): Float = when (anchor) {
        Anchor.CENTRE -> (width - WIDTH) / 2f
        Anchor.LEFT -> 0f
        Anchor.RIGHT -> width - WIDTH
    }
}
