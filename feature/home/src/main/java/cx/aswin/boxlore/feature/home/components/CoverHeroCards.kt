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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ArtworkShade
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic

private val CoverTileShape = RoundedCornerShape(18.dp)

/**
 * The Cover hero card: the cover fills the card and fades into a shade ([rememberCoverShade]) behind
 * the words, with the solid cards' type (context line, measured title, show name and progress) and a
 * Play / details pair in the shade's colours. Playback and details use the carousel's callbacks.
 */
@Composable
@Suppress("LongParameterList")
internal fun CoverHeroCard(
    item: SmartHeroItem,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
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
    val shade = rememberCoverShade(imageUrl)
    Card(
        onClick = onArrowClick,
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = shade.deep, contentColor = shade.content),
        modifier = modifier.fillMaxSize().testTag("home_hero_cover"),
    ) {
        Box(Modifier.fillMaxSize()) {
            OptimizedImage(
                url = imageUrl,
                fallbackUrls = heroArtworkSources(item),
                proxyWidth = 800,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().coverScrim(shade.deep)
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            ) {
                CoverCopy(item, shade, progressState)
                Spacer(Modifier.height(16.dp))
                HeroActions(
                    pause = pause,
                    resume = item.type == HeroType.RESUME || current,
                    colors = HeroActionColors(play = shade.accent, onPlay = shade.onAccent, details = shade.container, onDetails = shade.content),
                    item = item,
                    onPlayClick = onClick,
                    onDetailsClick = onArrowClick,
                )
            }
        }
    }
}

/**
 * The words over a full-artwork card: an optional one-line context (Cover's; Duotone shows the solid
 * cards' context block at the top instead), the measured title, the show name and progress.
 */
@Composable
internal fun CoverCopy(item: SmartHeroItem, shade: ArtworkShade, progressState: HeroProgressState?, showContext: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val baseStyle = heroTitleStyle()
    val title = heroTitle(item)
    val supportingName = heroSupportingName(item)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        // The card has room above the words, so the title takes the largest size that fits two lines.
        val titleStyle = remember(title, baseStyle, width) {
            measureHeroTitle(title, baseStyle, measurer, Constraints(maxWidth = width), Int.MAX_VALUE).layoutInput.style
        }
        Column(Modifier.fillMaxWidth()) {
            if (showContext) {
                Text(
                    coverContext(item, shade.content),
                    style = HeroContextTypography.label(),
                    color = shade.softContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("home_hero_context"),
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                title,
                style = titleStyle,
                color = shade.content,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("home_hero_title").semantics { heading() },
            )
            if (supportingName.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    supportingName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = shade.softContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("home_hero_supporting_name"),
                )
            }
            if (progressState != null) {
                Spacer(Modifier.height(14.dp))
                HeroProgress(progressState.value, shade.accent, shade.container, Modifier.testTag("home_hero_progress"), playing = progressState.playing)
            }
        }
    }
}

/** One line of context: the status in [strong] (Resume, New episode, #1, a genre) and its detail. */
@Composable
private fun coverContext(item: SmartHeroItem, strong: Color): AnnotatedString {
    val strongStyle = SpanStyle(color = strong, fontWeight = GoogleSansWeight.semiBold)
    if (item.type == HeroType.SPOTLIGHT) {
        return when (val context = remember(item.label, item.podcast.genre) { heroSpotlightContext(item.label, item.podcast.genre) }) {
            is HeroSpotlightContext.Rank -> {
                val number = stringResource(R.string.home_hero_rank_number, context.number)
                val market = stringResource(R.string.home_hero_rank_market, context.market)
                buildAnnotatedString {
                    withStyle(strongStyle) { append(number) }
                    append(" ")
                    append(market)
                }
            }
            is HeroSpotlightContext.Genre -> {
                val trending = stringResource(R.string.home_hero_trending_in)
                buildAnnotatedString {
                    append(trending)
                    append(" ")
                    withStyle(strongStyle) { append(context.name) }
                }
            }
            is HeroSpotlightContext.General -> buildAnnotatedString { withStyle(strongStyle) { append(context.label) } }
        }
    }
    val label = episodeContextLabel(item)
    val resume = item.type == HeroType.RESUME
    val duration = HomeHeroStyle.minutes(item.podcast, resume)?.let { heroDurationText(it) }
    val detail = duration?.let { if (resume) stringResource(R.string.home_hero_remaining, it) else it }
    return buildAnnotatedString {
        withStyle(strongStyle) { append(label) }
        if (detail != null) {
            append(" · ")
            append(detail)
        }
    }
}

/**
 * The Cover collection card: up to four covers as tiles on a solid card, each fading into its own
 * shade behind its title. A tile opens its episode; the Play button on its cover plays or pauses it.
 */
@Composable
@Suppress("LongParameterList")
internal fun CoverHeroGridCard(
    items: List<Podcast>,
    title: String,
    mode: HeroGridMode,
    onCellClick: (Podcast) -> Unit,
    onPlaybackClick: (Podcast) -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayItems = items.take(4)
    Card(
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("home_hero_collection_heading").semantics { heading() },
                )
                val countDescription = pluralStringResource(R.plurals.home_hero_episode_count, displayItems.size, displayItems.size)
                Text(
                    displayItems.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = countDescription },
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                coverGridRows(displayItems.size).forEachIndexed { rowIndex, row ->
                    Row(
                        Modifier.weight(coverGridRowWeight(displayItems.size, rowIndex)).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { index ->
                            val podcast = displayItems[index]
                            CoverGridTile(
                                podcast = podcast,
                                mode = mode,
                                wide = coverGridTileIsWide(displayItems.size, index),
                                nowPlaying = HomePlaybackStateLogic.isHeroItemPlaying(
                                    itemEpisodeId = podcast.latestEpisode?.id,
                                    itemPodcastId = podcast.id,
                                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                                    currentPlayingPodcastId = currentPlayingPodcastId,
                                    isPlaying = isPlaying,
                                ),
                                onClick = onCellClick,
                                onPlaybackClick = onPlaybackClick,
                                tag = "home_hero_collection_$index",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rows of tile positions: two stack full width, three put the first across the top, four make a 2×2 grid. */
internal fun coverGridRows(count: Int): List<List<Int>> = when {
    count <= 0 -> emptyList()
    count == 1 -> listOf(listOf(0))
    count == 2 -> listOf(listOf(0), listOf(1))
    count == 3 -> listOf(listOf(0), listOf(1, 2))
    else -> listOf(listOf(0, 1), listOf(2, 3))
}

/** The first of three tiles runs across the top, so its row is a little taller. */
internal fun coverGridRowWeight(count: Int, row: Int): Float = if (count == 3 && row == 0) 1.2f else 1f

/** A tile alone in its row has room for a bigger title. */
internal fun coverGridTileIsWide(count: Int, index: Int): Boolean = count <= 2 || (count == 3 && index == 0)

@Composable
@Suppress("LongParameterList")
private fun CoverGridTile(
    podcast: Podcast,
    mode: HeroGridMode,
    wide: Boolean,
    nowPlaying: Boolean,
    onClick: (Podcast) -> Unit,
    onPlaybackClick: (Podcast) -> Unit,
    tag: String,
    modifier: Modifier,
) {
    val currentPodcast by rememberUpdatedState(podcast)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPlayback by rememberUpdatedState(onPlaybackClick)
    val shade = rememberCoverShade(podcast.imageUrl)
    val resume = mode == HeroGridMode.Resume
    val duration = HomeHeroStyle.minutes(podcast, resume)?.let { heroDurationText(it) }
    Box(
        modifier.clip(CoverTileShape).background(shade.deep).testTag(tag)
            .expressiveClickable(shape = CoverTileShape) { currentOnClick(currentPodcast) },
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 400,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // A new episode's length sits on its cover; Resume tiles keep time left beside their progress.
        if (!resume && duration != null) {
            CoverTileLength(duration, shade, Modifier.align(Alignment.TopStart).padding(10.dp).testTag("${tag}_time"))
        }
        if (podcast.latestEpisode != null) {
            CoverTilePlayback(nowPlaying, shade, Modifier.align(Alignment.TopEnd).testTag("${tag}_play")) { currentOnPlayback(currentPodcast) }
        }
        CoverTileCopy(
            title = podcast.latestEpisode?.title?.ifEmpty { podcast.title } ?: podcast.title,
            wide = wide,
            shade = shade,
            progress = HomeHeroStyle.progress(podcast).takeIf { resume && it > 0f },
            timeLeft = if (resume) duration?.let { stringResource(R.string.home_hero_remaining, it) } else null,
            nowPlaying = nowPlaying,
            tag = tag,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** The tile's title over its shade, then Resume progress and time left on one row. */
@Composable
@Suppress("LongParameterList")
private fun CoverTileCopy(
    title: String,
    wide: Boolean,
    shade: ArtworkShade,
    progress: Float?,
    timeLeft: String?,
    nowPlaying: Boolean,
    tag: String,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxWidth().coverScrim(shade.deep, fade = 64.dp, solid = 8.dp)
            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = if (wide) 17.sp else 13.sp,
                lineHeight = if (wide) 21.sp else 17.sp,
                fontWeight = GoogleSansWeight.bold,
            ),
            color = shade.content,
            maxLines = if (wide) 2 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("${tag}_title"),
        )
        if (progress != null || timeLeft != null) {
            // Progress and time left share one row, as on the solid cards.
            Row(
                Modifier.fillMaxWidth().testTag("${tag}_metadata"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (progress != null) {
                    HeroProgress(progress, shade.accent, shade.container, Modifier.weight(1f).testTag("${tag}_progress"), playing = nowPlaying)
                }
                if (timeLeft != null) {
                    Text(
                        timeLeft,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = shade.softContent,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp),
                        modifier = Modifier.testTag("${tag}_time"),
                    )
                }
            }
        }
    }
}

/** A new episode's length on a solid chip in the tile's shade. */
@Composable
private fun CoverTileLength(length: String, shade: ArtworkShade, modifier: Modifier) {
    Text(
        length,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        fontWeight = GoogleSansWeight.semiBold,
        color = shade.content,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(shade.deep).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Play or Pause on the tile's cover, a 32dp button in the tile's shade inside a 48dp target. */
@Composable
private fun CoverTilePlayback(playing: Boolean, shade: ArtworkShade, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(if (playing) R.string.home_hero_pause else R.string.home_hero_play)
    Box(
        modifier.size(48.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .expressiveClickable(shape = CircleShape, feedback = ExpressivePressFeedback.Control, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).background(shade.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = shade.onAccent, modifier = Modifier.size(18.dp))
        }
    }
}
