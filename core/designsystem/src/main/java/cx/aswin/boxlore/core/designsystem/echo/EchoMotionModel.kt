package cx.aswin.boxlore.core.designsystem.echo

import java.nio.ByteBuffer

/** Echo's screen in one clip frame: centre and size as fractions of the square frame, and its tilt in degrees. */
data class EchoScreenTrack(val centerX: Float, val centerY: Float, val width: Float, val height: Float, val degrees: Float)

/**
 * A motion clip: an alpha-packed video ([size] wide; colour on top, alpha below it as grey and, with
 * [mask], where the screen's glass is visible below that) of Echo moving from its rest pose and back, with
 * its screen tracked in every frame for the drawn eyes. The eyes show only through the glass mask, so a
 * card or hand in front of the screen covers them. [rest] is the still the clip starts and ends on.
 */
data class EchoMotionClip(
    val id: String,
    val file: String,
    val fps: Int,
    val frames: Int,
    val size: Int,
    val face: EchoFace,
    val rest: String,
    val tracks: List<EchoScreenTrack>,
    val mask: Boolean = false,
    /** Everything the clip covers, across all its frames. */
    val reach: EchoReach? = null,
    /** The face and gaze through the clip, each beat from its share of the clip on; empty shows [face] looking out. */
    val script: List<EchoClipBeat> = emptyList(),
    /** Per frame, the big eye seen through a magnifier held over the screen, or null; empty when the clip has none. */
    val lens: List<EchoLensEye?> = emptyList(),
    /** Frames where the eyes blink, as a prop passes close to the screen. */
    val blinks: Set<Int> = emptySet(),
) {
    init {
        require(fps > 0 && frames > 0 && tracks.size == frames) { "Clip $id has ${tracks.size} tracks for $frames frames" }
        require(lens.isEmpty() || lens.size == frames) { "Clip $id has ${lens.size} lens frames for $frames frames" }
    }

    val millis: Long get() = frames * 1000L / fps

    fun frameAt(elapsedMillis: Long): Int = (elapsedMillis * fps / 1000).toInt().coerceIn(0, frames - 1)

    /** The face and gaze at [frame]: the script's beat then (blinking on [blinks] frames), or [face] looking out. */
    fun lookAt(frame: Int): EchoClipBeat {
        val share = if (frames > 1) frame / (frames - 1f) else 0f
        val beat = script.lastOrNull { it.at <= share } ?: EchoClipBeat(0f, face, EchoGaze(0f, 0f))
        return if (frame in blinks) beat.copy(face = EchoFace.Blink) else beat
    }

    fun lensAt(frame: Int): EchoLensEye? = lens.getOrNull(frame)
}

/** From [at] (a share of the clip) on, Echo's screen shows [face] looking toward [gaze]. */
data class EchoClipBeat(val at: Float, val face: EchoFace, val gaze: EchoGaze)

/**
 * One big eye seen through a magnifier over Echo's screen: centred on the glass at [centerX], [centerY]
 * with the glass [radius] (fractions of the square frame), drawn at [scale] of its full size as the glass
 * arrives or leaves, or as a blink. The clip's glass band holds where it shows (the glass, less its glints).
 */
data class EchoLensEye(val centerX: Float, val centerY: Float, val radius: Float, val scale: Float, val blink: Boolean)

/**
 * A sequence plays [steps] once, then repeats [loop] forever. Without a loop, its last step is a still
 * held forever (a zero hold).
 */
data class EchoSequence(val steps: List<EchoCharacterStep>, val loop: List<EchoCharacterStep> = emptyList()) {
    init {
        require(steps.isNotEmpty())
        if (loop.isEmpty()) require(steps.last().millis == 0L) { "A sequence without a loop ends on a resting still" } else require(loop.all { it.millis > 0 })
        require(steps.dropLast(if (loop.isEmpty()) 1 else 0).all { it.millis > 0 })
    }

    /** When the first still begins: the eyes' idle script runs from here. */
    val restStartMillis: Long get() = steps.takeWhile { it.clip != null }.sumOf { it.millis }

    val lastStill: String get() = (steps + loop).last { it.clip == null }.frame
}

/** Where a sequence is: the step showing, how far into it, and its position in play order (it changes with every step). */
data class EchoTimelinePoint(val step: EchoCharacterStep, val stepElapsed: Long, val order: Long, val next: EchoCharacterStep?, val untilNext: Long)

fun EchoSequence.at(elapsedMillis: Long): EchoTimelinePoint {
    var t = elapsedMillis.coerceAtLeast(0L)
    steps.forEachIndexed { index, step ->
        val last = index == steps.lastIndex
        if (step.millis == 0L) return EchoTimelinePoint(step, t, index.toLong(), null, Long.MAX_VALUE)
        if (t < step.millis) return EchoTimelinePoint(step, t, index.toLong(), if (last) loop.firstOrNull() else steps[index + 1], step.millis - t)
        t -= step.millis
    }
    val cycle = loop.sumOf { it.millis }
    val round = t / cycle
    t %= cycle
    loop.forEachIndexed { index, step ->
        if (t < step.millis) {
            val order = steps.size + round * loop.size + index
            return EchoTimelinePoint(step, t, order, loop[(index + 1) % loop.size], step.millis - t)
        }
        t -= step.millis
    }
    error("Unreachable: the loop covers its whole cycle")
}

/** One plane of a decoded YUV frame: its bytes and how far apart rows and pixels sit. */
class EchoYuvPlane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int) {
    fun at(row: Int, col: Int): Int = buffer[row * rowStride + col * pixelStride].toInt() and 0xFF
}

/** A decoded YUV 4:2:0 frame (chroma at half resolution), read from [cropLeft], [cropTop]. */
class EchoYuvFrame(val y: EchoYuvPlane, val u: EchoYuvPlane, val v: EchoYuvPlane, val cropLeft: Int = 0, val cropTop: Int = 0)

/**
 * Unpacks one decoded frame of an alpha-packed clip (BT.601 limited range) into [size] x [size] ARGB
 * pixels: colour from the top band, alpha from the second band's luma, and, when [maskOut] is given, the
 * glass mask from the third band's luma (one byte per pixel). Near-empty and near-full alpha snap to
 * 0 and 255, so compression noise never leaves a haze or holes.
 */
fun unpackEchoClipFrame(frame: EchoYuvFrame, size: Int, out: IntArray, maskOut: ByteArray? = null) {
    if (maskOut != null) {
        for (row in 0 until size) {
            for (col in 0 until size) {
                maskOut[row * size + col] = limitedToFull(frame.y.at(frame.cropTop + 2 * size + row, frame.cropLeft + col)).toByte()
            }
        }
    }
    for (row in 0 until size) {
        for (col in 0 until size) {
            out[row * size + col] = echoClipPixel(frame, size, row, col)
        }
    }
}

/** One ARGB pixel: transparent where the alpha band is (nearly) empty, otherwise the colour band's YUV as RGB. */
private fun echoClipPixel(frame: EchoYuvFrame, size: Int, row: Int, col: Int): Int {
    val x = frame.cropLeft + col
    val alpha = snapAlpha(limitedToFull(frame.y.at(frame.cropTop + size + row, x)))
    if (alpha == 0) return 0
    val chromaRow = (frame.cropTop + row) / 2
    val c = frame.y.at(frame.cropTop + row, x) - 16
    val d = frame.u.at(chromaRow, x / 2) - 128
    val e = frame.v.at(chromaRow, x / 2) - 128
    val r = clampByte((298 * c + 409 * e + 128) shr 8)
    val g = clampByte((298 * c - 100 * d - 208 * e + 128) shr 8)
    val b = clampByte((298 * c + 516 * d + 128) shr 8)
    return (alpha shl 24) or (r shl 16) or (g shl 8) or b
}

private fun snapAlpha(alpha: Int): Int = when {
    alpha < EchoAlphaFloor -> 0
    alpha > 255 - EchoAlphaFloor -> 255
    else -> alpha
}

private fun limitedToFull(luma: Int): Int = clampByte((298 * (luma - 16) + 128) shr 8)

private fun clampByte(value: Int): Int = if (value < 0) {
    0
} else if (value > 255) {
    255
} else {
    value
}

private const val EchoAlphaFloor = 8
