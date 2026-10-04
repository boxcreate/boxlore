package cx.aswin.boxlore.feature.explore

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import cx.aswin.boxlore.core.designsystem.components.optimizedImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun loadLoreArtworkAccent(context: Context, sources: List<String>): Int? = resolveLoreArtworkAccent(
    sources = sources,
    // Match the card backing's URL so its download and the palette share Coil's disk cache.
    optimize = { it.optimizedImageUrl(width = 320) },
    readAccent = { url ->
        withTimeoutOrNull(3_000) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(96, 96)
                .memoryCacheKey("lore-palette:$url")
                .build()
            val result = Coil.imageLoader(context).execute(request) as? SuccessResult
            if (result == null) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    val bitmap = result.drawable.toBitmap(width = 96, height = 96)
                    val swatches = Palette.from(bitmap).maximumColorCount(24).generate().swatches.map {
                        LoreArtworkSwatch(it.rgb, it.population, it.hsl[1], it.hsl[2])
                    }
                    selectLoreArtworkSwatch(swatches)?.let { rgb ->
                        val hsl = FloatArray(3)
                        ColorUtils.colorToHSL(rgb, hsl)
                        hsl[1] = hsl[1].coerceIn(0.35f, 0.85f)
                        hsl[2] = hsl[2].coerceIn(0.30f, 0.58f)
                        ColorUtils.HSLToColor(hsl)
                    }
                }
            }
        }
    },
)

internal data class LoreArtworkSwatch(val rgb: Int, val population: Int, val saturation: Float, val lightness: Float)

/** Prefer a meaningful artwork hue over a large white/black/grey logo background. */
internal fun selectLoreArtworkSwatch(swatches: List<LoreArtworkSwatch>): Int? {
    val colourful = swatches.filter {
        it.population > 0 && it.saturation >= 0.12f && it.lightness in 0.08f..0.92f
    }
    val largest = colourful.maxOfOrNull { it.population }?.toFloat() ?: return null
    return colourful.maxByOrNull {
        kotlin.math.sqrt(it.population / largest) * (0.35f + it.saturation)
    }?.rgb
}
