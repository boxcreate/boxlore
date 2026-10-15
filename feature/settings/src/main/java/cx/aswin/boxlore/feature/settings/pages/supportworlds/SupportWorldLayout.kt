package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth

/** Landscape windows shorter than this put the stage beside the controls instead of above them. */
internal val SupportSideBySideMaxHeight = 560.dp

/** Height the stacked controls (tier info, picker, button) need at 100% text size. */
internal val SupportStackedControlsHeight = 330.dp

/** Height the side-by-side controls need at 100% text size. */
internal val SupportSideControlsHeight = 270.dp

internal val SupportMissionCardHeight = 104.dp

/** The stage keeps at least this much height before the mission card gives way. */
internal val SupportMinStageHeight = 220.dp

private const val MIN_STAGE_SCALE = 0.4f

/** Which arrangement a world page uses for the space Settings gives it. */
internal data class SupportWorldArrangement(val sideBySide: Boolean, val showMission: Boolean)

/**
 * The world page never scrolls. Tall windows stack the stage over the controls and drop the
 * mission card before the stage gets too small; short landscape windows sit them side by side.
 */
internal fun supportWorldArrangement(width: Dp, height: Dp, fontScale: Float): SupportWorldArrangement {
    val textScale = fontScale.coerceAtLeast(1f)
    val sideBySide = width > height && height < SupportSideBySideMaxHeight
    val controls = if (sideBySide) SupportSideControlsHeight else SupportStackedControlsHeight
    val stageReserve = if (sideBySide) 0.dp else SupportMinStageHeight
    val showMission = height >= controls * textScale + SupportMissionCardHeight + stageReserve
    return SupportWorldArrangement(sideBySide, showMission)
}

@Composable
internal fun SupportWorldBody(
    modifier: Modifier,
    stage: @Composable (Modifier) -> Unit,
    info: @Composable (compact: Boolean) -> Unit,
    picker: @Composable (compact: Boolean) -> Unit,
    cta: @Composable () -> Unit,
    mission: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val arrangement = supportWorldArrangement(maxWidth, maxHeight, LocalDensity.current.fontScale)
        if (arrangement.sideBySide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ScaledStageSlot(Modifier.weight(1f).fillMaxHeight(), stage)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    info(true)
                    picker(true)
                    cta()
                    if (arrangement.showMission) mission()
                }
            }
        } else {
            Column(
                modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScaledStageSlot(Modifier.weight(1f).fillMaxWidth(), stage)
                info(false)
                Spacer(Modifier.height(12.dp))
                picker(false)
                Spacer(Modifier.height(12.dp))
                cta()
                if (arrangement.showMission) {
                    Spacer(Modifier.height(12.dp))
                    mission()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Gives the stage the space left over: centred when it fits, scaled down from the bottom when not. */
@Composable
private fun ScaledStageSlot(modifier: Modifier, stage: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        if (maxHeight >= SupportStageHeight) {
            stage(Modifier)
        } else {
            val scale = (maxHeight / SupportStageHeight).coerceAtLeast(MIN_STAGE_SCALE)
            stage(
                Modifier
                    .align(Alignment.BottomCenter)
                    .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            )
        }
    }
}
