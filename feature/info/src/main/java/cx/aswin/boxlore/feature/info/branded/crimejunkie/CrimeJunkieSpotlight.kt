package cx.aswin.boxlore.feature.info.branded.crimejunkie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.VariableWeights
import cx.aswin.boxlore.feature.info.branded.brandedLength
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.rememberBrandedOswald
import cx.aswin.boxlore.feature.info.sections.stripHtml
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The latest episode as an evidence tag: clipped corners on the left, a dashed tear line and an
 * eyelet, the case type stamped from the title, the name in condensed capitals, the episode's own
 * art and two lines of its description.
 */
@Composable
internal fun CrimeJunkieSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val palette = crimeJunkiePalette()
    val oswald = rememberBrandedOswald()
    val episode = spotlight.episode
    val case = remember(episode.title) { crimeJunkieCaseTitle(episode.title) }
    val selectLabel = stringResource(R.string.podcast_episode_select)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(EvidenceTagShape)
            .background(palette.card)
            .then(if (spotlight.isSelected) Modifier.border(BorderStroke(2.dp, palette.ink), EvidenceTagShape) else Modifier)
            .drawBehind {
                val unit = 1.dp.toPx()
                // The tear line, then the eyelet with the page showing through its hole.
                drawLine(
                    palette.chipLine,
                    Offset(32.75f * unit, 10 * unit),
                    Offset(32.75f * unit, size.height - 10 * unit),
                    strokeWidth = 1.5f * unit,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.5f * unit, 3f * unit)),
                )
                drawCircle(palette.accent, radius = 9 * unit, center = Offset(16 * unit, size.height / 2f))
                drawCircle(palette.page, radius = 5 * unit, center = Offset(16 * unit, size.height / 2f))
            }
            .expressiveClickable(shape = EvidenceTagShape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen),
    ) {
        // The eyelet and tear line are drawn on the left in every language, so the text clears them there too.
        Column(Modifier.absolutePadding(left = 46.dp, top = 14.dp, right = 14.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.branded_latest_episode).uppercase(Locale.getDefault()),
                    style = condensed(oswald, 13.sp, 16.sp, 600, 1.2.sp),
                    color = palette.accent,
                    maxLines = 1,
                )
                Text(
                    spotlightDetails(spotlight),
                    style = condensed(oswald, 12.sp, 16.sp, 500, 0.8.sp),
                    color = palette.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    case.stamp?.let { CaseStamp(it, palette, oswald) }
                    Text(
                        case.name.uppercase(Locale.getDefault()),
                        style = condensed(oswald, 28.sp, 30.sp, 700, 0.3.sp),
                        color = palette.ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        // Screen readers get the episode's own title, stamp and name together, not spelled-out capitals.
                        modifier = Modifier
                            .padding(top = if (case.stamp != null) 7.dp else 0.dp)
                            .clearAndSetSemantics { text = AnnotatedString(episode.title) },
                    )
                }
                OptimizedImage(
                    url = spotlight.episodePicture ?: spotlight.showPicture ?: episode.podcastImageUrl,
                    fallbackUrls = listOf(spotlight.showPicture, episode.podcastImageUrl),
                    proxyWidth = 300,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
            val notes = remember(episode.description) { stripHtml(episode.description) }
            if (notes.isNotEmpty()) {
                Text(
                    notes,
                    style = brandedSansStyle(14.sp, 20.sp),
                    color = palette.sub,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            BrandedEpisodeActions(spotlight, palette.actions, showProgress = true)
        }
    }
}

/** The case type, stamped at a slight angle in a ruled box. */
@Composable
private fun CaseStamp(stamp: String, palette: CrimeJunkiePalette, oswald: VariableWeights) {
    Text(
        stamp.uppercase(Locale.getDefault()),
        style = condensed(oswald, 12.sp, 15.sp, 600, 1.4.sp),
        color = palette.accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clearAndSetSemantics {}
            .rotate(-2f)
            .border(1.5.dp, palette.accent, RoundedCornerShape(2.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** "AIRED OCT 26 · 45 MIN", or that you played it; the progress under the buttons says how long is left. */
@Composable
private fun spotlightDetails(spotlight: BrandedSpotlight): String {
    val episode = spotlight.episode
    val length = if (spotlight.isCompleted) stringResource(R.string.episode_info_played) else brandedLength(episode.duration)
    val aired = airedDate(episode.publishedDate)?.let { stringResource(R.string.crimejunkie_aired, it) }.orEmpty()
    return listOf(aired, length).filter(String::isNotBlank).joinToString(" · ").uppercase(Locale.getDefault())
}

private fun condensed(oswald: VariableWeights, size: TextUnit, lineHeight: TextUnit, weight: Int, letterSpacing: TextUnit): TextStyle =
    brandedTextStyle(oswald[weight], size, lineHeight, letterSpacing = letterSpacing).copy(fontWeight = FontWeight(weight))

/** The tag's outline: corners clipped 26dp on the eyelet's side. */
internal object EvidenceTagShape : Shape {
    private val Clip = 26.dp

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { Clip.toPx() }.coerceAtMost(size.height / 2f)
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

/** A title's case type and the name it names: "MURDERED: Heather Turner". */
internal data class CrimeJunkieCaseTitle(val stamp: String?, val name: String)

/** Longest case type that reads as a stamp; "MYSTERIOUS DEATH OF" is 19 characters. */
private const val MAX_STAMP_LENGTH = 40

/**
 * Splits "LABEL: Name" (99% of the show's titles) into the stamp and the name. Anything else, a
 * special without a label or a colon far into the title, shows whole with no stamp.
 */
internal fun crimeJunkieCaseTitle(title: String): CrimeJunkieCaseTitle {
    val split = title.indexOf(": ")
    if (split in 1..MAX_STAMP_LENGTH) {
        val stamp = title.substring(0, split).trim()
        val name = title.substring(split + 2).trim()
        if (stamp.isNotEmpty() && name.isNotEmpty()) return CrimeJunkieCaseTitle(stamp, name)
    }
    return CrimeJunkieCaseTitle(null, title.trim())
}

/** "Oct 26", or null when the date is unknown. */
internal fun airedDate(publishedSeconds: Long, locale: Locale = Locale.getDefault()): String? =
    if (publishedSeconds <= 0) null else SimpleDateFormat("MMM d", locale).format(Date(publishedSeconds * 1000))
