package cx.aswin.boxlore.feature.player.v2

import androidx.compose.runtime.staticCompositionLocalOf

/** The tablet shell owns full-window overlays even when playback starts in a narrow pane. */
enum class PlayerFullscreenOverlay { Video, Transcript }

val LocalPlayerFullscreenRequest = staticCompositionLocalOf<((PlayerFullscreenOverlay, Boolean) -> Unit)?> { null }
