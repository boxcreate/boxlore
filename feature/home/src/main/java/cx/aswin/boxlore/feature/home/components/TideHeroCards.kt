package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Tide hero card: the cover sits uncropped across the top, and a solid panel in its deep shade
 * ([rememberCoverShade]) rises over the cover's lower part on a wavy edge. The panel rises only as far
 * as the words need ([tidePanelTop]), so short titles show more of the cover. A Play / Pause disc sits
 * in a notch on the edge, ringed with Resume progress; the words below are Cover's (context line,
 * title, show name), and tapping the card opens the episode.
 */
@Composable
@Suppress("LongParameterList")
internal fun TideHeroCard(
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
    val hasEpisode = item.podcast.latestEpisode != null
    val label = heroActionLabel(item, pause, resume = item.type == HeroType.RESUME || current)
    Card(
        onClick = onArrowClick,
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = shade.deep, contentColor = shade.content),
        modifier = modifier.fillMaxSize().testTag("home_hero_tide"),
    ) {
        TideCardLayout(
            cover = {
                OptimizedImage(
                    url = imageUrl,
                    fallbackUrls = heroArtworkSources(item),
                    proxyWidth = 800,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag("home_hero_artwork"),
                )
            },
            panel = { TidePanel(shade.deep) },
            copy = { CoverCopy(item, shade, progressState = null, contextEndInset = TideDiscReserve) },
            disc = {
                TidePlayDisc(
                    icon = if (!hasEpisode) TideDiscIcon.Details else if (pause) TideDiscIcon.Pause else TideDiscIcon.Play,
                    label = label,
                    progress = progressState,
                    shade = shade,
                    ground = shade.deep,
                    onClick = if (hasEpisode) onClick else onArrowClick,
                )
            },
        )
    }
}

/**
 * Where the panel's edge sits on a [width] × [height] card (dp) whose words are [copy] tall: far enough
 * up to leave the words [TideCopyGap] below the edge, but always over the cover's lower part, between
 * 65% of the width and 16dp above the cover's bottom.
 */
internal fun tidePanelTop(width: Float, height: Float, copy: Float): Float {
    val highest = width * TidePanelHighest
    val lowest = maxOf(highest, width - TidePanelLowestGap)
    return (height - TideBottomInset - copy - TideCopyGap).coerceIn(highest, lowest)
}

/**
 * The single card's layers: the square [cover] at the top, the wavy [panel] from [tidePanelTop] down,
 * the [copy] at the bottom and the Play [disc] on the edge, 12dp from the end. Shared with the loading
 * card, so both put the edge and the disc in the same place for the same words.
 */
@Composable
internal fun TideCardLayout(
    cover: @Composable () -> Unit,
    panel: @Composable () -> Unit,
    copy: @Composable () -> Unit,
    disc: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(content = { cover(); panel(); copy(); disc() }, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val inset = TideInset.roundToPx()
        val words = measurables[2].measure(Constraints(maxWidth = (width - inset * 2).coerceAtLeast(0)))
        val panelTop = tidePanelTop(width / density, height / density, words.height / density).dp.roundToPx()
        val art = measurables[0].measure(Constraints.fixed(width, width))
        val ground = measurables[1].measure(Constraints.fixed(width, (height - panelTop).coerceAtLeast(0)))
        val discSize = TideDiscSize.roundToPx()
        val play = measurables[3].measure(Constraints.fixed(discSize, discSize))
        layout(width, height) {
            art.placeRelative(0, 0)
            ground.placeRelative(0, panelTop)
            words.placeRelative(inset, height - TideBottomInset.dp.roundToPx() - words.height)
            play.placeRelative(width - TideDiscEndInset.roundToPx() - discSize, panelTop + TideDiscDrop.roundToPx() - discSize / 2)
        }
    }
}

/** The panel's wavy top edge, on the wavy progress indicator's 24dp wavelength, then solid [color] below. */
@Composable
internal fun TidePanel(color: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        val mid = TideWaveMid.toPx()
        val amplitude = TideWaveAmplitude.toPx()
        val wavelength = TideWavelength.toPx()
        val path = Path()
        val steps = (size.width / 2.dp.toPx()).roundToInt().coerceAtLeast(2)
        for (step in 0..steps) {
            val x = size.width * step / steps
            val y = mid + amplitude * sin(2 * PI * x / wavelength).toFloat()
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.close()
        drawPath(path, color)
    }
}

internal enum class TideDiscIcon { Play, Pause, Details }

/**
 * The Play disc on the panel's edge: an 88dp notch in the panel's [ground] colour, Resume progress as a
 * wavy ring (moving while the episode plays) and a 56dp button in the shade's accent.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongParameterList")
private fun TidePlayDisc(icon: TideDiscIcon, label: String, progress: HeroProgressState?, shade: ArtworkShade, ground: Color, onClick: () -> Unit) {
    val description = stringResource(R.string.home_hero_listening_progress)
    val animated by animateFloatAsState(progress?.value ?: 0f, WavyProgressIndicatorDefaults.ProgressAnimationSpec, label = "tide progress")
    Box(Modifier.size(TideDiscSize).background(ground, CircleShape), contentAlignment = Alignment.Center) {
        if (progress != null) {
            val stroke = Stroke(with(LocalDensity.current) { 4.dp.toPx() }, cap = StrokeCap.Round)
            CircularWavyProgressIndicator(
                progress = { animated },
                color = shade.accent,
                trackColor = shade.container,
                stroke = stroke,
                trackStroke = stroke,
                waveSpeed = if (progress.playing) WavyProgressIndicatorDefaults.CircularWavelength else 0.dp,
                modifier = Modifier.size(76.dp).testTag("home_hero_progress").semantics { contentDescription = description },
            )
        }
        Box(
            Modifier.size(56.dp).testTag("home_hero_play")
                .semantics {
                    contentDescription = label
                    role = Role.Button
                }
                .expressiveClickable(shape = CircleShape, feedback = ExpressivePressFeedback.Control, onClick = onClick)
                .background(shade.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (icon) {
                    TideDiscIcon.Play -> Icons.Rounded.PlayArrow
                    TideDiscIcon.Pause -> Icons.Rounded.Pause
                    TideDiscIcon.Details -> Icons.AutoMirrored.Rounded.ArrowForward
                },
                contentDescription = null,
                tint = shade.onAccent,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/**
 * Tide's Jump back in and New episodes cards: the covers fill the top, each opening its episode with a
 * Play / Pause button in its corner, and the panel rises over them on the same wavy edge in the first
 * cover's shade, with the heading and the collection's size and time. Four make a 2×2 square with
 * Resume progress and time left (or a new episode's length) on each cover; two or three sit in one row
 * and the panel lists each episode with its progress and time.
 */
@Composable
@Suppress("LongParameterList")
internal fun TideHeroGridCard(
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
    val resume = mode == HeroGridMode.Resume
    val square = displayItems.size >= 4
    val playing = displayItems.map {
        HomePlaybackStateLogic.isHeroItemPlaying(
            itemEpisodeId = it.latestEpisode?.id,
            itemPodcastId = it.id,
            currentPlayingEpisodeId = currentPlayingEpisodeId,
            currentPlayingPodcastId = currentPlayingPodcastId,
            isPlaying = isPlaying,
        )
    }
    Card(
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = shade.deep, contentColor = shade.content),
        modifier = modifier.fillMaxSize().testTag("home_hero_tide_collection"),
    ) {
        TideCollectionLayout(
            square = square,
            count = displayItems.size,
            covers = {
                displayItems.forEachIndexed { index, podcast ->
                    TideCoverTile(podcast, resume, showStatus = square, playing[index], onCellClick, onPlaybackClick, "home_hero_collection_$index")
                }
            },
            panel = { TidePanel(shade.deep) },
            copy = {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = if (square) 28.sp else 24.sp,
                            lineHeight = if (square) 32.sp else 30.sp,
                            fontWeight = GoogleSansWeight.bold,
                        ),
                        color = shade.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home_hero_collection_heading").semantics { heading() },
                    )
                    Text(
                        tideCollectionSummary(displayItems, resume),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (square) 14.sp else 13.sp),
                        color = shade.softContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (square) 6.dp else 2.dp),
                    )
                    if (!square) {
                        Column(Modifier.padding(top = 10.dp)) {
                            displayItems.forEachIndexed { index, podcast ->
                                HorizontalDivider(color = shade.container)
                                TideEpisodeRow(podcast, resume, displayItems.size, playing[index], shade, onCellClick, "home_hero_collection_$index")
                            }
                        }
                    }
                }
            },
        )
    }
}

/**
 * Four covers make a square, two or three a row up to 34% of the card's height; the panel's edge sits
 * 20dp above the square's foot (higher if the heading needs it) or 9dp above the row's, and the words
 * sit at the bottom under a square, or just under the edge above a row's episode list.
 */
@Composable
private fun TideCollectionLayout(square: Boolean, count: Int, covers: @Composable () -> Unit, panel: @Composable () -> Unit, copy: @Composable () -> Unit) {
    Layout(content = { covers(); panel(); copy() }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val inset = TideInset.roundToPx()
        val tiles = measurables.dropLast(2)
        val columns = if (square) 2 else count.coerceAtLeast(1)
        val tileWidth = width / columns
        val tileHeight = if (square) tileWidth else minOf(tileWidth, (height * TideRowCoverShare).roundToInt())
        val placedTiles = tiles.map { it.measure(Constraints.fixed(tileWidth, tileHeight)) }
        val coversBottom = if (square) tileHeight * 2 else tileHeight
        val wordsMax = Constraints(maxWidth = (width - inset * 2).coerceAtLeast(0))
        val words = measurables.last().measure(
            if (square) wordsMax else wordsMax.copy(maxHeight = (height - coversBottom).coerceAtLeast(0)),
        )
        val bottom = TideBottomInset.dp.roundToPx()
        val panelTop = if (square) {
            minOf(coversBottom - 20.dp.roundToPx(), height - bottom - words.height - 24.dp.roundToPx())
        } else {
            coversBottom - 9.dp.roundToPx()
        }.coerceAtLeast(0)
        val ground = measurables[measurables.size - 2].measure(Constraints.fixed(width, (height - panelTop).coerceAtLeast(0)))
        layout(width, height) {
            placedTiles.forEachIndexed { index, tile ->
                val column = index % columns
                val row = index / columns
                tile.placeRelative(column * tileWidth, row * tileHeight)
            }
            ground.placeRelative(0, panelTop)
            val wordsTop = if (square) height - bottom - words.height else panelTop + 22.dp.roundToPx()
            words.placeRelative(inset, wordsTop)
        }
    }
}

/** "4 episodes · 1h 53m left", or the new episodes' total length; just the count when no lengths are known. */
@Composable
private fun tideCollectionSummary(items: List<Podcast>, resume: Boolean): String {
    val count = pluralStringResource(R.plurals.home_hero_episode_count, items.size, items.size)
    val minutes = items.mapNotNull { HomeHeroStyle.minutes(it, resume) }.takeIf { it.isNotEmpty() }?.sum() ?: return count
    val total = heroDurationText(minutes)
    return stringResource(R.string.home_hero_collection_summary, count, if (resume) stringResource(R.string.home_hero_remaining, total) else total)
}

/**
 * One collection cover: tapping it opens the episode; the button in its top end corner plays or pauses
 * it. In the square, its top start corner shows Resume progress with time left, or a new episode's length.
 */
@Composable
@Suppress("LongParameterList")
private fun TideCoverTile(
    podcast: Podcast,
    resume: Boolean,
    showStatus: Boolean,
    nowPlaying: Boolean,
    onClick: (Podcast) -> Unit,
    onPlaybackClick: (Podcast) -> Unit,
    tag: String,
) {
    val currentPodcast by rememberUpdatedState(podcast)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPlayback by rememberUpdatedState(onPlaybackClick)
    val shade = rememberCoverShade(podcast.imageUrl)
    val title = podcast.latestEpisode?.title?.ifEmpty { podcast.title } ?: podcast.title
    val duration = HomeHeroStyle.minutes(podcast, resume)?.let { heroDurationText(it) }
    val progress = HomeHeroStyle.progress(podcast).takeIf { resume && it > 0f }
    Box(
        Modifier.background(shade.deep).testTag(tag)
            .semantics { contentDescription = title }
            .expressiveClickable { currentOnClick(currentPodcast) },
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 400,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (showStatus && duration != null) {
            TideCoverStatus(
                text = if (resume) stringResource(R.string.home_hero_remaining, duration) else duration,
                progress = progress,
                shade = shade,
                tag = tag,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
        if (podcast.latestEpisode != null) {
            TideCoverPlayback(nowPlaying, shade, Modifier.align(Alignment.TopEnd).testTag("${tag}_play")) { currentOnPlayback(currentPodcast) }
        }
    }
}

/** Resume progress as a small ring beside the time left, or a new episode's length, on a solid chip. */
@Composable
private fun TideCoverStatus(text: String, progress: Float?, shade: ArtworkShade, tag: String, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(shade.deep)
            .padding(start = if (progress != null) 5.dp else 9.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (progress != null) {
            Canvas(Modifier.size(16.dp).testTag("${tag}_progress")) {
                val stroke = 2.5.dp.toPx()
                val radius = size.minDimension / 2f - stroke / 2f
                drawCircle(shade.container, radius, style = Stroke(stroke))
                drawArc(
                    shade.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            fontWeight = GoogleSansWeight.medium,
            color = shade.content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag("${tag}_time"),
        )
    }
}

/** Play or Pause on a cover: a 28dp button in the cover's accent inside a 48dp target. */
@Composable
private fun TideCoverPlayback(playing: Boolean, shade: ArtworkShade, modifier: Modifier, onClick: () -> Unit) {
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
        Box(Modifier.size(28.dp).background(shade.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = shade.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}

/** One episode in a two- or three-episode panel: its title, then progress and time left (or its length). */
@Composable
@Suppress("LongParameterList")
private fun TideEpisodeRow(
    podcast: Podcast,
    resume: Boolean,
    count: Int,
    nowPlaying: Boolean,
    card: ArtworkShade,
    onClick: (Podcast) -> Unit,
    tag: String,
) {
    val currentPodcast by rememberUpdatedState(podcast)
    val currentOnClick by rememberUpdatedState(onClick)
    val shade = rememberCoverShade(podcast.imageUrl)
    val title = podcast.latestEpisode?.title?.ifEmpty { podcast.title } ?: podcast.title
    val duration = HomeHeroStyle.minutes(podcast, resume)?.let { heroDurationText(it) }
    val progress = HomeHeroStyle.progress(podcast).takeIf { resume && it > 0f }
    Column(
        Modifier.fillMaxWidth().testTag("${tag}_row").expressiveClickable { currentOnClick(currentPodcast) }.padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = if (count >= 3) 15.sp else 16.sp,
                lineHeight = if (count >= 3) 19.sp else 20.sp,
                fontWeight = GoogleSansWeight.bold,
            ),
            color = card.content,
            maxLines = if (count >= 3) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("${tag}_title"),
        )
        if (progress != null || duration != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (progress != null) {
                    HeroProgress(progress, shade.accent, card.container, Modifier.weight(1f).testTag("${tag}_progress"), playing = nowPlaying)
                }
                if (duration != null) {
                    Text(
                        if (resume) stringResource(R.string.home_hero_remaining, duration) else duration,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = card.softContent,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("${tag}_time"),
                    )
                }
            }
        }
    }
}

private val TideInset = 20.dp
private val TideDiscSize = 88.dp
private val TideDiscEndInset = 12.dp
private val TideDiscDrop = 8.dp

/** The disc's width and its end inset, plus a gap: the context line stops short of it. */
internal val TideDiscReserve = 88.dp

private val TideWaveMid = 6.dp
private val TideWaveAmplitude = 5.dp
private val TideWavelength = 24.dp
private const val TideBottomInset = 24f
private const val TideCopyGap = 40f
private const val TidePanelHighest = 0.65f
private const val TidePanelLowestGap = 16f
private const val TideRowCoverShare = 0.34f
