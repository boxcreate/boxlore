package cx.aswin.boxlore.feature.info.branded.crimejunkie

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import cx.aswin.boxlore.feature.info.branded.BrandedTag
import cx.aswin.boxlore.feature.info.branded.BrandedTagStyle
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import java.util.Locale

/**
 * Fingerprint: the official splatter mark over a fingerprint drawn in code, the wordmark, the
 * publisher with the © beside it, then the description, all centred. The usual chips follow.
 */
@Composable
internal fun CrimeJunkieHeader(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val palette = crimeJunkiePalette()
    Box(modifier.fillMaxWidth()) {
        // The print's 160dp box starts 32dp above the mark and is centred on it.
        Fingerprint(palette.fingerprintAlpha, Modifier.fillMaxWidth().height(CrimeJunkieFingerprint.BOX_HEIGHT.dp).offset(y = (-32).dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(palette.mark), contentDescription = null, modifier = Modifier.size(width = 138.dp, height = 93.dp))
            Spacer(Modifier.height(15.dp))
            Image(
                painter = painterResource(R.drawable.crimejunkie_wordmark),
                contentDescription = stringResource(R.string.crimejunkie_wordmark_description),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(palette.ink),
                modifier = Modifier.size(width = 178.dp, height = 15.9.dp).semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            PublisherLine(podcast, palette)
            if (description.text.isNotEmpty()) CrimeJunkieDescription(description, palette)
            Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
            Spacer(Modifier.height(if (description.text.isNotEmpty()) 14.dp else 16.dp))
            Box(Modifier.fillMaxWidth()) { chips() }
        }
    }
}

@Composable
private fun Fingerprint(alpha: Float, modifier: Modifier) {
    Spacer(
        modifier
            .graphicsLayer {
                this.alpha = alpha
                // Its own layer, so the fades take away only the print.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val unit = 1.dp.toPx()
                val centre = Offset(size.width / 2f, CrimeJunkieFingerprint.CENTRE_Y * unit)
                val ridges = fingerprintRidges(centre, unit)
                onDrawBehind { drawFingerprint(ridges, centre, unit) }
            },
    )
}

/** "AUDIOCHUCK" in spaced capitals, the © hanging off its end. */
@Composable
private fun PublisherLine(podcast: Podcast, palette: CrimeJunkiePalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            podcast.artist.uppercase(Locale.getDefault()),
            style = brandedSansStyle(11.sp, 14.sp, weight = 600).copy(letterSpacing = 1.8.sp),
            color = palette.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // A long name gives way, so the © keeps its place at the end.
            modifier = Modifier.weight(1f, fill = false),
        )
        BrandedCopyrightButton(
            palette.copyright,
            stringResource(R.string.crimejunkie_copyright),
            // The design's © is 26dp wide, pulled 4dp in: it takes 22dp, its icon centred 13dp in.
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(22.dp.roundToPx(), placeable.height) { placeable.place((-2).dp.roundToPx(), 0) }
            },
        )
    }
}

/** The description, centred; tapping it opens the full text. */
@Composable
private fun CrimeJunkieDescription(description: BrandedDescription, palette: CrimeJunkiePalette) {
    Text(
        text = description.text,
        style = brandedSansStyle(14.5.sp, 21.sp),
        color = palette.sub,
        textAlign = TextAlign.Center,
        maxLines = if (description.expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp)
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .expressiveClickable(shape = RoundedCornerShape(8.dp)) { description.onToggle() },
    )
}

/** The tag: ink, with boxlore's mark in the splatter's magenta. */
@Composable
internal fun CrimeJunkieTag(modifier: Modifier) {
    val palette = crimeJunkiePalette()
    BrandedTag(
        BrandedTagStyle(
            fill = palette.tag,
            mark = CrimeJunkieColors.SplatMagenta,
            text = palette.onTag,
            textStyle = brandedSansStyle(13.sp, 16.sp, weight = 600),
            corner = 14.dp,
        ),
        modifier,
    )
}
