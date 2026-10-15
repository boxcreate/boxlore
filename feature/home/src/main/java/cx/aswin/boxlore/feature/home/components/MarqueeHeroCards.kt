package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ArtworkShade
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableHeroList
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How much room Marquee's hero leaves at its top for Home's floating top bar; 0 anywhere else. */
val LocalHeroTopInset = compositionLocalOf { 0.dp }

/** Your shows' sheet starts this far up over Marquee's hero, with rounded top corners. */
internal val MarqueeSheetOverlap = 24.dp
internal val MarqueeSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Play (172dp) and details beside it: Play keeps one width on every phone, and the up-next covers take the rest. */
internal val MarqueeActionsWidth = 223.dp

/** The hero's height: the floating top bar's room, the cards' usual height and the sheet's overlap. */
@Composable
internal fun marqueeHeroHeight() = LocalHeroTopInset.current + homeHeroHeight() + MarqueeSheetOverlap

/** A Marquee cover's colours: an optional two-tone filter and the shade its words sit on. */
internal data class MarqueeLook(val filter: ColorFilter?, val shade: ArtworkShade)

/**
 * Marquee's colours for the cover at [imageUrl]: Dark (shown as Full color) keeps the cover as it is
 * under a plain shade; Artwork and App theme redraw it in two tones, as Duotone does, under the
 * matching shade.
 */
@Composable
internal fun rememberMarqueeLook(imageUrl: String?): MarqueeLook {
    val fullColor = HeroCoverShade.sanitize(LocalHeroCoverShade.current) == HeroCoverShade.DARK
    val look = rememberDuotoneLook(imageUrl)
    val filter = remember(fullColor, look.duotone) { if (fullColor) null else look.duotone.colorFilter() }
    return MarqueeLook(filter, look.shade)
}

/** A card further along the hero, shown as a small cover beside Play. */
internal data class MarqueeNext(val index: Int, val imageUrl: String?, val label: String)

/**
 * The Marquee hero: one card at a time across the whole width, from the top of the screen (under the
 * floating top bar) down to where Your shows' rounded sheet begins. Swiping moves between cards, as
 * do the up-next covers beside Play; collections pick their episode from a strip of covers.
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
    Box(modifier.fillMaxWidth().height(marqueeHeroHeight())) {
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
                )
            }
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
 * Marquee's single-episode card: the cover across the whole card in [rememberMarqueeLook]'s colours,
 * a shade at the top for the top bar and behind the words at the bottom, Cover's words, and Play with a
 * fixed width, details beside it, and covers of the next cards in the room left over.
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
    Box(modifier.fillMaxSize().background(shade.deep).testTag("home_hero_marquee").expressiveClickable(onClick = onArrowClick)) {
        OptimizedImage(
            url = imageUrl,
            fallbackUrls = heroArtworkSources(item),
            proxyWidth = 1080,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = look.filter,
            modifier = Modifier.fillMaxSize().testTag("home_hero_artwork"),
        )
        MarqueeTopShade(shade.deep)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().coverScrim(shade.deep)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp + MarqueeSheetOverlap),
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

/** A deep shade behind the floating top bar, fading out below it. */
@Composable
private fun MarqueeTopShade(deep: Color) {
    val topInset = LocalHeroTopInset.current
    if (topInset <= 0.dp) return
    Box(
        Modifier.fillMaxWidth().height(topInset + 48.dp)
            .background(Brush.verticalGradient(listOf(deep.copy(alpha = 0.62f), deep.copy(alpha = 0f)))),
    )
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

/**
 * Marquee's Jump back in and New episodes cards: the chosen episode's cover fills the card, with the
 * collection's name and position at the top and the episode's words, Play and details at the bottom.
 * Below them a strip of covers picks the episode: while the card is on screen it advances on its own
 * every 3.5 seconds, until the listener taps a cover or scrolls the strip.
 */
@Composable
@Suppress("LongParameterList")
internal fun MarqueeCollectionCard(
    items: List<Podcast>,
    title: String,
    mode: HeroGridMode,
    active: Boolean,
    onCellClick: (Podcast) -> Unit,
    onPlaybackClick: (Podcast) -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val display = items.take(4)
    if (display.isEmpty()) return
    var selected by rememberSaveable(display.size) { mutableIntStateOf(0) }
    var chosen by rememberSaveable { mutableStateOf(false) }
    val index = selected.coerceIn(0, display.lastIndex)
    val podcast = display[index]
    val resume = mode == HeroGridMode.Resume
    MarqueeAutoAdvance(running = active && !chosen && display.size > 1) { selected = (selected + 1) % display.size }
    val shade = rememberMarqueeLook(podcast.imageUrl).shade
    val item = SmartHeroItem(
        if (resume) HeroType.RESUME else HeroType.JUMP_BACK_IN,
        podcast,
        if (resume) "Resume" else "NEW EPISODE",
        description = podcast.latestEpisode?.title,
    )
    val nowPlaying = HomePlaybackStateLogic.isHeroItemPlaying(
        itemEpisodeId = podcast.latestEpisode?.id,
        itemPodcastId = podcast.id,
        currentPlayingEpisodeId = currentPlayingEpisodeId,
        currentPlayingPodcastId = currentPlayingPodcastId,
        isPlaying = isPlaying,
    )
    Box(modifier.fillMaxSize().background(shade.deep).testTag("home_hero_marquee_collection")) {
        Crossfade(targetState = podcast, label = "marqueeCollectionCover") { shown ->
            OptimizedImage(
                url = shown.imageUrl,
                fallbackUrls = shown.artworkUrls(),
                proxyWidth = 1080,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberMarqueeLook(shown.imageUrl).filter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MarqueeTopShade(shade.deep)
        MarqueeCollectionPosition(title, index, display.size, shade, Modifier.padding(start = 20.dp, top = LocalHeroTopInset.current + 12.dp))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().coverScrim(shade.deep)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp + MarqueeSheetOverlap),
        ) {
            CoverCopy(item, shade, progressState = null)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(MarqueeActionsWidth)) {
                HeroActions(
                    pause = nowPlaying,
                    resume = resume,
                    colors = HeroActionColors(play = shade.accent, onPlay = shade.onAccent, details = shade.container, onDetails = shade.content),
                    item = item,
                    onPlayClick = { onPlaybackClick(podcast) },
                    onDetailsClick = { onCellClick(podcast) },
                )
            }
            Spacer(Modifier.height(16.dp))
            MarqueeStrip(display, index, resume, shade, onSelect = { picked ->
                selected = picked
                chosen = true
            }, onScrolled = { chosen = true })
        }
    }
}

/** "Jump back in · 2 of 4", in the collection's words at the top of the card. */
@Composable
private fun MarqueeCollectionPosition(title: String, index: Int, count: Int, shade: ArtworkShade, modifier: Modifier) {
    val position = stringResource(R.string.home_hero_collection_position, index + 1, count)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = shade.content, fontWeight = GoogleSansWeight.semiBold)) { append(title) }
            append(" · ")
            append(position)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = shade.softContent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag("home_hero_collection_position"),
    )
}

/** Calls [advance] every 3.5 seconds while [running] and Home is on screen. */
@Composable
private fun MarqueeAutoAdvance(running: Boolean, advance: () -> Unit) {
    val currentAdvance by rememberUpdatedState(advance)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(MarqueeAdvanceMillis)
                currentAdvance()
            }
        }
    }
}

/**
 * The collection's covers in a scrolling row: the chosen one is outlined and kept in view, the others
 * dimmed, and Resume covers show their progress along the foot. Tapping a cover chooses it.
 */
@Composable
@Suppress("LongParameterList")
private fun MarqueeStrip(items: List<Podcast>, selected: Int, resume: Boolean, card: ArtworkShade, onSelect: (Int) -> Unit, onScrolled: () -> Unit) {
    val scroll = rememberScrollState()
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val currentOnScrolled by rememberUpdatedState(onScrolled)
    LaunchedEffect(dragged) { if (dragged) currentOnScrolled() }
    val size = marqueeBlockSize(items.size)
    val step = with(LocalDensity.current) { (size + MarqueeBlockGap).toPx() }
    LaunchedEffect(selected, step) {
        val target = (selected * step + step / 2f - scroll.viewportSize / 2f).roundToInt().coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target)
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(MarqueeBlockGap)) {
        items.forEachIndexed { index, podcast ->
            MarqueeBlock(podcast, size, index == selected, resume, card, "home_hero_collection_$index") { onSelect(index) }
        }
    }
}

/** Blocks grow when there are fewer of them. */
internal fun marqueeBlockSize(count: Int): Dp = when {
    count <= 2 -> 120.dp
    count == 3 -> 108.dp
    else -> 104.dp
}

@Composable
@Suppress("LongParameterList")
private fun MarqueeBlock(podcast: Podcast, size: Dp, isSelected: Boolean, resume: Boolean, card: ArtworkShade, tag: String, onClick: () -> Unit) {
    val look = rememberMarqueeLook(podcast.imageUrl)
    val title = podcast.latestEpisode?.title?.ifEmpty { podcast.title } ?: podcast.title
    val progress = HomeHeroStyle.progress(podcast).takeIf { resume && it > 0f }
    val dim by animateColorAsState(if (isSelected) Color.Transparent else card.deep.copy(alpha = 0.5f), label = "marqueeBlockDim")
    Box(
        Modifier.size(size).clip(MarqueeBlockShape).background(look.shade.deep).testTag(tag)
            .semantics {
                contentDescription = title
                selected = isSelected
                role = Role.Tab
            }
            .expressiveClickable(shape = MarqueeBlockShape, onClick = onClick),
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 320,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = look.filter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.matchParentSize().background(dim))
        if (progress != null) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(8.dp).fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(card.container).testTag("${tag}_progress"),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(look.shade.accent))
            }
        }
        if (isSelected) Box(Modifier.matchParentSize().border(3.dp, card.content, MarqueeBlockShape))
    }
}

private const val MarqueeUpNextCount = 3
private const val MarqueeAdvanceMillis = 3_500L
private val MarqueeUpNextSize = 48.dp
private val MarqueeUpNextStep = 38.dp
private val MarqueeUpNextLead = 10.dp
private val MarqueeUpNextShape = RoundedCornerShape(15.dp)
private val MarqueeBlockGap = 10.dp
private val MarqueeBlockShape = RoundedCornerShape(16.dp)
