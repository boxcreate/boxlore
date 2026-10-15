package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.drawOutline
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic

/** True when the listener chose the classic hero cards in Appearance settings. */
val LocalClassicHeroCards = staticCompositionLocalOf { false }

private val ClassicGridCellCorner = RoundedCornerShape(14.dp)
private val ClassicGridTitleFontSize = 13.sp
private val ClassicGridTitleLineHeight = 17.sp
private const val CLASSIC_GRID_TITLE_LINES = 3
private const val CLASSIC_NEW_WINDOW_SECONDS = 2 * 24 * 60 * 60L
private val classicSessionSeed = kotlin.random.Random.nextInt()

/**
 * The hero card from before the solid redesign: the cover fills the card under a dark scrim, with
 * the label, titles and a split Play / details button over it. Kept as the Classic option.
 */
@Composable
internal fun ClassicHeroCard(
    item: SmartHeroItem,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrim = remember {
        Brush.verticalGradient(listOf(0.15f, 0.35f, 0.55f, 0.75f, 0.92f).map { Color.Black.copy(alpha = it) })
    }
    Card(
        onClick = onArrowClick,
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Hero).fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            OptimizedImage(
                url = item.podcast.latestEpisode?.imageUrl,
                fallbackUrls = item.podcast.artworkUrls(),
                proxyWidth = 800,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    drawRect(scrim)
                },
            )
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                ClassicHeroBadge(item.label)
                Column {
                    ClassicHeroText(item)
                    Spacer(Modifier.height(16.dp))
                    ClassicHeroActions(item, onClick, onArrowClick, currentPlayingPodcastId, currentPlayingEpisodeId, isPlaying)
                }
            }
        }
    }
}

@Composable
private fun ClassicHeroText(item: SmartHeroItem) {
    val showName = item.podcast.title.replace("+", " ")
    val publisher = item.podcast.artist.replace("+", " ")
    val description = (item.description ?: item.podcast.latestEpisode?.title)?.replace("+", " ")
    if (item.type == HeroType.SPOTLIGHT) {
        Text(showName, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
        Text(publisher, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        }
    } else {
        Text(description ?: showName, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text("$showName • $publisher", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
@Suppress("LongParameterList")
private fun ClassicHeroActions(
    item: SmartHeroItem,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
) {
    val isCurrent = HomePlaybackStateLogic.isHeroItemCurrent(
        itemEpisodeId = item.podcast.latestEpisode?.id,
        itemPodcastId = item.podcast.id,
        currentPlayingEpisodeId = currentPlayingEpisodeId,
        currentPlayingPodcastId = currentPlayingPodcastId,
    )
    val showPause = isPlaying && isCurrent
    val leading = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp)
    val trailing = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = leading,
            modifier = Modifier.height(48.dp).width(160.dp)
                .expressiveClickable(shape = leading, feedback = ExpressivePressFeedback.Control, onClick = onClick),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        when {
                            showPause -> R.string.home_hero_pause
                            item.type == HeroType.RESUME || isCurrent -> R.string.home_hero_resume
                            else -> R.string.home_hero_play
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = trailing,
            modifier = Modifier.size(48.dp)
                .expressiveClickable(shape = trailing, feedback = ExpressivePressFeedback.Control, onClick = onArrowClick),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(
                        if (item.type == HeroType.SPOTLIGHT) R.string.home_hero_show_details else R.string.home_hero_episode_details,
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ClassicHeroBadge(label: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * The collection card from before the redesign: up to four covers under a scrim with their episode
 * titles. Jump back in tiles play their episode; New episodes tiles open episode info.
 */
@Composable
@Suppress("LongParameterList")
internal fun ClassicHeroGridCard(
    items: List<Podcast>,
    title: String,
    mode: HeroGridMode,
    onCellClick: (Podcast) -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val shapes = remember(mode) {
        ExpressiveShapes.Decorative.shuffled(kotlin.random.Random(classicSessionSeed + mode.ordinal)).let { it[0] to it[1] }
    }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Hero).fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().classicGridBackground(mode, primary, shapes.first, shapes.second))
            Column(Modifier.fillMaxSize()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    val cell: @Composable (Podcast, Modifier) -> Unit = { podcast, cellModifier ->
                        ClassicGridCell(
                            podcast = podcast,
                            mode = mode,
                            onClick = onCellClick,
                            isNowPlaying = mode == HeroGridMode.Resume &&
                                HomePlaybackStateLogic.isHeroItemPlaying(
                                    itemEpisodeId = podcast.latestEpisode?.id,
                                    itemPodcastId = podcast.id,
                                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                                    currentPlayingPodcastId = currentPlayingPodcastId,
                                    isPlaying = isPlaying,
                                ),
                            modifier = cellModifier,
                        )
                    }
                    ClassicGridLayout(items.take(4), cell)
                }
            }
        }
    }
}

/** Two stack, three split two over one, four make a 2×2 grid. */
@Composable
private fun ClassicGridLayout(items: List<Podcast>, cell: @Composable (Podcast, Modifier) -> Unit) {
    val rows = when (items.size) {
        0 -> return
        1 -> listOf(items)
        2 -> items.map { listOf(it) }
        else -> items.chunked(2)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                row.forEach { podcast -> cell(podcast, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun ClassicGridCell(
    podcast: Podcast,
    mode: HeroGridMode,
    onClick: (Podcast) -> Unit,
    isNowPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentPodcast by rememberUpdatedState(podcast)
    val currentOnClick by rememberUpdatedState(onClick)
    val progress = podcast.resumeProgress?.takeIf { mode == HeroGridMode.Resume && it > 0f }
    val isNew = mode == HeroGridMode.NewEpisodes &&
        (
            podcast.rssHasNewEpisodes ||
                podcast.latestEpisode?.publishedDate?.let { it > 0L && System.currentTimeMillis() / 1000L - it < CLASSIC_NEW_WINDOW_SECONDS } == true
            )
    val titleHeight = with(LocalDensity.current) { ClassicGridTitleLineHeight.toDp() * CLASSIC_GRID_TITLE_LINES }
    val scrim = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.35f to Color.Black.copy(alpha = 0.15f),
            0.55f to Color.Black.copy(alpha = 0.45f),
            0.75f to Color.Black.copy(alpha = 0.75f),
            1f to Color.Black.copy(alpha = 0.92f),
        )
    }
    Box(
        modifier
            .clip(ClassicGridCellCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (isNowPlaying) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, ClassicGridCellCorner) else Modifier)
            .expressiveClickable(shape = ClassicGridCellCorner) { currentOnClick(currentPodcast) },
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 400,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().drawWithContent {
                drawContent()
                drawRect(scrim)
            },
        )
        if (isNew) {
            Text(
                stringResource(R.string.home_hero_new).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp),
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(6.dp).align(Alignment.TopStart).clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (isNowPlaying) {
            Box(
                Modifier.padding(6.dp).align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Pause, stringResource(R.string.home_hero_playing), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.CenterStart) {
                Text(
                    podcast.latestEpisode?.title ?: podcast.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = ClassicGridTitleFontSize,
                        lineHeight = ClassicGridTitleLineHeight,
                        fontWeight = GoogleSansWeight.bold,
                        color = Color.White,
                    ),
                    maxLines = CLASSIC_GRID_TITLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(CircleShape).background(MaterialTheme.colorScheme.inversePrimary))
                }
            }
        }
    }
}

private fun Modifier.classicGridBackground(mode: HeroGridMode, color: Color, first: Shape, second: Shape): Modifier = drawWithCache {
    val firstSize = 180.dp.toPx()
    val secondSize = 220.dp.toPx()
    val offsetX = -50.dp.toPx()
    val offsetY = -30.dp.toPx()
    val inset = 150.dp.toPx()
    val firstOutline = first.createOutline(Size(firstSize, firstSize), layoutDirection, this)
    val secondOutline = second.createOutline(Size(secondSize, secondSize), layoutDirection, this)
    onDrawBehind {
        val firstTop = if (mode == HeroGridMode.Resume) offsetY else size.height - inset
        val secondTop = if (mode == HeroGridMode.Resume) size.height - inset else offsetY
        translate(left = offsetX, top = firstTop) { drawOutline(firstOutline, color, alpha = 0.06f) }
        translate(left = size.width - inset, top = secondTop) { drawOutline(secondOutline, color, alpha = 0.06f) }
    }
}
