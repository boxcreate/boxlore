package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic

@Composable
@Suppress("LongParameterList") // Existing action and playback callbacks.
fun HeroCard(
    item: SmartHeroItem,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    onToggleSubscription: () -> Unit,
    currentPlayingPodcastId: String? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val foreground = heroContentColor()
    val container = heroContainerColor()
    val current = HomePlaybackStateLogic.isHeroItemCurrent(
        itemEpisodeId = item.podcast.latestEpisode?.id,
        itemPodcastId = item.podcast.id,
        currentPlayingEpisodeId = currentPlayingEpisodeId,
        currentPlayingPodcastId = currentPlayingPodcastId,
    )
    val pause = isPlaying && current
    val progress = HomeHeroStyle.progress(item.podcast)
    val progressState = if (item.type == HeroType.RESUME && progress > 0f) HeroProgressState(progress, pause) else null

    Card(
        onClick = onArrowClick,
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = foreground),
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Hero).fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (LocalBoxloreWindowLayout.current.usesTabletLayout) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp)) {
                if (LocalBoxloreWindowLayout.current.usesTabletLayout && maxHeight < 240.dp) {
                    ShortTabletHeroBody(item, foreground)
                } else {
                    HeroBody(item, foreground, progressState, Modifier.fillMaxSize())
                }
                }
            } else {
                HeroBody(item, foreground, progressState, Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp))
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                HeroActions(
                    pause = pause,
                    resume = item.type == HeroType.RESUME || current,
                    foreground = foreground,
                    container = container,
                    item = item,
                    onPlayClick = onClick,
                    onDetailsClick = onArrowClick,
                )
            }
        }
    }
}

@Composable
private fun ShortTabletHeroBody(item: SmartHeroItem, foreground: Color) {
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        HeroArtwork(item, Modifier.size(80.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(heroTitle(item), color = foreground, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("home_hero_title").semantics { heading() })
            Text(heroSupportingName(item), color = foreground, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HeroCardContext(item, foreground, wide = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HeroBody(item: SmartHeroItem, foreground: Color, progressState: HeroProgressState?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = heroTitleStyle()
    val supportingStyle = MaterialTheme.typography.bodyMedium
    val wideContext = density.fontScale > 1.3f
    BoxWithConstraints(modifier.testTag("home_hero_body")) {
        val wideContextHeight = if (wideContext) heroWideContextHeight(item, maxWidth - 32.dp) else 0.dp
        val textConstraints = Constraints(maxWidth = with(density) { (maxWidth - 32.dp).roundToPx() })
        val supportingName = heroSupportingName(item)
        val supportingHeight = if (supportingName.isNotBlank()) {
            measurer.measure(supportingName, supportingStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = textConstraints).size.height
        } else {
            0
        }
        val desiredHeader = if (wideContext) wideContextHeight + 8.dp + maxWidth * 0.72f else maxWidth * 0.65f
        val supportingGap = if (supportingHeight > 0) 6.dp else 0.dp
        val progressSpace = if (progressState != null) 20.dp else 0.dp
        val titleBudget = with(density) { (maxHeight - desiredHeader - 12.dp - supportingGap - progressSpace).roundToPx() } - supportingHeight
        val title = measureHeroTitle(heroTitle(item), titleStyle, measurer, textConstraints, titleBudget)
        val copyReserve = with(density) { (title.size.height + supportingHeight).toDp() } + supportingGap + progressSpace + 1.dp
        val headerLimit = (maxHeight - copyReserve - 12.dp).coerceAtLeast(0.dp)
        val headerHeight = minOf(headerLimit, desiredHeader)
        Column(Modifier.fillMaxSize()) {
            HeroLead(item, foreground, Modifier.fillMaxWidth().height(headerHeight))
            Spacer(Modifier.height(12.dp))
            HeroCopy(item, foreground, title.layoutInput.style, progressState, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun HeroLead(item: SmartHeroItem, foreground: Color, modifier: Modifier) {
    val wideContext = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(modifier.testTag("home_hero_lead")) {
        val wideContextHeight = if (wideContext) heroWideContextHeight(item, maxWidth - 32.dp) else 0.dp
        if (wideContext) {
            val contextHeight = wideContextHeight
            val artworkSize = minOf(maxWidth * 0.72f, (maxHeight - contextHeight - 8.dp).coerceAtLeast(0.dp))
            Column(Modifier.fillMaxSize()) {
                HeroCardContext(item, foreground, wide = true, Modifier.fillMaxWidth().height(contextHeight).padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                HeroArtwork(item, Modifier.size(artworkSize).align(Alignment.End))
            }
        } else {
            val artworkSize = minOf(maxHeight, maxWidth * 0.65f)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroCardContext(item, foreground, wide = false, Modifier.weight(1f).fillMaxSize().padding(horizontal = 16.dp))
                HeroArtwork(item, Modifier.size(artworkSize).align(Alignment.Top))
            }
        }
    }
}

@Composable
private fun HeroArtwork(item: SmartHeroItem, modifier: Modifier) {
    val sources = if (item.type == HeroType.SPOTLIGHT) item.podcast.artworkUrls() else item.podcast.latestEpisode?.artworkUrls(item.podcast) ?: item.podcast.artworkUrls()
    HeroArtworkMerge(heroArtworkUrl(item.podcast, showFirst = item.type == HeroType.SPOTLIGHT), modifier.testTag("home_hero_artwork"), sources)
}

@Composable
private fun HeroCopy(item: SmartHeroItem, foreground: Color, titleStyle: TextStyle, progressState: HeroProgressState?, modifier: Modifier) {
    val supportingName = heroSupportingName(item)
    Column(modifier.padding(horizontal = 16.dp).testTag("home_hero_copy"), verticalArrangement = Arrangement.Center) {
        Text(
            text = heroTitle(item),
            style = titleStyle,
            color = foreground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("home_hero_title").semantics { heading() },
        )
        if (supportingName.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = supportingName,
                style = MaterialTheme.typography.bodyMedium,
                color = foreground.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("home_hero_supporting_name"),
            )
        }
        if (progressState != null) {
            Spacer(Modifier.height(8.dp))
            HeroProgress(progressState.value, foreground, foreground.copy(alpha = 0.2f), Modifier.testTag("home_hero_progress"), playing = progressState.playing)
        }
    }
}

@Composable
private fun heroTitleStyle() = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 29.sp, fontWeight = GoogleSansWeight.bold)

private fun heroTitle(item: SmartHeroItem) = (if (item.type == HeroType.SPOTLIGHT) item.podcast.title else item.description ?: item.podcast.latestEpisode?.title ?: item.podcast.title).replace("+", " ")

private fun heroSupportingName(item: SmartHeroItem) =
    (if (item.type == HeroType.SPOTLIGHT) item.podcast.artist else item.podcast.title).replace("+", " ")

@Composable
private fun heroActionLabel(item: SmartHeroItem, pause: Boolean, resume: Boolean) = stringResource(
    when {
        item.podcast.latestEpisode == null -> R.string.home_hero_show_details
        pause -> R.string.home_hero_pause
        resume -> R.string.home_hero_resume
        item.type == HeroType.SPOTLIGHT -> R.string.home_hero_play_latest
        else -> R.string.home_hero_play
    },
)

@Composable
private fun HeroActions(
    pause: Boolean,
    resume: Boolean,
    foreground: Color,
    container: Color,
    item: SmartHeroItem,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit,
) {
    val hasEpisode = item.podcast.latestEpisode != null
    val spotlight = item.type == HeroType.SPOTLIGHT
    val actionLabel = heroActionLabel(item, pause, resume)
    val playShape = when {
        !hasEpisode -> RoundedCornerShape(26.dp)
        pause -> CircleShape
        else -> RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 8.dp, bottomEnd = 8.dp)
    }
    val detailsShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 26.dp, bottomEnd = 26.dp)
    val actionStyle = HeroContextTypography.action()
    val labelHeight = rememberTextMeasurer().measure(actionLabel, actionStyle, maxLines = 1).size.height
    val actionHeight = (with(LocalDensity.current) { labelHeight.toDp() } + 20.dp).coerceAtLeast(48.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = foreground,
            contentColor = container,
            shape = playShape,
            modifier = Modifier.weight(1f).height(actionHeight).testTag("home_hero_play")
                .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = playShape, onClick = if (hasEpisode) onPlayClick else onDetailsClick),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    when {
                    !hasEpisode -> Icons.AutoMirrored.Filled.ArrowForward
                    pause -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                    null,
                    Modifier.size(22.dp).testTag("home_hero_play_icon")
                )
                Text(
                    actionLabel,
                    style = actionStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = actionStyle.fontSize),
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).testTag("home_hero_play_label"),
                )
            }
        }
        if (hasEpisode) {
            Surface(
            color = HomeHeroStyle.secondaryContainer(MaterialTheme.colorScheme),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = detailsShape,
            modifier = Modifier.size(actionHeight).testTag("home_hero_details")
                .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = detailsShape, onClick = onDetailsClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    stringResource(if (spotlight) R.string.home_hero_show_details else R.string.home_hero_episode_details),
                    Modifier.size(20.dp),
                )
            }
        }
        }
    }
}
