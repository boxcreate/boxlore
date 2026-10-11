package cx.aswin.boxlore.core.designsystem.briefing

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme

/** The dark cover needs white system icons even when the article uses a light theme. */
@Composable
fun BriefingPageStatusBars(coverVisible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    val dark = LocalEffectiveDarkTheme.current
    val defaultLightAppearance by rememberUpdatedState(!dark)
    DisposableEffect(view) {
        onDispose { controller.isAppearanceLightStatusBars = defaultLightAppearance }
    }
    SideEffect { controller.isAppearanceLightStatusBars = !coverVisible && !dark }
}
