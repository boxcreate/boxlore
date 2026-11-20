package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.text.currentLocale
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

private val StoryShape = RoundedCornerShape(12.dp)
private val PictureShape = RoundedCornerShape(12.dp)

/** Seven lines of the notes tall, so the picture and the notes beside it end together. */
private val PictureSize = 147.dp

/** Lines of the episode's notes: beside the picture, or under the title when there is none. */
private const val NOTES_BESIDE_PICTURE = 7
private const val NOTES_WITHOUT_PICTURE = 5

/**
 * The latest episode: "Latest episode" with the day and length, the title, then the episode's own
 * picture, square as the feed has it and shown whole, with its notes beside it. News needs its
 * notes, so they always show. An episode without a picture of its own shows no picture: its notes
 * run under the title instead. The show's dawn stands in while the picture loads or if it fails.
 */
@Composable
internal fun DailySpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val p = DailyColors.current
    val episode = spotlight.episode
    val picture = spotlight.episodePicture
    val franklin = rememberDailyFranklin()
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val notes = remember(episode.description) { stripHtml(episode.description) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(StoryShape)
            .background(if (spotlight.isSelected) p.control else p.page)
            .expressiveClickable(shape = StoryShape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen),
    ) {
        Kicker(spotlight, franklin)
        val (titleSize, titleLine) = if (picture != null) 24 to 28 else 26 to 30
        Text(
            episode.title,
            style = brandedTextStyle(rememberDailySerif(600, titleSize.toFloat()), titleSize.sp, titleLine.sp, letterSpacing = (-0.2).sp),
            color = p.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (picture != null) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Picture(picture)
                if (notes.isNotEmpty()) Notes(notes, NOTES_BESIDE_PICTURE, Modifier.weight(1f))
            }
        } else if (notes.isNotEmpty()) {
            Notes(notes, NOTES_WITHOUT_PICTURE, Modifier.padding(top = 8.dp))
        }
        BrandedEpisodeActions(spotlight, p.actions, Modifier.padding(top = 14.dp), showProgress = true)
    }
}

@Composable
private fun Notes(text: String, lines: Int, modifier: Modifier) {
    Text(
        text,
        style = brandedTextStyle(rememberDailySerif(400, 15f), 15.sp, 21.sp),
        color = DailyColors.current.sub,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The episode's own picture, square and whole, over the show's dawn, which shows until it arrives. */
@Composable
private fun Picture(url: String) {
    Box(Modifier.size(PictureSize).clip(PictureShape).background(DailyColors.dawn())) {
        OptimizedImage(
            url = url,
            proxyWidth = 480,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            errorContent = {},
            showLoadingPlaceholder = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** "LATEST EPISODE · NOV 20, 2026 · 28 MIN": the label in the sky's blue, then the day it came out and its length, or that you played it. */
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
    val length = if (spotlight.isCompleted) stringResource(R.string.episode_info_played) else brandedLength(episode.duration, currentLocale())
    val details = listOf(date, length).filter(String::isNotBlank).joinToString(" · ")
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = p.accent, fontFamily = franklin[700], fontWeight = FontWeight.Bold)) {
                append(stringResource(R.string.branded_latest_episode).uppercase(locale))
            }
            if (details.isNotEmpty()) append(" · ${details.uppercase(locale)}")
        },
        style = brandedTextStyle(franklin[600], 11.sp, 14.sp, letterSpacing = 1.2.sp).copy(fontWeight = FontWeight.SemiBold),
        color = p.sub,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}
