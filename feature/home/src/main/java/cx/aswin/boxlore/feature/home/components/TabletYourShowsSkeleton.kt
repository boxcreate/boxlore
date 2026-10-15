package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer

@Composable
internal fun TabletYourShowsSkeleton(subscribedCount: Int, modifier: Modifier) {
    val count = if (subscribedCount == 0) 10 else subscribedCount + if (subscribedCount > 1) 1 else 0
    val background = Modifier.clip(RoundedCornerShape(30.dp)).background(homeMixModuleColor())
    Column(modifier.widthIn(max = 1200.dp).fillMaxWidth().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TabletShowPlaceholder(Modifier.width(120.dp).height(24.dp))
            TabletShowPlaceholder(Modifier.size(32.dp))
        }
        YourShowsModuleFrame(
            sidebar = boxloreContentWidthDp() >= 672f,
            selectorCount = count,
            background = background,
            selectors = { TabletSelectorSkeletons(count) },
            content = {
                Column(Modifier.padding(top = HomeMixLayout.TopPadding, bottom = HomeMixLayout.BottomPadding)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = HomeMixLayout.ContentInset).height(HomeMixLayout.HeaderHeight), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TabletShowPlaceholder(Modifier.width(132.dp).height(34.dp))
                        TabletShowPlaceholder(Modifier.width(88.dp).height(HomeMixLayout.HeaderHeight))
                    }
                    Spacer(Modifier.height(HomeMixLayout.HeaderGap))
                    TabletMixSkeletons()
                }
            },
        )
    }
}

@Composable
private fun TabletSelectorSkeletons(count: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(YourShowsLayout.Padding)) {
        val columns = LocalYourShowsPickerColumns.current.takeIf { it > 0 } ?: ((maxWidth + 8.dp) / (YourShowsLayout.TileWidth + 8.dp)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until count).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { index ->
                        Box(Modifier.size(YourShowsLayout.TileWidth).padding(YourShowsLayout.TilePadding)) {
                            TabletShowPlaceholder(Modifier.size(YourShowsLayout.CoverSize).testTag("home_show_skeleton_$index"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletMixSkeletons() {
    val layout = LocalYourShowsRailLayout.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(layout.height).clipToBounds()) {
        if (layout.stacked) {
            Column(Modifier.padding(horizontal = HomeMixLayout.ContentInset), verticalArrangement = Arrangement.spacedBy(HomeMixLayout.CardGap)) {
                repeat(3) { TabletShowPlaceholder(Modifier.fillMaxWidth().height(homeMixCardHeight()).testTag("home_mix_skeleton_$it")) }
            }
        } else {
            val cardWidth = HomeMixLayout.railCardWidth(maxWidth)
            Row(Modifier.padding(horizontal = HomeMixLayout.ContentInset), horizontalArrangement = Arrangement.spacedBy(HomeMixLayout.CardGap)) {
                repeat(3) { TabletShowPlaceholder(Modifier.width(cardWidth).height(homeMixCardHeight()).testTag("home_mix_skeleton_$it")) }
            }
        }
    }
}

@Composable
private fun TabletShowPlaceholder(modifier: Modifier) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Box(modifier.m3Shimmer(base, highlight, RoundedCornerShape(20.dp)))
}
