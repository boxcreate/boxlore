package cx.aswin.boxlore.feature.history.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
 * The Insights tab's story card (A4): "Your October story", 150dp, 28dp corners, in the story's
 * primary; a blob and a happy Echo at the left, a New badge until it is opened, and a play button.
 */
@Composable
internal fun StoryEntryCard(month: StoryMonth, isNew: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = rememberStoryPalette()
    val spec = rememberEchoCharacterSpec()
    val name = StoryText.monthName(month.month, currentLocale())
    val label = stringResource(R.string.history_story_watch, name)
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp)
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
        Box(Modifier.offset((-18).dp, 36.dp).size(160.dp).graphicsLayer { rotationZ = -8f }.clip(StoryBlobShape.Entry).background(palette.podium))
        // The board's picture: Echo's outline plus a 3% margin, 168dp tall from 12dp down, running off the card's foot.
        spec?.reaches?.get(ENTRY_POSE)?.let { reach ->
            val square = StoryEchoStage.pose(reach, StoryEchoBox(12f, 12f, 168f))
            EchoCharacter(
                ENTRY_SEQUENCE,
                Modifier.offset(square.left.dp, square.top.dp).wrapContentSize(Alignment.TopStart, unbounded = true).size(square.side.dp),
                accents = palette.echoOwn,
            )
        }
        Row(Modifier.fillMaxSize().padding(start = 138.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isNew) {
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(palette.onPrimary).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.history_story_new), style = StoryType.of(11f, 14f, GoogleSansWeight.bold), color = palette.onContainer)
                    }
                }
                Text(stringResource(R.string.history_story_entry_title, name), style = StoryType.of(20f, 24f, GoogleSansWeight.semiBold), color = palette.onPrimary)
                Text(stringResource(R.string.history_story_entry_meta), style = StoryType.of(13f, 18f), color = palette.onPrimary)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(palette.onPrimary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * "Earlier stories" (A5): a tile a month, three across, 10dp apart: surfaceContainer, 22dp corners,
 * 168dp tall, with a primaryContainer blob and Echo in the month's outfit above the month and its time.
 * Each row is its own list item, so only rows on screen are composed.
 */
internal fun LazyListScope.earlierStories(months: List<StoryMonth>, onOpen: (StoryMonth) -> Unit) {
    if (months.isEmpty()) return
    item(key = "earlier-stories") {
        Text(stringResource(R.string.history_story_earlier), style = TimelineType.header, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
    }
    months.chunked(TILES_PER_ROW).forEach { row ->
        item(key = "earlier-stories-${row.first().month}") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { month -> StoryTile(month, Modifier.weight(1f)) { onOpen(month) } }
                repeat(TILES_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private const val TILES_PER_ROW = 3
private const val ENTRY_SEQUENCE = "story-entry"
private const val ENTRY_POSE = "front-rest"

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
