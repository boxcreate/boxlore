package cx.aswin.boxlore.core.designsystem.echo

import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoMotionModelTest {
    private val wave = EchoCharacterStep("rest", EchoFace.Happy, 1_000, clip = "wave")
    private val think = EchoCharacterStep("rest", EchoFace.Curious, 2_000, clip = "think")
    private fun rest(ms: Long) = EchoCharacterStep("rest", EchoFace.Idle, ms)

    @Test fun `a sequence plays its steps once and then rests forever`() {
        val sequence = EchoSequence(listOf(wave, rest(0)))
        assertEquals(wave, sequence.at(0).step)
        assertEquals(400L, sequence.at(400).stepElapsed)
        assertEquals(rest(0), sequence.at(400).next)
        assertEquals(600L, sequence.at(400).untilNext)
        assertEquals(rest(0), sequence.at(1_000).step)
        assertEquals(rest(0), sequence.at(600_000).step)
        assertNull(sequence.at(600_000).next)
        assertEquals(1_000L, sequence.restStartMillis)
    }

    @Test fun `a looping sequence repeats its loop, and every step it plays has its own order`() {
        val sequence = EchoSequence(listOf(wave), loop = listOf(rest(1_500), think))
        assertEquals(wave, sequence.at(999).step)
        assertEquals(rest(1_500), sequence.at(1_000).step)
        assertEquals(think, sequence.at(2_500).step)
        assertEquals(rest(1_500), sequence.at(4_500).step)
        assertEquals(think, sequence.at(4_500).next)
        val orders = (0 until 20_000 step 50).map { sequence.at(it.toLong()).order }
        assertTrue(orders.zipWithNext().all { (a, b) -> b == a || b == a + 1 }, "order only ever steps forward by one")
        assertEquals(sequence.at(2_500).order + 1, sequence.at(4_500).order)
    }

    @Test fun `sequences that would leave Echo nowhere to rest are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { EchoSequence(listOf(wave)) }
        assertThrows(IllegalArgumentException::class.java) { EchoSequence(listOf(rest(0), wave), loop = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { EchoSequence(listOf(wave), loop = listOf(rest(0))) }
    }

    @Test fun `a clip shows its frames in time and holds the last`() {
        val clip = EchoMotionClip("wave", "clips/wave.mp4", fps = 24, frames = 48, size = 4, face = EchoFace.Happy, rest = "rest", tracks = List(48) { EchoScreenTrack(0.5f, 0.4f, 0.2f, 0.18f, 0f) })
        assertEquals(2_000L, clip.millis)
        assertEquals(0, clip.frameAt(0))
        assertEquals(12, clip.frameAt(500))
        assertEquals(47, clip.frameAt(5_000))
        assertThrows(IllegalArgumentException::class.java) { clip.copy(tracks = clip.tracks.drop(1)) }
    }

    @Test fun `a clip's face script holds each beat from its share of the clip, and blink frames close the eyes`() {
        val clip = EchoMotionClip(
            "story", "clips/story.mp4", fps = 24, frames = 11, size = 4, face = EchoFace.Idle, rest = "rest",
            tracks = List(11) { EchoScreenTrack(0.5f, 0.4f, 0.2f, 0.18f, 0f) },
            script = listOf(EchoClipBeat(0f, EchoFace.Idle, EchoGaze(0f, 1f)), EchoClipBeat(0.5f, EchoFace.Happy, EchoGaze(0f, 0f))),
            blinks = setOf(8),
        )
        assertEquals(EchoClipBeat(0f, EchoFace.Idle, EchoGaze(0f, 1f)), clip.lookAt(4))
        assertEquals(EchoFace.Happy, clip.lookAt(5).face)
        assertEquals(EchoFace.Blink, clip.lookAt(8).face)
        assertEquals(EchoFace.Happy, clip.copy(script = emptyList(), blinks = emptySet(), face = EchoFace.Happy).lookAt(3).face)
        assertNull(clip.lensAt(3))
        assertThrows(IllegalArgumentException::class.java) { clip.copy(lens = listOf(null)) }
    }

    @Test fun `an alpha-packed frame unpacks to colour from the top half and alpha from the bottom`() {
        // 2x2 frame: a 2x4 luma plane (colour rows, then alpha rows) and one chroma sample per 2x2 block.
        val size = 2
        val luma = byteArrayOf(
            235.toByte(),
            235.toByte(), // white
            16,
            16, //                     black
            235.toByte(),
            16, //          alpha: opaque, transparent
            126,
            20, //                   alpha: about half, and noise that snaps to transparent
        )
        val y = ByteBuffer.wrap(luma)
        val u = ByteBuffer.wrap(byteArrayOf(128.toByte()))
        val v = ByteBuffer.wrap(byteArrayOf(128.toByte()))
        val out = IntArray(size * size)
        unpackEchoClipFrame(EchoYuvFrame(EchoYuvPlane(y, 2, 1), EchoYuvPlane(u, 1, 2), EchoYuvPlane(v, 1, 2)), size, out)
        assertEquals(0xFFFFFFFF.toInt(), out[0])
        assertEquals(0, out[1])
        val half = out[2] ushr 24
        assertTrue(half in 120..135, "alpha $half")
        assertEquals(0, out[2] and 0xFFFFFF)
        assertEquals(0, out[3])
    }

    @Test fun `unpacking reads indigo back from limited-range chroma`() {
        // BT.601 limited range for the brand indigo #573DF5: Y 93, U 205, V 126.
        val y = ByteBuffer.wrap(byteArrayOf(93, 93, 93, 93, 235.toByte(), 235.toByte(), 235.toByte(), 235.toByte()))
        val u = ByteBuffer.wrap(byteArrayOf(205.toByte()))
        val v = ByteBuffer.wrap(byteArrayOf(126.toByte()))
        val out = IntArray(4)
        unpackEchoClipFrame(EchoYuvFrame(EchoYuvPlane(y, 2, 1), EchoYuvPlane(u, 1, 2), EchoYuvPlane(v, 1, 2)), 2, out)
        val r = out[0] shr 16 and 0xFF
        val g = out[0] shr 8 and 0xFF
        val b = out[0] and 0xFF
        assertTrue(kotlin.math.abs(r - 0x57) < 4 && kotlin.math.abs(g - 0x3D) < 4 && kotlin.math.abs(b - 0xF5) < 4, "got ${Integer.toHexString(out[0])}")
        assertEquals(0xFF, out[0] ushr 24)
    }
}
