package cx.aswin.boxlore.feature.info.branded.sherlock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedArtNow
import cx.aswin.boxlore.feature.info.branded.brandedDetails
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate

/**
 * The latest episode as the story's opening page in the Strand: the episode's own picture as its
 * plate, with when it came out and how long it is (or that it was played) under it; Haité's
 * headpiece, the story in small capitals ([SherlockFeed.shortStory]), the part and the reader in
 * italic, a double rule, and the description's first paragraph under a drop cap, cut short with an
 * ellipsis. A long story name steps down a size onto two lines and the text gives up a line. The
 * episode cards' buttons follow.
 */
@Composable
internal fun SherlockSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val episode = spotlight.episode
    val fonts = rememberStrandFonts()
    val title = remember(episode.title) { SherlockFeed.shortStory(SherlockFeed.story(episode.title)) }
    val part = remember(episode.title) { SherlockFeed.part(episode.title) }
    val notes = remember(episode.description) { SherlockFeed.firstParagraph(episode.description) }
    val long = SherlockFeed.isLongTitle(title)
    val selectLabel = stringResource(R.string.podcast_episode_select)
    val shape = RoundedCornerShape(3.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(SherlockColors.Panel)
            .then(if (spotlight.isSelected) Modifier.border(3.dp, SherlockColors.Text, shape) else Modifier)
            .expressiveClickable(shape = shape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen)
            .padding(10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PAGE_HEIGHT)
                .boxShadow(fill = SherlockColors.Paper, color = SherlockColors.Shadow, offsetY = 6.dp, blur = 12.dp, cornerRadius = 0.dp)
                .paper(),
        ) {
            Column(Modifier.width(PLATE_COLUMN).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Plate(spotlight)
                Text(
                    brandedDetails(formatEpisodeRelativeDate(episode.publishedDate), episode.duration, spotlight.isCompleted, stringResource(R.string.episode_info_played)),
                    style = brandedTextStyle(fonts.italic, 10.sp, 12.sp),
                    color = SherlockColors.Italic,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp, bottom = 10.dp),
                )
            }
            Column(Modifier.weight(1f).padding(top = 7.dp, end = 10.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.sherlock_fleuron),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp, 21.dp).alpha(FLEURON_ALPHA),
                    contentScale = ContentScale.FillBounds,
                )
                Text(
                    "$title.",
                    style = if (long) {
                        brandedTextStyle(fonts.smallCaps, 15.sp, 17.sp, 1.sp)
                    } else {
                        brandedTextStyle(fonts.smallCaps, 18.sp, 20.sp, 1.4.sp)
                    },
                    color = SherlockColors.Print,
                    textAlign = TextAlign.Center,
                    maxLines = if (long) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    // Screen readers hear the episode's own title.
                    modifier = Modifier.clearAndSetSemantics {
                        heading()
                        text = AnnotatedString(episode.title)
                    },
                )
                Text(
                    part?.let { stringResource(R.string.sherlock_part_read_by, it) } ?: stringResource(R.string.sherlock_read_by),
                    style = brandedTextStyle(fonts.italic, 11.sp, 13.sp),
                    color = SherlockColors.Italic,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp).clearAndSetSemantics {},
                )
                DoubleRule(Modifier.padding(top = 4.dp).width(120.dp).height(3.dp))
                if (notes.isNotEmpty()) {
                    DropCapNotes(
                        notes = notes,
                        lines = if (long) 2 else 3,
                        fonts = fonts,
                        modifier = Modifier.padding(top = 5.dp).fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BrandedEpisodeActions(spotlight, SherlockColors.Actions, showProgress = true)
    }
}

/** The episode's own picture, tipped in as a plate with a thin ink rule round it. */
@Composable
private fun Plate(spotlight: BrandedSpotlight) {
    Box(
        Modifier
            .size(PLATE)
            .border(0.8.dp, SherlockColors.Ink)
            .background(SherlockColors.PaperOuter)
            .padding(3.3.dp),
    ) {
        OptimizedImage(
            url = spotlight.episodePicture ?: spotlight.showPicture,
            fallbackUrls = listOf(spotlight.showPicture),
            proxyWidth = 240,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(PLATE - 6.6.dp),
        )
    }
}

/**
 * The description under a drop cap: the cap is the first letter, the rest of the first three words
 * follow in small capitals, and the text runs beside the cap, [lines] lines in all, ending in an
 * ellipsis when there is more.
 */
@Composable
private fun DropCapNotes(notes: String, lines: Int, fonts: StrandFonts, modifier: Modifier) {
    val styled = remember(notes, fonts) {
        val words = notes.split(' ')
        val lead = words.take(LEAD_WORDS).joinToString(" ").drop(1)
        val rest = words.drop(LEAD_WORDS).joinToString(" ")
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = fonts.smallCaps, letterSpacing = 0.4.sp)) { append(lead) }
            if (rest.isNotEmpty()) {
                append(" ")
                append(rest)
            }
        }
    }
    // Screen readers hear the paragraph as written.
    Row(modifier.clearAndSetSemantics { text = AnnotatedString(notes) }) {
        DropCap(notes.take(1), if (lines == 2) 26.dp else 29.dp, fonts, Modifier.padding(top = if (lines == 2) 1.dp else 2.dp, end = CAP_GAP))
        Text(
            styled,
            style = brandedTextStyle(fonts.roman, 11.6.sp, 14.sp).copy(color = SherlockColors.Body),
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The drop cap: the letter in small capitals, cream on an ink square with a thin flourished frame. */
@Composable
private fun DropCap(letter: String, size: Dp, fonts: StrandFonts, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .background(SherlockColors.Ink)
            .drawBehind {
                // The frame and flourishes, drawn in the design's 29-unit box.
                val unit = this.size.width / CAP_BOX
                val stroke = Stroke(width = 0.7f * unit)
                drawRect(SherlockColors.DropCapOrnament, Offset(1.6f * unit, 1.6f * unit), Size(25.8f * unit, 25.8f * unit), alpha = ORNAMENT_ALPHA, style = stroke)
                drawPath(capFlourish(unit), SherlockColors.DropCapOrnament, alpha = ORNAMENT_ALPHA, style = stroke)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = brandedTextStyle(fonts.smallCaps, (size.value * 0.9f).sp, (size.value * 0.97f).sp),
            color = SherlockColors.DropCapLetter,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun capFlourish(unit: Float): Path = Path().apply {
    fun m(x: Float, y: Float) = moveTo(x * unit, y * unit)
    fun c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = cubicTo(x1 * unit, y1 * unit, x2 * unit, y2 * unit, x * unit, y * unit)
    m(3f, 25f)
    c(6f, 20f, 3f, 16f, 7f, 13f)
    c(9f, 11.6f, 10f, 13.4f, 8.6f, 14.4f)
    m(26f, 4f)
    c(23f, 9f, 26f, 13f, 22f, 16f)
    c(20f, 17.4f, 19f, 15.6f, 20.4f, 14.6f)
    m(3f, 5f)
    c(6f, 5f, 7f, 7f, 6f, 9f)
    m(26f, 24f)
    c(23f, 24f, 22f, 22f, 23f, 20f)
}

/** The double rule under the reader's line. */
@Composable
private fun DoubleRule(modifier: Modifier) {
    Spacer(
        modifier.drawBehind {
            val unit = 1.dp.toPx()
            drawLine(SherlockColors.Rule, Offset(0f, 0.4f * unit), Offset(size.width, 0.4f * unit), strokeWidth = 0.8f * unit)
            drawLine(SherlockColors.Rule, Offset(0f, size.height - 0.25f * unit), Offset(size.width, size.height - 0.25f * unit), strokeWidth = 0.5f * unit)
        },
    )
}

/** The page's cream, darker at the gutter, with a fine grain. */
@Composable
private fun Modifier.paper(): Modifier {
    val grain = brandedArtNow(R.drawable.sherlock_paper_grain)
    val brush = remember(grain) { ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)) }
    return drawBehind {
        drawRect(
            Brush.horizontalGradient(
                0f to SherlockColors.PaperGutter,
                0.05f to SherlockColors.PaperEdge,
                0.4f to SherlockColors.Paper,
                1f to SherlockColors.PaperOuter,
            ),
        )
        drawRect(brush)
    }
}

private val PAGE_HEIGHT = 138.dp
private val PLATE_COLUMN = 96.dp
private val PLATE = 80.dp
private val CAP_GAP = 6.dp
private const val CAP_BOX = 29f
private const val LEAD_WORDS = 3
private const val FLEURON_ALPHA = 0.85f
private const val ORNAMENT_ALPHA = 0.75f
