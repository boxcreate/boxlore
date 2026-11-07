package cx.aswin.boxlore.feature.info.branded.darknet

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The flag-bearer art's motion, in the design's own units: the art is drawn in a 1200-unit square,
 * and only the cloth moves. A travelling wave 260 units long runs out from the pole every 2.8
 * seconds; how far each point moves is read from the amplitude map, still where the flag is tied
 * and at the figure, the pole and the trident, strongest at the free end.
 */
internal object DarknetFlagWave {
    const val UNITS = 1200f
    const val WAVELENGTH = 260f
    const val PERIOD_SECONDS = 2.8f

    /** The strongest push, in units, before the vertical and horizontal shares below. */
    const val SCALE = 46f
    const val VERTICAL = 0.5f
    const val HORIZONTAL = 0.18f

    /**
     * How far the point at [x] (in units) moves at [seconds], for an amplitude of [amp] (0 to 1),
     * in units: mostly up and down, a little along the cloth.
     */
    fun push(x: Float, amp: Float, seconds: Float): Offset {
        val phase = 2f * PI.toFloat() * (x / WAVELENGTH - seconds / PERIOD_SECONDS)
        return Offset(-SCALE * amp * HORIZONTAL * cos(phase), -SCALE * amp * VERTICAL * sin(phase))
    }
}

/**
 * The flag as a mesh over the art: its corners follow [DarknetFlagWave], so the cloth waves while
 * everything else stays put. Works on every Android version the app supports.
 */
internal class FlagMesh(private val art: Bitmap, amplitude: Bitmap) {
    private val columns = MESH
    private val rows = MESH
    private val amp = FloatArray((columns + 1) * (rows + 1))
    private val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        val w = amplitude.width
        val h = amplitude.height
        val pixels = IntArray(w * h)
        amplitude.getPixels(pixels, 0, w, 0, 0, w, h)
        for (j in 0..rows) {
            for (i in 0..columns) {
                val x = (i.toFloat() / columns * (w - 1)).toInt()
                val y = (j.toFloat() / rows * (h - 1)).toInt()
                amp[j * (columns + 1) + i] = ((pixels[y * w + x] shr 16) and 0xFF) / 255f
            }
        }
    }

    /** Draws the art in the square at [left], [top] with sides [size] pixels long, [seconds] into the wave. */
    fun draw(canvas: android.graphics.Canvas, left: Float, top: Float, size: Float, seconds: Float, alpha: Int) {
        val unit = size / DarknetFlagWave.UNITS
        var k = 0
        for (j in 0..rows) {
            for (i in 0..columns) {
                val u = i.toFloat() / columns
                val v = j.toFloat() / rows
                val a = amp[j * (columns + 1) + i]
                val push = if (a > 0f) DarknetFlagWave.push(u * DarknetFlagWave.UNITS, a, seconds) else Offset.Zero
                vertices[k++] = left + u * size + push.x * unit
                vertices[k++] = top + v * size + push.y * unit
            }
        }
        paint.alpha = alpha
        canvas.drawBitmapMesh(art, columns, rows, vertices, 0, null, 0, paint)
    }

    private companion object {
        const val MESH = 40
    }
}

/** A speck of red ink drifting down through the light, on the design's 369dp board. */
internal data class InkFleck(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val dx: Float,
    val dy: Float,
    val rotation: Float,
    val seconds: Float,
    val delay: Float,
) {
    /** How far through its drift it is at [t] seconds, 0 to 1. */
    fun phase(t: Float): Float = ((t + delay) % seconds) / seconds

    /** It fades in, drifts, and fades out at the end of each drift. */
    fun alpha(p: Float): Float = when {
        p < FADE -> p / FADE * PEAK
        p > 1f - FADE -> (1f - p) / FADE * PEAK
        else -> PEAK
    }

    companion object {
        private const val FADE = 0.15f
        private const val PEAK = 0.9f

        /** Fourteen flecks across the light, the same on every visit. */
        fun scatter(count: Int = 14, seed: Int = 31): List<InkFleck> {
            val r = Random(seed)
            fun between(a: Float, b: Float) = a + r.nextFloat() * (b - a)
            return List(count) {
                val w = between(2f, 5.5f)
                InkFleck(
                    x = between(10f, 355f),
                    y = between(96f, 300f),
                    width = w,
                    height = w * between(0.5f, 1.3f),
                    dx = between(-26f, 26f),
                    dy = between(14f, 40f),
                    rotation = between(-160f, 160f),
                    seconds = between(7f, 12f),
                    delay = between(0f, 8f),
                )
            }
        }
    }
}

/** The header's motion in seconds of its own clock. */
internal object DarknetMotion {
    /** How long the motion takes to come up to speed once it starts. */
    const val EASE_IN_SECONDS = 0.5f

    /**
     * Where the clock is after [real] seconds of motion: it starts from rest, comes up to full speed
     * over [EASE_IN_SECONDS], then runs on for as long as the header is on screen.
     */
    fun eased(real: Float): Float = when {
        real <= 0f -> 0f
        real < EASE_IN_SECONDS -> real * real / (2f * EASE_IN_SECONDS)
        else -> real - EASE_IN_SECONDS / 2f
    }

    /** The red light breathes over five seconds: 0 at its dimmest, 1 at its brightest. */
    fun breath(t: Float): Float = 0.5f - 0.5f * cos(2f * PI.toFloat() * t / 5f)

    /** The light shafts sway five degrees each way over fourteen seconds. */
    fun sway(t: Float): Float = -5f * cos(2f * PI.toFloat() * t / 14f)
}

/**
 * The light shafts fanning out from behind the figure: every 19°, a thin shaft brightest at 9° and
 * gone by 12°, fading with distance from the light. Rendered once into a picture at half resolution
 * (they are soft, so it never shows) and then only turned and screened on each frame.
 */
internal object DarknetRays {
    /** The shafts' centre on the 369dp board, and how far they reach, in dp. */
    val Centre = Offset(196.2f, 262.4f)
    const val REACH = 225.5f

    private const val RESOLUTION = 0.5f

    @Volatile private var last: Pair<Float, ImageBitmap>? = null

    fun cached(density: Float): ImageBitmap? = last?.takeIf { it.first == density }?.second

    fun render(density: Float): ImageBitmap {
        cached(density)?.let { return it }
        val side = (REACH * 2f * density * RESOLUTION).toInt().coerceAtLeast(2)
        val bitmap = createBitmap(side, side)
        val canvas = Canvas(bitmap)
        val c = side / 2f
        val stops = ArrayList<Pair<Float, Int>>()
        var a = 0f
        while (a < 360f) {
            stops += (a + 7f) / 360f to CLEAR
            stops += (a + 9f) / 360f to LIGHT
            if ((a + 12f) / 360f <= 1f) stops += (a + 12f) / 360f to CLEAR
            a += 19f
        }
        val rays = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(c, c, stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray())
        }
        canvas.drawCircle(c, c, c, rays)
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(c, c, c, intArrayOf(0xFF000000.toInt(), 0x99000000.toInt(), 0x00000000), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawCircle(c, c, c, fade)
        return bitmap.asImageBitmap().also {
            it.prepareToDraw()
            last = density to it
        }
    }

    private const val CLEAR = 0x00FF281E
    private const val LIGHT = 0x8CFF3C28.toInt()
}

/**
 * Light and shade travelling along the cloth with the wave, soft-lit onto it, only where the cloth
 * is: stripes one wavelength apart, kept to the cloth by its mask, drawn straight onto the art with
 * no layer of their own.
 */
internal class FlagShade(mask: Bitmap) {
    private val maskShader = BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val maskWidth = mask.width.toFloat()

    /** One wavelength of stripes from x 0 to 1, stretched and moved by [stripeMatrix] each frame, so the wave allocates nothing. */
    private val stripes = LinearGradient(0f, 0f, 1f, 0f, StripeColors, StripeStops, Shader.TileMode.REPEAT)
    private val stripeMatrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.SOFT_LIGHT
        shader = ComposeShader(maskShader, stripes, PorterDuff.Mode.SRC_IN)
    }

    fun draw(canvas: android.graphics.Canvas, left: Float, top: Float, size: Float, seconds: Float, alpha: Float) {
        matrix.setScale(size / maskWidth, size / maskWidth)
        matrix.postTranslate(left, top)
        maskShader.setLocalMatrix(matrix)
        val period = size * DarknetFlagWave.WAVELENGTH / DarknetFlagWave.UNITS
        val shift = left + period * (seconds / DarknetFlagWave.PERIOD_SECONDS)
        stripeMatrix.setScale(period, 1f)
        stripeMatrix.postTranslate(shift, 0f)
        stripes.setLocalMatrix(stripeMatrix)
        paint.alpha = (alpha * 255).toInt()
        canvas.drawRect(left, top, left + size, top + size, paint)
    }

    private companion object {
        val StripeColors = intArrayOf(0x80000000.toInt(), 0x47FFFFFF, 0x80000000.toInt())
        val StripeStops = floatArrayOf(0f, 0.5f, 1f)
    }
}
