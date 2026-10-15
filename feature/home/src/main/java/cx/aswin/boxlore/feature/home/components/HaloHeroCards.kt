package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

/**
 * The Halo hero card: the cover, cut into a nine-sided cookie, sits in the top end corner of a card
 * tinted from it ([rememberCoverShade]), inside a ring that follows the cookie's edge and carries
 * Resume progress along the part of the ring the card shows ([haloGeometry]). While its episode
 * plays, the cookie and ring turn like the compact player ([rememberHaloRotation]). The solid cards'
 * context block sits beside the cover, and Cover's title, show name and Play / details pair sit at
 * the bottom. At large text sizes the context runs across the top and the cover sits below it.
 */
@Composable
@Suppress("LongParameterList")
internal fun HaloHeroCard(
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
    val wide = LocalDensity.current.fontScale > HaloWideFontScale
    val rotation = rememberHaloRotation(item.podcast.latestEpisode?.id ?: item.podcast.id, running = pause)
    Card(
        onClick = onArrowClick,
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = shade.deep, contentColor = shade.content),
        modifier = modifier.fillMaxSize().testTag("home_hero_halo"),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contextHeight = if (wide) heroWideContextHeight(item, maxWidth - HaloInset * 2) else 0.dp
            val top = if (wide) (HaloInset + contextHeight + 12.dp).value else 0f
            val geometry = haloGeometry(maxWidth.value, maxHeight.value, top)
            HaloRing(geometry, progressState, rotation, track = shade.container, color = shade.accent, Modifier.fillMaxSize())
            OptimizedImage(
                url = imageUrl,
                fallbackUrls = heroArtworkSources(item),
                proxyWidth = 600,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.offset(geometry.coverStart.dp, geometry.coverTop.dp).size(geometry.cover.dp).haloCookieClip(rotation)
                    .testTag("home_hero_artwork"),
            )
            Column(Modifier.fillMaxSize().padding(HaloInset)) {
                if (wide) {
                    HeroCardContext(item, shade.content, wide = true, Modifier.fillMaxWidth().height(contextHeight))
                } else {
                    HeroCardContext(item, shade.content, wide = false, Modifier.width(geometry.contextWidth.dp))
                }
                Spacer(Modifier.weight(1f))
                CoverCopy(item, shade, progressState = null, showContext = false)
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
 * Halo's Jump back in and New episodes cards: a card in the first cover's shade with the heading and
 * count, and each episode's cookie cover in a ring that fills with Resume progress from the top. The
 * cover plays or pauses its episode (the button on it shows which); the rest of the tile opens the
 * episode. Four make a 2×2 grid; two or three become rows with larger covers and the title beside them.
 */
@Composable
@Suppress("LongParameterList")
internal fun HaloHeroGridCard(
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
    val shade = rememberCoverShade(displayItems.firstOrNull()?.imageUrl)
    Card(
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = shade.deep, contentColor = shade.content),
        modifier = modifier.fillMaxSize().testTag("home_hero_halo_collection"),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
            HeroCollectionHeading(title, displayItems.size, shade.softContent)
            Spacer(Modifier.height(8.dp))
            val tile: @Composable (Int, Boolean, Modifier) -> Unit = { index, stacked, tileModifier ->
                val podcast = displayItems[index]
                HaloGridTile(
                    podcast = podcast,
                    resume = mode == HeroGridMode.Resume,
                    stacked = stacked,
                    rows = displayItems.size,
                    card = shade,
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
                    modifier = tileModifier,
                )
            }
            if (displayItems.size >= 4) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(listOf(0, 1), listOf(2, 3)).forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { tile(it, true, Modifier.weight(1f).fillMaxHeight()) }
                        }
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    displayItems.indices.forEach { index ->
                        if (index > 0) HorizontalDivider(color = shade.container)
                        tile(index, false, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

/**
 * One collection episode: its ringed cover with the play button, then (below it in the grid, beside
 * it in rows) the title and Resume's time left or a new episode's length.
 */
@Composable
@Suppress("LongParameterList")
private fun HaloGridTile(
    podcast: Podcast,
    resume: Boolean,
    stacked: Boolean,
    rows: Int,
    card: ArtworkShade,
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
    val duration = HomeHeroStyle.minutes(podcast, resume)?.let { heroDurationText(it) }
    val meta = duration?.let { if (resume) stringResource(R.string.home_hero_remaining, it) else it }
    val title = podcast.latestEpisode?.title?.ifEmpty { podcast.title } ?: podcast.title
    val titleStyle = haloTileTitleStyle(stacked, rows)
    val textReserve = with(LocalDensity.current) { titleStyle.lineHeight.toDp() * 2 + 16.sp.toDp() } + 12.dp
    BoxWithConstraints(
        modifier.clip(HaloTileShape).testTag(tag).expressiveClickable(shape = HaloTileShape) { currentOnClick(currentPodcast) },
    ) {
        val ring = if (stacked) {
            minOf(maxWidth * 0.78f, maxHeight - textReserve, HaloStackedRingMax)
        } else {
            minOf(maxHeight - 12.dp, maxWidth * 0.42f, HaloRowRingMax)
        }.coerceAtLeast(HaloRingMin)
        val cover: @Composable () -> Unit = {
            HaloTileCover(podcast, ring, HomeHeroStyle.progress(podcast).takeIf { resume && it > 0f }, nowPlaying, card, shade, tag) {
                currentOnPlayback(currentPodcast)
            }
        }
        val copy: @Composable (TextAlign, Modifier) -> Unit = { align, copyModifier ->
            HaloTileCopy(title, meta, titleStyle, haloTileTitleLines(stacked, rows), align, card, tag, copyModifier)
        }
        if (stacked) {
            Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                cover()
                Spacer(Modifier.height(8.dp))
                copy(TextAlign.Center, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                cover()
                Spacer(Modifier.width(14.dp))
                copy(TextAlign.Start, Modifier.weight(1f))
            }
        }
    }
}

/** Titles are 13sp in the grid, 18sp when two episodes share the card and 16sp for three. */
@Composable
private fun haloTileTitleStyle(stacked: Boolean, rows: Int): TextStyle = when {
    stacked -> MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = GoogleSansWeight.bold)
    rows <= 2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = GoogleSansWeight.bold)
    else -> MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = GoogleSansWeight.bold)
}

private fun haloTileTitleLines(stacked: Boolean, rows: Int): Int = when {
    stacked -> 2
    rows <= 2 -> 4
    else -> 3
}

/** A tile's title and, when known, Resume's time left or a new episode's length. */
@Composable
@Suppress("LongParameterList")
private fun HaloTileCopy(
    title: String,
    meta: String?,
    titleStyle: TextStyle,
    titleLines: Int,
    align: TextAlign,
    card: ArtworkShade,
    tag: String,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = titleStyle,
            color = card.content,
            textAlign = align,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("${tag}_title"),
        )
        if (meta != null) {
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = card.softContent,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("${tag}_time"),
            )
        }
    }
}

/**
 * A collection cover cut into a cookie inside its ring, filled clockwise from the top with Resume
 * progress in the cover's accent. Tapping it plays or pauses; a small button in the end corner
 * shows which, ringed in the card's colour.
 */
@Composable
@Suppress("LongParameterList")
private fun HaloTileCover(
    podcast: Podcast,
    size: Dp,
    progress: Float?,
    playing: Boolean,
    card: ArtworkShade,
    shade: ArtworkShade,
    tag: String,
    onPlay: () -> Unit,
) {
    val label = stringResource(if (playing) R.string.home_hero_pause else R.string.home_hero_play)
    val hasEpisode = podcast.latestEpisode != null
    val interaction = remember { MutableInteractionSource() }
    // The press scales the whole cover without clipping it, so the button over the ring's corner stays
    // whole; only the ripple is kept inside the ring.
    val playModifier = if (hasEpisode) {
        Modifier.testTag("${tag}_play").semantics {
            contentDescription = label
            role = Role.Button
        }.expressiveClickable(interaction, indication = null, feedback = ExpressivePressFeedback.Control, onClick = onPlay)
    } else {
        Modifier
    }
    Box(Modifier.size(size).then(playModifier)) {
        if (hasEpisode) Box(Modifier.matchParentSize().clip(CircleShape).indication(interaction, ripple()))
        val progressModifier = if (progress != null) Modifier.testTag("${tag}_progress") else Modifier
        Canvas(Modifier.fillMaxSize().then(progressModifier)) {
            val stroke = HaloStroke.toPx()
            val radius = this.size.minDimension / 2f - stroke / 2f
            drawCircle(card.container, radius, style = Stroke(stroke))
            if (progress != null) {
                drawArc(
                    shade.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 320,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.Center).size(size * HaloTileCoverScale).clip(HaloCookie),
        )
        if (hasEpisode) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(HaloTileBadge).background(shade.accent, CircleShape)
                    .border(3.dp, card.deep, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = shade.onAccent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private val HaloInset = 20.dp
private val HaloStroke = 4.dp
private val HaloTileShape = RoundedCornerShape(18.dp)
private val HaloTileBadge = 30.dp
private val HaloStackedRingMax = 112.dp
private val HaloRowRingMax = 118.dp
private val HaloRingMin = 48.dp
private const val HaloTileCoverScale = 0.8f
private const val HaloWideFontScale = 1.3f
