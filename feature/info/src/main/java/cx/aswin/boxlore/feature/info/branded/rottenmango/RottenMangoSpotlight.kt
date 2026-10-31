package cx.aswin.boxlore.feature.info.branded.rottenmango

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedLength
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import java.util.Locale

private val FrameShape = RoundedCornerShape(2.dp)

/** Old dark wood with worn highlights, lit from the top left. */
private val Wood = arrayOf(
    0f to Color(0xFF2A1F17),
    0.14f to Color(0xFF5A4730),
    0.30f to Color(0xFF1E1610),
    0.50f to Color(0xFF4A3A27),
    0.68f to Color(0xFF17110C),
    0.86f to Color(0xFF5B4831),
    1f to Color(0xFF21180F),
)

/** The gilt lip inside the wood, tarnished. */
private val Lip = arrayOf(0f to Color(0xFF6E5A38), 0.5f to Color(0xFF3B2E1C), 1f to Color(0xFF7A6440))

/** The plate's brass, kept light enough behind its engraving for 4.5:1, darkening only below the text. */
private val Brass = arrayOf(0f to Color(0xFFBCA46D), 0.7f to RottenMangoColors.PlateBrass, 1f to Color(0xFF6F5C35))

/**
 * The latest episode as an old, darkened frame from her wall: "Latest episode" in dull red, the
 * title typed inside like a case file and the episode cards' own buttons under it, its number on a
 * tarnished brass plate hung from the frame.
 */
@Composable
internal fun RottenMangoSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val typewriter = rememberRottenMangoTypewriter()
    val noise = rememberTapeNoise()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .boxShadow(fill = Color(0xFF2A1F17), color = Color(0xB3000000), offsetY = 10.dp, blur = 22.dp, cornerRadius = 2.dp)
                .clip(FrameShape)
                .drawBehind { drawFrame() }
                .then(if (spotlight.isSelected) Modifier.border(2.dp, RottenMangoColors.Ink, FrameShape) else Modifier)
                .expressiveClickable(shape = FrameShape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen)
                .padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().drawBehind { drawDiagonal(Lip) }.padding(2.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind { drawInside(noise) }
                        // Room at the bottom for the plate hung over the frame's edge.
                        .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 16.dp),
                ) {
                    LatestLine(spotlight, typewriter)
                    Text(
                        spotlight.episode.title,
                        style = brandedTextStyle(typewriter, 18.5.sp, 23.sp),
                        color = RottenMangoColors.FrameTitle,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    BrandedEpisodeActions(spotlight, RottenMangoColors.Actions, Modifier.padding(top = 12.dp), showProgress = true)
                }
            }
        }
        Plate(
            spotlight.episode,
            typewriter,
            // Hung 12dp over the frame's bottom edge.
            Modifier.align(Alignment.CenterHorizontally).layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = 12.dp.roundToPx()
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
            },
        )
    }
}

/** "LATEST EPISODE · 2D AGO · 48 MIN" typed small, the first words in dull red. */
@Composable
private fun LatestLine(spotlight: BrandedSpotlight, typewriter: FontFamily) {
    val latest = stringResource(R.string.branded_latest_episode).uppercase(Locale.getDefault())
    val details = latestDetails(spotlight).uppercase(Locale.getDefault())
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = RottenMangoColors.FrameRed)) { append(latest) }
            if (details.isNotEmpty()) append(" · $details")
        },
        style = brandedTextStyle(typewriter, 12.sp, 13.sp, letterSpacing = 0.6.sp),
        color = RottenMangoColors.FrameLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** When it came out and how long it is, or that you played it; the progress under the buttons says how long is left. */
@Composable
private fun latestDetails(spotlight: BrandedSpotlight): String {
    val episode = spotlight.episode
    val length = if (spotlight.isCompleted) stringResource(R.string.episode_info_played) else brandedLength(episode.duration)
    return listOf(formatEpisodeRelativeDate(episode.publishedDate), length).filter(String::isNotBlank).joinToString(" · ")
}

/** "No. 556" between two rivets on the brass plate, or "Bonus" for the few episodes the feed leaves unnumbered. */
@Composable
private fun Plate(episode: Episode, typewriter: FontFamily, modifier: Modifier) {
    val number = episode.episodeNumber
    val text = if (number != null) stringResource(R.string.rottenmango_number, number) else stringResource(R.string.rottenmango_bonus)
    val spoken = if (number != null) stringResource(R.string.rottenmango_number_description, number) else text
    val engraving = with(LocalDensity.current) { Shadow(Color(0x4DE6CD96), Offset(0f, 1.dp.toPx()), 0f) }
    Row(
        modifier
            .height(24.dp)
            .boxShadow(fill = Color(0xFF6F5C35), color = Color(0xB3000000), offsetY = 2.dp, blur = 5.dp, cornerRadius = 2.dp)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind { drawPlate() }
            .padding(horizontal = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Rivet()
        Text(text, style = brandedTextStyle(typewriter, 13.sp, 16.sp, letterSpacing = 0.8.sp).copy(shadow = engraving), color = RottenMangoColors.PlateText, maxLines = 1)
        Rivet()
    }
}

@Composable
private fun Rivet() {
    Box(Modifier.size(4.dp).drawBehind { drawCircle(RottenMangoColors.PlateDot) })
}

/** CSS's 135deg: from the top left corner to the bottom right one. */
private fun DrawScope.drawDiagonal(stops: Array<Pair<Float, Color>>) {
    val half = (size.width + size.height) * 0.70710677f / 2f
    val reach = Offset(0.70710677f, 0.70710677f) * half
    drawRect(Brush.linearGradient(*stops, start = center - reach, end = center + reach))
}

/** The wood, a black inner edge and a faint worn highlight along its top. */
private fun DrawScope.drawFrame() {
    drawDiagonal(Wood)
    val line = 1.dp.toPx()
    drawRect(Color(0xD9000000), style = androidx.compose.ui.graphics.drawscope.Stroke(width = line * 2))
    drawRect(Color(0x38AA8C5A), topLeft = Offset(line, line), size = Size(size.width - line * 2, line))
}

/** The dark inside: a glow up and to the left, faint grain, and shadow sinking in from every edge. */
private fun DrawScope.drawInside(noise: ImageBitmap) {
    clipRect {
        val glowAt = Offset(size.width * 0.4f, size.height * 0.3f)
        val radiusX = size.width * 1.1f
        scale(scaleX = 1f, scaleY = size.height / radiusX, pivot = glowAt) {
            drawRect(
                Brush.radialGradient(0f to Color(0xFF221010), 0.6f to Color(0xFF160809), 1f to Color(0xFF0E0506), center = glowAt, radius = radiusX),
                topLeft = Offset(0f, glowAt.y - radiusX * 2),
                size = Size(size.width, radiusX * 4),
            )
        }
        drawGrain(noise, alpha = 0.07f)
        // CSS inset 0 0 18px: about 42% at the edge, gone 18dp in.
        val reach = 18.dp.toPx()
        val sink = arrayOf(0f to Color(0x6B000000), 0.33f to Color(0x29000000), 1f to Color.Transparent)
        drawRect(Brush.verticalGradient(*sink, startY = 0f, endY = reach), size = Size(size.width, reach))
        drawRect(Brush.verticalGradient(*sink, startY = size.height, endY = size.height - reach), topLeft = Offset(0f, size.height - reach), size = Size(size.width, reach))
        drawRect(Brush.horizontalGradient(*sink, startX = 0f, endX = reach), size = Size(reach, size.height))
        drawRect(Brush.horizontalGradient(*sink, startX = size.width, endX = size.width - reach), topLeft = Offset(size.width - reach, 0f), size = Size(reach, size.height))
    }
}

/** Brass with a bright top edge and a dark bottom one. */
private fun DrawScope.drawPlate() {
    drawRect(Brush.verticalGradient(*Brass))
    val line = 1.dp.toPx()
    drawRect(Color(0x73DCC896), size = Size(size.width, line))
    drawRect(Color(0xB31E1408), topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
}
