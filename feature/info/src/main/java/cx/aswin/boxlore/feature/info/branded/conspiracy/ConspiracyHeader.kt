package cx.aswin.boxlore.feature.info.branded.conspiracy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightButton
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedDescriptionExtras
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import java.util.Locale

/**
 * The evidence board ([EvidenceBoard]) with the publisher typed across its foot and the © beside it,
 * then the description, centred, which opens on tap. The usual chips follow.
 */
@Composable
internal fun ConspiracyHeader(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val palette = conspiracyPalette()
    val fonts = rememberConspiracyFonts()
    val clues = remember(feed) { boardClues(feed.map(Episode::title)) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth()) {
            EvidenceBoard(clues, stringResource(R.string.conspiracy_wordmark_description), palette, fonts)
            PublisherLine(podcast, palette, fonts, Modifier.align(Alignment.BottomCenter))
        }
        if (description.text.isNotEmpty()) {
            Text(
                description.text,
                style = brandedSansStyle(14.5.sp, 21.sp),
                color = palette.textMuted,
                textAlign = TextAlign.Center,
                maxLines = if (description.expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 28.dp, end = 28.dp, top = 8.dp)
                    .fillMaxWidth()
                    .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                    .expressiveClickable(shape = RoundedCornerShape(8.dp)) { description.onToggle() },
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
        Spacer(Modifier.height(if (description.text.isNotEmpty()) 18.dp else 14.dp))
        chips()
    }
}

/** The publisher typed in spaced capitals, the © beside it. */
@Composable
private fun PublisherLine(podcast: Podcast, palette: ConspiracyPalette, fonts: ConspiracyFonts, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = 28.dp).heightIn(min = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            podcast.artist.uppercase(Locale.getDefault()),
            style = brandedTextStyle(fonts.courier, 11.sp, 14.sp, letterSpacing = 1.6.sp).copy(fontWeight = FontWeight.Bold),
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // A long name gives way, so the © keeps its place at the end.
            modifier = Modifier.weight(1f, fill = false),
        )
        BrandedCopyrightButton(palette.copyright, stringResource(R.string.conspiracy_copyright))
    }
}
