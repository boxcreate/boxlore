package cx.aswin.boxlore.feature.info.branded.serial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedLength
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import java.util.Locale

/**
 * The latest episode as the top card of a stack: its own picture on a black card with the logo's
 * white rim and two more cards stepping down-left behind it; beside it the season and episode in
 * red capitals ("METRO · Episode 3"), the title without the season's name in front, and when it
 * came out and how long it is. The episode cards' buttons follow.
 */
@Composable
internal fun SerialSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val episode = spotlight.episode
    val line = remember(episode.title, episode.seasonNumber, episode.episodeNumber) { serialEpisodeLine(episode) }
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val label = when {
        line.season != null && line.episode != null -> stringResource(R.string.serial_season_episode, line.season, line.episode)
        line.season != null -> line.season
        line.episode != null -> stringResource(R.string.serial_episode, line.episode)
        else -> stringResource(R.string.branded_latest_episode)
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(SerialColors.Card)
            .then(if (spotlight.isSelected) Modifier.border(3.dp, SerialColors.Text, shape) else Modifier)
            .expressiveClickable(shape = shape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EpisodeStack(spotlight)
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    label.uppercase(Locale.getDefault()),
                    style = brandedSansStyle(10.5.sp, 13.sp, weight = 700).copy(letterSpacing = 1.8.sp),
                    color = SerialColors.RedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    line.headline,
                    style = brandedSansStyle(17.sp, 21.sp, weight = 600),
                    color = SerialColors.Text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // Screen readers hear the episode's own title.
                    modifier = Modifier.padding(top = 5.dp).clearAndSetSemantics { text = AnnotatedString(episode.title) },
                )
                Text(
                    listOf(formatEpisodeRelativeDate(episode.publishedDate), brandedLength(episode.duration))
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    style = brandedSansStyle(12.sp, 14.sp),
                    color = SerialColors.Muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BrandedEpisodeActions(spotlight, SerialColors.Actions, showProgress = true)
    }
}

/** The episode's picture on the top card, two plain cards stepping down-left behind it. */
@Composable
private fun EpisodeStack(spotlight: BrandedSpotlight) {
    val shape = RoundedCornerShape(9.dp)
    Box(Modifier.padding(start = 10.dp).size(114.dp)) {
        for (k in 2 downTo 1) {
            Spacer(
                Modifier
                    .offset((-5 * k).dp, (5 * k).dp)
                    .size(104.dp)
                    .background(SerialColors.CardBlack, shape)
                    .border(2.dp, SerialColors.Rim, shape),
            )
        }
        Box(
            Modifier
                .size(104.dp)
                .boxShadow(fill = SerialColors.CardBlack, color = StackShadow, offsetY = 6.dp, blur = 12.dp, cornerRadius = 9.dp)
                .border(2.dp, SerialColors.Rim, shape)
                .padding(4.dp),
        ) {
            OptimizedImage(
                url = spotlight.episodePicture ?: spotlight.showPicture,
                fallbackUrls = listOf(spotlight.showPicture),
                proxyWidth = 300,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

private val StackShadow = Color(0x66080205)
