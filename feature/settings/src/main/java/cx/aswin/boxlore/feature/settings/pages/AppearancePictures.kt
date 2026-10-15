package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.prefs.FullPlayerStyle

/*
 * Small drawings of each choice, in the current theme's roles: the shape of the screen, not a
 * miniature of it. Text is drawn as bars so the pictures read the same in any language.
 */

/** Standard puts the cover first; bunpod puts the title and controls first. */
@Composable
internal fun PlayerStylePicture(style: String) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(150.dp).background(colors.surfaceContainerHighest).padding(12.dp)) {
        if (FullPlayerStyle.sanitize(style) == FullPlayerStyle.BUNPOD) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PictureBar(0.85f, 8.dp, colors.onSurface)
                PictureBar(0.6f, 8.dp, colors.onSurface)
                PictureBar(0.4f, 5.dp, colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PictureBlock(44.dp, 22.dp, colors.primary, CircleShape)
                    PictureBlock(16.dp, 16.dp, colors.secondaryContainer, CircleShape)
                    PictureBlock(16.dp, 16.dp, colors.secondaryContainer, CircleShape)
                }
                PictureBar(1f, 3.dp, colors.primary)
                PictureBlock(44.dp, 44.dp, colors.tertiaryContainer, RoundedCornerShape(8.dp))
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PictureBlock(64.dp, 64.dp, colors.tertiaryContainer, RoundedCornerShape(10.dp))
                PictureBar(0.7f, 7.dp, colors.onSurface)
                PictureBar(0.45f, 5.dp, colors.onSurfaceVariant)
                PictureBar(0.9f, 3.dp, colors.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PictureBlock(14.dp, 14.dp, colors.secondaryContainer, CircleShape)
                    PictureBlock(36.dp, 20.dp, colors.primary, CircleShape)
                    PictureBlock(14.dp, 14.dp, colors.secondaryContainer, CircleShape)
                }
            }
        }
    }
}

/** Floating is a pill with Lore beside it; Classic is a full-width bar with labelled tabs. */
@Composable
internal fun NavigationBarPicture(style: NavigationStyle) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(110.dp).background(colors.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PictureBar(0.7f, 6.dp, colors.onSurfaceVariant.copy(alpha = 0.5f))
            PictureBar(0.45f, 6.dp, colors.onSurfaceVariant.copy(alpha = 0.3f))
            PictureBlock(null, 20.dp, colors.surfaceContainerHigh, RoundedCornerShape(6.dp))
        }
        if (style == NavigationStyle.Floating) {
            Row(Modifier.align(Alignment.BottomStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.width(100.dp).height(28.dp).clip(CircleShape).background(colors.surfaceContainerHighest).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PictureBlock(34.dp, 20.dp, colors.primary, CircleShape)
                    PictureBlock(9.dp, 9.dp, colors.onSurfaceVariant, CircleShape)
                    PictureBlock(9.dp, 9.dp, colors.onSurfaceVariant, RoundedCornerShape(2.dp))
                }
                PictureBlock(28.dp, 28.dp, colors.surfaceContainerHighest, CircleShape)
            }
        } else {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(38.dp).background(colors.surfaceContainerHighest),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (index == 0) {
                            PictureBlock(24.dp, 12.dp, colors.primary, CircleShape)
                        } else {
                            PictureBlock(10.dp, 10.dp, colors.onSurfaceVariant, CircleShape)
                        }
                        PictureBlock(16.dp, 3.dp, if (index == 0) colors.onSurface else colors.onSurfaceVariant, CircleShape)
                    }
                }
            }
        }
    }
}

@Composable
private fun PictureBar(fraction: Float, height: Dp, color: Color) {
    Box(Modifier.fillMaxWidth(fraction).height(height).clip(CircleShape).background(color))
}

@Composable
private fun PictureBlock(width: Dp?, height: Dp, color: Color, shape: androidx.compose.ui.graphics.Shape) {
    val sized = if (width == null) Modifier.fillMaxWidth().height(height) else Modifier.size(width, height)
    Box(sized.clip(shape).background(color))
}
