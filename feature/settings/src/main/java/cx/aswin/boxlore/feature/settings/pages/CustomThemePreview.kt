package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** Actual Material components preview the roles used by the app, without interactive sample actions. */
@Composable
internal fun CustomThemePreview(colors: ColorScheme, compact: Boolean = false) {
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(color = colors.background, contentColor = colors.onBackground, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().testTag("custom-theme-preview").clearAndSetSemantics { contentDescription = "Theme preview: episode card, playback controls and navigation" }) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("boxlore", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Surface(color = colors.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text("New", color = colors.onTertiaryContainer, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                if (!compact) {
                    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.secondaryContainer) {
                            Icon(Icons.Rounded.Podcasts, null, tint = colors.onSecondaryContainer, modifier = Modifier.padding(10.dp).size(22.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Why curiosity matters", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("A closer listen · 24 min", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {}, shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Text("Play")
                    }
                    FilledTonalButton(onClick = {}, shape = RoundedCornerShape(8.dp)) { Icon(Icons.Rounded.Download, null) }
                    FilledTonalButton(onClick = {}, shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp)) { Icon(Icons.Rounded.Add, null) }
                }
                if (!compact) LinearProgressIndicator(progress = { 0.4f }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
                if (!compact) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(Icons.Rounded.Home, Icons.Rounded.Explore, Icons.AutoMirrored.Rounded.LibraryBooks).forEachIndexed { index, icon ->
                        Surface(color = if (index == 0) colors.secondaryContainer else colors.background, shape = MaterialTheme.shapes.extraLarge) {
                            Icon(icon, null, tint = if (index == 0) colors.onSecondaryContainer else colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).size(20.dp))
                        }
                    }
                }
                }
            }
        }
    }
}
