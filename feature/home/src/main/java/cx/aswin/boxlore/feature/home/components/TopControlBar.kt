package cx.aswin.boxlore.feature.home.components

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.components.TopBarUtilityActions

private const val TAG = "StylizedLogo"

/**
 * Collapsing M3-aligned Top Bar with stylized variable logo and profile.
 *
 * @param scrollFraction 0f = fully expanded (roomier), 1f = fully collapsed
 */
@Composable
fun TopControlBar(
    scrollFractionProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    showUtilityIcons: Boolean = true,
    onFeedbackClick: () -> Unit = {},
    onFeedbackLongClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAvatarLongClick: () -> Unit = {},
) {
    // Expanded state: roomier padding, surface color
    // Collapsed state: compact padding, surfaceContainerLow color
    val expandedPadding = 16.dp
    val collapsedPadding = 8.dp

    val expandedColor = MaterialTheme.colorScheme.surface
    val collapsedColor = MaterialTheme.colorScheme.surfaceContainerLow

    // Update system status bar icon color to match background (evaluated once per theme change, not on scroll)
    val view = LocalView.current
    if (!view.isInEditMode) {
        val isLightStatusBar =
            remember(expandedColor, collapsedColor) {
                val luminance = (
                    0.299f * expandedColor.red +
                        0.587f * expandedColor.green +
                        0.114f * expandedColor.blue
                    )
                luminance > 0.5f
            }
        LaunchedEffect(isLightStatusBar) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar
        }
    }

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .then(
                remember(expandedColor, collapsedColor) {
                    Modifier.drawBehind {
                        val fraction = scrollFractionProvider().coerceIn(0f, 1f)
                        val color = lerp(expandedColor, collapsedColor, fraction)
                        drawRect(color)
                    }
                },
            ).statusBarsPadding()
            .layout { measurable, constraints ->
                val fraction = scrollFractionProvider().coerceIn(0f, 1f)
                val currentPadding =
                    androidx.compose.ui.unit
                        .lerp(expandedPadding, collapsedPadding, fraction)
                val paddingPx = currentPadding.roundToPx()

                val placeable =
                    measurable.measure(
                        constraints.copy(
                            minWidth = constraints.minWidth,
                            maxWidth = constraints.maxWidth,
                        ),
                    )

                val height = placeable.height + paddingPx * 2
                layout(placeable.width, height) {
                    placeable.place(0, paddingPx)
                }
            }.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Left Section (Logo)
        cx.aswin.boxlore.core.designsystem.components
            .BoxLoreLogo()

        if (showUtilityIcons) {
            TopBarUtilityActions(
                onFeedbackClick = onFeedbackClick,
                onFeedbackLongClick = onFeedbackLongClick,
                onSettingsClick = onAvatarClick,
                onSettingsLongClick = onAvatarLongClick,
                settingsButtonModifier = Modifier.testTag("home_settings_button"),
            )
        }
    }
}
