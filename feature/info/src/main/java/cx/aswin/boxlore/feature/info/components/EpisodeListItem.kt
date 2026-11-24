package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.effectiveEpisodeType
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
import cx.aswin.boxlore.feature.info.sections.stripHtml

@Composable
// One row for every list style and state; splitting it up is its own change (kotlin:S3776).
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "kotlin:S3776")
fun EpisodeListItem(
    episode: Episode,
    isLiked: Boolean,
    accentColor: Color,
    // Playback State
    isPlaying: Boolean,
    isResume: Boolean,
    progress: Float,
    timeLeft: String?,
    // Download State
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isQueued: Boolean,
    isCompleted: Boolean,
    isUpNext: Boolean = false,
    isArchived: Boolean = false,
    selectionActive: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPlayClick: () -> Unit,
    onToggleLike: () -> Unit,
    onQueueClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMarkPlayedClick: () -> Unit,
    showMarkPlayedButton: Boolean = true,
    podcastImageUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val markPlayedClick = rememberPodcastEpisodeControlClick(onMarkPlayedClick)
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val selected = episodeSelectionColors()
    Surface(
        modifier = modifier.fillMaxWidth().expressiveClickable(
            shape = RoundedCornerShape(24.dp),
            onLongClickLabel = selectLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) selected.container else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (isSelected) selected.content else MaterialTheme.colorScheme.onSurface,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A wide card puts the picture, text and buttons on one line; Showcase lists keep their cards.
            val wide = maxWidth >= WideEpisodeCardMinWidth && LocalBrandedPage.current == null
            val actions: @Composable (Modifier) -> Unit = { actionsModifier ->
                PodcastEpisodeActions(
                    state = PodcastEpisodeActionsState(isPlaying, isResume, isLiked, isDownloaded, isDownloading, isQueued),
                    callbacks = PodcastEpisodeActionsCallbacks(onPlayClick, onToggleLike, onDownloadClick, onQueueClick),
                    accentColor = accentColor,
                    playMaxWidth = ListPlayMaxWidth,
                    playWidth = if (wide) WideCardPlayWidth else null,
                    modifier = actionsModifier,
                )
            }
            val markPlayed: @Composable () -> Unit = {
                EpisodeCompletionPill(isCompleted = isCompleted, onToggleCompletion = markPlayedClick)
            }
            val progressBar: @Composable () -> Unit = {
                podcastEpisodeCardProgress(isResume, isCompleted, progress)?.let {
                    PodcastEpisodeProgress(it, timeLeft, accentColor)
                }
            }
            if (wide) {
                Row(Modifier.padding(14.dp).testTag(WIDE_EPISODE_CARD_TAG), verticalAlignment = Alignment.CenterVertically) {
                    EpisodeCardArt(episode, podcastImageUrl, isSelected, isCompleted, WideCardArtSize)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        EpisodeCardText(episode, isArchived, isUpNext)
                        if (!selectionActive && showMarkPlayedButton) {
                            Spacer(Modifier.height(6.dp))
                            markPlayed()
                        }
                        if (!selectionActive) progressBar()
                    }
                    if (!selectionActive) {
                        Spacer(modifier = Modifier.width(16.dp))
                        actions(Modifier)
                    }
                }
            } else {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        EpisodeCardArt(episode, podcastImageUrl, isSelected, isCompleted, CardArtSize)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { EpisodeCardText(episode, isArchived, isUpNext) }
                    }
                    if (!selectionActive) {
                        Spacer(modifier = Modifier.height(14.dp))
                        actions(Modifier)
                        if (showMarkPlayedButton) {
                            Spacer(Modifier.height(8.dp))
                            markPlayed()
                        }
                        progressBar()
                    }
                }
            }
        }
    }
}

/** The card's picture, with a check when it is selected or played. */
@Composable
private fun EpisodeCardArt(episode: Episode, podcastImageUrl: String?, isSelected: Boolean, isCompleted: Boolean, size: Dp) {
    Box(modifier = Modifier.size(size)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            OptimizedImage(
                url = episode.imageUrl,
                fallbackUrls = listOf(episode.podcastImageUrl, podcastImageUrl),
                proxyWidth = 200, // 76-88dp thumbnails
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        if (isSelected || isCompleted) {
            Box(
                modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape,
                    ).border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(if (isSelected) R.string.podcast_episode_selected else R.string.episode_info_played),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** The card's words: archived and Up next tags, the title, the details line, type tags and the description. */
@Composable
private fun EpisodeCardText(episode: Episode, isArchived: Boolean, isUpNext: Boolean) {
    if (isArchived) EpisodeArchivedPill()
    if (isUpNext) {
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

    // Two-line titles keep titleMedium's own line spacing, so they don't crowd at large text.
    Text(
        text = episode.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = GoogleSansWeight.bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(EPISODE_CARD_TITLE_TAG),
    )
    Spacer(modifier = Modifier.height(4.dp))
    val locale = currentLocale()
    val seasonEpisode = seasonEpisodeShort(episode.seasonNumber, episode.episodeNumber)?.asString().orEmpty()
    val timeHighlight = LocalBrandedPage.current?.style?.episodeTimeColor
    val highlightFamily = rememberGoogleSansFamily(weight = 600)
    // The line listeners scan most: date and length, one step up from the smallest text.
    Text(
        text = episodeDetailsLine(
            parts = listOf(seasonEpisode, formatEpisodeRelativeDate(episode.publishedDate, locale), formatEpisodeListDuration(episode.duration, locale)),
            highlightIndex = 1,
            highlight = timeHighlight?.let { SpanStyle(color = it, fontFamily = highlightFamily) },
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(EPISODE_CARD_DETAILS_TAG),
    )
    val type = episode.effectiveEpisodeType?.takeUnless { it == "full" }
    val isVideo = episode.enclosureType?.startsWith("video/") == true
    if (type != null || isVideo) {
        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (type != null) {
                val typeLabel = when (type) {
                    "bonus" -> stringResource(R.string.episode_info_bonus)
                    "trailer" -> stringResource(R.string.episode_info_trailer)
                    else -> type.replaceFirstChar { it.titlecase(locale) }
                }
                EpisodeTypeChip(typeLabel)
            }
            if (isVideo) EpisodeTypeChip(stringResource(R.string.episode_info_video), video = true)
        }
    }

    // Description Preview
    val stripped = stripHtml(episode.description)
    if (stripped.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stripped,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
    }
}

/** Cards this wide or wider put everything on one line. */
internal val WideEpisodeCardMinWidth = 680.dp
private val CardArtSize = 76.dp
private val WideCardArtSize = 88.dp
private val WideCardPlayWidth = 120.dp

internal const val WIDE_EPISODE_CARD_TAG = "podcast_episode_wide_card"
internal const val EPISODE_CARD_TITLE_TAG = "podcast_episode_card_title"
internal const val EPISODE_CARD_DETAILS_TAG = "podcast_episode_card_details"

/** Joins the non-blank [parts] with " · ", styling the part at [highlightIndex] when [highlight] is set. */
internal fun episodeDetailsLine(parts: List<String>, highlightIndex: Int = -1, highlight: SpanStyle? = null): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { index, part ->
        if (part.isBlank()) return@forEachIndexed
        if (length > 0) append(" · ")
        if (index == highlightIndex && highlight != null) withStyle(highlight) { append(part) } else append(part)
    }
}
