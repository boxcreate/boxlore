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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.components.TopBarUtilityActions
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout

private const val TAG = "StylizedLogo"

/**
 * Collapsing M3-aligned Top Bar with stylized variable logo and profile.
 *
 * @param scrollFraction 0f = fully expanded (roomier), 1f = fully collapsed
 * @param overArtwork how much of the bar sits over Marquee's cover (1) rather than Home's body (0). Over
 * the cover the bar is clear (the cover shows a frosted band behind it) and its wordmark and status
 * icons take [artworkContent]; as the body's edge slides up under it, the bar fills with the body's
 * own colour. Null for every other hero style.
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
    overArtwork: (() -> Float)? = null,
    artworkContent: Color = Color.Unspecified,
) {
    // Expanded state: roomier padding, surface color
    // Collapsed state: compact padding, surfaceContainerLow color
    val expandedPadding = 16.dp
    val collapsedPadding = 8.dp

    val expandedColor = MaterialTheme.colorScheme.surface
    val collapsedColor = if (LocalBoxloreWindowLayout.current.usesTabletLayout) expandedColor else MaterialTheme.colorScheme.surfaceContainerLow

    // Over artwork, only crossing halfway changes anything, so scrolling does not recompose the bar.
    val overHero by remember(overArtwork) { derivedStateOf { overArtwork != null && overArtwork() > 0.5f } }
    val onArtwork = artworkContent.takeOrElse { OnArtworkWordmark }

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
        LaunchedEffect(isLightStatusBar, overHero, onArtwork) {
            val window = (view.context as Activity).window
            // Over a cover, dark words mean a light cover, so the status icons go dark too.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                if (overHero) onArtwork.luminance() < 0.5f else isLightStatusBar
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
                        val color = if (overArtwork != null) {
                            // Clear over the cover; the body's colour as its edge arrives, never a darker fill.
                            expandedColor.copy(alpha = 1f - overArtwork().coerceIn(0f, 1f))
                        } else {
                            lerp(expandedColor, collapsedColor, fraction)
                        }
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
                .BoxLoreLogo(textColor = onArtwork)
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

/** The wordmark over Marquee's cover when the hero gives no colour of its own. */
private val OnArtworkWordmark = Color(0xFFF4F1FA)
