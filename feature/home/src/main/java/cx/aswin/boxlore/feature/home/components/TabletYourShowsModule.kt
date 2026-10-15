package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast

internal data class YourShowsRailLayout(val stacked: Boolean = false, val height: androidx.compose.ui.unit.Dp = HomeMixLayout.RailHeight)
internal val LocalYourShowsRailLayout = staticCompositionLocalOf { YourShowsRailLayout() }
internal val LocalYourShowsPickerColumns = staticCompositionLocalOf { 0 }

/** Larger libraries scroll inside this many sidebar rows, composing only visible covers like the phone grid. */
internal const val YourShowsSidebarMaxRows = 4

@Composable
internal fun YourShowsModuleFrame(sidebar: Boolean, selectorCount: Int, background: Modifier, selectors: @Composable () -> Unit, content: @Composable () -> Unit) {
    val latestSelectors = rememberUpdatedState(selectors)
    val latestContent = rememberUpdatedState(content)
    val retainedSelectors = remember { movableContentOf { latestSelectors.value() } }
    val retainedContent = remember { movableContentOf { latestContent.value() } }
    if (!sidebar) {
        Column(background.testTag("home_your_shows_surface")) {
            retainedSelectors()
            YourShowsDivider()
            retainedContent()
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("home_your_shows_surface")) {
        val sideBySide = maxWidth >= 672.dp
        val gap = 16.dp
        val density = LocalDensity.current
        val paneGap = with(density) { gap.roundToPx() }
        val halfWidthPx = ((with(density) { maxWidth.roundToPx() } - paneGap) / 2).coerceAtLeast(1)
        val preferredColumns = ((selectorCount + 1) / 2).coerceAtLeast(2)
        val tilePx = with(density) { YourShowsLayout.TileWidth.roundToPx() }
        val paddingPx = with(density) { YourShowsLayout.Padding.roundToPx() }
        val tileGapPx = with(density) { 8.dp.roundToPx() }
        val pickerWidthPx = (paddingPx * 2 + tilePx * preferredColumns + tileGapPx * (preferredColumns - 1))
            .coerceAtLeast(with(density) { 168.dp.roundToPx() }).coerceAtMost(halfWidthPx)
            .let { if (sideBySide) it else with(density) { maxWidth.roundToPx() } }
        val columns = ((pickerWidthPx - paddingPx * 2 + tileGapPx) / (tilePx + tileGapPx)).coerceAtLeast(1)
        val rows = ((selectorCount + columns - 1) / columns).coerceAtMost(YourShowsSidebarMaxRows)
        val pickerHeightPx = if (selectorCount > 1) paddingPx * 2 + tilePx * rows + tileGapPx * (rows - 1) else 0
        val pickerHeight = with(density) { pickerHeightPx.toDp() }
        val railLayout = yourShowsRailLayout(sideBySide, selectorCount, pickerWidthPx, halfWidthPx, pickerHeight)
        Layout(content = {
            Box(background.testTag("home_shows_picker_pane"))
            Box(background.testTag("home_shows_content_pane"))
            Box { CompositionLocalProvider(LocalYourShowsPickerColumns provides columns) { retainedSelectors() } }
            Box { CompositionLocalProvider(LocalYourShowsRailLayout provides railLayout) { retainedContent() } }
        }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
            if (constraints.maxWidth < 672.dp.roundToPx()) {
                val selector = measurables[2].measure(Constraints.fixedWidth(constraints.maxWidth))
                val body = measurables[3].measure(Constraints.fixedWidth(constraints.maxWidth))
                val pickerBackground = measurables[0].measure(Constraints.fixed(selector.width, selector.height))
                val bodyBackground = measurables[1].measure(Constraints.fixed(body.width, body.height))
                return@Layout layout(constraints.maxWidth, selector.height + body.height + gap.roundToPx()) {
                    pickerBackground.placeRelative(0, 0)
                    selector.placeRelative(0, 0)
                    bodyBackground.placeRelative(0, selector.height + gap.roundToPx())
                    body.placeRelative(0, selector.height + gap.roundToPx())
                }
            }
            val contentWidth = (constraints.maxWidth - pickerWidthPx - gap.roundToPx()).coerceAtLeast(1)
            val selector = measurables[2].measure(Constraints.fixedWidth(pickerWidthPx))
            val body = measurables[3].measure(Constraints.fixedWidth(contentWidth))
            val height = maxOf(selector.height, body.height)
            val pickerBackground = measurables[0].measure(Constraints.fixed(selector.width, height))
            val bodyBackground = measurables[1].measure(Constraints.fixed(body.width, height))
            layout(constraints.maxWidth, height) {
                pickerBackground.placeRelative(0, 0)
                bodyBackground.placeRelative(selector.width + gap.roundToPx(), 0)
                selector.placeRelative(0, (height - selector.height) / 2)
                body.placeRelative(selector.width + gap.roundToPx(), 0)
            }
        }
    }
}

@Composable
private fun yourShowsRailLayout(sideBySide: Boolean, selectorCount: Int, pickerWidthPx: Int, halfWidthPx: Int, pickerHeight: Dp): YourShowsRailLayout {
    if (!sideBySide) return YourShowsRailLayout()
    val stacked = selectorCount > 6 && pickerWidthPx == halfWidthPx
    return YourShowsRailLayout(
        stacked = stacked,
        height = (pickerHeight - (HomeMixLayout.totalHeight - HomeMixLayout.RailHeight))
            .coerceAtLeast(if (stacked) homeMixCardHeight() * 2 + HomeMixLayout.CardGap else homeMixCardHeight()),
    )
}

@Composable
internal fun TabletSingleShowSpotlight(podcast: Podcast, lastSeenId: String?, pin: CoverPin, onOpen: (Podcast) -> Unit) {
    Column(
        Modifier.fillMaxWidth().expressiveClickable(shape = RoundedCornerShape(30.dp), onClick = { onOpen(podcast) }).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectorCover(podcast, lastSeenId, isSelected = true, onClick = { onOpen(podcast) }, modifier = Modifier.fillMaxWidth().aspectRatio(1f), pin = pin, maximumCoverSize = 152.dp)
        Text(podcast.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (podcast.artist.isNotBlank()) Text(podcast.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Wide groups use two bounded episode columns without changing their order or scroll owner. */
@Composable
internal fun YourShowsEpisodeRows(count: Int, allowGrid: Boolean, content: @Composable (Int, Int) -> Unit) {
    if (!allowGrid) {
        repeat(count) { content(it, 1) }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth > 720.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(if (columns == 2) 8.dp else 2.dp)) {
            (0 until count).chunked(columns).forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    indices.forEach { index -> Box(Modifier.weight(1f)) { content(index, columns) } }
                    if (indices.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun VerticalYourShowsSelectors(items: List<Podcast?>, content: @Composable (Podcast?) -> Unit) {
        val columns = LocalYourShowsPickerColumns.current.coerceAtLeast(1)
        if ((items.size + columns - 1) / columns > YourShowsSidebarMaxRows) {
            val gap = 8.dp
            LazyVerticalGrid(
                columns = GridCells.FixedSize(YourShowsLayout.TileWidth),
                modifier = Modifier.fillMaxWidth()
                    .height(YourShowsLayout.Padding * 2 + YourShowsLayout.TileWidth * YourShowsSidebarMaxRows + gap * (YourShowsSidebarMaxRows - 1))
                    .testTag("home_show_sidebar_grid"),
                contentPadding = PaddingValues(YourShowsLayout.Padding),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            ) {
                items(items, key = { it?.id ?: "mixtape" }) { podcast -> content(podcast) }
            }
            return
        }
        Column(Modifier.fillMaxWidth().padding(YourShowsLayout.Padding), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { podcast -> androidx.compose.runtime.key(podcast?.id ?: "mixtape") { content(podcast) } }
                }
            }
        }
}
