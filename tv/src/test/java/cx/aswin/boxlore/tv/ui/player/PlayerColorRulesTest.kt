package cx.aswin.boxlore.tv.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerColorRulesTest {
    private fun cover(vararg parts: Pair<Int, Int>): IntArray = parts.flatMap { (argb, count) -> List(count) { argb } }.toIntArray()

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val grey = 0xFF808080.toInt()
    private val red = 0xFFD02020.toInt()
    private val teal = 0xFF1A7A80.toInt()

    @Test
    fun `the cover's colour wins over white margins and black lettering`() {
        val seed = PlayerColorRules.seed(cover(white to 1200, black to 800, red to 300))
        assertNotNull(seed)
        val r = seed!! shr 16 and 0xFF
        val g = seed shr 8 and 0xFF
        assertTrue(r > 150 && g < 80, "expected the red, got ${Integer.toHexString(seed)}")
    }

    @Test
    fun `the larger colour wins when a cover has two`() {
        val seed = PlayerColorRules.seed(cover(teal to 1500, red to 200, black to 300))!!
        val r = seed shr 16 and 0xFF
        val b = seed and 0xFF
        assertTrue(b > r, "expected the teal, got ${Integer.toHexString(seed)}")
    }

    @Test
    fun `black, white and grey covers have no colour and use boxlore's iris`() {
        assertNull(PlayerColorRules.seed(cover(white to 1000, black to 900, grey to 400)))
        assertNull(PlayerColorRules.seed(IntArray(0)))
        assertEquals(PlayerColorRules.palette(PlayerColorRules.IRIS), PlayerColorRules.palette(null))
    }

    @Test
    fun `text stays readable on every surface whatever the cover's hue`() {
        val seeds = (0 until 360 step 15).flatMap { hue -> listOf(0.35f, 1f).map { saturation -> hsv(hue.toFloat(), saturation) } } + PlayerColorRules.IRIS
        seeds.forEach { seed ->
            val p = PlayerColorRules.palette(seed)
            val pairs =
                listOf(p.backgroundTop, p.backgroundBottom, p.container, p.panel).flatMap { surface -> listOf(p.title to surface, p.soft to surface) } +
                    listOf(p.onContainer to p.container, p.onPrimary to p.primary, p.onFocus to p.focus)
            pairs.forEach { (text, surface) ->
                val ratio = PlayerColorRules.contrast(text, surface)
                assertTrue(ratio >= 4.5, "seed ${Integer.toHexString(seed)}: ${Integer.toHexString(text)} on ${Integer.toHexString(surface)} is $ratio")
            }
        }
    }

    private fun hsv(hue: Float, saturation: Float): Int {
        val c = saturation
        val x = c * (1 - Math.abs((hue / 60f) % 2 - 1))
        val (r, g, b) =
            when ((hue / 60f).toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
        val m = 1 - c
        val ch = { v: Float -> ((v + m) * 255).toInt() }
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
