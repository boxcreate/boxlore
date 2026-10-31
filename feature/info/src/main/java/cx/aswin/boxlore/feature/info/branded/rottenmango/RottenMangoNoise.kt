package cx.aswin.boxlore.feature.info.branded.rottenmango

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import java.util.Random
import kotlin.math.roundToInt

/**
 * Grain like old tape's: grey specks of uneven strength, seeded so every run looks the same. It
 * stands in for the boards' SVG turbulence, boosted the same way (2.4 × grey − 0.7).
 */
internal object RottenMangoNoise {
    /** Pixels per side; one tile covers [TILE_DP]. */
    const val SIZE = 320
    const val TILE_DP = 160

    fun pixels(seed: Long = 7L): IntArray {
        val random = Random(seed)
        return IntArray(SIZE * SIZE) {
            val level = (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 3f
            val grey = ((2.4f * level - 0.7f).coerceIn(0f, 1f) * 255).roundToInt()
            val alpha = ((0.35f + 0.3f * random.nextFloat()) * 255).roundToInt()
            (alpha shl 24) or (grey shl 16) or (grey shl 8) or grey
        }
    }
}

@Composable
internal fun rememberTapeNoise(): ImageBitmap = remember {
    Bitmap.createBitmap(RottenMangoNoise.pixels(), RottenMangoNoise.SIZE, RottenMangoNoise.SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Multiplies a layer's colours by [factor], like CSS brightness(). */
internal fun brightness(factor: Float): ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(factor, factor, factor, 1f) })

/**
 * The grain tiled over [area], screened onto what is below, shifted by [shift] dp; [glow] brightens it
 * as the static bursts.
 */
internal fun DrawScope.drawGrain(noise: ImageBitmap, alpha: Float, shift: Offset = Offset.Zero, glow: Float = 1f, area: Rect = size.toRect()) {
    val tileScale = RottenMangoNoise.TILE_DP.dp.toPx() / RottenMangoNoise.SIZE
    val unit = 1.dp.toPx()
    clipRect(area.left, area.top, area.right, area.bottom) {
        withTransform({
            translate(shift.x * unit, shift.y * unit)
            scale(tileScale, tileScale, pivot = Offset.Zero)
        }) {
            // Drawn in the tile's own pixels, past the edges so the shift never shows a gap.
            val margin = RottenMangoNoise.SIZE.toFloat()
            drawRect(
                ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)),
                topLeft = Offset(-margin, (area.top - unit * 64) / tileScale - margin),
                size = Size(size.width / tileScale + margin * 2, (area.height + unit * 128) / tileScale + margin * 2),
                alpha = alpha,
                colorFilter = if (glow != 1f) brightness(glow) else null,
                blendMode = BlendMode.Screen,
            )
        }
    }
}
