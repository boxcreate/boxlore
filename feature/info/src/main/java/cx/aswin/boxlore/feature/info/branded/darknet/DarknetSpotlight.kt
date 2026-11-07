package cx.aswin.boxlore.feature.info.branded.darknet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import cx.aswin.boxlore.feature.info.sections.stripHtml
import java.util.Locale

/**
 * The latest episode on a charred card: a scorched top edge and an ember glow at its foot,
 * "// LATEST · EP 182", the episode's name in Anton capitals, when it came out and how long it is,
 * two lines of its notes, and its own illustration taped up beside them like a flyer, tilted, that
 * straightens while you press the card. The episode cards' buttons follow.
 */
@Composable
internal fun DarknetSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val fonts = rememberDarknetFonts()
    val episode = spotlight.episode
    val title = remember(episode.title, episode.episodeNumber) { darknetTitle(episode.title, episode.episodeNumber) }
    var pressed by remember { mutableStateOf(false) }
    val straighten by animateFloatAsState(if (pressed) 1f else 0f, tween(200), label = "darknet_flyer")
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val label = title.number?.let { stringResource(R.string.darknet_latest_number, it) } ?: stringResource(R.string.darknet_latest)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .clip(Charred)
            .drawBehind {
                drawRect(DarknetColors.Card)
                // Embers glowing up from the card's foot.
                val centre = Offset(size.width / 2f, size.height * 1.15f)
                scale(scaleX = 1f, scaleY = 0.6f * size.height / (0.9f * size.width), pivot = centre) {
                    drawCircle(
                        Brush.radialGradient(0f to Color(0x59E03C1B), 0.7f to Color(0x00E03C1B), center = centre, radius = 0.9f * size.width),
                        radius = 0.9f * size.width * 3f,
                        center = centre,
                    )
                }
            }
            .then(if (spotlight.isSelected) Modifier.border(3.dp, DarknetColors.Text, Charred) else Modifier)
            .expressiveClickable(shape = Charred, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen)
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = DarknetColors.Slash)) { append("//") }
                        append(" ")
                        append(label.uppercase(Locale.getDefault()))
                    },
                    style = brandedTextStyle(fonts.black, 10.sp, 13.sp, letterSpacing = 1.2.sp),
                    color = DarknetColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title.headline.uppercase(Locale.getDefault()),
                    style = brandedTextStyle(fonts.anton, 32.sp, 35.sp, letterSpacing = 0.5.sp),
                    color = DarknetColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // Shown in capitals; screen readers hear the episode's own title.
                    modifier = Modifier.padding(top = 3.dp).clearAndSetSemantics { text = AnnotatedString(episode.title) },
                )
                Text(
                    listOf(formatEpisodeRelativeDate(episode.publishedDate), brandedLength(episode.duration))
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                        .uppercase(Locale.getDefault()),
                    style = brandedTextStyle(fonts.bold, 10.sp, 13.sp, letterSpacing = 1.sp),
                    color = DarknetColors.Muted,
                    maxLines = 1,
                )
                val notes = remember(episode.description) { stripHtml(episode.description) }
                if (notes.isNotEmpty()) {
                    Text(
                        notes,
                        style = brandedSansStyle(12.5.sp, 17.sp),
                        color = DarknetColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Flyer(spotlight, straighten)
        }
        Spacer(Modifier.height(12.dp))
        BrandedEpisodeActions(spotlight, DarknetColors.Actions, showProgress = true)
    }
}

/** The episode's illustration on white paper, taped up at a tilt; it straightens as [straighten] goes to 1. */
@Composable
private fun Flyer(spotlight: BrandedSpotlight, straighten: Float) {
    Box(
        Modifier
            .padding(top = 2.dp, end = 4.dp)
            .size(108.dp)
            .graphicsLayer {
                rotationZ = -4f * (1f - straighten)
                scaleX = 1f + 0.03f * straighten
                scaleY = scaleX
            },
    ) {
        Box(
            Modifier
                .size(108.dp)
                .boxShadow(fill = DarknetColors.Flyer, color = Color(0x99000000), offsetY = 8.dp, blur = 14.dp, cornerRadius = 0.dp)
                .padding(3.dp),
        ) {
            OptimizedImage(
                url = spotlight.episodePicture ?: spotlight.showPicture,
                fallbackUrls = listOf(spotlight.showPicture),
                proxyWidth = 300,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(102.dp),
            )
        }
        Spacer(
            Modifier
                .offset(x = 36.dp, y = (-7).dp)
                .size(width = 40.dp, height = 13.dp)
                .rotate(3f)
                .boxShadow(fill = DarknetColors.Tape, color = Color(0x4D000000), offsetY = 1.dp, blur = 2.dp, cornerRadius = 0.dp),
        )
    }
}

/** The card's scorched top edge, burnt in an uneven line up to 5% of its height deep. */
private val Charred = GenericShape { size, _ ->
    val edge = listOf(
        0f to 4f, 6f to 1f, 11f to 5f, 17f to 0f, 24f to 3f, 31f to 0f, 38f to 4f, 45f to 1f,
        52f to 5f, 60f to 0f, 67f to 3f, 74f to 0f, 81f to 4f, 88f to 1f, 94f to 5f, 100f to 2f,
    )
    edge.forEachIndexed { i, (x, y) ->
        // As the design cuts it: both in shares of the card's width and height.
        val px = x / 100f * size.width
        val py = y / 100f * size.height
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}
