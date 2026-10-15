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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
        MarqueeFrost(podcast.imageUrl, podcast.artworkUrls(), look.filter, look.topContent, look.veilAlpha, marqueeHeroHeight())
        if (active) MarqueeReportTone(look.topContent)
        MarqueeCollectionPosition(title, index, display.size, shade, Modifier.padding(start = 20.dp, top = LocalHeroTopInset.current + 12.dp))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().coverScrim(shade.deep)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = MarqueeCopyBottom),
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
 * The collection's covers in a scrolling row, in their own colours and in front of the card's shade: the
 * chosen one is outlined and kept in view, and Resume covers show their progress along the foot.
 * Tapping a cover chooses it.
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

private const val MarqueeAdvanceMillis = 3_500L
private val MarqueeBlockGap = 10.dp
private val MarqueeBlockShape = RoundedCornerShape(16.dp)
