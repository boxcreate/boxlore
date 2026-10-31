package cx.aswin.boxlore.feature.info.branded.waveform

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightButton
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedDescriptionExtras
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.skewX
import java.util.Locale

/**
 * Signal: the traced logo across the top over a faint pattern of the show's gadget icons, the
 * wordmark, the show's name, who makes it with the © beside it, then the description, centred.
 * The usual chips follow.
 */
@Composable
internal fun WaveformHeader(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val pattern = painterResource(R.drawable.waveform_pattern)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier.fillMaxWidth().drawBehind {
            // The pattern runs from the top of the screen to just below the publisher line.
            val top = -(statusBar + WaveformPage.headerTop).toPx()
            val left = (size.width - PatternWidth.toPx()) / 2f + 5.5.dp.toPx()
            clipRect(top = top, bottom = 198.dp.toPx()) {
                translate(left, top - 40.dp.toPx()) {
                    with(pattern) { draw(Size(PatternWidth.toPx(), PatternHeight.toPx()), alpha = 0.06f) }
                }
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WaveformLogo()
        Spacer(Modifier.height(20.dp))
        Image(
            painter = painterResource(R.drawable.waveform_wordmark),
            contentDescription = podcast.title,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.size(width = 150.dp, height = 13.5.dp).semantics { heading() },
        )
        Spacer(Modifier.height(6.5.dp))
        Text(
            stringResource(R.string.waveform_subtitle),
            style = brandedSansStyle(15.sp, 20.sp),
            color = WaveformColors.Subtitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(2.dp))
        PublisherLine()
        if (description.text.isNotEmpty()) WaveformDescription(description)
        Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
        Spacer(Modifier.height(if (description.text.isNotEmpty()) 18.dp else 22.dp))
        Box(Modifier.fillMaxWidth()) { chips() }
    }
}

private val PatternWidth = 900.dp

/** Android spaces letters on both sides; the design's spacing trails each letter, so its text starts 1.2dp sooner. */
internal val LetterSpacingLead = (-1.2).dp
private val PatternHeight = 420.dp

/** "MKBHD · VOX MEDIA" in spaced capitals, the © hanging off its end. */
@Composable
private fun PublisherLine() {
    val barlow = rememberWaveformBarlow()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.waveform_publisher).uppercase(Locale.getDefault()),
            style = brandedTextStyle(barlow, 10.5.sp, 14.sp, letterSpacing = 2.4.sp).copy(fontWeight = FontWeight.SemiBold),
            color = WaveformColors.Publisher,
            maxLines = 1,
            modifier = Modifier.offset(x = LetterSpacingLead),
        )
        BrandedCopyrightButton(
            WaveformColors.Copyright,
            stringResource(R.string.waveform_copyright),
            // The design's © is 26dp wide, pulled 4dp in: it takes 22dp, its icon centred 13dp in.
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(22.dp.roundToPx(), placeable.height) { placeable.place((-2).dp.roundToPx(), 0) }
            },
        )
    }
}

/** The description, centred under the publisher line; tapping it opens the full text. */
@Composable
private fun WaveformDescription(description: BrandedDescription) {
    Text(
        text = description.text,
        style = brandedSansStyle(14.sp, 20.sp),
        color = WaveformColors.Description,
        textAlign = TextAlign.Center,
        maxLines = if (description.expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .expressiveClickable(shape = RoundedCornerShape(8.dp)) { description.onToggle() },
    )
}

/** The "boxlore pick" tag: a black pill with a fine ring and the logo's red slash. */
@Composable
internal fun WaveformTag(modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .height(28.dp)
            .clip(shape)
            .background(WaveformColors.Page)
            .border(1.dp, WaveformColors.TagBorder, shape)
            .padding(start = 12.dp, end = 13.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 13.dp).skewX(-22f).background(WaveformColors.Red))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.branded_pick_tag), style = brandedSansStyle(13.sp, 16.sp, weight = 500), color = WaveformColors.Text)
    }
}
