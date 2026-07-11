package cx.aswin.boxcast.feature.player.v2

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Below-the-fold show-notes preview. Renders the episode description as plain text,
 * clipped to a few lines, with an inline expand ("Read more") and a jump to the
 * full episode details screen.
 */
@Composable
fun NotesPreviewCard(
    description: String,
    colorScheme: ColorScheme,
    onOpenEpisodeInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plainText = remember(description) {
        androidx.core.text.HtmlCompat.fromHtml(
            description,
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
        ).toString().trim()
    }
    if (plainText.isBlank()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 28.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 28.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 28.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 28.dp, smoothnessAsPercentBR = 60
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show notes",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onOpenEpisodeInfo,
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                ) {
                    Text("Episode details")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = plainText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                ),
                color = colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
            ) {
                Text(
                    text = if (expanded) "Show less" else "Read more",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
