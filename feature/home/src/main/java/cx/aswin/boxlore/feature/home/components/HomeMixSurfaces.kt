package cx.aswin.boxlore.feature.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles

@Composable
private fun usesPureMixSurfaces(): Boolean = LocalSurfaceStyle.current in setOf(
    SurfaceStyles.AMOLED,
    SurfaceStyles.PURE_WHITE,
    SurfaceStyles.DYNAMIC_OLED_WHITE,
)

/** Tonal separation replaces the outline on Pure surfaces, including loading and empty states. */
@Composable
internal fun homeMixModuleColor(): Color = if (usesPureMixSurfaces()) {
    MaterialTheme.colorScheme.surfaceContainer
} else {
    MaterialTheme.colorScheme.surfaceContainerLow
}

@Composable
internal fun homeMixRestingCardColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh
