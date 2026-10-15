package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ArtworkShade
import cx.aswin.boxlore.core.designsystem.theme.cachedArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.loadArtworkPalette
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableHeroList
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How much room Marquee's hero leaves at its top for Home's floating top bar; 0 anywhere else. */
val LocalHeroTopInset = compositionLocalOf { 0.dp }

/** Your shows' sheet starts this far up over Marquee's hero, with rounded top corners. */
internal val MarqueeSheetOverlap = 24.dp
internal val MarqueeSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Play (172dp) and details beside it: Play keeps one width on every phone, and the up-next covers take the rest. */
internal val MarqueeActionsWidth = 223.dp

/** The hero's height: the floating top bar's room, the cards' usual height less 48dp, and the sheet's overlap. */
@Composable
internal fun marqueeHeroHeight() = LocalHeroTopInset.current + homeHeroHeight() - MarqueeShorter + MarqueeSheetOverlap

/** The words and actions sit this far above the hero's foot: the sheet's overlap and the page dots' room. */
internal val MarqueeCopyBottom = MarqueeSheetOverlap + MarqueeDotsReserve

/**
 * A Marquee cover's colours: an optional two-tone filter, the shade its words sit on, and the colour
 * Home's top bar gives its wordmark over the cover's top ([topContent], with its frost's [veilAlpha]).
 */
internal data class MarqueeLook(val filter: ColorFilter?, val shade: ArtworkShade, val topContent: Color, val veilAlpha: Float)

/**
 * Marquee's colours for the cover at [imageUrl]: Dark (shown as Full color) keeps the cover as it is
 * under a plain shade, and the top bar's words turn dark over light covers; Artwork and App theme
 * redraw it in two dark-leaning tones, as Duotone does, under the matching shade.
 */
@Composable
internal fun rememberMarqueeLook(imageUrl: String?): MarqueeLook {
    val fullColor = HeroCoverShade.sanitize(LocalHeroCoverShade.current) == HeroCoverShade.DARK
    val look = rememberDuotoneLook(imageUrl)
    val filter = remember(fullColor, look.duotone) { if (fullColor) null else look.duotone.colorFilter() }
    val average = if (fullColor) rememberCoverAverage(imageUrl) else null
    val topContent = if (fullColor) marqueeTopContent(average) else MarqueeLightContent
    return MarqueeLook(filter, look.shade, topContent, marqueeVeilAlpha(toneKnown = !fullColor || average != null))
}

/** The cover's mean colour from the shared picker (cached ones at once, others once read); null for covers with no palette. */
@Composable
private fun rememberCoverAverage(imageUrl: String?): Int? {
    val context = LocalContext.current
    val url = imageUrl?.trim()?.takeIf(String::isNotEmpty)
    var average by remember(url) { mutableStateOf(url?.let(::cachedArtworkPalette)?.average) }
    LaunchedEffect(url) {
        if (url != null && average == null) average = runCatching { loadArtworkPalette(context, url) }.getOrNull()?.average
    }
    return average
}

/** A card further along the hero, shown as a small cover beside Play. */
internal data class MarqueeNext(val index: Int, val imageUrl: String?, val label: String)

/**
 * The Marquee hero: one card at a time across the whole width, from the top of the screen (under the
 * floating top bar) down to where Your shows' rounded sheet begins. Swiping moves between cards, as
 * do the up-next covers beside Play; collections pick their episode from a strip of covers. The hero
 * also moves on by itself ([marqueeDwellMillis]), holding while a finger is on it, counting again from
 * when it lifts, and resting while it is mostly scrolled away or Home is off screen.
 */
@Composable
@Suppress("LongParameterList")
internal fun MarqueeHero(
    heroItems: StableHeroList,
    playing: HeroPlayingState,
    playAction: (SmartHeroItem, Int) -> () -> Unit,
    collectionPlayback: (SmartHeroItem, Int) -> (Podcast) -> Unit,
    onDetailsClick: (Podcast) -> Unit,
    onArrowClick: (SmartHeroItem, Int) -> Unit,
    onPageShown: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = heroItems.list
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val currentOnPageShown by rememberUpdatedState(onPageShown)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { currentOnPageShown(it) } }
    val scroll = LocalHeroScrollOffset.current
    val heroHeight = marqueeHeroHeight()
    var touches by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val halfHeight = with(LocalDensity.current) { heroHeight.toPx() } / 2f
    val onScreen by remember(scroll, halfHeight) { derivedStateOf { scroll() < halfHeight } }
    val settled = pager.settledPage.coerceIn(0, items.lastIndex)
    val rotate = marqueeShouldRotate(items.size, LocalMarqueeAutoAdvance.current && rememberMotionAllowed(), holding || dragged, onScreen)
    val dwell = marqueeDwellMillis(items[settled])
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(rotate, touches, settled, dwell, lifecycle) {
        if (!rotate) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(dwell)
            pager.animateScrollToPage((settled + 1) % items.size)
        }
    }
    Box(
        modifier.fillMaxWidth().height(heroHeight).clipToBounds().testTag("home_hero_marquee_hero")
            .marqueeTouches(onPress = {
                holding = true
                touches++
            }, onRelease = { holding = false }),
    ) {
        // Everything but the sheet's edge stays still as Home scrolls, so the body slides over it.
        Box(Modifier.fillMaxSize().marqueePinned(scroll)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val item = items[page]
            if (item.type == HeroType.RESUME_GRID || item.type == HeroType.NEW_EPISODES_GRID) {
                val resume = item.type == HeroType.RESUME_GRID
                MarqueeCollectionCard(
                    items = item.gridItems,
                    title = stringResource(if (resume) R.string.home_hero_resume_collection else R.string.home_hero_new_collection),
                    mode = if (resume) HeroGridMode.Resume else HeroGridMode.NewEpisodes,
                    active = pager.settledPage == page,
                    onCellClick = onDetailsClick,
                    onPlaybackClick = collectionPlayback(item, page),
                    currentPlayingPodcastId = playing.podcastId,
                    currentPlayingEpisodeId = playing.episodeId,
                    isPlaying = playing.isPlaying,
                )
            } else {
                MarqueeHeroCard(
                    item = item,
                    onClick = playAction(item, page),
                    onArrowClick = { onArrowClick(item, page) },
                    currentPlayingPodcastId = playing.podcastId,
                    currentPlayingEpisodeId = playing.episodeId,
                    isPlaying = playing.isPlaying,
                    upNext = marqueeUpNext(items, page),
                    onUpNext = { scope.launch { pager.animateScrollToPage(it) } },
                    active = pager.settledPage == page,
                )
            }
        }
        MarqueePageDots(items.size, pager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = MarqueeSheetOverlap + MarqueeDotsBottom))
        }
        // Your shows' sheet begins over the hero's foot, so the hero reads as Home's backdrop.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(MarqueeSheetOverlap).background(MaterialTheme.colorScheme.surface, MarqueeSheetShape))
    }
}

/** The next three cards after [page], wrapping round, each by its cover and what it is. */
@Composable
private fun marqueeUpNext(items: List<SmartHeroItem>, page: Int): List<MarqueeNext> {
    if (items.size < 2) return emptyList()
    return (1..minOf(MarqueeUpNextCount, items.size - 1)).map { step ->
        val index = (page + step) % items.size
        val item = items[index]
        val name = when (item.type) {
            HeroType.RESUME_GRID -> stringResource(R.string.home_hero_resume_collection)
            HeroType.NEW_EPISODES_GRID -> stringResource(R.string.home_hero_new_collection)
            else -> heroTitle(item)
        }
        val image = when (item.type) {
            HeroType.RESUME_GRID, HeroType.NEW_EPISODES_GRID -> item.gridItems.firstOrNull()?.imageUrl
            else -> heroArtworkUrl(item.podcast, showFirst = item.type == HeroType.SPOTLIGHT)
        }
        MarqueeNext(index, image, stringResource(R.string.home_hero_go_to, name))
    }
}

/**
 * Marquee's single-episode card: the cover across the whole card in [rememberMarqueeLook]'s colours, a
 * frosted band behind the top bar ([MarqueeFrost]) and a shade behind the words at the bottom, Cover's
 * words, and Play with a fixed width, details beside it, and covers of the next cards in the room left
 * over. While [active], it tells the top bar which words read over its cover.
 */
@Composable
@Suppress("LongParameterList")
internal fun MarqueeHeroCard(
    item: SmartHeroItem,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    upNext: List<MarqueeNext>,
    onUpNext: (Int) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val current = HomePlaybackStateLogic.isHeroItemCurrent(
        itemEpisodeId = item.podcast.latestEpisode?.id,
        itemPodcastId = item.podcast.id,
        currentPlayingEpisodeId = currentPlayingEpisodeId,
        currentPlayingPodcastId = currentPlayingPodcastId,
    )
    val pause = isPlaying && current
    val progress = HomeHeroStyle.progress(item.podcast)
    val progressState = if (item.type == HeroType.RESUME && progress > 0f) HeroProgressState(progress, pause) else null
    val imageUrl = heroArtworkUrl(item.podcast, showFirst = item.type == HeroType.SPOTLIGHT)
    val look = rememberMarqueeLook(imageUrl)
    val shade = look.shade
    BoxWithConstraints(modifier.fillMaxSize().background(shade.deep).testTag("home_hero_marquee").expressiveClickable(onClick = onArrowClick)) {
        OptimizedImage(
            url = imageUrl,
            fallbackUrls = heroArtworkSources(item),
            proxyWidth = 1080,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = look.filter,
            modifier = Modifier.fillMaxSize().testTag("home_hero_artwork"),
        )
        MarqueeFrost(imageUrl, heroArtworkSources(item), look.filter, look.topContent, look.veilAlpha, maxHeight)
        if (active) MarqueeReportTone(look.topContent)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().coverScrim(shade.deep)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = MarqueeCopyBottom),
        ) {
            CoverCopy(item, shade, progressState)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(MarqueeActionsWidth)) {
                    HeroActions(
                        pause = pause,
                        resume = item.type == HeroType.RESUME || current,
                        colors = HeroActionColors(play = shade.accent, onPlay = shade.onAccent, details = shade.container, onDetails = shade.content),
                        item = item,
                        onPlayClick = onClick,
                        onDetailsClick = onArrowClick,
                    )
                }
                MarqueeUpNextCovers(upNext, ring = shade.deep, onUpNext, Modifier.weight(1f))
            }
        }
    }
}

/** Tells Home's top bar which words read over the settled card's cover. */
@Composable
internal fun MarqueeReportTone(content: Color) {
    val tone = LocalMarqueeTopTone.current
    LaunchedEffect(tone, content) { tone?.content = content }
}

/** As many up-next covers as fit beside Play (up to three), each overlapping the one before it. */
@Composable
private fun MarqueeUpNextCovers(items: List<MarqueeNext>, ring: Color, onSelect: (Int) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterEnd) {
        val room = maxWidth - MarqueeUpNextLead
        val fit = if (room < MarqueeUpNextSize) 0 else ((room - MarqueeUpNextSize) / MarqueeUpNextStep).toInt() + 1
        val shown = items.take(fit.coerceAtMost(items.size))
        Layout(content = { shown.forEach { MarqueeUpNextCover(it, ring, onSelect) } }) { measurables, _ ->
            val size = MarqueeUpNextSize.roundToPx()
            val step = MarqueeUpNextStep.roundToPx()
            val placeables = measurables.map { it.measure(Constraints.fixed(size, size)) }
            val width = if (placeables.isEmpty()) 0 else size + step * (placeables.size - 1)
            layout(width, if (placeables.isEmpty()) 0 else size) {
                placeables.forEachIndexed { index, placeable -> placeable.placeRelative(index * step, 0) }
            }
        }
    }
}

@Composable
private fun MarqueeUpNextCover(next: MarqueeNext, ring: Color, onSelect: (Int) -> Unit) {
    val look = rememberMarqueeLook(next.imageUrl)
    Box(
        Modifier.size(MarqueeUpNextSize).clip(MarqueeUpNextShape).background(ring)
            .testTag("home_hero_up_next_${next.index}")
            .semantics {
                contentDescription = next.label
                role = Role.Button
            }
            .expressiveClickable(shape = MarqueeUpNextShape) { onSelect(next.index) }
            .padding(3.dp).clip(RoundedCornerShape(12.dp)),
    ) {
        OptimizedImage(
            url = next.imageUrl,
            proxyWidth = 160,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = look.filter,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val MarqueeUpNextCount = 3
private val MarqueeUpNextSize = 48.dp
private val MarqueeUpNextStep = 38.dp
private val MarqueeUpNextLead = 10.dp
private val MarqueeUpNextShape = RoundedCornerShape(15.dp)
private val MarqueeShorter = 48.dp
