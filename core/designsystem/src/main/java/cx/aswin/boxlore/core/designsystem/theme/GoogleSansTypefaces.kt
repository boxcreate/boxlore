package cx.aswin.boxlore.core.designsystem.theme

import android.content.Context
import android.graphics.Typeface
import cx.aswin.boxlore.core.prefs.FontRoundnessAxis

/** Android [Typeface] loader for Google Sans Flex with a ROND axis value. */
object GoogleSansTypefaces {
    fun create(
        context: Context,
        style: Int = Typeface.NORMAL,
        roundness: Float = GoogleSansFlexRoundness,
    ): Typeface {
        val italic = style == Typeface.ITALIC || style == Typeface.BOLD_ITALIC
        val weight =
            when (style) {
                Typeface.BOLD, Typeface.BOLD_ITALIC -> 700
                else -> 400
            }
        return create(context, weight = weight, italic = italic, roundness = roundness)
    }

    fun create(
        context: Context,
        weight: Int,
        italic: Boolean = false,
        roundness: Float = GoogleSansFlexRoundness,
    ): Typeface =
        GoogleSansFlexTypeface.create(
            context = context,
            weight = weight,
            roundness = roundness.toInt(),
            italic = italic,
        )

    /** Reads lettering roundness from the theme fast-cache SharedPreferences. */
    fun cachedRoundness(context: Context): Float =
        FontRoundnessAxis.cachedAxisValue(context).toFloat()
}
