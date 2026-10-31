package cx.aswin.boxlore.feature.info.branded.conspiracy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import cx.aswin.boxlore.feature.info.sections.stripHtml
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(topStart = 0.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)

/** How long an unplayed latest episode keeps its NEW stamp. */
private const val NEW_FOR_SECONDS = 7L * 24 * 3600

/** The latest episode filed in its own folder: a dated tab, a stamp, typed title and notes. */
@Composable
internal fun ConspiracySpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val palette = conspiracyPalette()
    val fonts = rememberConspiracyFonts()
    val episode = spotlight.episode
    val filed = remember(episode.publishedDate) { filedDate(episode.publishedDate) }
    // The card is drawn 2dp up over its tab, so the column ends 2dp short and the list keeps its gap.
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        PaperTab(
            stringResource(R.string.conspiracy_latest_filed, filed),
            fonts,
            Modifier.size(width = 214.dp, height = 25.dp),
            grain = true,
        )
        val selectLabel = stringResource(R.string.podcast_episode_select)
        Box(
            Modifier
                .offset(y = (-2).dp)
                .fillMaxWidth()
                .boxShadow(fill = ConspiracyColors.Mustard, color = palette.cardShadow, offsetY = 8.dp, blur = 20.dp, cornerRadius = 20.dp)
                .clip(CardShape)
                .then(if (spotlight.isSelected) Modifier.border(BorderStroke(3.dp, ConspiracyColors.Ink), CardShape) else Modifier)
                .expressiveClickable(shape = CardShape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen),
        ) {
            Image(painterResource(R.drawable.conspiracy_paper_grain), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Column(Modifier.padding(16.dp)) {
                StampLine(spotlight, fonts)
                Text(
                    episode.title,
                    style = brandedTextStyle(fonts.typewriter, 22.sp, 26.sp),
                    color = ConspiracyColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                val notes = remember(episode.description) { stripHtml(episode.description) }
                if (notes.isNotEmpty()) {
                    Text(
                        notes,
                        style = brandedTextStyle(fonts.courier, 13.sp, 18.sp),
                        color = ConspiracyColors.CardText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                spotlight.progress?.let { TypedProgress(it, spotlight.timeLeft, fonts) }
                Spacer(Modifier.height(12.dp))
                BrandedEpisodeActions(spotlight, ConspiracyColors.Actions)
            }
        }
    }
}

/** NEW in red for an unplayed recent episode, PLAYED in ink once heard, then the typed age and length. */
@Composable
private fun StampLine(spotlight: BrandedSpotlight, fonts: ConspiracyFonts) {
    val episode = spotlight.episode
    val now = System.currentTimeMillis() / 1000
    val fresh = !spotlight.isCompleted &&
        spotlight.progress == null &&
        !spotlight.actions.isPlaying &&
        episode.publishedDate > 0 &&
        now - episode.publishedDate <= NEW_FOR_SECONDS
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            spotlight.isCompleted -> Stamp(stringResource(R.string.episode_info_played), ConspiracyColors.Ink, fonts)
            fresh -> Stamp(stringResource(R.string.conspiracy_new), ConspiracyColors.Stamp, fonts)
        }
        Text(
            typedDetails(formatEpisodeRelativeDate(episode.publishedDate), episode.duration),
            style = brandedTextStyle(fonts.courier, 13.sp, 16.sp, letterSpacing = 1.sp).copy(fontWeight = FontWeight.Bold),
            color = ConspiracyColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Stamp(label: String, color: Color, fonts: ConspiracyFonts) {
    Box(
        Modifier
            .rotate(-4f)
            .height(22.dp)
            .border(2.dp, color, RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(Locale.getDefault()), style = brandedTextStyle(fonts.typewriter, 13.sp, 16.sp, letterSpacing = 1.5.sp), color = color, maxLines = 1)
    }
}

/** "2D AGO · 55 MIN", typed. */
internal fun typedDetails(age: String, durationSeconds: Int): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val length = when {
        durationSeconds < 60 -> ""
        hours > 0 -> "$hours HR $minutes MIN"
        else -> "$minutes MIN"
    }
    return listOf(age, length).filter { it.isNotBlank() }.joinToString(" · ").uppercase(Locale.getDefault())
}

/** "28 OCT 2026" for the folder's tab. */
internal fun filedDate(publishedSeconds: Long, locale: Locale = Locale.getDefault()): String =
    if (publishedSeconds <= 0) "" else SimpleDateFormat("d MMM yyyy", locale).format(Date(publishedSeconds * 1000)).uppercase(locale)

@Composable
private fun TypedProgress(progress: Float, timeLeft: String?, fonts: ConspiracyFonts) {
    val animated by animateFloatAsState(progress, label = "conspiracy_spotlight_progress")
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape),
            color = ConspiracyColors.Ink,
            trackColor = ConspiracyColors.Ink.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        if (!timeLeft.isNullOrBlank()) {
            Text(
                timeLeft.uppercase(Locale.getDefault()),
                style = brandedTextStyle(fonts.courier, 12.sp, 16.sp, letterSpacing = 1.sp).copy(fontWeight = FontWeight.Bold),
                color = ConspiracyColors.Ink,
                maxLines = 1,
            )
        }
    }
}
