package cx.aswin.boxlore.feature.player.v2.logic

import android.content.pm.ActivityInfo

/** When the full-screen video asks for an orientation. */
internal enum class FullscreenVideoPhase { Enter, Exit }

/**
 * The orientation full-screen video requests, or null to leave it alone. Only phone layouts are turned
 * sideways or upright on entry; tablets and inner screens are never forced. Leaving gives rotation back:
 * the orientation the activity had before ([original]) or the system's choice, never a portrait lock.
 */
internal fun fullscreenVideoOrientation(
    phase: FullscreenVideoPhase,
    isLandscape: Boolean,
    tabletLayout: Boolean,
    original: Int?,
): Int? = when (phase) {
    FullscreenVideoPhase.Enter -> when {
        tabletLayout -> null
        isLandscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    FullscreenVideoPhase.Exit -> original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
