package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic

enum class HeroGridMode { Resume, NewEpisodes }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Existing collection API plus an independent artwork playback action.
fun HeroGridCard(
    items: List<Podcast>,
    title: String,
    mode: HeroGridMode,
    onCellClick: (Podcast) -> Unit,
    currentPlayingPodcastId: String? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    onPlaybackClick: (Podcast) -> Unit = onCellClick,
    carouselItemDrawInfo: CarouselItemDrawInfo? = null,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    artworkColorFilter: (@Composable (Podcast) -> ColorFilter?)? = null,
) {
    val displayItems = items.take(4)
    // Block cards use the theme's hero roles; Duotone passes its own tones and a filter for each cover.
    val foreground = contentColor.takeOrElse { heroContentColor() }
    val container = containerColor.takeOrElse { heroContainerColor() }
    Card(
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = foreground),
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Hero).fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Material's minSize includes an off-screen anchor; visible peeks start at 40dp.
            val minimumPeekWidth = with(LocalDensity.current) { 40.dp.toPx() }
            val contentVisible by remember(carouselItemDrawInfo, minimumPeekWidth) {
                derivedStateOf { carouselItemDrawInfo == null || HeroPeekMotion.contentAlpha(carouselItemDrawInfo.expansion(minimumPeekWidth)) > 0f }
            }
            Column(
                Modifier.fillMaxSize().padding(18.dp)
                    .graphicsLayer { alpha = HeroPeekMotion.contentAlpha(carouselItemDrawInfo.expansion(minimumPeekWidth)) }
                    .then(if (contentVisible) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroCollectionHeading(title, foreground, Modifier.weight(1f))
                    val countDescription = pluralStringResource(R.plurals.home_hero_episode_count, displayItems.size, displayItems.size)
                    Text(
                        displayItems.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = foreground.copy(alpha = 0.8f),
                        modifier = Modifier.testTag("home_hero_collection_count").semantics { contentDescription = countDescription },
                    )
                }
                Spacer(Modifier.height(12.dp))
                HeroCollectionRows(displayItems, foreground, Modifier.weight(1f)) { index, podcast, cellModifier, twoColumns ->
                    val nowPlaying = HomePlaybackStateLogic.isHeroItemPlaying(
                        itemEpisodeId = podcast.latestEpisode?.id,
                        itemPodcastId = podcast.id,
                        currentPlayingEpisodeId = currentPlayingEpisodeId,
                        currentPlayingPodcastId = currentPlayingPodcastId,
                        isPlaying = isPlaying,
                    )
                    HeroCollectionRow(
                        podcast = podcast,
                        mode = mode,
                        onClick = onCellClick,
                        onPlaybackClick = onPlaybackClick,
                        nowPlaying = nowPlaying,
                        count = displayItems.size,
                        tag = "home_hero_collection_$index",
                        modifier = cellModifier,
                        twoColumns = twoColumns,
                        enabled = contentVisible,
                        foreground = foreground,
                        artworkColorFilter = artworkColorFilter?.invoke(podcast),
                    )
                }
            }
            if (carouselItemDrawInfo != null) {
                HeroCollectionPeek(title, foreground, carouselItemDrawInfo, minimumPeekWidth, contentVisible, Modifier.align(Alignment.Center).padding(horizontal = 18.dp))
                HeroCollectionPeekCount(displayItems.size, foreground, carouselItemDrawInfo, minimumPeekWidth, contentVisible, Modifier.align(Alignment.TopCenter).padding(top = 28.dp))
            }
        }
    }
}

/** The regular header stays in place while the independent vertical preview fades away. */
@Composable
private fun HeroCollectionHeading(title: String, foreground: Color, modifier: Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
        color = foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize, stepSize = 1.sp),
        modifier = modifier.testTag("home_hero_collection_heading").semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroCollectionPeek(title: String, foreground: Color, info: CarouselItemDrawInfo?, minimumPeekWidth: Float, contentVisible: Boolean, modifier: Modifier) {
    val peekInset = with(LocalDensity.current) { 12.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Text(
        title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.semiBold, letterSpacing = 0.3.sp),
        color = foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize, stepSize = 1.sp),
        modifier = modifier.testTag("home_hero_collection_peek")
            .then(if (contentVisible) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() })
            .graphicsLayer {
                alpha = HeroPeekMotion.peekAlpha(info.expansion(minimumPeekWidth))
                rotationZ = if (rtl) 90f else -90f
                scaleX = minOf(1f, (minimumPeekWidth - peekInset).coerceAtLeast(1f) / size.height.coerceAtLeast(1f))
                scaleY = scaleX
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroCollectionPeekCount(count: Int, foreground: Color, info: CarouselItemDrawInfo, minimumPeekWidth: Float, contentVisible: Boolean, modifier: Modifier) {
    val description = pluralStringResource(R.plurals.home_hero_episode_count, count, count)
    Text(
        count.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = foreground.copy(alpha = 0.75f),
        modifier = modifier.testTag("home_hero_collection_peek_count")
            .then(if (contentVisible) Modifier.clearAndSetSemantics {} else Modifier.semantics { contentDescription = description })
            .graphicsLayer { alpha = HeroPeekMotion.peekAlpha(info.expansion(minimumPeekWidth)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private fun CarouselItemDrawInfo?.expansion(minimumPeekWidth: Float): Float =
    if (this == null) 1f else HeroPeekMotion.expansion(size, maxOf(minSize, minimumPeekWidth), maxSize)

@Composable
@Suppress("LongParameterList") // Row state and independent navigation/playback targets stay local to Home.
private fun HeroCollectionRow(
    podcast: Podcast,
    mode: HeroGridMode,
    onClick: (Podcast) -> Unit,
    onPlaybackClick: (Podcast) -> Unit,
    nowPlaying: Boolean,
    count: Int,
    tag: String,
    modifier: Modifier,
    twoColumns: Boolean,
    enabled: Boolean,
    foreground: Color,
    artworkColorFilter: ColorFilter?,
) {
    val currentPodcast by rememberUpdatedState(podcast)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentPlaybackClick by rememberUpdatedState(onPlaybackClick)
    BoxWithConstraints(modifier) {
        // A short hero (sideways phones, split-screen halves) leaves rows too low for the artwork and two
        // lines of words: the row keeps a square cover and its title, and drops the progress line.
        // Two to a line, a row keeps a Play-sized cover and one title line beside it.
        val compact = twoColumns || maxHeight < CollectionRowCompactHeight
        val padding = if (compact) 4.dp else 10.dp
        val artworkSize = if (twoColumns) CollectionPlayTarget else collectionArtworkSize(count, maxWidth, maxHeight, compact, padding)
        Row(
            Modifier.fillMaxSize().testTag(tag)
                .expressiveClickable(enabled = enabled, shape = HomeHeroStyle.CollectionArtworkShape) { currentOnClick(currentPodcast) }
                // Compact rows let the cover's Play target take the row's full height (at least 48dp).
                .padding(vertical = if (compact) 0.dp else padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val playbackLabel = stringResource(if (nowPlaying) R.string.home_hero_pause else R.string.home_hero_play)
            val artworkAction = if (podcast.latestEpisode != null) {
                Modifier.semantics {
                    contentDescription = playbackLabel
                    role = Role.Button
                }.expressiveClickable(enabled = enabled, shape = HomeHeroStyle.CollectionArtworkShape) { currentPlaybackClick(currentPodcast) }
            } else {
                Modifier
            }
            // On compact rows the cover keeps its drawn size; the target around it is at least 48dp each way.
            val target = if (compact) {
                Modifier.width(maxOf(artworkSize, CollectionPlayTarget)).fillMaxHeight()
            } else {
                Modifier.size(artworkSize)
            }
            Box(target.testTag("${tag}_artwork").then(artworkAction), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(artworkSize)) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    fallbackUrls = podcast.artworkUrls(),
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = artworkColorFilter,
                    modifier = Modifier.fillMaxSize().clip(HomeHeroStyle.CollectionArtworkShape),
                )
                if (podcast.latestEpisode != null) {
                    HeroCollectionPlayback(
                        nowPlaying,
                        Modifier.align(Alignment.BottomEnd).testTag("${tag}_play"),
                    )
                }
            }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                HeroCollectionTitle(podcast, count, foreground, "${tag}_title", maxLines = if (compact) 1 else null)
                if (!compact) HeroCollectionMetadata(podcast, mode, foreground, nowPlaying, tag)
            }
        }
    }
}

@Composable
private fun HeroCollectionPlayback(playing: Boolean, modifier: Modifier) {
    Box(
        modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.62f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HeroCollectionTitle(podcast: Podcast, count: Int, foreground: Color, tag: String, maxLines: Int? = null) {
    Text(
        podcast.latestEpisode?.title?.ifEmpty { podcast.title } ?: podcast.title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = if (count <= 2) 18.sp else 15.sp,
            lineHeight = if (count <= 2) 22.sp else 18.sp,
            fontWeight = GoogleSansWeight.bold,
        ),
        color = foreground,
        maxLines = maxLines ?: if (count <= 2) 3 else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun HeroCollectionMetadata(podcast: Podcast, mode: HeroGridMode, foreground: Color, playing: Boolean, tag: String) {
    val resume = mode == HeroGridMode.Resume
    val progress = HomeHeroStyle.progress(podcast)
    val minutes = HomeHeroStyle.minutes(podcast, resume)
    val showProgress = resume && progress > 0f
    if (!showProgress && minutes == null) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val metadataWidth = maxWidth
        val timeWidth = (metadataWidth - 32.dp).coerceAtLeast(0.dp)
        Row(
            Modifier.fillMaxWidth().testTag("${tag}_metadata"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                HeroProgress(
                    progress,
                    foreground,
                    foreground.copy(alpha = 0.2f),
                    modifier = Modifier.weight(1f).testTag("${tag}_progress"),
                    playing = playing,
                )
            }
            if (minutes != null) {
                val duration = heroDurationText(minutes)
                Text(
                    if (resume) stringResource(R.string.home_hero_remaining, duration) else duration,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = foreground.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 12.sp),
                    modifier = Modifier.widthIn(max = if (showProgress) timeWidth else metadataWidth).testTag("${tag}_time"),
                )
            }
        }
    }
}

/** Collection rows lower than this (a short hero) show a square cover and one title line. */
private val CollectionRowCompactHeight = 64.dp

/** The smallest Play target a collection row's cover may have, and so the lowest row a short hero shows. */
internal val CollectionPlayTarget = 48.dp

/** Rows between collection rows. */
private val CollectionDivider = 1.dp

/**
 * How many columns [count] collection rows take in [height]. Portrait phones and tablets stack every
 * row; a short hero (sideways phones, split-screen halves) that can't keep each row's Play cover a
 * full [CollectionPlayTarget] tall lays the rows out two to a line, so every episode stays on the card.
 */
internal fun collectionColumns(height: Dp, count: Int): Int {
    if (count <= 1) return 1
    val fit = ((height + CollectionDivider) / (CollectionPlayTarget + CollectionDivider)).toInt()
    return if (fit >= count) 1 else 2
}

/** The collection's rows, stacked or two to a line ([collectionColumns]), each given [cell]'s modifier. */
@Composable
private fun HeroCollectionRows(
    items: List<Podcast>,
    foreground: Color,
    modifier: Modifier,
    cell: @Composable (index: Int, podcast: Podcast, modifier: Modifier, twoColumns: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val columns = collectionColumns(maxHeight, items.size)
        Column(Modifier.fillMaxSize()) {
            items.chunked(columns).forEachIndexed { line, lineItems ->
                if (line > 0) HorizontalDivider(color = foreground.copy(alpha = 0.15f))
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    lineItems.forEachIndexed { column, podcast ->
                        cell(line * columns + column, podcast, Modifier.weight(1f).fillMaxHeight(), columns > 1)
                    }
                    repeat(columns - lineItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A collection row's cover: larger for fewer, wider rows; on compact rows no taller than the row allows. */
private fun collectionArtworkSize(count: Int, width: Dp, height: Dp, compact: Boolean, padding: Dp): Dp {
    val preferred = when {
        count <= 2 && width >= 254.dp -> 98.dp
        count <= 2 -> 78.dp
        count == 3 && width >= 254.dp -> 78.dp
        else -> 68.dp
    }
    return if (compact) minOf(preferred, (height - padding * 2).coerceAtLeast(0.dp)) else preferred
}
