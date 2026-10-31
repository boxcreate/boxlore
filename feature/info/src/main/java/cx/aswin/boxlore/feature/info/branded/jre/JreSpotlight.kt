package cx.aswin.boxlore.feature.info.branded.jre

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.BrandedTightText
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.jre.JreColors.drawCurtain
import cx.aswin.boxlore.feature.info.branded.jre.JreColors.drawStageLight
import cx.aswin.boxlore.feature.info.branded.rememberBrandedAnton
import cx.aswin.boxlore.feature.info.branded.rememberBrandedOswald
import cx.aswin.boxlore.feature.info.components.formatEpisodeDuration
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import java.util.Locale
import kotlin.math.abs

private val PanelShape = RoundedCornerShape(18.dp)
private val PictureShape = RoundedCornerShape(11.dp)

/** The episode pictures are 1500 square with the template's strip in the top 270: the rest is shown. */
private const val PICTURE_ASPECT = 1500f / 1230f

/** On a wide window the poster keeps a phone's proportions, centred under the badge. */
private val MaxCardWidth = 480.dp

/** The picture's width on the design board, which the no-picture type is sized against. */
private val BoardPictureWidth = 321.dp

/**
 * The latest episode as the show's own thumbnail: the guest in the studio with the number and name
 * already on it, the template's top strip trimmed off, in a warm raised panel so its black edges
 * never melt into the page, with the episode cards' own buttons inside it. A black label in the
 * template's style says it is the latest. An episode without a picture yet gets its number and
 * guest set the same way on the curtain.
 */
@Composable
internal fun JreSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val episode = spotlight.episode
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val details = latestDetails(spotlight)
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp).wrapContentWidth().widthIn(max = MaxCardWidth)) {
        Column(
            Modifier
                .fillMaxWidth()
                .boxShadow(fill = JreColors.Panel, color = JreColors.PanelShadow, offsetY = 10.dp, blur = 24.dp, cornerRadius = 18.dp)
                .border(if (spotlight.isSelected) BorderStroke(2.dp, JreColors.Ink) else BorderStroke(1.dp, JreColors.PanelRing), PanelShape)
                .clip(PanelShape)
                .expressiveClickable(shape = PanelShape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen)
                .padding(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PICTURE_ASPECT)
                    .clip(PictureShape)
                    .background(JreColors.Picture)
                    // The title, label and progress are drawn in the picture, so screen readers get them here.
                    .clearAndSetSemantics {
                        text = AnnotatedString(episode.title)
                        stateDescription = details
                        spotlight.progress?.let { progressBarRangeInfo = ProgressBarRangeInfo(it, 0f..1f) }
                    },
            ) {
                // The number and guest on the curtain: shown with no picture of the episode's own, while
                // its picture loads, and if it never does. The picture covers it once it arrives.
                NoPicture(episode)
                spotlight.episodePicture?.let { picture -> WholePicture(picture, partway = spotlight.progress != null) }
                spotlight.progress?.let { WatchedBar(it) }
                LatestLabel(details)
            }
            BrandedEpisodeActions(spotlight, JreColors.Actions, Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * The episode's picture whole, its title included: the label's black strip runs across the top, as the
 * template's own does, and the progress bar has its own line at the bottom, so neither covers the
 * picture. Scaled to fit between them, it leaves the sides to a blurred, darkened copy of itself.
 */
@Composable
private fun WholePicture(picture: String, partway: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(LabelHeight).background(JreColors.Label))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            EpisodePicture(picture, Modifier.matchParentSize().blur(18.dp), colorFilter = ColorFilter.tint(SideShade, BlendMode.SrcAtop))
            Box(Modifier.fillMaxHeight().aspectRatio(PICTURE_ASPECT).align(Alignment.Center).clipToBounds()) {
                // The square picture hangs from the bottom, so the template's strip falls off the top.
                EpisodePicture(
                    picture,
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                        .aspectRatio(1f)
                        .align(Alignment.BottomCenter),
                )
            }
        }
        if (partway) Box(Modifier.fillMaxWidth().height(WatchedBarHeight).background(JreColors.Label))
    }
}

@Composable
private fun EpisodePicture(url: String, modifier: Modifier, colorFilter: ColorFilter? = null) {
    OptimizedImage(
        url = url,
        proxyWidth = 700,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter,
        errorContent = {},
        showLoadingPlaceholder = false,
        modifier = modifier,
    )
}

/** The blurred copy at the picture's sides, darkened so the picture itself leads. */
private val SideShade = Color(0x99000000)
private val LabelHeight = 32.dp
private val WatchedBarHeight = 4.dp

/** How far you are, as a thin orange bar along the picture's bottom edge, the way a watched video's thumbnail shows it. */
@Composable
private fun BoxScope.WatchedBar(progress: Float) {
    val shown by animateFloatAsState(progress, label = "jre_spotlight_progress")
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(WatchedBarHeight).background(JreColors.WatchedTrack)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown.coerceIn(0f, 1f)).background(JreColors.Orange))
    }
}

/** "LATEST EPISODE  2D AGO · 3H 4M" on the template's black, hung from the picture's corner. */
@Composable
private fun BoxScope.LatestLabel(details: String) {
    val oswald = rememberBrandedOswald()
    val style = brandedTextStyle(oswald[600], 16.sp, 19.sp, letterSpacing = 0.9.sp).copy(fontWeight = FontWeight.SemiBold)
    Row(
        Modifier
            .align(Alignment.TopStart)
            .height(LabelHeight)
            .background(JreColors.Label, RoundedCornerShape(bottomEnd = 10.dp))
            .padding(start = 12.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.branded_latest_episode).uppercase(Locale.getDefault()), style = style, color = JreColors.Orange, maxLines = 1)
        Text(details.uppercase(Locale.getDefault()), style = style, color = JreColors.LabelMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** When it came out and how long it is; how long is left while you are partway; or that you played it. */
@Composable
private fun latestDetails(spotlight: BrandedSpotlight): String {
    val episode = spotlight.episode
    val length = when {
        spotlight.isCompleted -> stringResource(R.string.episode_info_played)
        spotlight.progress != null && !spotlight.timeLeft.isNullOrBlank() -> spotlight.timeLeft
        else -> formatEpisodeDuration(episode.duration)
    }
    return listOf(formatEpisodeRelativeDate(episode.publishedDate), length).filter(String::isNotBlank).joinToString(" · ")
}

/**
 * No picture yet: the number in white and the guest in orange, set as the template sets them, on the
 * curtain. Like the picture it stands in for, it scales with the card, not the font size setting;
 * screen readers get the title from the card.
 */
@Composable
private fun NoPicture(episode: Episode) {
    val anton = rememberBrandedAnton()
    val title = remember(episode.title) { jreThumbnailTitle(episode.title) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCurtain()
            drawStageLight(Offset(size.width * 0.72f, size.height * 0.4f), radiusX = size.width * 0.55f, radiusY = size.height * 0.7f, strength = 0.2f, reach = 0.7f)
            drawRect(Brush.verticalGradient(*CurtainShadeStops))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val unit = maxWidth / BoardPictureWidth
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            fun type(board: Float) = with(density) { (board * unit).dp.toSp() }
            fun nameStyle(scale: Float) = brandedTextStyle(anton, type(48f * scale), type(50f * scale), letterSpacing = type(0.2f * scale))
            val name = remember(title.name) { jreGuestLines(title.name).uppercase(Locale.getDefault()) }
            val nameWidth = constraints.maxWidth - with(density) { (30.dp * unit).roundToPx() }
            // Two guests are set smaller, as the pictures set them, so each line fits whole.
            val nameScale = remember(name, nameWidth, unit, anton) { fittingScale(measurer, name, nameWidth, ::nameStyle) }
            // Measured without the height left below the number, so a long name is clipped by the picture, not squeezed.
            Column(Modifier.padding(start = 15.dp * unit, end = 15.dp * unit, top = maxHeight * 0.39f).wrapContentHeight(Alignment.Top, unbounded = true)) {
                title.number?.let {
                    BrandedTightText(AnnotatedString(it), brandedTextStyle(anton, type(55f), type(51f), letterSpacing = type(0.2f)), color = Color.White, maxLines = 1)
                }
                BrandedTightText(AnnotatedString(name), nameStyle(nameScale), color = JreColors.Orange, maxLines = 2)
            }
        }
    }
}

/**
 * Black over the curtain, top to bottom: a little under the label, none behind the number, then
 * deepening behind the guest so the orange name holds 3:1 even on the brightest fold in the light.
 */
internal val CurtainShade = listOf(0f to 0.35f, 0.25f to 0f, 0.45f to 0.45f, 1f to 0.72f)
private val CurtainShadeStops = CurtainShade.map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }.toTypedArray()

/** The smallest share of the guest's size the curtain goes down to before it lets the name end in "…". */
private const val MIN_NAME_SCALE = 0.55f

/**
 * The largest share of the board's size, in 5% steps, at which [name] sets each of its lines whole
 * across [width] pixels: a line that would wrap, or a third line, makes it smaller.
 */
private fun fittingScale(measurer: TextMeasurer, name: String, width: Int, style: (Float) -> TextStyle): Float {
    val lines = name.count { it == '\n' } + 1
    var scale = 1f
    while (scale > MIN_NAME_SCALE) {
        val layout = measurer.measure(AnnotatedString(name), style(scale), maxLines = 2, constraints = Constraints(maxWidth = width.coerceAtLeast(1)))
        if (layout.lineCount <= lines && !layout.hasVisualOverflow) return scale
        scale -= 0.05f
    }
    return MIN_NAME_SCALE
}

/** A title's number and guest, as the episode pictures set them: "#2573 - Legion of Skanks". */
internal data class JreThumbnailTitle(val number: String?, val name: String)

private val Numbered = Regex("""^(#\d+)\s+-\s+(.+)$""")
private val MmaShow = Regex("""^JRE MMA Show (#\d+)\s+(?:with|-)\s+(.+)$""", RegexOption.IGNORE_CASE)

/**
 * "#2573 - Legion of Skanks" (9 in 10 titles) and "JRE MMA Show #187 with Robert Whittaker" split
 * into the number and the guest; any other title is the name, with no number.
 */
internal fun jreThumbnailTitle(title: String): JreThumbnailTitle {
    val trimmed = title.trim()
    val match = Numbered.matchEntire(trimmed) ?: MmaShow.matchEntire(trimmed)
    return match?.let { JreThumbnailTitle(it.groupValues[1], it.groupValues[2].trim()) } ?: JreThumbnailTitle(null, trimmed)
}

/**
 * The guest over two lines as the pictures set them, broken at the space that best balances the
 * lines: "Legion / of Skanks", "Dr. Andrew / Huberman". Ten letters or fewer stay on one line.
 */
internal fun jreGuestLines(name: String): String {
    if (name.length <= 10) return name
    val breakAt = name.indices.filter { name[it] == ' ' }.minByOrNull { abs(2 * it + 1 - name.length) } ?: return name
    return name.substring(0, breakAt) + "\n" + name.substring(breakAt + 1)
}
