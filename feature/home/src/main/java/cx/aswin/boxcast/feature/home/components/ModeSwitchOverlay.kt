package cx.aswin.boxcast.feature.home.components

import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.io.File

/**
 * Full-screen branded transition overlay shown when switching between
 * Podcast and Radio modes. Displays the BOXCAST logo with the "O" 
 * replaced by a mic (podcast) or radio icon that crossfades during the switch.
 *
 * Acts as a delightful loading screen while the radio API fetches data.
 */
@Composable
fun ModeSwitchOverlay(
    isVisible: Boolean,
    isRadioMode: Boolean,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate the overlay alpha
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 250 else 400,
            easing = FastOutSlowInEasing
        ),
        label = "overlayAlpha"
    )
    
    // Scale animation for the logo
    val logoScale = remember { Animatable(0.8f) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            // Entrance: scale up
            logoScale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
            // Settle
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            )
            // Hold for a moment
            delay(600)
            // Signal completion
            onAnimationComplete()
        } else {
            logoScale.snapTo(0.8f)
        }
    }
    
    if (overlayAlpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(100f)
                .alpha(overlayAlpha)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            // The BOXCAST logo with icon replacing the "O"
            BrandedLogoWithIcon(
                isRadioMode = isRadioMode,
                modifier = Modifier.scale(logoScale.value)
            )
        }
    }
}

/**
 * Renders B[icon]XCAST where [icon] crossfades between podcast mic and radio.
 * Uses the same RobotoFlex variable font styling as the real BoxCastLogo.
 */
@Composable
private fun BrandedLogoWithIcon(
    isRadioMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // Same letter styles as BoxCastLogo but larger, skip index 1 ("O")
    data class LetterStyle(val weight: Float, val slant: Float, val scaleX: Float)
    val letterStyles = listOf(
        LetterStyle(900f, 0f, 1.5f),    // B
        // O is replaced by icon
        LetterStyle(1000f, 0f, 1.0f),   // X
        LetterStyle(300f, -12f, 1.8f),  // C
        LetterStyle(700f, 0f, 1.0f),    // A
        LetterStyle(400f, -8f, 1.3f),   // S
        LetterStyle(1000f, 0f, 1.2f)    // T
    )
    val lettersNoO = "BXCAST"
    
    // Create typefaces
    val typefaces = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            letterStyles.mapIndexed { index, style ->
                try {
                    val fontRes = context.resources.openRawResource(
                        cx.aswin.boxcast.core.designsystem.R.font.robotoflex_variable
                    )
                    val tempFile = File.createTempFile("font_switch_$index", ".ttf", context.cacheDir)
                    tempFile.outputStream().use { fontRes.copyTo(it) }
                    
                    val typeface = android.graphics.Typeface.Builder(tempFile)
                        .setFontVariationSettings(
                            "'wght' ${style.weight}, 'slnt' ${style.slant}"
                        )
                        .build()
                    
                    tempFile.delete()
                    typeface
                } catch (e: Exception) {
                    Log.e("ModeSwitchOverlay", "Failed to create typeface: ${e.message}")
                    android.graphics.Typeface.DEFAULT
                }
            }
        } else {
            List(6) { android.graphics.Typeface.DEFAULT }
        }
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // B
        LogoLetter(
            char = 'B',
            typeface = typefaces[0],
            scaleX = letterStyles[0].scaleX,
            textColor = textColor,
            fontSize = 40f
        )
        
        // Animated icon replacing "O"
        AnimatedContent(
            targetState = isRadioMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) +
                    scaleIn(initialScale = 0.6f, animationSpec = tween(400, easing = FastOutSlowInEasing)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(300)) +
                            scaleOut(targetScale = 0.6f, animationSpec = tween(300))
                    )
            },
            label = "IconMorph"
        ) { radioMode ->
            Icon(
                imageVector = if (radioMode) Icons.Rounded.Radio else Icons.Rounded.Podcasts,
                contentDescription = if (radioMode) "Radio Mode" else "Podcast Mode",
                modifier = Modifier
                    .size(36.dp)
                    .offset(y = (-2).dp), // Slight nudge to align with text baseline
                tint = primaryColor
            )
        }
        
        // X C A S T
        lettersNoO.drop(1).forEachIndexed { index, char ->
            val adjustedIndex = index + 1 // Skip B (0) since we start after X
            LogoLetter(
                char = char,
                typeface = typefaces[adjustedIndex],
                scaleX = letterStyles[adjustedIndex].scaleX,
                textColor = textColor,
                fontSize = 40f
            )
        }
    }
}

@Composable
private fun LogoLetter(
    char: Char,
    typeface: android.graphics.Typeface,
    scaleX: Float,
    textColor: Color,
    fontSize: Float
) {
    AndroidView(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                text = char.toString()
                textSize = fontSize
                setTextColor(android.graphics.Color.WHITE)
                this.typeface = typeface
                includeFontPadding = false
                this.scaleX = scaleX
            }
        },
        update = { tv ->
            tv.typeface = typeface
            tv.scaleX = scaleX
            tv.setTextColor(textColor.hashCode())
        }
    )
}
