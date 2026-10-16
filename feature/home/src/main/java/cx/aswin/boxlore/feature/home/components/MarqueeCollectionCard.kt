package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ArtworkShade
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Marquee's Jump back in and New episodes cards: the chosen episode's cover fills the card, with one
 * chip for the collection's name, the episode's time and its place in the collection above the
 * episode's title, show, Play and details at the bottom.
 * Below them a strip of covers picks the episode: while the card is on screen it advances on its own
 * every 5 seconds. Dragging the strip holds it, and after a tap or a drag it waits 8 seconds before
 * moving on again, so the listener's choice stays put while they look at it.
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
    // Each tap or drag restarts the wait; a drag in progress holds the marquee.
    var touches by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    val index = selected.coerceIn(0, display.lastIndex)
    val podcast = display[index]
    val resume = mode == HeroGridMode.Resume
    val allowed = LocalMarqueeAutoAdvance.current && rememberMotionAllowed()
    MarqueeAutoAdvance(running = allowed && active && !holding && display.size > 1, touches = touches) { selected = (selected + 1) % display.size }
    val look = rememberMarqueeLook(podcast.imageUrl)
    val shade = look.shade
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
    // Clipped: the backdrop reaches past the card, and a neighbouring card must never draw over this one.
    Box(modifier.fillMaxSize().clipToBounds().background(shade.deep).testTag("home_hero_marquee_collection")) {
        Crossfade(targetState = podcast, label = "marqueeCollectionCover") { shown ->
            Box(Modifier.fillMaxSize()) { MarqueeBackdrop(shown.imageUrl, shown.artworkUrls(), rememberMarqueeLook(shown.imageUrl), coverTag = null) }
        }
        if (active) MarqueeReportTone(look.topContent)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().coverScrim(shade.deep.copy(alpha = MarqueeWordsScrim), fade = MarqueeWordsFade)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = MarqueeCopyBottom),
        ) {
            // The collection's name and position sit in the words' own column, so nothing can overlap them.
            val minutes = HomeHeroStyle.minutes(podcast, resume)?.let { heroDurationText(it) }
            val time = minutes?.let { if (resume) stringResource(R.string.home_hero_remaining, it) else it }
            MarqueeChip(marqueeCollectionLabel(title, index, display.size, time), shade, tag = "home_hero_collection_position")
            Spacer(Modifier.height(10.dp))
            // The chip already says Resume or new and how long; the words start at the title.
            CoverCopy(item, shade, progressState = null, showContext = false)
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
                touches++
            }, onDragging = { dragging ->
                holding = dragging
                if (dragging) touches++
            })
        }
    }
}

/**
 * Calls [advance] every 5 seconds while [running] and Home is on screen. Once the listener has
 * touched the strip ([touches] above 0, and again each time it changes) it first waits 8 seconds.
 */
@Composable
private fun MarqueeAutoAdvance(running: Boolean, touches: Int, advance: () -> Unit) {
    val currentAdvance by rememberUpdatedState(advance)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, touches, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var wait = if (touches > 0) MarqueeResumeMillis else MarqueeStripStepMillis
            while (isActive) {
                delay(wait)
                currentAdvance()
                wait = MarqueeStripStepMillis
            }
        }
    }
}

/**
 * The collection's covers in a scrolling row, in their own colours and in front of the card's shade: the
 * chosen one is outlined and kept in view, and Resume covers show their progress along the foot.
 * Tapping a cover chooses it.
 */
@Composable
@Suppress("LongParameterList")
private fun MarqueeStrip(items: List<Podcast>, selected: Int, resume: Boolean, card: ArtworkShade, onSelect: (Int) -> Unit, onDragging: (Boolean) -> Unit) {
    val scroll = rememberScrollState()
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val currentOnDragging by rememberUpdatedState(onDragging)
    LaunchedEffect(dragged) { currentOnDragging(dragged) }
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
    count <= 2 -> 104.dp
    count == 3 -> 96.dp
    else -> 88.dp
}

@Composable
@Suppress("LongParameterList")
private fun MarqueeBlock(podcast: Podcast, size: Dp, isSelected: Boolean, resume: Boolean, card: ArtworkShade, tag: String, onClick: () -> Unit) {
    val title = podcast.latestEpisode?.title?.ifEmpty { podcast.title } ?: podcast.title
    val progress = HomeHeroStyle.progress(podcast).takeIf { resume && it > 0f }
    Box(
        Modifier.size(size).clip(MarqueeBlockShape).background(card.container).testTag(tag)
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
            modifier = Modifier.fillMaxSize(),
        )
        if (progress != null) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(8.dp).fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(card.container).testTag("${tag}_progress"),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(card.accent))
            }
        }
        if (isSelected) Box(Modifier.matchParentSize().border(3.dp, card.content, MarqueeBlockShape))
    }
}

private const val MarqueeResumeMillis = 8_000L
private val MarqueeBlockGap = 10.dp
private val MarqueeBlockShape = RoundedCornerShape(16.dp)
