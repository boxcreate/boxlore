package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

private val TileShape = RoundedCornerShape(20.dp)
private val PictureShape = RoundedCornerShape(14.dp)

/**
 * One visual choice: a picture of the option, its name, and a check when it is picked. Every
 * visual Appearance choice (hero cards, colors, navigation bar, player, letters) uses this tile, so
 * they all read and select the same way. Picked tiles sit on the primary container with a primary
 * outline; the whole tile is one radio button.
 */
@Composable
internal fun SettingsPictureTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    picture: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clip(TileShape).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = TileShape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().clip(PictureShape), contentAlignment = Alignment.Center, content = picture)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A titled block of picture tiles, styled like [SettingsGroup]'s title and footer but without its
 * card: the tiles are solid surfaces themselves.
 */
@Composable
internal fun SettingsTileSection(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
        if (footer != null) {
            Text(
                text = footer,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shows [content] as a picture: an overlay listens to touches without consuming them, so controls
 * drawn inside (a preview's buttons) never react, while the surrounding tile still receives the tap.
 * The content is hidden from accessibility services; the tile or stage describes it instead.
 */
@Composable
internal fun SettingsPicture(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clearAndSetSemantics {}) {
        content()
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            },
        )
    }
}

/**
 * [items] as one horizontally scrolling row of tiles that share the tallest one's height, opening
 * scrolled to [selectedIndex] so the current choice is in view. Tiles are sized to the row
 * ([settingsPictureRowTileWidth]) so two show whole and the next is cut off at the edge, which tells
 * the listener the row scrolls; [tileWidth] overrides that.
 */
@Composable
internal fun <T> SettingsPictureRow(
    items: List<T>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    tileWidth: Dp = Dp.Unspecified,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = if (tileWidth.isSpecified) tileWidth else settingsPictureRowTileWidth(maxWidth)
        val scroll = rememberScrollState()
        val step = with(LocalDensity.current) { (width + TileGap).roundToPx() }
        LaunchedEffect(Unit) {
            if (selectedIndex > 0) scroll.scrollTo(selectedIndex * step)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll).height(IntrinsicSize.Min).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            items.forEach { item -> tile(item, Modifier.width(width).fillMaxHeight()) }
        }
    }
}

/**
 * A tile width for a [rowWidth] row that shows two tiles whole and about a third of the next, so the
 * cut-off tile reads as more to scroll; kept between 132dp (pictures stay legible) and 200dp.
 */
internal fun settingsPictureRowTileWidth(rowWidth: Dp): Dp =
    ((rowWidth - TileGap * 2) / RowVisibleTiles).coerceIn(RowTileMin, RowTileMax)

private val TileGap = 8.dp
private const val RowVisibleTiles = 2.35f
private val RowTileMin = 132.dp
private val RowTileMax = 200.dp

/** [items] as equal-width tiles in rows of [columns]; tiles in a row share the tallest one's height. */
@Composable
internal fun <T> SettingsPictureGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item -> tile(item, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
