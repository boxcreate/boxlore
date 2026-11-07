package cx.aswin.boxlore.feature.info.branded.desicrime

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.brandedDetails
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.rememberBrandedArt
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import kotlin.math.roundToInt

/**
 * The latest case written up on a sticky note pinned to a strip of their board: its number and
 * "newest case" in red pen, the case's title in their hand ([DesiCrimeFeed.case]), and when it came
 * out and how long it is, or that it was played. The episode cards' buttons follow on the cork.
 */
@Composable
internal fun DesiCrimeSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val episode = spotlight.episode
    val fonts = rememberDesiCrimeFonts()
    val case = remember(episode.title) { DesiCrimeFeed.case(episode.title) }
    val cork by rememberBrandedArt(R.drawable.desicrime_cork)
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .drawBehind {
                drawRect(DesiCrimeColors.Cork)
                cork?.firstOrNull()?.let { bitmap ->
                    // The board's cork at its own scale, centred, under the lamp's last warmth and shadow.
                    val scale = size.width.coerceAtLeast(CORK_WIDTH.dp.toPx()) / bitmap.width
                    val width = (bitmap.width * scale).roundToInt()
                    val height = (bitmap.height * scale).roundToInt()
                    drawImage(bitmap, dstOffset = IntOffset(((size.width - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()), dstSize = IntSize(width, height))
                }
                drawRect(Brush.radialGradient(0f to CardLamp, 1f to CardShadow, center = Offset(size.width / 2f, size.height * 0.2f), radius = size.width * 0.9f))
                drawRect(DesiCrimeColors.Frame, style = Stroke(4.dp.toPx()))
                drawRect(FrameShade, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            .then(if (spotlight.isSelected) Modifier.border(3.dp, DesiCrimeColors.Text, shape) else Modifier)
            .expressiveClickable(shape = shape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 11.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { rotationZ = -0.8f }
                .drawWithContent {
                    drawContent()
                    drawBrassPin(Offset(size.width / 2f, 2.dp.toPx()), 11.dp.toPx())
                },
        ) {
            StickyPaper(Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 8.dp)) {
                Text(
                    case.number?.let { stringResource(R.string.desicrime_newest_case, it) } ?: stringResource(R.string.desicrime_newest),
                    style = brandedTextStyle(fonts.handBold, 13.sp, 15.sp),
                    color = DesiCrimeColors.RedPen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    case.main,
                    style = brandedTextStyle(fonts.handBold, 18.5.sp, 20.sp),
                    color = DesiCrimeColors.NoteInk,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // Screen readers hear the episode's own title.
                    modifier = Modifier.padding(top = 3.dp).clearAndSetSemantics {
                        heading()
                        text = AnnotatedString(episode.title)
                    },
                )
                Text(
                    brandedDetails(formatEpisodeRelativeDate(episode.publishedDate), episode.duration, spotlight.isCompleted, stringResource(R.string.episode_info_played)),
                    style = brandedTextStyle(fonts.hand, 13.sp, 15.sp),
                    color = DesiCrimeColors.NoteDetails,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        BrandedEpisodeActions(spotlight, DesiCrimeColors.Actions, showProgress = true)
    }
}

private const val CORK_WIDTH = 369f
private val CardLamp = Color(0x1AFFAA5A)
private val CardShadow = Color(0x94120802)
private val FrameShade = Color(0x66000000)
