package cx.aswin.boxlore.core.designsystem.theme

import androidx.annotation.StringRes
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.prefs.FontRoundnessAxis

/**
 * Google Sans Flex **ROND** axis presets for Appearance → Lettering.
 * Pref keys match [cx.aswin.boxlore.core.prefs.UserPreferencesRepository] (`crisp` / `soft` / `round`).
 */
object FontRoundness {
    const val CRISP = FontRoundnessAxis.CRISP
    const val SOFT = FontRoundnessAxis.SOFT
    const val ROUND = FontRoundnessAxis.ROUND

    const val DEFAULT_KEY = ROUND

    // Literals keep these compile-time constants (Axis ints live in :core:prefs).
    const val AXIS_CRISP = 0f
    const val AXIS_SOFT = 50f
    const val AXIS_ROUND = 100f

    /** A lettering choice: [label] is its English name, kept until Settings shows [labelRes]. */
    data class Entry(val key: String, val label: String, val axis: Float, @StringRes val labelRes: Int,)

    val entries =
        listOf(
            Entry(CRISP, "Crisp", AXIS_CRISP, R.string.font_roundness_crisp),
            Entry(SOFT, "Soft", AXIS_SOFT, R.string.font_roundness_soft),
            Entry(ROUND, "Round", AXIS_ROUND, R.string.font_roundness_round),
        )

    fun sanitizeKey(key: String?): String = FontRoundnessAxis.sanitizeKey(key)

    fun axisValue(key: String?): Float = FontRoundnessAxis.axisValue(key).toFloat()
}

/**
 * Default Flex roundness (Round / ROND 100). Prefer [FontRoundness.axisValue] when a pref is available.
 */
const val GoogleSansFlexRoundness = FontRoundness.AXIS_ROUND
