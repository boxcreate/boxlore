package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.feature.settings.R

@Composable
internal fun ThemeEditorPreview(dark: Boolean, colors: ColorScheme, compact: Boolean, minimal: Boolean, modifier: Modifier = Modifier, onDarkChange: (Boolean) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.theme_editor_preview), style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = if (compact) Modifier else Modifier.weight(1f))
            ConnectedOptionSelector(
                listOf(false to stringResource(R.string.theme_editor_light), true to stringResource(R.string.theme_editor_dark)),
                selected = dark,
                onSelect = onDarkChange,
                modifier = if (compact) Modifier.weight(1f) else Modifier.width(180.dp),
                labelStyle = MaterialTheme.typography.labelLarge,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            )
        }
        CustomThemePreview(colors, compact, minimal)
    }
}

/** Real theme roles, with decorative samples excluded from the accessibility action list. */
@Composable
internal fun CustomThemePreview(colors: ColorScheme, compact: Boolean = false, minimal: Boolean = false) {
    val description = stringResource(
        when {
        minimal -> R.string.theme_editor_preview_minimal_description
        compact -> R.string.theme_editor_preview_compact_description
        else -> R.string.theme_editor_preview_description
    }
    )
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(
            color = colors.background,
            contentColor = colors.onBackground,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().testTag("custom-theme-preview").clearAndSetSemantics { contentDescription = description },
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (minimal) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(colors.primary, colors.secondary, colors.tertiary).forEach { color ->
                            Surface(color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f).size(12.dp)) {}
                        }
                    }
                } else {
                    ThemePreviewEpisode(compact)
                }
                if (!compact) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(progress = { 0.66f }, modifier = Modifier.weight(1f), trackColor = colors.surfaceContainerHighest, drawStopIndicator = {})
                        Text(stringResource(R.string.theme_editor_preview_remaining), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(Icons.Rounded.Home, Icons.Rounded.Explore, Icons.AutoMirrored.Rounded.LibraryBooks).forEachIndexed { index, icon ->
                            Surface(color = if (index == 0) colors.secondaryContainer else colors.background, shape = MaterialTheme.shapes.extraLarge) {
                                Icon(icon, null, tint = if (index == 0) colors.onSecondaryContainer else colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewEpisode(compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = colors.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.size(if (compact) 44.dp else 56.dp)) {
            Icon(painterResource(cx.aswin.boxlore.core.designsystem.R.drawable.ic_boxlore_app_mark), null, tint = colors.onSecondaryContainer, modifier = Modifier.padding(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.theme_editor_preview_episode), style = MaterialTheme.typography.titleSmall, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!compact) Text(stringResource(R.string.theme_editor_preview_duration), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Surface(color = colors.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.theme_editor_preview_new), color = colors.onTertiaryContainer, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        Surface(color = colors.primary, shape = CircleShape, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Rounded.PlayArrow, null, tint = colors.onPrimary, modifier = Modifier.padding(10.dp))
        }
    }
}
