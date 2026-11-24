package cx.aswin.boxlore.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The rail's item for the current page, so a page can send Left straight to the rail; null outside one. */
val LocalRailFocus = staticCompositionLocalOf<FocusRequester?> { null }

/**
 * Left from this item opens [rail]. For the first item of a row that sits above content starting
 * further left, where the remote would otherwise drop down to that content instead.
 */
fun Modifier.leftOpens(rail: FocusRequester?): Modifier = if (rail == null) this else focusProperties { left = rail }

/** The four places the rail leads to, with the phone's icons (filled when current). */
enum class RailTab(val route: String, val label: Int, val selectedIcon: ImageVector, val icon: ImageVector) {
    Search(TvRoutes.SEARCH, R.string.tv_nav_search, Icons.Filled.Search, Icons.Filled.Search),
    Home(TvRoutes.HOME, R.string.tv_nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    Library(TvRoutes.LIBRARY, R.string.tv_nav_library, Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
    Settings(TvRoutes.SETTINGS, R.string.tv_nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

/** The open rail's width; closed, it is just its icons. */
private val OpenRailWidth = 260.dp
private const val SCRIM_ALPHA = 0.6f

/**
 * The rail on the left: icons only until the remote reaches it, then labels over a dimmed page.
 * It opens in one step, with a solid dim and no width animation, because animating a drawer's
 * width lays the whole screen out again on every frame, which older TVs cannot keep up with. The
 * current page keeps a tonal fill; the item the remote is on gets the iris fill.
 *
 * The page hands the remote to the rail only on Left, so Up or Down at a page's edge never opens
 * it. Right or Back closes the rail and returns to the item the remote was on before.
 */
@Composable
fun RailScaffold(current: RailTab, go: TvNavigator, nowPlaying: StateFlow<NowPlaying?>, atRoot: Boolean = true, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val currentItem = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val closeToContent = {
        runCatching { contentFocus.requestFocus() }
        Unit
    }
    BackHandler(enabled = open, onBack = closeToContent)
    val rightCloses =
        Modifier.onPreviewKeyEvent { event ->
            val right = event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight
            if (right) closeToContent()
            right
        }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .focusRequester(contentFocus)
                .focusProperties { onExit = { if (requestedFocusDirection != FocusDirection.Left) cancelFocusChange() } }
                .focusRestorer()
                .focusGroup(),
        ) {
            CompositionLocalProvider(LocalRailFocus provides currentItem) { content() }
        }
        if (open) Box(Modifier.fillMaxSize().background(TvColors.scrim.copy(alpha = SCRIM_ALPHA)))
        Box(
            Modifier.fillMaxHeight()
                .then(if (open) Modifier.width(OpenRailWidth).background(TvColors.surfaceHigh) else Modifier)
                .onFocusChanged { open = it.hasFocus }
                .padding(start = TvSpace.safeSide - 8.dp, end = 12.dp, top = TvSpace.safeTop, bottom = TvSpace.safeTop),
        ) {
            Image(painterResource(R.drawable.ic_boxlore_mark), contentDescription = null, modifier = Modifier.padding(start = 16.dp, top = 4.dp).size(28.dp))
            Column(
                // Coming into the rail lands on the current page, not the item nearest the remote.
                Modifier.align(Alignment.CenterStart).focusRestorer(currentItem).focusGroup().then(rightCloses),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RailTab.entries.forEach { tab ->
                    val selected = tab == current
                    RailItem(
                        tab = tab,
                        selected = selected,
                        open = open,
                        // Closed, only the current page can take the remote, so Left from anywhere lands on it.
                        modifier = if (selected) Modifier.focusRequester(currentItem) else Modifier.focusProperties { canFocus = open },
                        onClick = {
                            when {
                                !selected -> go.tab(tab.route)
                                // A show or episode page sits under Home: Home goes back to the top of it.
                                !atRoot -> go.home()
                                else -> closeToContent()
                            }
                        },
                    )
                }
            }
            // Down from the last place, like the others only while the rail is open.
            NowPlayingRailItem(
                nowPlaying = nowPlaying,
                open = open,
                onClick = go::player,
                modifier = Modifier.align(Alignment.BottomStart).focusProperties { canFocus = open }.then(rightCloses),
            )
        }
    }
}

/** What the rail shows of the episode in the player; nothing changes it but a new episode or play and pause. */
private data class RailNow(val title: String, val artworkUrl: String?, val isPlaying: Boolean)

/**
 * The episode in the player, kept at the foot of the rail on every page: its cover with a badge
 * for playing or paused, and with the rail open its title. OK opens the player; the remote's own
 * play and pause key works from anywhere.
 */
@Composable
private fun NowPlayingRailItem(nowPlaying: StateFlow<NowPlaying?>, open: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val now by remember(nowPlaying) {
        nowPlaying.map { it?.takeIf { playing -> playing.loaded }?.let { playing -> RailNow(playing.episode.title, playing.episode.artworkUrl, playing.isPlaying) } }
            .distinctUntilChanged()
    }.collectAsState(null)
    val shown = now ?: return
    Surface(
        onClick = onClick,
        modifier = if (open) modifier.fillMaxWidth() else modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(28.dp)),
        colors =
        ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TvColors.onSurface,
            focusedContainerColor = TvColors.primary,
            focusedContentColor = TvColors.onPrimary,
            pressedContainerColor = TvColors.primary,
            pressedContentColor = TvColors.onPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp)) {
                Artwork(shown.artworkUrl, Modifier.fillMaxSize(), CircleShape)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(18.dp).background(TvColors.surfaceHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (shown.isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = if (shown.isPlaying) TvColors.primary else TvColors.onSurface,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            if (open) {
                Column(Modifier.padding(end = 8.dp)) {
                    Text(shown.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(if (shown.isPlaying) R.string.tv_now_playing else R.string.tv_paused), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

/** One place in the rail: its icon, and its name while the rail is open. */
@Composable
private fun RailItem(tab: RailTab, selected: Boolean, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = if (open) modifier.fillMaxWidth() else modifier,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors =
        ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvColors.primaryContainer else Color.Transparent,
            contentColor = if (selected) TvColors.onPrimaryContainer else TvColors.onSurfaceVariant,
            focusedContainerColor = TvColors.primary,
            focusedContentColor = TvColors.onPrimary,
            pressedContainerColor = TvColors.primary,
            pressedContentColor = TvColors.onPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = stringResource(tab.label), modifier = Modifier.size(24.dp))
            if (open) Text(stringResource(tab.label), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}
