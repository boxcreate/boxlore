package cx.aswin.boxlore.wear.ui.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.ui.components.ScreenFit
import cx.aswin.boxlore.wear.ui.components.rememberScreenScale

/** The places Home leads to. */
internal enum class HomeLink { Shows, JumpBackIn, NewEpisodes, Search, DailyMix, Downloads, Settings }

private val TileHeight = 52.dp
private val LeftTile = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 10.dp, bottomEnd = 10.dp)
private val RightTile = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 26.dp, bottomEnd = 26.dp)

@Composable
internal fun HomeScreen(
    nowPlaying: NowPlaying?,
    hasDailyMix: Boolean,
    onOpenPlayer: () -> Unit,
    onOpen: (HomeLink) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val atTop by remember { derivedStateOf { !listState.canScrollBackward } }
    // Another episode can make the dome taller. A list resting near the top goes back to the very
    // top then, or the taller dome would push its own first line off the screen.
    val domeKey = nowPlaying?.let { it.episode.id to it.place }
    LaunchedEffect(domeKey) { if (listState.anchorItemIndex <= 1) listState.scrollToItem(0) }
    ScreenScaffold(
        scrollState = listState,
        // At rest the time sits on the dome and needs no patch behind it; once the list moves, it does.
        timeText = { TimeText(backgroundColor = if (atTop) Color.Transparent else TimeTextDefaults.backgroundColor()) },
    ) { contentPadding ->
        val round = LocalConfiguration.current.isScreenRound
        val side = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
        // On a round watch the dome starts at the very top of the screen, behind the time, and spans its width.
        val listPadding =
            PaddingValues(top = if (round) 0.dp else contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
        TransformingLazyColumn(state = listState, contentPadding = listPadding) {
            item {
                val modifier = if (round) Modifier else Modifier.padding(horizontal = side)
                if (nowPlaying != null) NowPlayingDome(nowPlaying, onOpenPlayer, modifier) else ReadyDome(modifier)
            }
            item {
                TilePair(
                    first = HomeLink.Shows,
                    second = HomeLink.JumpBackIn,
                    onOpen = onOpen,
                    modifier = Modifier.padding(horizontal = side).transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item {
                TilePair(
                    first = HomeLink.NewEpisodes,
                    second = HomeLink.Search,
                    onOpen = onOpen,
                    modifier = Modifier.padding(horizontal = side).transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item {
                // Without a Daily Mix, Downloads and Settings share the last row.
                TilePair(
                    first = if (hasDailyMix) HomeLink.DailyMix else HomeLink.Downloads,
                    second = if (hasDailyMix) HomeLink.Downloads else HomeLink.Settings,
                    onOpen = onOpen,
                    modifier = Modifier.padding(horizontal = side).transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            if (hasDailyMix) {
                item {
                    FilledTonalButton(
                        onClick = { onOpen(HomeLink.Settings) },
                        modifier = Modifier.padding(horizontal = side).transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.wear_settings)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TilePair(
    first: HomeLink,
    second: HomeLink,
    onOpen: (HomeLink) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val firstSource = remember { MutableInteractionSource() }
    val secondSource = remember { MutableInteractionSource() }
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 6.dp),
        transformation = transformation,
    ) {
        HomeTile(first, LeftTile, firstSource, { onOpen(first) }, Modifier.animateWidth(firstSource))
        HomeTile(second, RightTile, secondSource, { onOpen(second) }, Modifier.animateWidth(secondSource))
    }
}

@Composable
private fun HomeTile(
    link: HomeLink,
    shape: Shape,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(TileHeight),
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        interactionSource = interactionSource,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = link.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(link.label()),
                style = if (ScreenFit.isCompact(rememberScreenScale())) MaterialTheme.typography.bodyExtraSmall else MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun HomeLink.icon(): ImageVector =
    when (this) {
        HomeLink.Shows -> Icons.Rounded.Podcasts
        HomeLink.JumpBackIn -> Icons.Rounded.History
        HomeLink.NewEpisodes -> Icons.Rounded.AutoAwesome
        HomeLink.Search -> Icons.Rounded.Search
        HomeLink.DailyMix -> Icons.Rounded.Shuffle
        HomeLink.Downloads -> Icons.Rounded.DownloadDone
        HomeLink.Settings -> Icons.Rounded.Settings
    }

private fun HomeLink.label(): Int =
    when (this) {
        HomeLink.Shows -> R.string.wear_your_shows
        HomeLink.JumpBackIn -> R.string.wear_jump_back_in
        HomeLink.NewEpisodes -> R.string.wear_new_episodes
        HomeLink.Search -> R.string.wear_search
        HomeLink.DailyMix -> R.string.wear_daily_mix
        HomeLink.Downloads -> R.string.wear_downloads
        HomeLink.Settings -> R.string.wear_settings
    }
