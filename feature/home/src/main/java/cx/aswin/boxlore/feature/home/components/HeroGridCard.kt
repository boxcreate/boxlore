package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.drawOutline
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic

private val sessionSeed = kotlin.random.Random.nextInt()

private val GridCellCorner = RoundedCornerShape(14.dp)
private val TitleFontSize = 13.sp
private val TitleLineHeight = 17.sp
private const val TitleMaxLines = 3
private const val NewEpisodeWindowSeconds = 2 * 24 * 60 * 60L

/** Resume grid taps play; new-episodes grid opens episode info. */
enum class HeroGridMode {
    Resume,
    NewEpisodes,
}

/**
 * Hero Grid Card — Progressive Density Layout
 *
 * Supports 2, 3, or 4 items in a single hero card:
 * - 2 items: Stacked horizontally (top/bottom split)
 * - 3 items: 2 on top row + 1 wide spanning bottom
 * - 4 items: 2×2 grid
 */
@Composable
fun HeroGridCard(
    items: List<Podcast>,
    title: String,
    mode: HeroGridMode,
    onCellClick: (Podcast) -> Unit,
    currentPlayingPodcastId: String? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val shapes =
        remember(title) {
            val random = kotlin.random.Random(sessionSeed + title.hashCode())
            val shuffled = ExpressiveShapes.Decorative.shuffled(random)
            Pair(shuffled[0], shuffled[1])
        }
    val shape1 = shapes.first
    val shape2 = shapes.second

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier =
        modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .heroGridBackground(
                        title = title,
                        primaryColor = primaryColor,
                        shape1 = shape1,
                        shape2 = shape2,
                    ),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                RowHeader(title = title)

                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    val displayItems = items.take(4)
                    val cellContent: @Composable (Podcast, Modifier) -> Unit = { podcast, cellModifier ->
                        GridCell(
                            podcast = podcast,
                            mode = mode,
                            onClick = onCellClick,
                            isNowPlaying =
                            mode == HeroGridMode.Resume &&
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
                    when (displayItems.size) {
                        2 -> StackedLayout(displayItems, cellContent)
                        3 -> TwoOneLayout(displayItems, cellContent)
                        4 -> GridLayout2x2(displayItems, cellContent)
                        else ->
                            if (displayItems.isNotEmpty()) {
                                cellContent(displayItems[0], Modifier.fillMaxSize())
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun StackedLayout(
    items: List<Podcast>,
    cellContent: @Composable (Podcast, Modifier) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        cellContent(items[0], Modifier.weight(1f).fillMaxWidth())
        cellContent(items[1], Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun TwoOneLayout(
    items: List<Podcast>,
    cellContent: @Composable (Podcast, Modifier) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            cellContent(items[0], Modifier.weight(1f).fillMaxHeight())
            cellContent(items[1], Modifier.weight(1f).fillMaxHeight())
        }
        cellContent(items[2], Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun GridLayout2x2(
    items: List<Podcast>,
    cellContent: @Composable (Podcast, Modifier) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            cellContent(items[0], Modifier.weight(1f).fillMaxHeight())
            cellContent(items[1], Modifier.weight(1f).fillMaxHeight())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            cellContent(items[2], Modifier.weight(1f).fillMaxHeight())
            cellContent(items[3], Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun RowHeader(title: String) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun GridCell(
    podcast: Podcast,
    mode: HeroGridMode,
    onClick: (Podcast) -> Unit,
    isNowPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentPodcast by rememberUpdatedState(podcast)
    val currentOnClick by rememberUpdatedState(onClick)
    val showProgress =
        mode == HeroGridMode.Resume &&
            podcast.resumeProgress != null &&
            podcast.resumeProgress!! > 0f
    // Same freshness as Your Shows NEW: shared Room flag (RSS / PI direct-feed) or 48h window.
    val isNew =
        mode == HeroGridMode.NewEpisodes &&
            (
                podcast.rssHasNewEpisodes ||
                    podcast.latestEpisode?.let { episode ->
                        episode.publishedDate > 0L &&
                            (System.currentTimeMillis() / 1000L - episode.publishedDate) <
                            NewEpisodeWindowSeconds
                    } == true
                )
    val titleFootHeight =
        with(LocalDensity.current) {
            TitleLineHeight.toDp() * TitleMaxLines
        }

    val gradientBrush =
        remember {
            Brush.verticalGradient(
                colorStops =
                arrayOf(
                    0.0f to Color.Transparent,
                    0.35f to Color.Black.copy(alpha = 0.15f),
                    0.55f to Color.Black.copy(alpha = 0.45f),
                    0.75f to Color.Black.copy(alpha = 0.75f),
                    1.0f to Color.Black.copy(alpha = 0.92f),
                ),
            )
        }

    Box(
        modifier =
        modifier
            .clip(GridCellCorner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (isNowPlaying) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = GridCellCorner,
                    )
                } else {
                    Modifier
                },
            ).expressiveClickable {
                currentOnClick(currentPodcast)
            },
    ) {
        OptimizedImage(
            url = podcast.imageUrl.ifEmpty { podcast.fallbackImageUrl.orEmpty() },
            proxyWidth = 400,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier =
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(gradientBrush)
                },
        )

        if (isNew) {
            Box(
                modifier =
                Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    text = "NEW",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp),
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        if (isNowPlaying) {
            Box(
                modifier =
                Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Pause,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Column(
            modifier =
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val episodeTitle = podcast.latestEpisode?.title
            val primaryText = episodeTitle ?: podcast.title
            // Reserve 3 episode title lines (sp→dp); vertically center shorter titles.
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(titleFootHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = primaryText,
                    style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontSize = TitleFontSize,
                        lineHeight = TitleLineHeight,
                        fontWeight = GoogleSansWeight.bold,
                        color = Color.White,
                    ),
                    maxLines = TitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showProgress) {
                ProgressBar(
                    progress = podcast.resumeProgress!!,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.3f),
    indicatorColor: Color = MaterialTheme.colorScheme.inversePrimary,
) {
    Box(
        modifier =
        modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(indicatorColor),
        )
    }
}

private fun Modifier.heroGridBackground(
    title: String,
    primaryColor: Color,
    shape1: androidx.compose.ui.graphics.Shape,
    shape2: androidx.compose.ui.graphics.Shape,
): Modifier = this.drawWithCache {
    val size1Px = 180.dp.toPx()
    val size2Px = 220.dp.toPx()

    val shape1OffsetX = -50.dp.toPx()
    val shape1OffsetY = -30.dp.toPx()
    val shape2Inset = 150.dp.toPx()

    val outline1 =
        shape1.createOutline(
            size = Size(size1Px, size1Px),
            layoutDirection = layoutDirection,
            density = this,
        )
    val outline2 =
        shape2.createOutline(
            size = Size(size2Px, size2Px),
            layoutDirection = layoutDirection,
            density = this,
        )

    val isJumpBackIn = title.contains("JUMP", ignoreCase = true)
    val shapeAlpha = 0.06f

    onDrawBehind {
        if (isJumpBackIn) {
            translate(left = shape1OffsetX, top = shape1OffsetY) {
                drawOutline(
                    outline = outline1,
                    color = primaryColor,
                    alpha = shapeAlpha,
                )
            }
            translate(left = size.width - shape2Inset, top = size.height - shape2Inset) {
                drawOutline(
                    outline = outline2,
                    color = primaryColor,
                    alpha = shapeAlpha,
                )
            }
        } else {
            translate(left = shape1OffsetX, top = size.height - shape2Inset) {
                drawOutline(
                    outline = outline1,
                    color = primaryColor,
                    alpha = shapeAlpha,
                )
            }
            translate(left = size.width - shape2Inset, top = shape1OffsetY) {
                drawOutline(
                    outline = outline2,
                    color = primaryColor,
                    alpha = shapeAlpha,
                )
            }
        }
    }
}
