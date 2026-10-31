package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.VariableWeights
import cx.aswin.boxlore.feature.info.branded.brandedLength
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.sections.stripHtml
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val StoryShape = RoundedCornerShape(2.dp)

/** About the deck's five lines tall. */
private val PhotoSize = 112.dp

/**
 * The lead story: "Latest episode" with the day and length, the title as a headline, the episode's
 * own notes as the deck beside its photo, and the episode cards' buttons between two fine rules.
 * An episode without a photo of its own shows the show's cover; the show's dawn horizon stands in
 * while either loads, or if neither arrives.
 */
@Composable
internal fun DailySpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val p = DailyColors.current
    val episode = spotlight.episode
    val franklin = rememberDailyFranklin()
    val selectLabel = stringResource(R.string.podcast_episode_select)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(StoryShape)
            .background(if (spotlight.isSelected) p.control else p.page)
            .expressiveClickable(shape = StoryShape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.ink))
        Kicker(spotlight, franklin)
        Text(
            episode.title,
            style = brandedTextStyle(rememberDailySerif(600, 25f), 25.sp, 29.sp, letterSpacing = (-0.2).sp),
            color = p.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val deck = remember(episode.description) { stripHtml(episode.description) }
            Text(
                deck,
                style = brandedTextStyle(rememberDailySerif(400, 15f), 15.sp, 21.sp),
                color = p.sub,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Photo(spotlight.episodePicture, spotlight.showPicture ?: episode.podcastImageUrl)
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(p.soft))
        BrandedEpisodeActions(spotlight, p.actions, Modifier.padding(vertical = 6.dp), showProgress = true)
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.soft))
    }
}

/** The episode's photo, or the show's cover, over the dawn horizon, which shows until one arrives. */
@Composable
private fun Photo(picture: String?, cover: String?) {
    Box(Modifier.size(PhotoSize)) {
        Image(painterResource(R.drawable.daily_horizon), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (picture != null || cover != null) {
            OptimizedImage(
                url = picture ?: cover,
                fallbackUrls = listOf(cover),
                proxyWidth = 400,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                errorContent = {},
                showLoadingPlaceholder = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "LATEST EPISODE · 31 OCT 2026 · 49 MIN": the label in the show's blue, then the day it came out and its length, or that you played it. */
@Composable
private fun Kicker(spotlight: BrandedSpotlight, franklin: VariableWeights) {
    val p = DailyColors.current
    val episode = spotlight.episode
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(episode.publishedDate, locale) {
        if (episode.publishedDate <= 0) {
            ""
        } else {
            Instant.ofEpochSecond(episode.publishedDate).atZone(ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }
    val length = if (spotlight.isCompleted) stringResource(R.string.episode_info_played) else brandedLength(episode.duration)
    val details = listOf(date, length).filter(String::isNotBlank).joinToString(" · ")
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = p.kicker, fontFamily = franklin[700], fontWeight = FontWeight.Bold)) {
                append(stringResource(R.string.branded_latest_episode).uppercase(locale))
            }
            if (details.isNotEmpty()) append(" · ${details.uppercase(locale)}")
        },
        style = brandedTextStyle(franklin[600], 11.sp, 14.sp, letterSpacing = 1.2.sp).copy(fontWeight = FontWeight.SemiBold),
        color = p.sub,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 12.dp),
    )
}
