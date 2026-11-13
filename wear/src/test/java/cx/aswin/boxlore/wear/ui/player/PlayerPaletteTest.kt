package cx.aswin.boxlore.wear.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerPaletteTest {
    private val black = 0xFF000000.toInt()

    @Test
    fun `no artwork colour gives the brand colours`() {
        assertEquals(PlayerPalette.Brand, PlayerPalette.fromSeed(null))
    }

    @Test
    fun `grey artwork gives the brand colours`() {
        assertEquals(PlayerPalette.Brand, PlayerPalette.fromSeed(0xFF808080.toInt()))
        assertEquals(PlayerPalette.Brand, PlayerPalette.fromSeed(0xFF000000.toInt()))
        assertEquals(PlayerPalette.Brand, PlayerPalette.fromSeed(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `colourful artwork changes the player colours`() {
        val red = PlayerPalette.fromSeed(0xFFD32F2F.toInt())
        val green = PlayerPalette.fromSeed(0xFF2E7D32.toInt())

        assertNotEquals(PlayerPalette.Brand, red)
        assertNotEquals(red, green)
    }

    @Test
    fun `every hue keeps icons and text readable`() {
        seeds().forEach { seed ->
            val colors = PlayerPalette.fromSeed(seed)

            assertReadable(colors.onAccent, colors.accent, seed)
            assertReadable(colors.onContainer, colors.container, seed)
            assertReadable(colors.onContainer, colors.quiet, seed)
            assertReadable(colors.detail, black, seed)
            assertTrue(PlayerPalette.contrast(colors.accent, black) >= 3f, "ring too dim for ${seed.toUInt().toString(16)}")
        }
    }

    @Test
    fun `brand colours are readable too`() {
        val colors = PlayerPalette.Brand

        assertReadable(colors.onAccent, colors.accent, 0)
        assertReadable(colors.onContainer, colors.container, 0)
        assertReadable(colors.onContainer, colors.quiet, 0)
        assertReadable(colors.detail, black, 0)
    }

    @Test
    fun `contrast of black on white is twenty-one to one`() {
        assertEquals(21f, PlayerPalette.contrast(0xFFFFFFFF.toInt(), black), 0.01f)
    }

    private fun assertReadable(foreground: Int, background: Int, seed: Int) {
        val ratio = PlayerPalette.contrast(foreground, background)
        assertTrue(ratio >= 4.5f, "contrast $ratio for seed ${seed.toUInt().toString(16)}")
    }

    /** Vivid and muted colours around the whole colour wheel. */
    private fun seeds(): List<Int> {
        val result = ArrayList<Int>()
        for (red in 0..255 step 51) {
            for (green in 0..255 step 51) {
                for (blue in 0..255 step 51) {
                    result += (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
        }
        return result
    }
}
