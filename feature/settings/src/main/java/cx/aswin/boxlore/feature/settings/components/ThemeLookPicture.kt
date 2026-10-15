package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.settings.pages.ThemePreviewColors

/** A theme look in both modes side by side, for its picture tile. Picking a look never resets customized colors. */
@Composable
internal fun ThemeLookPicture(colors: ThemePreviewColors, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicThemePreview("Light", colors.light, Modifier.weight(1f))
        BasicThemePreview("Dark", colors.dark, Modifier.weight(1f))
    }
}

/** Background, one card and one button: a simple color comparison, not a miniature screen. */
@Composable
private fun BasicThemePreview(label: String, scheme: ColorScheme, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(scheme.background)) {
        Text(
            label,
            modifier = Modifier.padding(start = 10.dp, top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val inset = 10.dp.toPx()
            val width = (size.width - 2 * inset).coerceAtLeast(1f)
            val radius = CornerRadius(7.dp.toPx())
            drawRoundRect(scheme.surfaceContainerHigh, Offset(inset, 6.dp.toPx()), Size(width, 42.dp.toPx()), radius)
            drawRoundRect(scheme.onSurface, Offset(inset + 8.dp.toPx(), 14.dp.toPx()), Size(width * 0.45f, 3.dp.toPx()), radius)
            drawRoundRect(scheme.onSurfaceVariant, Offset(inset + 8.dp.toPx(), 22.dp.toPx()), Size(width * 0.62f, 2.dp.toPx()), radius)
            drawRoundRect(scheme.primary, Offset(inset + 8.dp.toPx(), 32.dp.toPx()), Size(width * 0.32f, 8.dp.toPx()), radius)
        }
    }
}
