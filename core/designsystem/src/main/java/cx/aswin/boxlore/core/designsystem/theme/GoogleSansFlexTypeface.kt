package cx.aswin.boxlore.core.designsystem.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.prefs.FontRoundnessAxis
import java.util.concurrent.ConcurrentHashMap

/** Android [Typeface] loader for bundled Google Sans Flex (ROND axis). */
object GoogleSansFlexTypeface {
    private data class TypefaceKey(val weight: Int, val roundness: Int, val italic: Boolean, val opticalSize: Float?, val width: Float?,)

    private val typefaceCache = ConcurrentHashMap<TypefaceKey, Typeface>()

    fun create(
        context: Context,
        weight: Int,
        roundness: Int,
        italic: Boolean = false,
        opticalSize: Float? = null,
        width: Float? = null,
    ): Typeface {
        val applicationContext = context.applicationContext
        val key =
            TypefaceKey(
                weight = weight.coerceIn(1, 1000),
                roundness = roundness,
                italic = italic,
                opticalSize = opticalSize,
                width = width,
            )
        return typefaceCache.getOrPut(key) {
            createUncached(
                context = applicationContext,
                weight = key.weight,
                roundness = roundness,
                italic = italic,
                opticalSize = opticalSize,
                width = width,
            )
        }
    }

    private fun createUncached(
        context: Context,
        weight: Int,
        roundness: Int,
        italic: Boolean,
        opticalSize: Float?,
        width: Float?,
    ): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createWithVariation(context, weight, roundness, italic, opticalSize, width)?.let { return it }
        }
        createFromResource(context, weight, italic)?.let { return it }
        return Typeface.create(Typeface.SANS_SERIF, styleForWeight(weight, italic))
    }

    fun createFromCachedRoundness(context: Context, weight: Int, italic: Boolean = false,): Typeface = create(context, weight, FontRoundnessAxis.cachedAxisValue(context), italic)

    private fun createWithVariation(
        context: Context,
        weight: Int,
        roundness: Int,
        italic: Boolean,
        opticalSize: Float?,
        width: Float?,
    ): Typeface? = try {
        val font =
            Font.Builder(context.resources, R.font.google_sans_flex_variable)
                .setFontVariationSettings(
                    variationSettings(
                        weight = weight,
                        roundness = roundness,
                        opticalSize = opticalSize,
                        width = width,
                    ),
                )
                .setWeight(weight.coerceIn(1, 1000))
                .setSlant(
                    if (italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT,
                )
                .build()
        val family = FontFamily.Builder(font).build()
        Typeface.CustomFallbackBuilder(family).build()
    } catch (_: Exception) {
        null
    }

    internal fun variationSettings(weight: Int, roundness: Int, opticalSize: Float?, width: Float?,): String = buildList {
        add("'wght' $weight")
        add("'ROND' $roundness")
        opticalSize?.let { add("'opsz' $it") }
        width?.let { add("'wdth' $it") }
    }.joinToString()

    private fun createFromResource(context: Context, weight: Int, italic: Boolean,): Typeface? = ResourcesCompat.getFont(context, R.font.google_sans_flex_variable)?.let { base ->
        Typeface.create(base, styleForWeight(weight, italic))
    }

    internal fun styleForWeight(weight: Int, italic: Boolean,): Int = when {
        italic && weight >= 600 -> Typeface.BOLD_ITALIC
        italic -> Typeface.ITALIC
        weight >= 600 -> Typeface.BOLD
        else -> Typeface.NORMAL
    }
}
