package cx.aswin.boxlore.feature.home.components

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
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
 * @param overArtwork the bar sits over Marquee's hero: clear at the top of Home, with light status
 * icons and wordmark over the artwork's shade, and solid again once Home scrolls past halfway.
 */
@Composable
@Suppress("LongParameterList")
fun TopControlBar(
    scrollFractionProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    showUtilityIcons: Boolean = true,
    onFeedbackClick: () -> Unit = {},
    onFeedbackLongClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAvatarLongClick: () -> Unit = {},
    overArtwork: Boolean = false,
) {
    // Expanded state: roomier padding, surface color
    // Collapsed state: compact padding, surfaceContainerLow color
    val expandedPadding = 16.dp
    val collapsedPadding = 8.dp

    val expandedColor = MaterialTheme.colorScheme.surface
    val collapsedColor = MaterialTheme.colorScheme.surfaceContainerLow

    // Over artwork, only crossing halfway changes anything, so scrolling does not recompose the bar.
    val overHero by remember(overArtwork) { derivedStateOf { overArtwork && scrollFractionProvider() < 0.5f } }

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
        val themeLightStatusBar by rememberUpdatedState(isLightStatusBar)
        LaunchedEffect(isLightStatusBar, overHero) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar && !overHero
        }
        // Leaving Home over the artwork must not keep light icons on the next screen.
        DisposableEffect(view) {
            onDispose {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = themeLightStatusBar
            }
        }
    }

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .then(
                remember(expandedColor, collapsedColor, overArtwork) {
                    Modifier.drawBehind {
                        val fraction = scrollFractionProvider().coerceIn(0f, 1f)
                        val color = if (overArtwork) collapsedColor.copy(alpha = fraction) else lerp(expandedColor, collapsedColor, fraction)
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
        if (overHero) {
            cx.aswin.boxlore.core.designsystem.components
                .BoxLoreLogo(textColor = OnArtworkWordmark)
        } else {
            cx.aswin.boxlore.core.designsystem.components
                .BoxLoreLogo()
        }

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

/** The wordmark over Marquee's artwork, which always sits under a deep shade at the top. */
private val OnArtworkWordmark = Color(0xFFF4F1FA)
