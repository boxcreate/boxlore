package cx.aswin.boxlore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Change only the dialog's chrome, leaving the underlying Activity untouched. */
@Composable
internal fun NativeDialogSystemBars(background: Color, dimmed: Boolean = false) {
    val view = LocalView.current
    val darkIcons = dialogUsesDarkSystemBarIcons(background, dimmed)
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}

internal fun dialogUsesDarkSystemBarIcons(background: Color, dimmed: Boolean): Boolean = !dimmed && background.luminance() > 0.5f
