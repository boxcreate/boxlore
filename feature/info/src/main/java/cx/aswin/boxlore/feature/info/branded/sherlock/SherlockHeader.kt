package cx.aswin.boxlore.feature.info.branded.sherlock

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

/**
 * The Strand on the desk ([StrandDesk]) with the © at the desk's right edge, then the description,
 * centred, which opens on tap. The usual chips follow.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun SherlockHeader(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val contents = remember(feed) { SherlockFeed.contents(feed) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth()) {
            StrandDesk(contents)
            BrandedCopyrightButton(
                SherlockColors.Copyright,
                stringResource(R.string.sherlock_copyright),
                Modifier.align(Alignment.BottomEnd).padding(end = 2.dp),
            )
        }
        if (description.text.isNotEmpty()) {
            Text(
                description.text,
                style = brandedSansStyle(14.5.sp, 21.sp),
                color = SherlockColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = if (description.expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 28.dp, end = 28.dp, top = 2.dp)
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
