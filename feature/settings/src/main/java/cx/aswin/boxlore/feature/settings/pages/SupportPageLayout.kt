package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorldBackdrop
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorldFont

/** Stage, gaps, padding and room for a 3-button navigation bar. None of it grows with text size. */
internal val SupportStackedFixedHeight = 280.dp

/** Tier info, picker, button and mission card at 100% text size. */
internal val SupportStackedTextHeight = 365.dp

internal val SupportMissionCardHeight = 165.dp

/** Padding and navigation bar room when the stage sits beside the controls. */
internal val SupportSideFixedHeight = 60.dp

/** Picker, button and mission card beside the stage at 100% text size. */
internal val SupportSideTextHeight = 340.dp

/** How the page fits the space Settings gives it. The page never scrolls. */
internal data class SupportPageArrangement(val sideBySide: Boolean, val showMission: Boolean)

/**
 * Windows tall enough keep today's stacked page with its spacing. Wide windows too short for it
 * put the stage beside the controls; narrow ones drop the mission card first.
 */
internal fun supportPageArrangement(width: Dp, height: Dp, fontScale: Float): SupportPageArrangement {
    val text = fontScale.coerceAtLeast(1f)
    return when {
        height >= SupportStackedFixedHeight + SupportStackedTextHeight * text ->
            SupportPageArrangement(sideBySide = false, showMission = true)
        width > height ->
            SupportPageArrangement(sideBySide = true, showMission = height >= SupportSideFixedHeight + SupportSideTextHeight * text)
        else -> SupportPageArrangement(sideBySide = false, showMission = false)
    }
}

@Composable
internal fun SupportPageBody(
    modifier: Modifier,
    stage: @Composable () -> Unit,
    picker: @Composable () -> Unit,
    cta: @Composable () -> Unit,
    mission: @Composable () -> Unit,
) {
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    BoxWithConstraints(modifier.fillMaxSize()) {
        val arrangement = supportPageArrangement(maxWidth, maxHeight, LocalDensity.current.fontScale)
        if (arrangement.sideBySide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { stage() }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    picker()
                    Spacer(modifier = Modifier.height(14.dp))
                    cta()
                    if (arrangement.showMission) {
                        Spacer(modifier = Modifier.height(20.dp))
                        mission()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .boxloreCardWidth(BoxloreCardFamily.Utility)
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                stage()
                Spacer(modifier = if (tablet) Modifier.height(20.dp) else Modifier.weight(1f))
                picker()
                Spacer(modifier = Modifier.height(14.dp))
                cta()
                Spacer(modifier = if (tablet) Modifier.height(20.dp) else Modifier.weight(1f))
                if (arrangement.showMission) mission()
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/** Today's cosmic aura for the power cells, or the world's painted scene. */
@Composable
internal fun BoxScope.SupportPageBackground(style: SupportPageStyle, activeAuraColor: Color) {
    val skin = style.skin
    if (skin != null) {
        SupportWorldBackdrop(skin, activeAuraColor)
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "support_page_atmosphere")
    val plasmaPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "plasma_pulse",
    )
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle_progress",
    )
    CosmicAtmosphereCanvas(
        auraColor = activeAuraColor,
        pulse = plasmaPulse,
        particleProgress = particleProgress,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .align(Alignment.TopCenter),
    )
}

/** The codename size that fits [text] on one line within [availableWidthPx]. */
@Composable
internal fun rememberFittedCodenameSize(
    text: String,
    font: SupportWorldFont,
    fullSize: Float,
    letterSpacing: Float,
    availableWidthPx: Int,
): Float {
    val measurer = rememberTextMeasurer()
    return remember(text, font, fullSize, letterSpacing, availableWidthPx) {
        val measured = measurer.measure(
            text = text,
            style = TextStyle(
                fontFamily = font.family,
                fontWeight = font.weight,
                fontSize = fullSize.sp,
                letterSpacing = letterSpacing.sp,
            ),
            maxLines = 1,
            softWrap = false,
        )
        // Display faces with swashes draw past their advance width, so leave them some room.
        supportCodenameFontSize(fullSize, measured.size.width.toFloat(), availableWidthPx * 0.9f)
    }
}
