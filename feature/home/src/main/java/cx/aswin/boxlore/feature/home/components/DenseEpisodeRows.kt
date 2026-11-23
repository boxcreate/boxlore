package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R

/**
 * One segment of a selected show's connected episode list, styled like the Daily Mix cards and
 * highlighted in the primary container while it plays. Every row is the same height: the title
 * block always reserves two lines plus one metadata line, and shorter titles centre in it.
 */
@Composable
internal fun DenseEpisodeRow(
    episode: Episode,
    podcast: Podcast,
    actions: DenseEpisodeRowActions,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    playback: DenseEpisodeRowPlayback = DenseEpisodeRowPlayback(),
    isEligibleForNewTag: Boolean = true,
) {
    val rowState = episodeRowState(episode, podcast, playback)

    Surface(
        shape = shape,
        color = if (rowState.isCurrentPlaying) MaterialTheme.colorScheme.primaryContainer else homeMixRestingCardColor(),
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable(shape = shape, onClick = actions.onClick)
            .testTag("home_filtered_episode_${episode.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DenseEpisodeArtwork(episode = episode, podcast = podcast, rowState = rowState, onPlay = actions.onPlay)
            Spacer(modifier = Modifier.width(12.dp))
            DenseEpisodeDetails(
                episode = episode,
                podcast = podcast,
                rowState = rowState,
                isEligibleForNewTag = isEligibleForNewTag,
            )
        }
    }
}

internal data class DenseEpisodeRowActions(
    val onClick: () -> Unit,
    val onPlay: () -> Unit,
)

internal data class DenseEpisodeRowPlayback(
    val overrideStatus: EpisodeStatus? = null,
    val overrideProgress: Float? = null,
    val currentPlayingEpisodeId: String? = null,
    val isPlaying: Boolean = false,
)

private data class DenseEpisodeRowState(
    val status: EpisodeStatus,
    val progress: Float,
    val isCompleted: Boolean,
    val isInProgress: Boolean,
    val isCurrentPlaying: Boolean,
)

private fun episodeRowState(
    episode: Episode,
    podcast: Podcast,
    playback: DenseEpisodeRowPlayback,
): DenseEpisodeRowState {
    val isPodcastLatestEpisode = podcast.latestEpisode?.id == episode.id
    val status = playback.overrideStatus ?: if (isPodcastLatestEpisode) podcast.episodeStatus else EpisodeStatus.UNPLAYED
    val progress = playback.overrideProgress ?: if (isPodcastLatestEpisode) (podcast.resumeProgress ?: 0f) else 0f
    return DenseEpisodeRowState(
        status = status,
        progress = progress,
        isCompleted = status == EpisodeStatus.COMPLETED,
        isInProgress = status == EpisodeStatus.IN_PROGRESS,
        isCurrentPlaying = playback.currentPlayingEpisodeId == episode.id && playback.isPlaying,
    )
}

private val RowArtworkSize = 48.dp
private val RowArtworkShape = RoundedCornerShape(12.dp)
private val RowTitleSize = 15.sp
private val RowTitleLineHeight = 19.sp
private const val ROW_TITLE_LINES = 2
private val RowMetaGap = 4.dp

/**
 * The episode's cover is its Play / Pause button (48dp): tapping it plays or pauses this episode,
 * while the rest of the row opens the episode.
 */
@Composable
private fun DenseEpisodeArtwork(
    episode: Episode,
    podcast: Podcast,
    rowState: DenseEpisodeRowState,
    onPlay: () -> Unit,
) {
    val playLabel = stringResource(if (rowState.isCurrentPlaying) R.string.home_hero_pause else R.string.home_hero_play)
    Box(Modifier.size(RowArtworkSize).clip(RowArtworkShape).testTag("home_mix_artwork_${episode.id}")) {
        OptimizedImage(
            url = episode.imageUrl,
            fallbackUrls = listOf(episode.podcastImageUrl, podcast.imageUrl, podcast.fallbackImageUrl),
            proxyWidth = 192,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (rowState.isCompleted) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(16.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    stringResource(R.string.home_mix_played),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Box(
            Modifier.matchParentSize().testTag("home_mix_play_${episode.id}")
                .semantics {
                    contentDescription = playLabel
                    role = Role.Button
                }
                .expressiveClickable(shape = RowArtworkShape, feedback = ExpressivePressFeedback.Control, onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            // A small local scrim on the cover keeps the mark readable on any artwork.
            Box(Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    if (rowState.isCurrentPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.DenseEpisodeDetails(
    episode: Episode,
    podcast: Podcast,
    rowState: DenseEpisodeRowState,
    isEligibleForNewTag: Boolean,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.weight(1f)) {
        val inProgress = rowState.isInProgress && rowState.progress > 0f
        // Narrow columns and large text leave the meta line too full for the bar: it gets its own line.
        val stackedBar = inProgress && denseRowStacksProgress(maxWidth, density.fontScale)
        Column(
            modifier = Modifier.fillMaxWidth().height(denseRowDetailsHeight(stackedBar)),
            verticalArrangement = Arrangement.spacedBy(RowMetaGap, Alignment.CenterVertically),
        ) {
            Text(
                text = episode.title,
                style = denseRowTitleStyle(),
                maxLines = ROW_TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            DenseEpisodeMetadataRow(
                episode = episode,
                podcast = podcast,
                rowState = rowState,
                isEligibleForNewTag = isEligibleForNewTag,
                inlineBar = !stackedBar,
            )
            if (stackedBar) DenseProgressBar(rowState.progress, Modifier.fillMaxWidth().testTag("home_dense_row_progress"))
        }
    }
}

@Composable
private fun denseRowTitleStyle() = MaterialTheme.typography.titleSmall.copy(
    fontWeight = GoogleSansWeight.bold,
    fontSize = RowTitleSize,
    lineHeight = RowTitleLineHeight,
)

/** The words' block: two title lines and the meta line, plus the progress bar's own line when it stacks. */
@Composable
private fun denseRowDetailsHeight(stackedBar: Boolean): Dp {
    val metaLineHeight = MaterialTheme.typography.bodySmall.lineHeight.takeIf { it.isSp } ?: 16.sp
    val base = with(LocalDensity.current) { (RowTitleLineHeight * ROW_TITLE_LINES).toDp() + RowMetaGap + metaLineHeight.toDp() }
    return if (stackedBar) base + RowMetaGap + DenseProgressHeight else base
}

/** Whether an in-progress row's bar moves under its meta line: a column under 260dp, or text above 115%. */
internal fun denseRowStacksProgress(columnWidth: Dp, fontScale: Float): Boolean = columnWidth < 260.dp || fontScale > 1.15f

@Composable
private fun DenseProgressBar(progress: Float, modifier: Modifier) {
    val progressLabel = stringResource(R.string.home_hero_listening_progress)
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.height(DenseProgressHeight).semantics { contentDescription = progressLabel },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
        gapSize = 2.dp,
    )
}

private val DenseProgressHeight = 4.dp

/**
 * The selected show's rows while they load: [rows] placeholders with each row's cover, two title
 * lines and the meta line where they will be, then the See all segment, in the same connected
 * shapes, so the list keeps its height when the episodes arrive.
 */
@Composable
internal fun DenseEpisodeRowSkeleton(rows: Int, modifier: Modifier = Modifier) {
    val segments = rows + 1
    Column(modifier.fillMaxWidth().testTag("home_selected_show_loading"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rows) { index ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(yourShowsListShape(index, segments)).background(homeMixRestingCardColor())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(Modifier.size(RowArtworkSize), shape = RowArtworkShape)
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f).height(denseRowDetailsHeight(stackedBar = false)),
                    verticalArrangement = Arrangement.spacedBy(RowMetaGap, Alignment.CenterVertically),
                ) {
                    SkeletonTextLines(denseRowTitleStyle(), lines = ROW_TITLE_LINES, widths = listOf(0.92f, 0.55f))
                    SkeletonTextLines(MaterialTheme.typography.bodySmall, lines = 1, widths = listOf(0.4f))
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(yourShowsListShape(rows, segments)).background(MaterialTheme.colorScheme.primaryContainer),
        )
    }
}

/** One line: date, then the length, or the progress bar and time left while in progress. */
@Composable
private fun DenseEpisodeMetadataRow(
    episode: Episode,
    podcast: Podcast,
    rowState: DenseEpisodeRowState,
    isEligibleForNewTag: Boolean,
    inlineBar: Boolean = true,
) {
    val isNew = isEligibleForNewTag && isNewEpisode(episode, podcast, rowState.status)
    val inProgress = rowState.isInProgress && rowState.progress > 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isNew) {
            DenseNewEpisodeBadge()
        }
        val date = formatRelativeDate(episode.publishedDate)
        // Without the bar beside it, the date gives way first, so the time left stays whole.
        DenseMetaText(date, modifier = if (inProgress && inlineBar) Modifier else Modifier.weight(1f, fill = false))
        if (inProgress && inlineBar) {
            // Never squeezed out of sight: the bar keeps at least 24dp and the words give way.
            DenseProgressBar(rowState.progress, Modifier.weight(1f).widthIn(min = 24.dp).testTag("home_dense_row_progress"))
        }
        denseDurationText(episode.duration, rowState.progress, inProgress)?.let { duration ->
            DenseMetaText(
                text = if (inProgress || date.isEmpty()) duration else "• $duration",
                color = if (inProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                bold = inProgress,
                modifier = Modifier.testTag("home_dense_row_time"),
            )
        }
    }
}

@Composable
private fun DenseNewEpisodeBadge() {
    Box(
        modifier =
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.home_hero_new),
            style =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = GoogleSansWeight.bold,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DenseMetaText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = if (bold) GoogleSansWeight.bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
