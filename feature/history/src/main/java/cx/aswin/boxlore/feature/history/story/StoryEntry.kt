package cx.aswin.boxlore.feature.history.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.echo.EchoAccents
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.echo.rememberEchoCharacterSpec
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.TimelineType
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import cx.aswin.boxlore.feature.history.timeline.durationText

/**
 * The Insights tab's story card (S0): 214dp, 28dp corners, in the story's primary. "Your September
 * story" with a New badge until it is opened and how many cards it has, the month's time and top show,
 * the top three covers fanned at the top right, a small Echo beside the play button.
 */
@Composable
internal fun StoryEntryCard(month: StoryMonth, isNew: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = rememberStoryPalette()
    val name = StoryText.monthName(month.month, currentLocale())
    val label = stringResource(R.string.history_story_watch, name)
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(214.dp)
            .clip(shape)
            .background(palette.primary)
            .expressiveClickable(shape = shape, onClick = onOpen)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    onOpen()
                    true
                }
            },
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(40.dp, (-50).dp).size(210.dp).graphicsLayer { rotationZ = -8f }.clip(StoryBlobShape.Entry).background(palette.podium))
        EntryCovers.forEach { spot ->
            val url = month.covers.getOrNull(spot.rank) ?: return@forEach
            Box(Modifier.align(Alignment.TopEnd).offset(-spot.end.dp, spot.top.dp).size(spot.size.dp).graphicsLayer { rotationZ = spot.rotation }) {
                StoryCover(url, StoryCoverSpot(0f, 0f, spot.size, spot.size * 0.26f, shadow = 6f))
            }
        }
        Column(Modifier.padding(start = 20.dp, top = 18.dp).widthIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isNew) {
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(palette.onPrimary).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.history_story_new), style = StoryType.of(11f, 14f, GoogleSansWeight.bold), color = palette.onContainer)
                    }
                }
                Text(stringResource(R.string.history_story_entry_meta, month.cards), style = StoryType.of(12f, 16f, GoogleSansWeight.semiBold), color = palette.onPrimary)
            }
            Text(stringResource(R.string.history_story_entry_title, name), style = StoryType.of(22f, 28f, GoogleSansWeight.semiBold), color = palette.onPrimary)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp, end = 150.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(durationText(month.totalMs), style = StoryType.of(36f, 40f, GoogleSansWeight.semiBold, -0.4f), color = palette.onPrimary, maxLines = 1)
            month.topShowName?.let { show ->
                Text(stringResource(R.string.history_story_entry_top, show), style = StoryType.of(14f, 19f), color = palette.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        EntryEcho(palette, Modifier.align(Alignment.BottomEnd).padding(end = 70.dp).offset(y = 6.dp))
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp).size(56.dp).clip(CircleShape).background(palette.onPrimary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = palette.primary, modifier = Modifier.size(24.dp))
        }
    }
}

/** A small Echo at rest, 104dp tall: the hello pose's outline plus the boards' 3% margin. A still, so it draws once. */
@Composable
private fun EntryEcho(palette: StoryPalette, modifier: Modifier) {
    val spec = rememberEchoCharacterSpec()
    val reach = spec?.reaches?.get(ENTRY_POSE) ?: return
    val square = StoryEchoStage.pose(reach, StoryEchoBox(0f, 0f, ENTRY_ECHO_HEIGHT))
    val width = (reach.right - reach.left) * square.side + ENTRY_ECHO_HEIGHT * 0.06f
    Box(modifier.size(width.dp, ENTRY_ECHO_HEIGHT.dp)) {
        EchoCharacter(
            ENTRY_SEQUENCE,
            Modifier.offset(square.left.dp, square.top.dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(square.side.dp),
            accents = palette.echoOwn,
        )
    }
}

private data class EntryCover(val rank: Int, val end: Float, val top: Float, val size: Float, val rotation: Float)

/** Two covers behind at an angle, the top show in front: right edges and tops in dp from the card's top right. */
private val EntryCovers = listOf(
    EntryCover(2, 22f, 26f, 62f, 12f),
    EntryCover(1, 84f, 26f, 62f, -12f),
    EntryCover(0, 50f, 16f, 70f, 0f),
)

private const val ENTRY_ECHO_HEIGHT = 104f

/**
 * "Earlier stories" (A5): a tile a month, three across, 10dp apart: surfaceContainer, 22dp corners,
 * 168dp tall, with a primaryContainer blob and Echo in the month's outfit above the month and its time.
 * Two rows show until [expanded]; a button under them shows the rest or folds them away again.
 * Each row is its own list item, so only rows on screen are composed.
 */
internal fun LazyListScope.earlierStories(months: List<StoryMonth>, expanded: Boolean, onExpand: (Boolean) -> Unit, onOpen: (StoryMonth) -> Unit) {
    if (months.isEmpty()) return
    item(key = "earlier-stories") {
        Text(stringResource(R.string.history_story_earlier), style = TimelineType.header, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
    }
    EarlierStories.shown(months, expanded).chunked(EarlierStories.TILES_PER_ROW).forEach { row ->
        item(key = "earlier-stories-${row.first().month}") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { month -> StoryTile(month, Modifier.weight(1f)) { onOpen(month) } }
                repeat(EarlierStories.TILES_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    if (EarlierStories.folds(months)) {
        item(key = "earlier-stories-more") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { onExpand(!expanded) }) {
                    Text(
                        if (expanded) {
                            stringResource(R.string.history_story_earlier_fewer)
                        } else {
                            pluralStringResource(R.plurals.history_story_earlier_all, months.size, months.size)
                        },
                    )
                    Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                }
            }
        }
    }
}

/** How many earlier stories show: two rows of three until the listener asks for the rest. */
internal object EarlierStories {
    const val TILES_PER_ROW = 3
    private const val ROWS_FOLDED = 2

    /** Whether there are more than two rows, so the list folds and gets its button. */
    fun folds(months: List<StoryMonth>): Boolean = months.size > TILES_PER_ROW * ROWS_FOLDED

    fun shown(months: List<StoryMonth>, expanded: Boolean): List<StoryMonth> = if (expanded) months else months.take(TILES_PER_ROW * ROWS_FOLDED)
}

private const val ENTRY_SEQUENCE = "story-hello-still"
private const val ENTRY_POSE = "t1-rest"

@Composable
private fun StoryTile(month: StoryMonth, modifier: Modifier, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val palette = rememberStoryPalette()
    val name = StoryText.monthTitle(month.month, currentLocale())
    val label = stringResource(R.string.history_story_watch, StoryText.monthName(month.month, currentLocale()))
    val shape = RoundedCornerShape(22.dp)
    val spec = rememberEchoCharacterSpec()
    val sequence = StoryLogic.tileSequence(month.outfit)
    Box(
        modifier
            .height(168.dp)
            .clip(shape)
            .background(colors.surfaceContainer)
            .expressiveClickable(shape = shape, onClick = onOpen)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    onOpen()
                    true
                }
            },
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(84.dp).clip(StoryBlobShape.Entry).background(colors.primaryContainer))
        // The boards' tile picture: Echo's outline plus a 3% margin, 100dp tall from 8dp down, centred.
        val reach = spec?.reaches?.get(month.outfit.asset + "-rest") ?: spec?.reaches?.get("t1-rest")
        if (reach != null) {
            val square = StoryEchoStage.pose(reach, StoryEchoBox(0f, TILE_ECHO_TOP, TILE_ECHO_HEIGHT, centerX = 0f))
            CenteredEcho(sequence, square.side, square.top, square.left, palette.echoOwn)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(name, style = StoryType.of(14f, 18f, GoogleSansWeight.semiBold), color = colors.onSurface, maxLines = 1)
            Text(durationText(month.totalMs), style = TimelineType.meta, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

private const val TILE_ECHO_HEIGHT = 100f
private const val TILE_ECHO_TOP = 8f

/** Echo's [side]-square frame at [top], its left edge [shift] from the tile's centre (so its outline is centred). */
@Composable
private fun CenteredEcho(sequence: String, side: Float, top: Float, shift: Float, accents: EchoAccents) {
    Layout(content = { EchoCharacter(sequence, Modifier.size(side.dp), accents = accents) }) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((constraints.maxWidth / 2f + shift.dp.toPx()).toInt(), top.dp.roundToPx())
        }
    }
}
