package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls

/** Four look choices in a row, each a small drawing plus "tap up to N". */
@Composable
internal fun FolderLookRow(
    selected: FolderDisplaySize?,
    onSelect: (FolderDisplaySize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FolderLookOptions.forEach { size ->
            val isSelected = size == selected
            Surface(
                onClick = { onSelect(size) },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        this.selected = isSelected
                        role = Role.RadioButton
                    },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // A fixed-height frame keeps all four choices the same height.
                    Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                        LookDiagram(size = size, highlighted = isSelected)
                    }
                    Text(text = folderLookName(size), style = MaterialTheme.typography.labelLarge, fontWeight = GoogleSansWeight.semiBold)
                    Text(
                        text = "tap up to ${folderSlotCount(size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A tiny sketch of where the look sits: a tile among shows, or a block of 1 to 3 rows on top. */
@Composable
private fun LookDiagram(size: FolderDisplaySize, highlighted: Boolean) {
    val strong = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val soft = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.width(48.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (size == FolderDisplaySize.COMPACT) {
            repeat(2) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) { col ->
                        DiagramBlock(color = if (rowIndex == 0 && col == 0) strong else soft, modifier = Modifier.weight(1f).height(14.dp))
                    }
                }
            }
        } else {
            DiagramBlock(color = strong, modifier = Modifier.fillMaxWidth().height((8 * size.spanRows + 4).dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) { DiagramBlock(color = soft, modifier = Modifier.weight(1f).height(8.dp)) }
            }
        }
    }
}

@Composable
private fun DiagramBlock(color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(3.dp)).background(color))
}

/** Tile folders show their icon (a tap opens the folder) or four covers (each opens its show). */
@Composable
internal fun TileCoverChoice(
    showsCovers: Boolean,
    onChange: (Boolean) -> Unit,
    iconKey: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TileCoverOption(
            selected = !showsCovers,
            title = "Show the icon",
            subtitle = "A tap opens the folder",
            icon = GenreIcons.folderIconOrFallback(iconKey),
            onClick = { onChange(false) },
            modifier = Modifier.weight(1f),
        )
        TileCoverOption(
            selected = showsCovers,
            title = "Show covers",
            subtitle = "Tap up to 4 shows",
            icon = Icons.Rounded.GridView,
            onClick = { onChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TileCoverOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics {
            this.selected = selected
            role = Role.RadioButton
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = GoogleSansWeight.semiBold)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Two by two look cards with a preview drawn from the folder's own covers. */
@Composable
internal fun FolderLookCards(
    selected: FolderDisplaySize,
    onSelect: (FolderDisplaySize) -> Unit,
    shows: List<Podcast>,
    iconKey: String?,
    tileShowsCovers: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FolderLookOptions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { size ->
                    FolderLookCard(
                        size = size,
                        selected = size == selected,
                        shows = shows,
                        iconKey = iconKey,
                        tileShowsCovers = tileShowsCovers,
                        onClick = { onSelect(size) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderLookCard(
    size: FolderDisplaySize,
    selected: Boolean,
    shows: List<Podcast>,
    iconKey: String?,
    tileShowsCovers: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics {
            this.selected = selected
            role = Role.RadioButton
        },
    ) {
        Box {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (size == FolderDisplaySize.COMPACT) {
                        TilePreview(shows = shows, iconKey = iconKey, showsCovers = tileShowsCovers)
                    } else {
                        RowsPreview(size = size, shows = shows)
                    }
                }
                Column {
                    Text(text = folderLookName(size), style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.semiBold)
                    Text(
                        text = folderLookDescription(size),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TilePreview(shows: List<Podcast>, iconKey: String?, showsCovers: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp),
        ) {
            if (showsCovers && shows.isNotEmpty()) {
                MiniCoverGrid(shows = shows.take(4), columns = 2, modifier = Modifier.padding(3.dp))
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = GenreIcons.folderIconOrFallback(iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

@Composable
private fun RowsPreview(size: FolderDisplaySize, shows: List<Podcast>) {
    val slots = calculateFolderSlots(shows, size)
    val cells: List<PreviewCell> = slots.visibleShows.map { PreviewCell.Show(it) } +
        (if (slots.hasOverflow) listOf(PreviewCell.Overflow(slots.overflowCount)) else emptyList())
    val spots = size.spanRows * 3
    // Covers shrink as rows are added so every look fits the same preview height.
    val cell = when (size.spanRows) {
        1 -> 44.dp
        2 -> 40.dp
        else -> 28.dp
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (cells + List((spots - cells.size).coerceAtLeast(0)) { PreviewCell.Empty }).take(spots).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { spot ->
                    Box(
                        modifier = Modifier.size(cell).clip(RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (spot) {
                            is PreviewCell.Show -> PreviewCover(spot.podcast)
                            is PreviewCell.Overflow -> Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = "+${spot.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            PreviewCell.Empty -> Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                        }
                    }
                }
            }
        }
    }
}

private sealed interface PreviewCell {
    data class Show(val podcast: Podcast) : PreviewCell
    data class Overflow(val count: Int) : PreviewCell
    data object Empty : PreviewCell
}

@Composable
private fun MiniCoverGrid(shows: List<Podcast>, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shows.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { show ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp))) { PreviewCover(show) }
                }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun PreviewCover(show: Podcast, modifier: Modifier = Modifier) {
    OptimizedImage(
        url = show.imageUrl.takeIf { it.isNotEmpty() } ?: show.fallbackImageUrl,
        fallbackUrls = show.artworkUrls(),
        proxyWidth = 120,
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
    )
}
