package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.effectiveEpisodeType
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.logic.EpisodeHeroForm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The episode's cover, show link, title and details, laid out for [form]. In
 * [EpisodeHeroForm.TwoColumn] the [rail] (the Play row) sits under the details, beside the cover.
 * Reading order stays cover, show, title, details, rail in every form.
 */
@Suppress("LongParameterList")
@Composable
internal fun EpisodeInfoHero(
    episode: Episode,
    podcastTitle: String,
    onPodcastClick: () -> Unit,
    completionState: EpisodeCompletionState,
    onToggleCompletion: () -> Unit,
    modifier: Modifier = Modifier,
    onMarkPlayedTipDismissed: () -> Unit = {},
    form: EpisodeHeroForm = EpisodeHeroForm.Stacked,
    artSize: Dp = 200.dp,
    rail: @Composable () -> Unit = {},
) {
    // Held here, above the layouts, so an opened title stays open when the window changes form.
    val titleExpanded = rememberSaveable(episode.id, episode.title) { mutableStateOf(false) }
    val art: @Composable (Modifier) -> Unit = { artModifier ->
        EpisodeHeroArt(episode, compact = form == EpisodeHeroForm.Row, artModifier.size(artSize))
    }
    val details: @Composable () -> Unit = {
        EpisodeShowLink(podcastTitle, onPodcastClick)
        EpisodeExpandableTitle(episode.id, episode.title, titleExpanded, compact = form == EpisodeHeroForm.Row)
        Spacer(Modifier.height(8.dp))
        EpisodeMetadata(episode, completionState, onToggleCompletion, onMarkPlayedTipDismissed)
    }
    // A size animation keyed on the form: a window change snaps to the new layout instead of animating it.
    key(form) {
        when (form) {
            EpisodeHeroForm.Stacked -> Column(modifier.fillMaxWidth().animateContentSize()) {
                art(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(20.dp))
                details()
            }
            EpisodeHeroForm.Row -> Row(modifier.fillMaxWidth().animateContentSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                art(Modifier.padding(top = 4.dp))
                Column(Modifier.weight(1f)) { details() }
            }
            EpisodeHeroForm.TwoColumn -> Row(modifier.fillMaxWidth().animateContentSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                art(Modifier)
                Column(Modifier.weight(1f)) {
                    details()
                    Spacer(Modifier.height(16.dp))
                    rail()
                }
            }
        }
    }
}

@Composable
private fun EpisodeHeroArt(episode: Episode, compact: Boolean, modifier: Modifier) {
    val corner = if (compact) 24.dp else 30.dp
    Surface(modifier = modifier.testTag(EPISODE_HERO_ART_TAG), shape = RoundedCornerShape(corner), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.fillMaxSize()) {
            OptimizedImage(
                url = episode.imageUrl,
                fallbackUrls = listOf(episode.podcastImageUrl),
                proxyWidth = 640,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // The small cover takes the list's smaller tags, so Bonus and Video stay on one line above the art.
            EpisodeArtworkTags(
                episode,
                if (compact) EpisodeTypeChipSize.Small else EpisodeTypeChipSize.Medium,
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(if (compact) 8.dp else 12.dp),
            )
        }
    }
}

@Composable
private fun EpisodeShowLink(podcastTitle: String, onPodcastClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).expressiveClickable(onClick = onPodcastClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            podcastTitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.episode_info_open_show, podcastTitle), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Date, length and number on one line that scrolls when it runs long, with the Archived and Mark played
 * pills fixed at its end. Only a column too narrow for the pills and [MinDetailsWidth] of details gives
 * the details the whole line and puts the pills at the end of the next one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeMetadata(episode: Episode, completion: EpisodeCompletionState, onToggleCompletion: () -> Unit, onTipDismissed: () -> Unit) {
    val locale = currentLocale()
    val date = remember(episode.publishedDate, locale) {
        episode.publishedDate.takeIf { it > 0L }?.let { LocaleFormats.date(it * 1_000L, LocaleFormats.DateStyle.DAY_MONTH_YEAR, locale) }
    }
    val number = episodeNumberLabel(episode)
    val metadata = listOfNotNull(date, formatEpisodeDuration(episode.duration, locale).takeIf(String::isNotBlank), number).joinToString(" · ")
    Layout(
        content = {
            if (metadata.isNotEmpty()) {
                Text(
                    metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.testTag(EPISODE_DETAILS_TAG).clipToBounds().basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 2_500,
                        repeatDelayMillis = 2_000,
                        spacing = MarqueeSpacing(32.dp),
                        velocity = 24.dp,
                    ),
                )
            }
            // Archiving is done from the show page's selection menu; here an archived episode is only marked.
            // The pills wrap, each whole, only in a column too narrow for both.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MetadataGap, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(MetadataGap),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (completion.isArchived) EpisodeArchivedPill()
                MarkPlayedTip(episode.id, completion.showTip, onTipDismissed) { EpisodeCompletionPill(completion.isCompleted, onToggleCompletion) }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val details = measurables.takeIf { it.size == 2 }?.first()
        val pills = measurables.last()
        val width = constraints.maxWidth
        val gap = MetadataGap.roundToPx()
        val pillsPlaced = pills.measure(Constraints(maxWidth = width))
        val oneLine = details == null || pills.maxIntrinsicWidth(Constraints.Infinity) + gap + MinDetailsWidth.roundToPx() <= width
        if (oneLine) {
            val detailsPlaced = details?.measure(Constraints.fixedWidth((width - pillsPlaced.width - gap).coerceAtLeast(0)))
            val height = maxOf(detailsPlaced?.height ?: 0, pillsPlaced.height)
            layout(width, height) {
                detailsPlaced?.placeRelative(0, (height - detailsPlaced.height) / 2)
                pillsPlaced.placeRelative(width - pillsPlaced.width, (height - pillsPlaced.height) / 2)
            }
        } else {
            val detailsPlaced = details.measure(Constraints.fixedWidth(width))
            val top = detailsPlaced.height + MetadataLineGap.roundToPx()
            layout(width, top + pillsPlaced.height) {
                detailsPlaced.placeRelative(0, 0)
                pillsPlaced.placeRelative(width - pillsPlaced.width, top)
            }
        }
    }
}

private val MetadataGap = 8.dp
private val MetadataLineGap = 4.dp

/** The least room the scrolling details keep beside the pills before the pills move to the next line. */
internal val MinDetailsWidth = 80.dp

/**
 * The Mark played tip as a small label pointing at the button, so nothing on the page moves: it
 * shows for four seconds, or until tapped away, then [onDismissed] runs once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkPlayedTip(episodeId: String, show: Boolean, onDismissed: () -> Unit, anchor: @Composable () -> Unit) {
    val state = rememberTooltipState(isPersistent = true)
    val dismissed by rememberUpdatedState(onDismissed)
    LaunchedEffect(episodeId, show) {
        if (!show) return@LaunchedEffect
        // A persistent tooltip's show() waits until it is dismissed, so it runs beside the timer.
        launch { state.show() }
        withTimeoutOrNull(MARK_PLAYED_TIP_MS) {
            snapshotFlow { state.isVisible }.first { it }
            snapshotFlow { state.isVisible }.first { !it }
        }
        state.dismiss()
        dismissed()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(R.string.episode_info_mark_tip)) } },
        state = state,
        enableUserInput = false,
    ) { anchor() }
}

private const val MARK_PLAYED_TIP_MS = 4_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeArtworkTags(episode: Episode, size: EpisodeTypeChipSize, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(if (size == EpisodeTypeChipSize.Small) 4.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val type = when (episode.effectiveEpisodeType?.lowercase()) {
            "bonus" -> R.string.episode_info_bonus
            "trailer" -> R.string.episode_info_trailer
            else -> null
        }
        type?.let { EpisodeTypeChip(stringResource(it), size = size) }
        if (episode.enclosureType?.startsWith("video/") == true) {
            // Beside a type tag on the small cover the camera alone keeps both tags on one line.
            val iconOnly = size == EpisodeTypeChipSize.Small && type != null
            EpisodeTypeChip(stringResource(R.string.episode_info_video), video = true, size = size, iconOnly = iconOnly)
        }
    }
}

@Composable
private fun episodeNumberLabel(episode: Episode): String? {
    val season = episode.seasonNumber ?: 0
    val number = episode.episodeNumber ?: 0
    return when {
        season > 0 && number > 0 -> stringResource(R.string.episode_info_season_episode, season, number)
        number > 0 -> stringResource(R.string.episode_info_number, number)
        season > 0 -> stringResource(R.string.episode_info_season, season)
        else -> null
    }
}

internal const val EPISODE_HERO_ART_TAG = "episode_info_hero_art"
internal const val EPISODE_DETAILS_TAG = "episode_info_details"
