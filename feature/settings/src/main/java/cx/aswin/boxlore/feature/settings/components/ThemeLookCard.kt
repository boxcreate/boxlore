package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.pages.ThemeLookOption

/** One full-width selectable look, with the same solid surface and accent roles as the app. */
@Composable
internal fun ThemeLookCard(
    look: ThemeLookOption,
    scheme: ColorScheme,
    selected: Boolean,
    customColors: Boolean,
    onSelect: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) {
                stateDescription = when {
                    !selected -> "Not selected"
                    customColors -> "Selected, customized colors. Tap to restore the original palette."
                    else -> "Selected"
                }
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column {
            ThemeLookPreview(scheme)
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(look.name, style = MaterialTheme.typography.titleLarge, fontWeight = GoogleSansWeight.semiBold)
                    Text(look.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected) {
                        Text(
                            if (customColors) "In use · customized colors" else "In use",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics {}) {
                        listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { color ->
                            Box(Modifier.size(12.dp).background(color, CircleShape))
                        }
                    }
                    if (selected) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.padding(5.dp).size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Decorative app preview. No network artwork, animations, or interactive miniature controls. */
@Composable
private fun ThemeLookPreview(scheme: ColorScheme) {
    MaterialTheme(colorScheme = scheme) {
        Column(
            modifier = Modifier.fillMaxWidth().background(scheme.background).clearAndSetSemantics {}.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("boxlore", style = MaterialTheme.typography.titleLarge, fontWeight = GoogleSansWeight.bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewEpisode("Deep space", scheme.primary, scheme.onPrimary, Modifier.weight(1f))
                PreviewEpisode("Slow mornings", scheme.tertiary, scheme.onTertiary, Modifier.weight(1f))
            }
            Surface(shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainerHigh) {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PreviewArtwork(scheme.secondary, scheme.onSecondary, Modifier.size(30.dp), rings = false)
                        Column(Modifier.weight(1f)) {
                            Text("A little curiosity", style = MaterialTheme.typography.labelMedium)
                            Text("Now playing", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                        Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(5.dp).size(20.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(scheme.outlineVariant)) {
                        Box(Modifier.fillMaxWidth(0.42f).height(2.dp).background(scheme.primary))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewEpisode(title: String, accent: Color, onAccent: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewArtwork(accent, onAccent, Modifier.fillMaxWidth().height(54.dp), rings = title == "Deep space")
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PreviewArtwork(accent: Color, onAccent: Color, modifier: Modifier, rings: Boolean) {
    Canvas(modifier.clip(MaterialTheme.shapes.medium).background(accent)) {
        if (rings) {
            val center = Offset(size.width * 0.65f, size.height * 0.48f)
            for (radius in listOf(0.2f, 0.43f, 0.68f)) {
                drawCircle(onAccent, radius = size.height * radius, center = center, style = Stroke(2.dp.toPx()))
            }
            drawCircle(onAccent, radius = 4.dp.toPx(), center = Offset(size.width * 0.25f, size.height * 0.45f))
        } else {
            for (index in 0..4) {
                val width = size.width / 9f
                val height = size.height * (if (index % 2 == 0) 0.35f else 0.65f)
                drawRoundRect(onAccent, Offset(size.width * 0.22f + index * width * 1.3f, (size.height - height) / 2), Size(width, height), androidx.compose.ui.geometry.CornerRadius(width))
            }
        }
    }
}
