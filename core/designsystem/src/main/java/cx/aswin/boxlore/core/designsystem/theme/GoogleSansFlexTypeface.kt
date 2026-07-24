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

/** Android [Typeface] loader for bundled Google Sans Flex (ROND axis). */
object GoogleSansFlexTypeface {
    fun create(
        context: Context,
        weight: Int,
        roundness: Int,
        italic: Boolean = false,
    ): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createWithVariation(context, weight, roundness, italic)?.let { return it }
        }
        createFromResource(context, weight, italic)?.let { return it }
        return Typeface.create(Typeface.SANS_SERIF, styleForWeight(weight, italic))
    }

    fun createFromCachedRoundness(
        context: Context,
        weight: Int,
        italic: Boolean = false,
    ): Typeface = create(context, weight, FontRoundnessAxis.cachedAxisValue(context), italic)

    private fun createWithVariation(
        context: Context,
        weight: Int,
        roundness: Int,
        italic: Boolean,
    ): Typeface? =
        try {
            val font =
                Font.Builder(context.resources, R.font.google_sans_flex_variable)
                    .setFontVariationSettings("'ROND' $roundness")
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

    private fun createFromResource(
        context: Context,
        weight: Int,
        italic: Boolean,
    ): Typeface? =
        ResourcesCompat.getFont(context, R.font.google_sans_flex_variable)?.let { base ->
            Typeface.create(base, styleForWeight(weight, italic))
        }

    internal fun styleForWeight(
        weight: Int,
        italic: Boolean,
    ): Int =
        when {
            italic && weight >= 600 -> Typeface.BOLD_ITALIC
            italic -> Typeface.ITALIC
            weight >= 600 -> Typeface.BOLD
            else -> Typeface.NORMAL
        }
}
