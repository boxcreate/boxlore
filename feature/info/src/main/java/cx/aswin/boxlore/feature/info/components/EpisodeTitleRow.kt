package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
import java.util.Locale

internal const val EPISODE_TITLE_ROW_TAG = "podcast_info_episode_title_row"
internal const val EPISODE_TITLE_ROW_PLAY_TAG = "podcast_info_episode_title_row_play"

/** What an [EpisodeTitleRow] shows besides its episode: playback, completion, Up next and selection. */
internal data class EpisodeTitleRowState(
    val isPlaying: Boolean = false,
    val isResume: Boolean = false,
    val progress: Float = 0f,
    val timeLeft: String? = null,
    val isCompleted: Boolean = false,
    val isUpNext: Boolean = false,
    /** Archived: listed only with Show archived on or in a search, and marked. */
    val isArchived: Boolean = false,
    val selectionActive: Boolean = false,
    val isSelected: Boolean = false,
)

/**
 * The show page's short episode row for Episode titles only: the title (two lines at most), its
 * details line and a play button whose ring shows listening progress. Artwork, description, type
 * chips and the like, download and queue buttons stay on the full [EpisodeListItem]. Played rows
 * carry a check before the details; during selection a leading mark replaces the play button, so
 * the row keeps its height.
 */
@Composable
internal fun EpisodeTitleRow(
    episode: Episode,
    state: EpisodeTitleRowState,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val selected = episodeSelectionColors()
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth().testTag(EPISODE_TITLE_ROW_TAG).expressiveClickable(
            shape = shape,
            onLongClickLabel = selectLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
        shape = shape,
        color = if (state.isSelected) selected.container else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (state.isSelected) selected.content else MaterialTheme.colorScheme.onSurface,
        border = if (state.isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = if (state.selectionActive) 16.dp else 8.dp, top = 8.dp, bottom = 8.dp)
                .heightIn(min = TITLE_ROW_CONTROL_SIZE),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.selectionActive) {
                EpisodeTitleRowSelectionMark(state.isSelected)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                if (state.isUpNext) EpisodeTitleRowUpNext()
                if (state.isArchived) EpisodeArchivedPill()
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(2.dp))
                EpisodeTitleRowDetails(episode, state)
            }
            if (!state.selectionActive) {
                Spacer(Modifier.width(8.dp))
                EpisodeTitleRowPlayButton(state, accentColor, onPlayClick)
            }
        }
    }
}

private val TITLE_ROW_CONTROL_SIZE = 48.dp

/** A regular episode as a short title row, for Episode titles only. */
@Composable
internal fun NormalEpisodeTitleRow(
    episode: Episode,
    playState: PodcastInfoViewModel.EpisodePlaybackState?,
    ui: EpisodeFeedRowUi,
    selection: EpisodeSelectionUi,
    onClick: () -> Unit,
    viewModel: PodcastInfoViewModel,
    modifier: Modifier,
) {
    EpisodeTitleRow(
        episode = episode,
        state = EpisodeTitleRowState(
            isPlaying = playState?.isPlaying == true,
            isResume = playState?.isResume == true,
            progress = playState?.progress ?: 0f,
            timeLeft = playState?.timeLeft?.asString(),
            isCompleted = episode.id in ui.indicators.completedEpisodeIds,
            isUpNext = episode.id == ui.autoScrolledEpisodeId,
            isArchived = episode.id in ui.indicators.archivedEpisodeIds,
            selectionActive = selection.isActive,
            isSelected = episode.id in selection.selectedEpisodeIds,
        ),
        accentColor = ui.accentColor,
        onClick = onClick,
        onLongClick = { selection.onLongPress(episode) },
        onPlayClick = { viewModel.onPlayClick(episode) },
        modifier = modifier,
    )
}

@Composable
private fun EpisodeTitleRowUpNext() {
    Surface(
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Text(
            stringResource(R.string.podcast_episode_up_next),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = GoogleSansWeight.bold),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The details line, after a check when the episode is played; time left replaces the length while it is underway. */
@Composable
private fun EpisodeTitleRowDetails(episode: Episode, state: EpisodeTitleRowState) {
    val underway = podcastEpisodeCardProgress(state.isResume, state.isCompleted, state.progress) != null
    val timeHighlight = LocalBrandedPage.current?.style?.episodeTimeColor
    val highlightFamily = rememberGoogleSansFamily(weight = 600)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.isCompleted) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.episode_info_played),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = episodeDetailsLine(
                parts = episodeTitleRowDetailParts(
                    episode,
                    timeLeft = state.timeLeft.takeIf { underway },
                    seasonEpisode = seasonEpisodeShort(episode.seasonNumber, episode.episodeNumber)?.asString().orEmpty(),
                    locale = currentLocale(),
                ),
                highlightIndex = 1,
                highlight = timeHighlight?.let { SpanStyle(color = it, fontFamily = highlightFamily) },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A round play button in the page accent, ringed by listening progress once the episode is underway. */
@Composable
private fun EpisodeTitleRowPlayButton(state: EpisodeTitleRowState, accentColor: Color, onPlayClick: () -> Unit) {
    val label = stringResource(
        when {
            state.isPlaying -> R.string.episode_info_pause
            state.isResume -> R.string.episode_info_resume
            else -> R.string.episode_info_play
        },
    )
    val progress = podcastEpisodeCardProgress(state.isResume, state.isCompleted, state.progress)
    Box(
        modifier = Modifier
            .size(TITLE_ROW_CONTROL_SIZE)
            .testTag(EPISODE_TITLE_ROW_PLAY_TAG)
            .expressiveClickable(shape = CircleShape, onClick = onPlayClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            val animated by animateFloatAsState(progress, label = "episode_title_row_progress")
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxSize(),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
        Box(
            modifier = Modifier.size(if (progress != null) 36.dp else 42.dp).background(accentColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = contentColorFor(accentColor),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** An empty ring, or a filled check while selected; shown only during selection. */
@Composable
private fun EpisodeTitleRowSelectionMark(isSelected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val mark = Modifier.size(24.dp)
    if (isSelected) {
        Box(mark.background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, stringResource(R.string.podcast_episode_selected), tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    } else {
        Box(mark.border(2.dp, scheme.outline, CircleShape))
    }
}

/**
 * A title row's details: season and episode, how long ago it came out, then [timeLeft] while the
 * episode is underway or else its length. Blank parts drop out of the line.
 */
internal fun episodeTitleRowDetailParts(
    episode: Episode,
    timeLeft: String?,
    seasonEpisode: String,
    locale: Locale,
    nowSeconds: Long = System.currentTimeMillis() / 1000,
): List<String> = listOf(
    seasonEpisode,
    formatEpisodeRelativeDate(episode.publishedDate, locale, nowSeconds),
    timeLeft?.takeIf { it.isNotBlank() } ?: formatEpisodeListDuration(episode.duration, locale),
)
