package cx.aswin.boxlore.feature.info.branded.vergecast

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.BrandedTightText
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.rememberBrandedAnton
import cx.aswin.boxlore.feature.info.components.episodeDetailsLine
import cx.aswin.boxlore.feature.info.components.formatEpisodeDuration
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import cx.aswin.boxlore.feature.info.sections.stripHtml

private val CardShape = RoundedCornerShape(28.dp)

/** "Latest episode" over an ultraviolet card, with The Verge's sticker slapped on its corner. */
@Composable
internal fun VergecastSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val palette = vergecastPalette()
    val anton = rememberBrandedAnton()
    Box(modifier.fillMaxWidth()) {
        // The sticker overlaps the space above the heading; its solid edge stops 4dp above the title.
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 2.dp)) {
            val heading = stringResource(R.string.branded_latest_episode)
            val accentStart = heading.lastIndexOf(' ').takeIf { it > 0 } ?: heading.length
            BrandedTightText(
                text = buildAnnotatedString {
                    append(heading.substring(0, accentStart))
                    withStyle(SpanStyle(color = palette.accentOnPage)) { append(heading.substring(accentStart)) }
                },
                style = brandedTextStyle(anton, 27.sp, 30.sp, letterSpacing = 0.2.sp),
                color = palette.text,
                modifier = Modifier.padding(start = 18.dp).semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            SpotlightCard(spotlight, anton, Modifier.padding(horizontal = 16.dp))
        }
        // The sticker image already carries its 5° lean and shadow, so it lands exactly as designed.
        Image(
            painter = painterResource(palette.sticker),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-37).dp).size(width = 206.dp, height = 199.dp),
        )
    }
}

@Composable
private fun SpotlightCard(spotlight: BrandedSpotlight, anton: FontFamily, modifier: Modifier) {
    val episode = spotlight.episode
    val selectLabel = stringResource(R.string.podcast_episode_select)
    Surface(
        shape = CardShape,
        color = VergecastColors.Ultraviolet,
        contentColor = Color.White,
        border = if (spotlight.isSelected) BorderStroke(3.dp, VergecastColors.Lime) else null,
        modifier = modifier.fillMaxWidth().expressiveClickable(
            shape = CardShape,
            onLongClickLabel = selectLabel,
            onLongClick = spotlight.onLongPress,
            onClick = spotlight.onOpen,
        ),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp)) {
            val highlight = SpanStyle(color = VergecastColors.Lime, fontFamily = rememberGoogleSansFamily(weight = 600, opticalSize = 13f))
            Text(
                text = episodeDetailsLine(
                    parts = listOf(
                        formatEpisodeRelativeDate(episode.publishedDate),
                        formatEpisodeDuration(episode.duration),
                        if (spotlight.isCompleted) stringResource(R.string.episode_info_played) else "",
                    ),
                    highlightIndex = 0,
                    highlight = highlight,
                ),
                style = brandedSansStyle(13.sp, 18.sp),
                color = VergecastColors.CardText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(24.dp))
            BrandedTightText(
                text = AnnotatedString(episode.title),
                style = brandedTextStyle(anton, 31.sp, 34.sp, letterSpacing = 0.1.sp),
                maxLines = 3,
            )
            val description = remember(episode.description) { stripHtml(episode.description) }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = brandedSansStyle(14.sp, 19.sp),
                    color = VergecastColors.CardText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            spotlight.progress?.let { SpotlightProgress(it, spotlight.timeLeft) }
            Spacer(Modifier.height(11.dp))
            BrandedEpisodeActions(spotlight, VergecastColors.Actions)
        }
    }
}

@Composable
private fun SpotlightProgress(progress: Float, timeLeft: String?) {
    val animated by animateFloatAsState(progress, label = "vergecast_spotlight_progress")
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            color = VergecastColors.Lime,
            trackColor = VergecastColors.DeepUltraviolet,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        if (!timeLeft.isNullOrBlank()) {
            Text(timeLeft, style = brandedSansStyle(12.sp, 16.sp), color = VergecastColors.CardText, maxLines = 1)
        }
    }
}
