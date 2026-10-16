package cx.aswin.boxlore.core.designsystem.echo

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoCharacterModelTest {
    private val assets = File("src/main/assets/echo-character")
    private val spec = parseEchoCharacterManifest(File(assets, "manifest.json").readText())
    private val grids = listOf(echoGrid(25f, 22f), echoGrid(90f, 71f), echoGrid(152f, 120f), echoGrid(200f, 158f), echoGrid(420f, 332f))

    @Test fun `every frame in the manifest ships as a WebP with its screen inside the frame`() {
        spec.screens.forEach { (id, box) ->
            val file = File(assets, "$id.webp")
            assertTrue(file.isFile, "missing $id.webp")
            assertEquals("RIFF", file.inputStream().use { String(it.readNBytes(4)) })
            assertTrue(box.left >= 0f && box.top >= 0f && box.right <= 1f && box.bottom <= 1f)
        }
        assertTrue("welcome" in spec.sequences)
    }

    @Test fun `every clip in the manifest ships as an MP4 with its screen tracked in every frame`() {
        assertTrue(spec.clips.isNotEmpty())
        spec.clips.values.forEach { clip ->
            val file = File(assets, clip.file)
            assertTrue(file.isFile, "missing ${clip.file}")
            assertEquals("ftyp", file.inputStream().use { it.readNBytes(8).drop(4).toByteArray().decodeToString() })
            assertEquals(clip.frames, clip.tracks.size)
            assertTrue(clip.tracks.all { it.centerX in 0f..1f && it.centerY in 0f..1f && it.width in 0.05f..0.6f && kotlin.math.abs(it.degrees) < 30f }, "${clip.id} screen leaves the frame")
            assertTrue(clip.rest in spec.screens)
        }
    }

    @Test fun `Echo blinks briefly and rarely while resting`() {
        val samples = (0 until 20_000 step 10).map { echoIsBlinking(it.toLong()) }
        val blinking = samples.count { it } / samples.size.toFloat()
        assertTrue(blinking in 0.02f..0.06f, "blinking share was $blinking")
        assertFalse(echoIsBlinking(0))
        assertFalse(echoIsBlinking(-5))
    }

    @Test fun `the LED grid is about 26 dots across, on whole pixels from 3px apart and fractional below`() {
        assertEquals(EchoGrid(8f, 25, 19), echoGrid(200f, 158f))
        listOf(90f, 152f, 240f, 420f).forEach { width ->
            val grid = echoGrid(width, width * 0.79f)
            assertTrue(grid.wholePixels && grid.pitch % 1f == 0f, "$grid for $width px")
            assertTrue(grid.columns * grid.pitch <= width && grid.rows * grid.pitch <= width * 0.79f, "$grid for $width px")
        }
        // A small Echo beside a bubble keeps the face's proportions instead of a 3px floor.
        listOf(25f, 60f).forEach { width ->
            val grid = echoGrid(width, width * 0.88f)
            assertFalse(grid.wholePixels)
            assertEquals(26, grid.columns, "$grid for $width px")
        }
    }

    @Test fun `frames decode at the drawn size in 64px steps, never above the source`() {
        assertEquals(0, echoDecodeSize(0))
        assertEquals(128, echoDecodeSize(100))
        assertEquals(384, echoDecodeSize(368))
        assertEquals(512, echoDecodeSize(900))
    }

    @Test fun `the face is two mirrored eyes inside the grid, and a blink sits low in the eye`() {
        val still = EchoFaceStep(0, 0)
        grids.forEach { grid ->
            EchoFace.entries.forEach { face ->
                val eyes = echoFaceEyes(face, still, grid)
                assertEquals(2, eyes.size, "$face has only eyes")
                assertTrue(eyes.flatMap { it.cells }.all { it.col in 0 until grid.columns && it.row in 0 until grid.rows }, "$face leaves $grid")
                if (face != EchoFace.Curious) {
                    val (left, right) = eyes
                    assertEquals(grid.columns - left.col - left.width, right.col, "$face eyes are mirrored on $grid")
                    assertEquals(left.row, right.row)
                }
            }
            val idle = echoFaceEyes(EchoFace.Idle, still, grid).first()
            val blink = echoFaceEyes(EchoFace.Blink, still, grid).first()
            assertTrue(blink.cells.size < idle.cells.size)
            assertTrue(blink.row > idle.row + idle.height / 2 - 1 && blink.row + blink.height <= idle.row + idle.height, "blink sits low on $grid")
        }
    }

    @Test fun `the tint leaves white, black, the glass and other colours alone`() {
        val untouched = intArrayOf(0xFFF5F4EF.toInt(), 0xFF161A20.toInt(), 0xFF000000.toInt(), 0xFFF3C28D.toInt(), 0xFF397B6D.toInt(), 0x00000000)
        val pixels = untouched.copyOf()
        tintEchoAccents(pixels, 0xFF006A6A.toInt(), darkTheme = false)
        assertTrue(untouched.contentEquals(pixels))
    }

    @Test fun `the tint moves Echo's indigo to the theme hue, calmly, and keeps the shading`() {
        val light = 0xFF7468C8.toInt()
        val dark = 0xFF3E3394.toInt()
        val pixels = intArrayOf(light, dark)
        tintEchoAccents(pixels, 0xFF006A6A.toInt(), darkTheme = false)
        pixels.forEach { argb ->
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            assertTrue(g > r && b > r, "teal-ish expected, got ${Integer.toHexString(argb)}")
            assertEquals(0xFF, argb ushr 24)
        }
        assertTrue(luma(pixels[0]) > luma(pixels[1]), "lighter parts stay lighter")
        assertTrue(saturation(pixels[0]) <= 0.6f, "the theme colour is calmed, not vivid")
    }

    @Test fun `the face colour is a light tint of the theme`() {
        val face = echoFaceColor(0xFF5145D8.toInt())
        assertTrue(luma(face) > 170)
        assertEquals(0xFF, face ushr 24)
    }

    private fun luma(argb: Int) = (argb shr 16 and 0xFF) * 299 / 1000 + (argb shr 8 and 0xFF) * 587 / 1000 + (argb and 0xFF) * 114 / 1000

    private fun saturation(argb: Int): Float {
        val c = intArrayOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
        val hi = c.max()
        val lo = c.min()
        return if (hi == 0) 0f else (hi - lo).toFloat() / hi
    }
}
