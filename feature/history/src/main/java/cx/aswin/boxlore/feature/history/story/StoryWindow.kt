package cx.aswin.boxlore.feature.history.story

import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The story's dialog window, edge to edge: it reaches under the status and navigation bars, so the
 * card's colour fills them instead of the page below showing through, and their icons read on
 * [background] (dark on a light card, light on a dark one). The story keeps its frame clear of the
 * bars itself (safe-drawing padding). Outside a dialog window it does nothing.
 */
@Composable
internal fun StoryWindowBars(background: Color) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        window.fillScreen()
        onDispose {}
    }
    val darkIcons = StoryWindowLogic.darkIcons(background)
    SideEffect {
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (darkIcons) light else 0, light)
    }
}

/** Lays the dialog's window over the whole screen, bars and cutout included, with clear bars and no dimming. */
@Suppress("DEPRECATION") // Bar colours: still honoured below Android 15, and harmless on it.
private fun Window.fillScreen() {
    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    setDecorFitsSystemWindows(false)
    attributes = attributes.apply {
        // A dialog's window stays inside the bars by default; fitting no insets lets it reach under them.
        setFitInsetsTypes(0)
        setFitInsetsSides(0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
    // Without this flag the system paints its own opaque (black) bars over the window: with it and
    // clear bar colours, the card's colour shows under the status bar and behind the navigation pill.
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    clearFlags(
        WindowManager.LayoutParams.FLAG_DIM_BEHIND or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
    )
    setDimAmount(0f)
    statusBarColor = android.graphics.Color.TRANSPARENT
    navigationBarColor = android.graphics.Color.TRANSPARENT
    isStatusBarContrastEnforced = false
    isNavigationBarContrastEnforced = false
}

internal object StoryWindowLogic {
    /** Dark bar icons on a light card, light ones on a dark card. */
    fun darkIcons(background: Color): Boolean = background.luminance() > LIGHT_CARD

    private const val LIGHT_CARD = 0.5f
}
