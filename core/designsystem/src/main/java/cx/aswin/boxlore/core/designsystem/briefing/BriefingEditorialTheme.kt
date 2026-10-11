package cx.aswin.boxlore.core.designsystem.briefing

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Editorial paper surfaces retain the listener's complete accent and error roles. */
fun briefingEditorialColors(base: ColorScheme, dark: Boolean): ColorScheme = base.copy(
    background = paperColor(dark, 0xFFF5F1E9, 0xFF171613),
    surface = paperColor(dark, 0xFFFFFAF1, 0xFF24221E),
    surfaceDim = paperColor(dark, 0xFFE6E1D8, 0xFF171613),
    surfaceBright = paperColor(dark, 0xFFFFFAF1, 0xFF39362F),
    surfaceContainerLowest = paperColor(dark, 0xFFFFFCF6, 0xFF12110F),
    surfaceContainerLow = paperColor(dark, 0xFFF8F4EC, 0xFF201E1A),
    surfaceContainer = paperColor(dark, 0xFFEEE9DF, 0xFF292720),
    surfaceContainerHigh = paperColor(dark, 0xFFE9E3D8, 0xFF312E27),
    surfaceContainerHighest = paperColor(dark, 0xFFE1DACE, 0xFF3A372F),
    onSurface = paperColor(dark, 0xFF252620, 0xFFF5F1E9),
    onBackground = paperColor(dark, 0xFF252620, 0xFFF5F1E9),
    onSurfaceVariant = paperColor(dark, 0xFF625D53, 0xFFC5BFB3),
    outline = paperColor(dark, 0xFF80776A, 0xFF9B9487),
    outlineVariant = paperColor(dark, 0xFFD7CFC2, 0xFF514C41),
)

@Composable
fun BriefingEditorialTheme(content: @Composable () -> Unit) {
    content()
}

private fun paperColor(dark: Boolean, light: Long, night: Long): Color = Color(if (dark) night else light)
