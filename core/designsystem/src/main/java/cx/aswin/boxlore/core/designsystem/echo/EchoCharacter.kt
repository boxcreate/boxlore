package cx.aswin.boxlore.core.designsystem.echo

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * The redesigned Echo, from the `echo-character` assets: stills and motion clips with a blank screen,
 * played in sequence, with the face (two pixel eyes) drawn in code on the screen's LED grid and the
 * accent parts tinted from the theme. Clips are real motion (alpha-packed video, decoded frame by frame
 * by [EchoClipFrames]); the eyes follow the screen through every clip frame, tilting with the head.
 * Between clips Echo rests on a still with its body still. With [idle] its eyes come alive there: they
 * dart to things and hold, blink as they move, and change shape now and then (see [EchoBeat]); without
 * it, Echo blinks on an uneven rhythm. Changing from one step to the next cross-fades briefly. With
 * animations off it shows the sequence's resting still, looking straight out.
 *
 * With [stageColor], Echo stands on its stage: a soft Material cookie shape behind it, a bit bigger
 * than its resting pose ([EchoStageOverEcho] times its height) and centred on it, measured from the
 * manifest's reach, so it fits any pose's framing.
 *
 * [frameScale] draws the square frame at that multiple of the canvas height, with Echo standing at
 * [floor] of the canvas height, so Echo can fill a wide, short box (its head, raised arms and hops reach
 * above it).
 *
 * [accents] colours Echo's accent parts and face: the theme's, calmed, by default (see [EchoAccents]).
 *
 * Everything starts after [startDelayMillis]. A new [playbackKey] (a new question, say) plays the
 * sequence again; [paused] holds Echo where it is. Stills decode off the main thread at the drawn size
 * and stay cached, with [prefetch] sequences' stills loaded ahead; if a frame isn't ready, the last one
 * stays up, so Echo never flashes empty.
 */
@Suppress("LongParameterList")
@Composable
fun EchoCharacter(
    sequence: String,
    modifier: Modifier = Modifier,
    playbackKey: Any? = null,
    paused: Boolean = false,
    startDelayMillis: Long = 0L,
    idle: EchoIdle? = null,
    prefetch: List<String> = emptyList(),
    frameScale: Float = 1f,
    floor: Float = EchoFloorLine,
    stageColor: Color? = null,
    accents: EchoAccents = EchoAccents.Theme,
) {
    val assets = LocalContext.current.applicationContext.assets
    val spec = rememberEchoCharacterSpec()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val dark = LocalEffectiveDarkTheme.current
    val paint = remember(accents, accent, dark) { accents.paint(accent, dark) }
    var sizePx by remember { mutableIntStateOf(0) }
    val stills = rememberEchoStills(spec, listOf(sequence) + prefetch, echoDecodeSize(sizePx), paint)
    val clipFrames = remember(assets, paint) { EchoClipFrames(assets, paint) }
    DisposableEffect(clipFrames) { onDispose { clipFrames.close() } }
    // A still with no clip or idle script (a story tile, the story entry card) draws once: its clock never runs.
    val still = idle == null && spec?.isStill(sequence) == true
    val (elapsed, disabled) = rememberEchoCharacterClock(sequence to playbackKey, paused || still, LocalEchoMotion.current)
    // Decode ahead of playback: the clip on screen, and the next one shortly before it starts.
    LaunchedEffect(spec, sequence, playbackKey, clipFrames) {
        val loaded = spec ?: return@LaunchedEffect
        val timeline = loaded.sequence(sequence)
        snapshotFlow { if (disabled.value) null else timeline.at(elapsed.value - startDelayMillis) }.collect { point ->
            point?.let { clipFrames.prepareFor(loaded, it) }
        }
    }
    val renderer = remember(clipFrames) { EchoRenderer(clipFrames) }
    val placement = EchoPlacement(frameScale, floor, stageColor, remember(paint) { Color(paint.face) }, idle)
    Canvas(modifier.aspectRatio(1f).onSizeChanged { sizePx = it.width }) {
        val loaded = spec ?: return@Canvas
        with(renderer) { draw(loaded, sequence, elapsed.value - startDelayMillis, disabled.value, stills, placement) }
    }
}

/** Echo's manifest, read once per process off the main thread; null until it loads (or if it can't). */
@Composable
fun rememberEchoCharacterSpec(): EchoCharacterSpec? {
    val assets = LocalContext.current.applicationContext.assets
    val spec by produceState<EchoCharacterSpec?>(EchoCharacterSpecCache.loaded, assets) {
        value = EchoCharacterSpecCache.loaded ?: loadEchoCharacterSpec(assets)?.also { EchoCharacterSpecCache.loaded = it }
    }
    return spec
}

private object EchoCharacterSpecCache {
    @Volatile var loaded: EchoCharacterSpec? = null
}

/** Readies the clip at [point] from its current frame, and the next clip shortly before it starts. */
private suspend fun EchoClipFrames.prepareFor(loaded: EchoCharacterSpec, point: EchoTimelinePoint) {
    val current = point.step.clip?.let(loaded.clips::get)
    val upcoming = point.next?.clip?.takeIf { point.untilNext < EchoClipLeadMillis }?.let(loaded.clips::get)
    prepare(current, current?.frameAt(point.stepElapsed) ?: 0, upcoming)
}

/** The stills [sequences] use, decoded and tinted off the main thread at [decodeSize] and kept while the size and theme hold. */
@Composable
private fun rememberEchoStills(spec: EchoCharacterSpec?, sequences: List<String>, decodeSize: Int, paint: EchoPaint): Map<String, ImageBitmap> {
    val assets = LocalContext.current.applicationContext.assets
    val frames = remember(paint, decodeSize) { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(spec, sequences, frames, decodeSize) {
        val loaded = spec ?: return@LaunchedEffect
        if (decodeSize == 0) return@LaunchedEffect
        sequences.flatMap { loaded.stills(it) }.distinct().filter { it !in frames }.forEach { id ->
            withContext(Dispatchers.Default) { decodeTintedFrame(assets, id, decodeSize, paint) }?.let { frames[id] = it }
        }
    }
    return frames
}

/** How Echo sits on the canvas and how it is dressed: see [EchoCharacter]. */
private class EchoPlacement(val frameScale: Float, val floor: Float, val stageColor: Color?, val faceColor: Color, val idle: EchoIdle?)

/** Draws Echo at a moment of its sequence: the stage, the frame (cross-faded between steps) and the eyes. Used during drawing only. */
private class EchoRenderer(private val clipFrames: EchoClipFrames) {
    private val shown = EchoShownFrame()
    private val stage = EchoStageShape()
    private val painter = EchoFacePainter()

    fun DrawScope.draw(loaded: EchoCharacterSpec, sequence: String, t: Long, still: Boolean, stills: Map<String, ImageBitmap>, placement: EchoPlacement) {
        val timeline = loaded.sequence(sequence)
        val point = if (still) null else timeline.at(t)
        val step = point?.step ?: EchoCharacterStep(timeline.lastStill, EchoFace.Idle, 0L)
        val clip = step.clip?.let(loaded.clips::get)
        if (point != null) shown.enter(point.order, t)
        show(loaded, step, clip, point, stills[step.frame])
        val image = shown.image ?: return
        // The frame, on whole pixels: at frameScale x the canvas height, its floor line at [floor] on the canvas.
        val side = (size.height * placement.frameScale).roundToInt()
        val frame = IntOffset(((size.width - side) / 2f).roundToInt(), (size.height * placement.floor - side * EchoFloorLine).roundToInt())
        placement.stageColor?.let { color -> loaded.reaches[timeline.lastStill]?.let { rest -> drawEchoStage(stage, rest, frame, side, color) } }
        drawEchoFrame(shown, image, frame, side, t)
        val on = shown.screen ?: return
        shown.lens?.let { lens ->
            with(painter) { drawLensEye(lens, on, frame, side, placement.faceColor, shown.mask) }
            return
        }
        val pose = echoRestingPose(point, clip, step, placement.idle, t - timeline.restStartMillis)
        with(painter) { drawFace(on, frame, side, echoLookAt(still, step, pose, shown.scripted, t), placement.faceColor, shown.mask) }
    }

    /** The clip frame due now, or the step's still; while neither is ready the last frame stays up (or the rest still, before anything has shown). */
    private fun show(loaded: EchoCharacterSpec, step: EchoCharacterStep, clip: EchoMotionClip?, point: EchoTimelinePoint?, still: ImageBitmap?) {
        val clipIndex = if (clip != null && point != null) clip.frameAt(point.stepElapsed) else -1
        val clipImage = clip?.let { clipFrames.frame(it.id, clipIndex) }
        when {
            clip != null && clipImage != null -> shown.showClip(clipImage, clip)
            still != null && (clip == null || shown.image == null) -> shown.showStill(still, loaded.screens.getValue(step.frame))
        }
    }
}

/** The frame on show, with the previous step's frame fading out over it for [EchoCrossFadeMillis]. */
private fun DrawScope.drawEchoFrame(shown: EchoShownFrame, image: ImageBitmap, frame: IntOffset, side: Int, t: Long) {
    val dst = IntSize(side, side)
    drawImage(image, srcSize = IntSize(image.width, image.height), dstOffset = frame, dstSize = dst, filterQuality = FilterQuality.High)
    val fade = shown.fadeFrom ?: return
    val fadeProgress = (t - shown.fadeStart) / EchoCrossFadeMillis.toFloat()
    if (fadeProgress < 1f && fade !== image) {
        drawImage(fade, srcSize = IntSize(fade.width, fade.height), dstOffset = frame, dstSize = dst, alpha = 1f - fadeProgress, filterQuality = FilterQuality.High)
    }
}

/** Echo's eye script while it rests on a still with its idle face; nothing during clips or other faces. */
private fun echoRestingPose(point: EchoTimelinePoint?, clip: EchoMotionClip?, step: EchoCharacterStep, idle: EchoIdle?, restingMillis: Long): EchoIdlePose? {
    if (point == null || clip != null || idle == null) return null
    return if (step.face == EchoFace.Idle && restingMillis >= 0) echoIdlePoseAt(restingMillis, idle) else null
}

/**
 * What Echo's face shows and where it looks: straight out when still, its idle script when resting, a
 * clip's face script during the clip, otherwise the step's face, blinking now and then.
 */
private fun echoLookAt(still: Boolean, step: EchoCharacterStep, pose: EchoIdlePose?, scripted: EchoClipBeat?, t: Long): EchoLook = EchoLook(
    face = when {
        still -> EchoFace.Idle
        pose != null -> pose.face
        scripted != null -> scripted.face
        step.face == EchoFace.Idle && echoIsBlinking(t) -> EchoFace.Blink
        else -> step.face
    },
    gaze = if (still) EchoGaze(0f, 0f) else pose?.gaze ?: scripted?.gaze ?: EchoGaze(0f, 0f),
)

private data class EchoLook(val face: EchoFace, val gaze: EchoGaze)

/**
 * Draws the eyes on Echo's screen, tilted with it. With a glass [mask] (clips) they show through the
 * glass only, so whatever Echo holds in front of its screen covers them. Eye layouts depend only on the
 * face, its whole-cell position and the grid, so each is built once. Used during drawing only.
 */
private class EchoFacePainter {
    private val eyes = HashMap<Triple<EchoFace, EchoFaceStep, Long>, List<EchoEye>>()

    fun DrawScope.drawFace(on: EchoScreenOnFrame, frame: IntOffset, side: Int, look: EchoLook, color: Color, mask: ImageBitmap?) {
        val screen = Rect(
            frame.x + (on.centerX - on.width / 2f) * side,
            frame.y + (on.centerY - on.height / 2f) * side,
            frame.x + (on.centerX + on.width / 2f) * side,
            frame.y + (on.centerY + on.height / 2f) * side,
        )
        val grid = echoGrid(screen.width, screen.height)
        val faceStep = echoFaceStep(look.gaze, grid)
        val layout = eyes.getOrPut(Triple(look.face, faceStep, grid.columns * 10_000L + grid.rows)) { echoFaceEyes(look.face, faceStep, grid) }
        if (mask == null) {
            rotate(on.degrees, pivot = screen.center) { drawEchoFace(layout, grid, screen, color, snap = on.degrees == 0f) }
            return
        }
        drawContext.canvas.saveLayer(Rect(frame.x.toFloat(), frame.y.toFloat(), (frame.x + side).toFloat(), (frame.y + side).toFloat()), Paint())
        rotate(on.degrees, pivot = screen.center) { drawEchoFace(layout, grid, screen, color, snap = false) }
        drawImage(mask, srcSize = IntSize(mask.width, mask.height), dstOffset = frame, dstSize = IntSize(side, side), blendMode = BlendMode.DstIn)
        drawContext.canvas.restore()
    }

    /**
     * One big eye through a magnifier, centred on the glass and tilted with the screen, showing only where
     * the clip's glass band says (the glass, less its glints, which stay on top).
     */
    fun DrawScope.drawLensEye(lens: EchoLensEye, on: EchoScreenOnFrame, frame: IntOffset, side: Int, color: Color, mask: ImageBitmap?) {
        val radius = lens.radius * side
        val pitch = minOf(EchoLensPitchOverScreen * on.width * side, radius * EchoLensPitchOverGlass) * lens.scale
        val pattern = if (lens.blink) EchoLensBlink else EchoLensOpen
        val rows = pattern.size
        val columns = pattern.first().length
        val center = Offset(frame.x + lens.centerX * side, frame.y + lens.centerY * side)
        val dot = pitch * EchoDotFill
        val inset = (pitch - dot) / 2f
        val bounds = Rect(frame.x.toFloat(), frame.y.toFloat(), (frame.x + side).toFloat(), (frame.y + side).toFloat())
        drawContext.canvas.saveLayer(bounds, Paint())
        rotate(on.degrees, pivot = center) {
            val glowRadius = maxOf(rows, columns) * pitch * EchoLensGlow
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.18f), Color.Transparent), center = center, radius = glowRadius), glowRadius, center)
            pattern.forEachIndexed { row, line ->
                line.forEachIndexed { col, cell ->
                    if (cell == '#') {
                        val topLeft = Offset(center.x + (col - columns / 2f) * pitch + inset, center.y + (row - rows / 2f) * pitch + inset)
                        drawRoundRect(color, topLeft, Size(dot, dot), CornerRadius(dot * 0.25f))
                    }
                }
            }
        }
        if (mask != null) {
            drawImage(mask, srcSize = IntSize(mask.width, mask.height), dstOffset = frame, dstSize = IntSize(side, side), blendMode = BlendMode.DstIn)
        }
        drawContext.canvas.restore()
    }
}

/** The magnified eye's dots: open, and a blink. */
private val EchoLensOpen = listOf(".##.", "####", "####", "####", "####", ".##.")
private val EchoLensBlink = listOf("####", "####")
private const val EchoLensPitchOverScreen = 1.5f / 26f
private const val EchoLensPitchOverGlass = 2f * 0.8f / 6f
private const val EchoLensGlow = 1.3f

/** Echo's stage, centred on its resting pose and [EchoStageOverEcho] times its height. */
private fun DrawScope.drawEchoStage(stage: EchoStageShape, rest: EchoReach, frame: IntOffset, side: Int, color: Color) {
    val size = (rest.bottom - rest.top) * side * EchoStageOverEcho
    val center = Offset(frame.x + (rest.left + rest.right) / 2f * side, frame.y + (rest.top + rest.bottom) / 2f * side)
    drawPath(stage.pathAt(size, center), color)
}

/** The stage's outline, a Material cookie, rebuilt only when its size or place changes. Used during drawing only. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class EchoStageShape {
    private val polygon = MaterialShapes.Cookie9Sided.normalized()
    private val path = android.graphics.Path()
    private val outline = path.asComposePath()
    private val matrix = android.graphics.Matrix()
    private var key: Triple<Float, Float, Float>? = null

    fun pathAt(size: Float, center: Offset): Path {
        val next = Triple(size, center.x, center.y)
        if (next != key) {
            path.reset()
            polygon.toPath(path)
            matrix.setScale(size, size)
            matrix.postTranslate(center.x - size / 2f, center.y - size / 2f)
            path.transform(matrix)
            key = next
        }
        return outline
    }
}

/** Where Echo's screen sits on the frame on show: centre and size as fractions of the frame, tilt in degrees. */
private data class EchoScreenOnFrame(val centerX: Float, val centerY: Float, val width: Float, val height: Float, val degrees: Float)

/** What is on screen, kept while the next frame decodes and faded out when the step changes. Written during drawing only. */
private class EchoShownFrame {
    var image: ImageBitmap? = null
        private set
    var mask: ImageBitmap? = null
        private set
    var screen: EchoScreenOnFrame? = null
        private set
    var lens: EchoLensEye? = null
        private set

    /** The clip's scripted face and gaze for the frame on show; null for unscripted clips and stills. */
    var scripted: EchoClipBeat? = null
        private set
    var fadeFrom: ImageBitmap? = null
        private set
    var fadeStart = 0L
        private set
    private var order = -1L

    /** A new step starts: what was on show fades out from [t]. */
    fun enter(stepOrder: Long, t: Long) {
        if (stepOrder == order) return
        fadeFrom = image
        fadeStart = t
        order = stepOrder
    }

    fun showClip(frame: EchoClipFrame, clip: EchoMotionClip) {
        val track = clip.tracks[frame.index]
        image = frame.image
        mask = frame.mask
        screen = EchoScreenOnFrame(track.centerX, track.centerY, track.width, track.height, track.degrees)
        lens = clip.lensAt(frame.index)
        scripted = if (clip.script.isNotEmpty() || frame.index in clip.blinks) clip.lookAt(frame.index) else null
    }

    fun showStill(still: ImageBitmap, box: EchoScreenBox) {
        image = still
        mask = null
        lens = null
        scripted = null
        screen = EchoScreenOnFrame(box.centerX, box.centerY, box.right - box.left, box.bottom - box.top, 0f)
    }
}

/** Frames decode at the drawn size, in 64px steps so small layout changes don't decode again, never above the 512px source. */
fun echoDecodeSize(drawnPx: Int): Int = if (drawnPx <= 0) 0 else minOf(EchoSourcePx, (drawnPx + 63) / 64 * 64)

/** Lit dots on the screen's LED grid: each eye is a small pixel pattern with a soft glow behind it. [snap] puts dots on whole pixels (an upright, resting screen). */
private fun DrawScope.drawEchoFace(eyes: List<EchoEye>, grid: EchoGrid, screen: Rect, color: Color, snap: Boolean) {
    val pitch = grid.pitch
    val whole = snap && grid.wholePixels
    fun snap(value: Float) = if (whole) value.roundToInt().toFloat() else value
    val originX = snap(screen.left + (screen.width - grid.columns * pitch) / 2f)
    val originY = snap(screen.top + (screen.height - grid.rows * pitch) / 2f)
    val dot = if (whole) maxOf(2, (pitch * EchoDotFill).roundToInt()).toFloat() else pitch * EchoDotFill
    val inset = snap((pitch - dot) / 2f)
    eyes.forEach { eye ->
        val center = Offset(originX + (eye.col + eye.width / 2f) * pitch, originY + (eye.row + eye.height / 2f) * pitch)
        val glowRadius = maxOf(eye.width, eye.height) * pitch * 0.9f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent), center = center, radius = glowRadius), glowRadius, center)
        eye.cells.forEach { cell ->
            if (cell.col in 0 until grid.columns && cell.row in 0 until grid.rows) {
                drawRoundRect(color, Offset(originX + cell.col * pitch + inset, originY + cell.row * pitch + inset), Size(dot, dot), CornerRadius(dot * 0.25f))
            }
        }
    }
}

/**
 * Elapsed time every frame (resting motion is continuous), scaled by the system animation scale and
 * restarting with [key]; it holds while [paused] or in the background. The second value is `true` when
 * animations are off.
 */
@Composable
private fun rememberEchoCharacterClock(key: Any, paused: Boolean, motion: Boolean): Pair<State<Long>, State<Boolean>> {
    val elapsed = remember(key) { mutableLongStateOf(0L) }
    val disabled = remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(key, owner, paused, motion) {
        if (!motion) {
            disabled.value = true
            return@LaunchedEffect
        }
        if (paused) return@LaunchedEffect
        val scale = currentCoroutineContext()[MotionDurationScale]
        var accumulated = elapsed.longValue * NanosPerMillisecond
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { scale?.scaleFactor ?: 1f }.collectLatest { factor ->
                disabled.value = factor <= 0f
                if (factor <= 0f) return@collectLatest
                var previous = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (previous != 0L) {
                            accumulated = EchoFrameTiming.advanceElapsedNanos(accumulated, now - previous, factor)
                            elapsed.longValue = EchoFrameTiming.elapsedMillis(accumulated)
                        }
                        previous = now
                    }
                }
            }
        }
    }
    return elapsed to disabled
}

private suspend fun loadEchoCharacterSpec(assets: AssetManager): EchoCharacterSpec? = try {
    withContext(Dispatchers.IO) { assets.open("$EchoCharacterAssets/manifest.json").bufferedReader().use { parseEchoCharacterManifest(it.readText()) } }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Log.w("EchoCharacter", "Unable to load Echo manifest", failure)
    null
}

private fun decodeTintedFrame(assets: AssetManager, id: String, sizePx: Int, paint: EchoPaint): ImageBitmap? = try {
    assets.open("$EchoCharacterAssets/$id.webp").use { stream ->
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = requireNotNull(BitmapFactory.decodeStream(stream, null, options))
        val bitmap = if (source.width > sizePx) {
            Bitmap.createScaledBitmap(source, sizePx, sizePx, true).also { if (it !== source) source.recycle() }
        } else {
            source
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        paint.tint(pixels)
        val tinted = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        tinted.setPixels(pixels, 0, tinted.width, 0, 0, tinted.width, tinted.height)
        tinted.asImageBitmap()
    }
} catch (failure: Exception) {
    Log.w("EchoCharacter", "Unable to load Echo frame $id", failure)
    null
}

const val EchoCharacterAssets = "echo-character"
private const val EchoSourcePx = 512
private const val EchoDotFill = 0.74f
private const val NanosPerMillisecond = 1_000_000.0
private const val EchoCrossFadeMillis = 120L

/**
 * Whether Echo moves. Off, every Echo shows its sequence's resting still, as with animations turned off.
 * UI tests and screenshot captures turn it off: otherwise Echo animates forever and the screen never idles.
 */
val LocalEchoMotion = staticCompositionLocalOf { true }

/** Echo's stage is this much taller than Echo at rest. */
const val EchoStageOverEcho = 1.14f

/** Where Echo stands in its square frame (the clips' and stills' floor line), as a share of the frame height. */
const val EchoFloorLine = 0.92f

/** How long before a clip starts its first frames decode. */
private const val EchoClipLeadMillis = 600L

/** Frame timing for Echo's clock: long gaps are capped before scaling, so returning to a screen never fast-forwards Echo. */
private object EchoFrameTiming {
    private const val MaxFrameDeltaNanos = 100_000_000L

    fun advanceElapsedNanos(elapsedNanos: Double, frameDeltaNanos: Long, scale: Float): Double =
        elapsedNanos + frameDeltaNanos.coerceIn(0L, MaxFrameDeltaNanos) / scale.toDouble()

    fun elapsedMillis(elapsedNanos: Double): Long = (elapsedNanos / NanosPerMillisecond).toLong()
}
