package cx.aswin.boxlore.core.designsystem.echo

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Decodes Echo's alpha-packed clips with the platform video decoder, in order from each clip's start,
 * and keeps a few tinted frames ready ahead of the one on screen, plus the opening frames of the clip
 * that plays next. All decoding runs on one background thread. Frames that fall behind are dropped and
 * their bitmaps reused once nothing can still be drawing them.
 */
class EchoClipFrames(private val assets: AssetManager, private val paint: EchoPaint) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "EchoClipDecoder") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val frames = ConcurrentHashMap<EchoClipFrameKey, EchoClipFrame>()

    // Decode thread only.
    private val decoders = HashMap<String, EchoClipDecoder>()
    private val broken = HashSet<String>()
    private val spare = ArrayDeque<Pair<Bitmap, Long>>()
    private var pixels = IntArray(0)
    private var maskBytes = ByteArray(0)

    @Volatile private var closed = false

    /** The decoded frame [index] of [clip], or the nearest one just before it; null when none is ready. */
    fun frame(clip: String, index: Int): EchoClipFrame? = (index downTo maxOf(0, index - 2)).firstNotNullOfOrNull { frames[EchoClipFrameKey(clip, it)] }

    /** Readies frames [frame] onwards of [current] and the opening frames of [upcoming]; drops everything else. */
    suspend fun prepare(current: EchoMotionClip?, frame: Int, upcoming: EchoMotionClip?) = withContext(dispatcher) {
        if (closed) return@withContext
        val keep = setOfNotNull(current?.id, upcoming?.id)
        decoders.keys.filter { it !in keep }.forEach { decoders.remove(it)?.close() }
        frames.keys.filter { it.clip !in keep || current?.leftBehind(it, frame) == true }.forEach(::release)
        current?.let { fill(it, frame, frame + EchoClipAhead) }
        upcoming?.takeIf { it.id != current?.id }?.let { fill(it, 0, EchoClipAhead - 1) }
    }

    /** A frame of this clip that playback at [frame] has moved past; the clip's last few frames stay for the next loop round. */
    private fun EchoMotionClip.leftBehind(key: EchoClipFrameKey, frame: Int): Boolean =
        key.clip == id && key.index < frame - 2 && key.index < frames - EchoClipAhead

    private fun fill(clip: EchoMotionClip, from: Int, to: Int) {
        if (clip.id in broken) return
        val last = minOf(to, clip.frames - 1)
        if ((from..last).all { frames.containsKey(EchoClipFrameKey(clip.id, it)) }) return
        try {
            val decoder = decoders.getOrPut(clip.id) { EchoClipDecoder(assets, clip) }
            // A loop came back round to an earlier frame: start the clip again.
            if (decoder.nextIndex > from && !frames.containsKey(EchoClipFrameKey(clip.id, from))) decoder.rewind()
            sizeBuffers(clip)
            decodeUntil(decoder, clip, from, last)
        } catch (failure: Exception) {
            // Echo then holds its rest still for this clip; nothing else depends on it.
            Log.w("EchoCharacter", "Unable to play Echo clip ${clip.id}", failure)
            broken += clip.id
            decoders.remove(clip.id)?.close()
        }
    }

    private fun sizeBuffers(clip: EchoMotionClip) {
        val area = clip.size * clip.size
        if (pixels.size != area) pixels = IntArray(area)
        if (clip.mask && maskBytes.size != area) maskBytes = ByteArray(area)
    }

    /** Decodes in order up to frame [last], keeping the frames from [from] on that aren't ready yet; stops if the clip ends. */
    private fun decodeUntil(decoder: EchoClipDecoder, clip: EchoMotionClip, from: Int, last: Int) {
        var decoded = true
        while (decoded && decoder.nextIndex <= last) {
            val index = decoder.nextIndex
            decoded = decoder.decodeNext(pixels, maskBytes.takeIf { clip.mask })
            if (decoded && index >= from && !frames.containsKey(EchoClipFrameKey(clip.id, index))) keep(clip, index)
        }
    }

    /** Tints the frame just decoded and keeps it, with its glass mask, in reused bitmaps. */
    private fun keep(clip: EchoMotionClip, index: Int) {
        paint.tint(pixels)
        val bitmap = obtain(clip.size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, clip.size, 0, 0, clip.size, clip.size)
        val mask = if (clip.mask) obtain(clip.size, Bitmap.Config.ALPHA_8).also { it.copyPixelsFromBuffer(ByteBuffer.wrap(maskBytes)) } else null
        frames[EchoClipFrameKey(clip.id, index)] = EchoClipFrame(bitmap.asImageBitmap(), mask?.asImageBitmap(), index)
    }

    private fun release(key: EchoClipFrameKey) {
        frames.remove(key)?.let { frame ->
            val now = SystemClock.uptimeMillis()
            spare.addLast(frame.image.asAndroidBitmap() to now)
            frame.mask?.let { spare.addLast(it.asAndroidBitmap() to now) }
        }
        while (spare.size > EchoSpareBitmaps) spare.removeFirst()
    }

    /** A bitmap to decode into: one dropped long enough ago that no frame can still be drawing it, or a new one. */
    private fun obtain(size: Int, config: Bitmap.Config): Bitmap {
        val now = SystemClock.uptimeMillis()
        val reusable = spare.indexOfFirst { (bitmap, at) -> now - at > EchoBitmapCoolMillis && bitmap.width == size && bitmap.height == size && bitmap.config == config }
        if (reusable >= 0) return spare.removeAt(reusable).first
        return Bitmap.createBitmap(size, size, config)
    }

    override fun close() {
        closed = true
        executor.execute {
            decoders.values.forEach { it.close() }
            decoders.clear()
            frames.clear()
            spare.clear()
        }
        executor.shutdown()
    }
}

private data class EchoClipFrameKey(val clip: String, val index: Int)

/** Decoded clip frame [index], tinted, and where the screen's glass is visible in it (alpha only), when the clip has a mask. */
class EchoClipFrame(val image: ImageBitmap, val mask: ImageBitmap?, val index: Int)

/** One clip's decoder: frames come out in order from the start; [rewind] starts again. Not thread-safe. */
private class EchoClipDecoder(assets: AssetManager, private val clip: EchoMotionClip) : Closeable {
    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var outputDone = false

    /** The frame [decodeNext] produces next. */
    var nextIndex = 0
        private set

    init {
        assets.openFd("$EchoCharacterAssets/${clip.file}").use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
        codec.configure(format, null, null, 0)
        codec.start()
    }

    /** Decodes the next frame into [out] (size x size ARGB) and, given [mask], its glass mask. False once the clip has ended. */
    fun decodeNext(out: IntArray, mask: ByteArray?): Boolean {
        if (outputDone) return false
        repeat(EchoDecodeAttempts) {
            if (!inputDone) feedInput()
            takeOutput(out, mask)?.let { return it }
        }
        error("Echo clip ${clip.id} stalled at frame $nextIndex")
    }

    /** One output buffer, if the codec has one ready: true for a decoded frame, false once the clip has ended, null for nothing yet. */
    private fun takeOutput(out: IntArray, mask: ByteArray?): Boolean? {
        val index = codec.dequeueOutputBuffer(info, EchoDecodeTimeoutUs)
        if (index < 0) return null
        val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
        val produced = info.size > 0
        if (produced) codec.getOutputImage(index)?.use { unpack(it, out, mask) }
        codec.releaseOutputBuffer(index, false)
        if (end) outputDone = true
        if (produced) nextIndex++
        return when {
            produced -> true
            end -> false
            else -> null
        }
    }

    fun rewind() {
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        codec.flush()
        inputDone = false
        outputDone = false
        nextIndex = 0
    }

    private fun feedInput() {
        val index = codec.dequeueInputBuffer(EchoDecodeTimeoutUs)
        if (index < 0) return
        val buffer = codec.getInputBuffer(index) ?: return
        val read = extractor.readSampleData(buffer, 0)
        if (read < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(index, 0, read, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun unpack(image: Image, out: IntArray, mask: ByteArray?) {
        val (y, u, v) = image.planes.map { EchoYuvPlane(it.buffer, it.rowStride, it.pixelStride) }
        unpackEchoClipFrame(EchoYuvFrame(y, u, v, image.cropRect.left, image.cropRect.top), clip.size, out, mask)
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }
}

/** Frames decoded ahead of the one on screen (about 170ms at 24fps). */
private const val EchoClipAhead = 4
private const val EchoSpareBitmaps = 12
private const val EchoBitmapCoolMillis = 400L
private const val EchoDecodeTimeoutUs = 10_000L
private const val EchoDecodeAttempts = 300
