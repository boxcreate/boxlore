package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.briefing.BRIEFING_COVER_ASPECT_RATIO
import cx.aswin.boxlore.core.designsystem.briefing.BriefingCover
import cx.aswin.boxlore.core.designsystem.briefing.BriefingCoverMetadata
import cx.aswin.boxlore.core.designsystem.briefing.BriefingEditorialTheme
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.EpisodeStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Kept as a public state name for callers and existing Home contracts.
enum class DailyBriefingCardState { NORMAL, CONFIRM_DISMISS, CONFIRM_FOREVER }

@Suppress("LongParameterList") // Existing Home callback API; composition is split below.
@Composable
fun DailyBriefingCard(
    briefing: Briefing,
    chapters: List<Chapter>,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    playbackStatus: EpisodeStatus? = null,
    playbackProgress: Float? = null,
    isBuffering: Boolean = false,
    onDismissForever: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
) {
    var expanded by remember(briefing.region, briefing.date) { mutableStateOf(false) }
    var cardState by remember(briefing.region, briefing.date) { mutableStateOf(DailyBriefingCardState.NORMAL) }
    val displayDate = remember(briefing.date) {
        runCatching { LocalDate.parse(briefing.date).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) }
            .getOrDefault(briefing.date)
    }
    val durationMinutes = if (chapters.isNotEmpty()) ((chapters.last().startTime + 30) / 60).toInt().coerceAtLeast(1) else null
    val remainingMinutes = durationMinutes?.let { minutes ->
        (minutes * (1 - (playbackProgress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f))).toInt().coerceAtLeast(1)
    }
    fun track(action: String) = AnalyticsHelper.trackDailyBriefingInteraction(action, briefing.region, briefing.date)

    val presentation = DailyBriefingPresentation(briefing, chapters, isPlaying, isBuffering, playbackStatus, playbackProgress, remainingMinutes)
    BriefingEditorialTheme {
        Surface(onClick = { if (cardState == DailyBriefingCardState.NORMAL) onClick() }, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = modifier.fillMaxWidth()) {
            Box {
                Column(Modifier.animateContentSize()) {
                    BriefingCover(
                        region = briefing.region,
                        editionDate = briefing.date,
                        modifier = Modifier.fillMaxWidth().aspectRatio(BRIEFING_COVER_ASPECT_RATIO).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                        trailing = {
                            Surface(Modifier.align(AbsoluteAlignment.TopRight).padding(8.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                IconButton(onClick = {
                                    track(if (cardState == DailyBriefingCardState.NORMAL) "dismiss_initiated" else "dismiss_cancelled")
                                    cardState = if (cardState == DailyBriefingCardState.NORMAL) DailyBriefingCardState.CONFIRM_DISMISS else DailyBriefingCardState.NORMAL
                                }) {
                                    Icon(Icons.Rounded.Close, if (cardState == DailyBriefingCardState.NORMAL) stringResource(R.string.briefing_hide) else stringResource(R.string.briefing_cancel_hide), Modifier.size(20.dp))
                                }
                            }
                        },
                        footer = {
                            val metadata = listOfNotNull(
                                displayDate,
                                chapters.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.briefing_story_count, it.size, it.size) },
                                durationMinutes?.let { stringResource(R.string.briefing_listen_minutes, it) },
                            ).joinToString(" · ")
                            BriefingCoverMetadata(metadata, Modifier.align(Alignment.BottomStart))
                        },
                    )
                    Surface(shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        AnimatedContent(cardState, label = "briefHomeState") { state ->
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                when (state) {
                                    DailyBriefingCardState.NORMAL -> DailyBriefingNormalContent(
                                        presentation,
                                        onPlayPauseClick,
                                        onClick,
                                        expanded,
                                        onExpandedChange = { expanded = it },
                                    )
                                    DailyBriefingCardState.CONFIRM_DISMISS -> {
                                        Text(stringResource(R.string.briefing_hide_today_title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.briefing_hide_today_description), style = MaterialTheme.typography.bodyMedium)
                                        Button(onClick = {
                                            track("dismissed_today")
                                            onDismiss()
                                        }) { Text(stringResource(R.string.briefing_hide_today)) }
                                        TextButton(onClick = {
                                            track("dismiss_forever_initiated")
                                            cardState = DailyBriefingCardState.CONFIRM_FOREVER
                                        }) { Text(stringResource(R.string.briefing_always_hide)) }
                                    }
                                    DailyBriefingCardState.CONFIRM_FOREVER -> {
                                        Text(stringResource(R.string.briefing_always_hide_title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.briefing_always_hide_description), style = MaterialTheme.typography.bodyMedium)
                                        Button(onClick = {
                                            track("dismissed_forever")
                                            onDismissForever()
                                        }) { Text(stringResource(R.string.briefing_confirm_always_hide)) }
                                        TextButton(onClick = {
                                            track("feedback_clicked")
                                            onFeedbackClick()
                                        }) { Text(stringResource(R.string.briefing_send_feedback)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DailyBriefingPresentation(
    val briefing: Briefing,
    val chapters: List<Chapter>,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val playbackStatus: EpisodeStatus?,
    val playbackProgress: Float?,
    val remainingMinutes: Int?,
)

@Composable
private fun DailyBriefingNormalContent(
    state: DailyBriefingPresentation,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val briefing = state.briefing
    val chapters = state.chapters
    val isPlaying = state.isPlaying
    val isBuffering = state.isBuffering
    val playbackStatus = state.playbackStatus
    val playbackProgress = state.playbackProgress
    val remainingMinutes = state.remainingMinutes

    Text(briefing.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 30.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onPlayPauseClick,
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
        ) {
            if (isBuffering) {
                BoxLoreLoader.CircularWavy(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (isPlaying) {
                    stringResource(R.string.briefing_card_pause)
                } else if (playbackStatus == EpisodeStatus.IN_PROGRESS) {
                    stringResource(R.string.briefing_card_resume)
                } else {
                    stringResource(R.string.briefing_card_listen)
                },
            )
        }
        FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.briefing_open))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(16.dp))
        }
    }
    if (playbackStatus == EpisodeStatus.IN_PROGRESS && playbackProgress != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(progress = { playbackProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f }, modifier = Modifier.weight(1f).height(4.dp), gapSize = 0.dp, drawStopIndicator = {})
            remainingMinutes?.let { Text(stringResource(R.string.briefing_remaining_minutes, it), style = MaterialTheme.typography.labelSmall) }
        }
    }
    DailyBriefingStories(briefing, chapters, expanded, onExpandedChange)
}

@Composable
private fun DailyBriefingStories(
    briefing: Briefing,
    chapters: List<Chapter>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val visible = chapters.take(if (expanded) chapters.size else 3)
    val expandLabel = if (expanded) {
        stringResource(R.string.briefing_show_fewer_stories)
    } else {
        stringResource(R.string.briefing_show_all_stories, chapters.size)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        visible.forEachIndexed { index, chapter ->
            val canExpand = chapters.size > 3 && index == visible.lastIndex
            val rowModifier = if (canExpand) {
                Modifier.semantics { contentDescription = expandLabel }
                .clickable {
                    onExpandedChange(!expanded)
                    AnalyticsHelper.trackDailyBriefingInteraction("card_chapters_toggled", briefing.region, briefing.date, mapOf("expanded" to !expanded))
                }
            } else {
                Modifier
            }
            Row(
                rowModifier.fillMaxWidth().heightIn(min = if (canExpand) 48.dp else 32.dp).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    String.format(Locale.ROOT, "%02d", index + 1),
                    modifier = Modifier.width((28 * LocalDensity.current.fontScale).dp),
                    color = MaterialTheme.colorScheme.primary,
                    // The app's UI font, with equal-width digits so the numbers stay aligned.
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = GoogleSansWeight.medium,
                    fontSize = 18.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    chapter.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (canExpand) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (expanded) {
                            stringResource(R.string.briefing_less_inline)
                        } else {
                            stringResource(R.string.briefing_all_stories_inline, chapters.size)
                        },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
