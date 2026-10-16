package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R as DesignR
import kotlin.math.ceil

// Curated for contrast, illustration and variety rather than a random wall of faces.
private val UpperCovers = listOf(
    DesignR.drawable.pod_cover_6, // Conan O'Brien Needs a Friend
    DesignR.drawable.pod_cover_83, // Science Quickly
    DesignR.drawable.pod_cover_5, // Good Hang
    DesignR.drawable.pod_cover_92, // Ologies
    DesignR.drawable.pod_cover_13, // Radiolab
    DesignR.drawable.pod_cover_8, // Tech Disruptors
)
private val LowerCovers = listOf(
    DesignR.drawable.pod_cover_11, // Let's Learn Everything
    DesignR.drawable.pod_cover_7, // SmartLess
    DesignR.drawable.pod_cover_81, // Short Wave
    DesignR.drawable.pod_cover_21, // The Stanford Psychology Podcast
    DesignR.drawable.pod_cover_22, // Comedy Bang! Bang!
    DesignR.drawable.pod_cover_77, // Hidden Brain
)
private val CoverGap = 12.dp

/** Bundled, sharp artwork; only the two row layers move, with no per-frame recomposition. */
@Composable
internal fun WelcomeCoverReel(
    clock: WelcomeMotionClock,
    coverSize: Dp,
    viewportWidth: Dp,
    panelColor: Color,
    modifier: Modifier = Modifier,
) {
    // Decorative movement keeps the same physical direction in RTL; the actual UI mirrors normally.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxWidth()) {
            Column(
                Modifier.graphicsLayer {
                    alpha = if (clock.animationsDisabled.value) 1f else WelcomeReelMotion.coverAlpha(clock.elapsedMillis.value)
                },
                verticalArrangement = Arrangement.spacedBy(CoverGap),
            ) {
                WelcomeCoverRow(UpperCovers, clock, coverSize, viewportWidth, reverse = false, phase = 0.0, rowDelayMillis = 0L)
                WelcomeCoverRow(
                    LowerCovers,
                    clock,
                    coverSize,
                    viewportWidth,
                    reverse = true,
                    phase = 0.24,
                    rowDelayMillis = WelcomeReelMotion.RowStaggerMillis,
                )
            }
            val edgeFraction = (16.dp / viewportWidth).coerceIn(0f, 0.1f)
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to panelColor,
                        edgeFraction to Color.Transparent,
                        (1f - edgeFraction) to Color.Transparent,
                        1f to panelColor,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun WelcomeCoverRow(
    covers: List<Int>,
    clock: WelcomeMotionClock,
    coverSize: Dp,
    viewportWidth: Dp,
    reverse: Boolean,
    phase: Double,
    rowDelayMillis: Long,
) {
    val cycleWidth = covers.size * (coverSize.value + CoverGap.value).toDouble()
    val painters = covers.map { painterResource(it) }
    // One copy for the wrap plus enough for the entire viewport, including wide windows.
    val copies = ceil(viewportWidth.value / cycleWidth).toInt() + 2
    Row(
        Modifier.wrapContentWidth(Alignment.Start, unbounded = true).graphicsLayer {
            val distance = WelcomeReelMotion.distanceDp(clock.elapsedMillis.value, rowDelayMillis)
            translationX = WelcomeReelMotion.wrappedOffsetDp(distance, cycleWidth, reverse, phase).dp.toPx()
        },
        horizontalArrangement = Arrangement.spacedBy(CoverGap),
    ) {
        repeat(copies) {
            painters.forEach { painter ->
                Image(
                    painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(coverSize).clip(RoundedCornerShape(22.dp)),
                )
            }
        }
    }
}
