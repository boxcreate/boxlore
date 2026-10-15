package cx.aswin.boxlore.feature.onboarding

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A decoded atlas; [scale] is its size relative to the 384 × 256 px source cells. */
internal class EchoOnboardingStrip(val spec: EchoOnboardingSpec, val image: ImageBitmap, val scale: Float)

private class EchoDrawnFrame(var index: Int)

/**
 * Echo's animated poses. Strips decode off the main thread at the size they are drawn, and the
 * current phase's strips plus the next likely one are loaded ahead, so a pose change never
 * waits on decoding. Frames are read during drawing only.
 */
@Composable
internal fun EchoOnboardingAvatar(
    phase: EchoOnboardingPhase,
    playbackKey: String,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    /** Load the next likely strips ahead; off where Echo only ever rests (Welcome). */
    prefetch: Boolean = true,
) {
    val assets = LocalContext.current.applicationContext.assets
    val specs by produceState<Map<String, EchoOnboardingSpec>?>(null, assets) {
        try {
            value = withContext(Dispatchers.IO) { assets.open("echo-onboarding/manifest.json").bufferedReader().use { parseEchoOnboardingSpecs(it.readText()) } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("EchoOnboarding", "Unable to load bundled manifest", failure)
        }
    }
    val cache = remember(assets) { EchoStripCache(assets) }
    val (elapsed, disabled) = rememberEchoOnboardingClock(phase, playbackKey, paused)
    BoxWithConstraints(modifier.aspectRatio(1.5f)) {
        val metadata = specs ?: return@BoxWithConstraints
        val cellWidthPx = constraints.maxWidth.coerceAtLeast(1)
        LaunchedEffect(phase, metadata, cellWidthPx, prefetch) {
            if (prefetch) echoPrefetchStrips(phase).forEach { id -> cache.load(metadata.getValue(id), cellWidthPx) }
        }
        // Compose only on a strip boundary. Individual pose frames are read in Canvas below.
        val id by remember(phase, playbackKey, metadata) { derivedStateOf { echoOnboardingPose(phase, elapsed.value, metadata).id } }
        val image by produceState(cache.peek(id), id, cellWidthPx) {
            cache.load(metadata.getValue(id), cellWidthPx)?.let { value = it }
        }
        val current = image ?: return@BoxWithConstraints
        // No crossfade: two half-transparent poses read as a blink. Until the next strip is ready,
        // the last drawn frame holds; then the new strip takes over on its own frame.
        val lastDrawn = remember { EchoDrawnFrame(current.spec.poster) }
        Canvas(Modifier.fillMaxSize()) {
            val pose = echoOnboardingPose(phase, elapsed.value, metadata)
            val spec = current.spec
            val frame = when {
                disabled.value -> spec.poster
                pose.id == spec.id -> spec.frameAt(pose.elapsed).also { lastDrawn.index = it }
                else -> lastDrawn.index.coerceIn(0, spec.durations.lastIndex)
            }
            val source = echoStripCell(spec.geometry, frame, current.scale)
            val geometry = spec.geometry
            val fit = min(size.width / geometry.cellWidth, size.height / geometry.cellHeight)
            val destination = IntSize((geometry.cellWidth * fit).roundToInt(), (geometry.cellHeight * fit).roundToInt())
            val offset = IntOffset(((size.width - destination.width) / 2).roundToInt(), ((size.height - destination.height) / 2).roundToInt())
            drawImage(current.image, source.first, source.second, offset, destination, filterQuality = FilterQuality.Medium)
        }
    }
}

/** The phase's own strips first, then the strip the next likely phase opens with. */
internal fun echoPrefetchStrips(phase: EchoOnboardingPhase): List<String> = when (phase) {
    // A question and the wait after it share these four, so the turn cycle never decodes.
    EchoOnboardingPhase.Welcome -> listOf("welcome", "asking", "waiting", "acknowledging", "thinking")
    EchoOnboardingPhase.Question -> listOf("asking", "waiting", "acknowledging", "thinking")
    EchoOnboardingPhase.Thinking -> listOf("acknowledging", "thinking", "asking", "waiting")
    EchoOnboardingPhase.Preparing -> listOf("preparing", "presenting")
    EchoOnboardingPhase.Presenting -> listOf("presenting", "waiting")
    EchoOnboardingPhase.Rest -> listOf("waiting", "asking", "acknowledging", "thinking")
}

/** Source rectangle of [frame] in an atlas decoded at [scale], without drift across columns. */
internal fun echoStripCell(geometry: EchoSpriteGeometry, frame: Int, scale: Float): Pair<IntOffset, IntSize> {
    val column = frame % geometry.columns
    val row = frame / geometry.columns
    val left = (column * geometry.cellWidth * scale).roundToInt()
    val top = (row * geometry.cellHeight * scale).roundToInt()
    val right = ((column + 1) * geometry.cellWidth * scale).roundToInt()
    val bottom = ((row + 1) * geometry.cellHeight * scale).roundToInt()
    return IntOffset(left, top) to IntSize(right - left, bottom - top)
}

/** Holds at most five decoded strips (up to about 31 MB at full size, less when drawn smaller). */
private class EchoStripCache(private val assets: AssetManager) {
    private val mutex = Mutex()
    private val strips = object : LinkedHashMap<String, EchoOnboardingStrip>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EchoOnboardingStrip>?): Boolean = size > MaxStrips
    }

    fun peek(id: String): EchoOnboardingStrip? = synchronized(strips) { strips[id] }

    suspend fun load(spec: EchoOnboardingSpec, cellWidthPx: Int): EchoOnboardingStrip? = mutex.withLock {
        val scale = (cellWidthPx.toFloat() / spec.geometry.cellWidth).coerceIn(0.25f, 1f)
        synchronized(strips) { strips[spec.id] }?.takeIf { abs(it.scale - scale) < 0.05f }?.let { return@withLock it }
        decode(spec, scale)?.also { strip -> synchronized(strips) { strips[spec.id] = strip } }
    }

    private suspend fun decode(spec: EchoOnboardingSpec, scale: Float): EchoOnboardingStrip? = try {
        withContext(Dispatchers.IO) {
            val (width, height) = spec.geometry.atlasSize(spec.durations.size)
            val options = BitmapFactory.Options().apply {
                if (scale < 1f) {
                    inScaled = true
                    inDensity = spec.geometry.cellWidth
                    inTargetDensity = (spec.geometry.cellWidth * scale).roundToInt()
                }
            }
            assets.open(spec.assetPath).use { stream ->
                val bitmap = requireNotNull(BitmapFactory.decodeStream(stream, null, options))
                val actual = bitmap.width.toFloat() / width
                require(abs(bitmap.height.toFloat() / height - actual) < 0.02f)
                EchoOnboardingStrip(spec, bitmap.asImageBitmap(), actual)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w("EchoOnboarding", "Unable to load bundled artwork", failure)
        null
    }

    private companion object {
        const val MaxStrips = 5
    }
}

/**
 * Poses change every 150 ms or more, so elapsed time is published in [EchoClockStepMillis] steps:
 * Echo redraws about 25 times a second instead of on every display frame. [paused] (a menu or
 * sheet is open over Echo) stops the clock and keeps its place.
 */
@Composable
private fun rememberEchoOnboardingClock(
    phase: EchoOnboardingPhase,
    playbackKey: String,
    paused: Boolean,
): Pair<androidx.compose.runtime.State<Long>, androidx.compose.runtime.State<Boolean>> {
    val elapsed = remember(phase, playbackKey) { mutableLongStateOf(0) }
    val accumulated = remember(phase, playbackKey) { DoubleArray(1) }
    val disabled = remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(phase, playbackKey, owner, paused) {
        if (paused) return@LaunchedEffect
        val scale = currentCoroutineContext()[MotionDurationScale]
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { scale?.scaleFactor ?: 1f }.collectLatest { factor ->
                disabled.value = factor <= 0f
                if (factor <= 0f) return@collectLatest
                var previous = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (previous != 0L) {
                            accumulated[0] = WelcomeFrameTiming.advanceElapsedNanos(accumulated[0], now - previous, factor)
                            val millis = WelcomeFrameTiming.elapsedMillis(accumulated[0])
                            if (millis - elapsed.longValue >= EchoClockStepMillis) elapsed.longValue = millis
                        }
                        previous = now
                    }
                }
            }
        }
    }
    return elapsed to disabled
}

internal const val EchoClockStepMillis = 40L
