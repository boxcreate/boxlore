package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreVisibleWindowSize
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines

/**
 * Lore's first-load placeholder: a card in the real card's place and shape, with a source row,
 * two heading lines, three body lines and the action bar softly shimmering. It carries no
 * `lore_active_card` tag and says nothing to screen readers; the header above it is already real.
 */
@Composable
internal fun LoreCardSkeleton(accentColor: Color, modifier: Modifier = Modifier) {
    val pane = cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds.current
    val windowSize = if (LocalBoxloreWindowLayout.current.usesTabletLayout && pane != null) {
        androidx.compose.ui.unit.DpSize(pane.width.dp, pane.height.dp)
    } else {
        boxloreVisibleWindowSize()
    }
    val compactSpacing = windowSize.width < 360.dp || windowSize.height < 720.dp
    val base = Color.White.copy(alpha = 0.10f)
    val highlight = Color.White.copy(alpha = 0.22f)
    val spec = loreTextSpecs.first()
    val heading = MaterialTheme.typography.headlineMedium.loreHeading(spec)
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    LoreDeckFrame(modifier.clearAndSetSemantics { }) { short ->
        ShimmerScope(active = true) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = loreCardColor(accentColor),
                modifier = Modifier.fillMaxSize().testTag(LORE_CARD_SKELETON_TAG),
            ) {
                Column(Modifier.fillMaxSize()) {
                    val side = if (short) 8.dp else 16.dp
                    SkeletonBlock(
                        modifier = Modifier.padding(start = side, end = side, top = if (compactSpacing) 8.dp else 12.dp)
                            .fillMaxWidth().height(loreBarHeight(compactSpacing)),
                        shape = CircleShape,
                        baseColor = base,
                        highlightColor = highlight,
                    )
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .padding(horizontal = spec.horizontalPadding.dp, vertical = spec.verticalPadding.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SkeletonTextLines(heading, lines = 2, widths = listOf(0.95f, 0.7f), baseColor = base, highlightColor = highlight)
                        Spacer(Modifier.height(spec.gap.dp))
                        SkeletonTextLines(body, lines = 3, widths = listOf(1f, 0.92f, 0.55f), baseColor = base, highlightColor = highlight)
                    }
                    SkeletonBlock(
                        modifier = Modifier.padding(horizontal = side, vertical = loreActionBarPadding(compactSpacing, short))
                            .fillMaxWidth().height(loreActionBarHeight(compactSpacing)),
                        shape = RoundedCornerShape(loreActionBarHeight(compactSpacing) / 2),
                        baseColor = base,
                        highlightColor = highlight,
                    )
                }
            }
        }
    }
}

internal const val LORE_CARD_SKELETON_TAG = "lore_card_skeleton"
