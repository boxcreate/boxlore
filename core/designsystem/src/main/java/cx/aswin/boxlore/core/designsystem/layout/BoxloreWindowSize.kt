package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Legacy configuration geometry, retained for screens whose phone layout used it. */
@Composable
fun boxloreConfigurationWindowSize(): DpSize {
    val window = LocalBoxloreWindowLayout.current
    if (window.isSpecified) return DpSize(window.configurationWidthDp.dp, window.configurationHeightDp.dp)
    // Isolated previews/tests have no app shell. Production reads only the shared model above.
    val configuration = LocalConfiguration.current
    return DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
}

/** Edge-to-edge geometry; the shell owns the measurement in the running app. */
@Composable
fun boxloreVisibleWindowSize(): DpSize {
    val window = LocalBoxloreWindowLayout.current
    if (window.isSpecified) return DpSize(window.widthDp.dp, window.heightDp.dp)
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}
