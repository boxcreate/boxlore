package cx.aswin.boxlore.feature.info.branded.desicrime

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import java.util.Locale

/**
 * Their crime board ([DesiCrimeBoard]), the hosts' names with the © beside them, then the
 * description, centred, which opens on tap. The usual chips follow.
 */
@Composable
internal fun DesiCrimeHeader(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val board = remember(feed, podcast) { DesiCrimeFeed.board(feed, podcast) }
    Column(modifier) {
        DesiCrimeBoard(board)
        HostsLine(Modifier.align(Alignment.CenterHorizontally).padding(top = 1.dp))
        if (description.text.isNotEmpty()) {
            Text(
                description.text,
                style = brandedSansStyle(14.5.sp, 21.sp),
                color = DesiCrimeColors.Muted,
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

/** ARYAAN MISRA & AISHWARYA SINGH, spaced out, with the © after it; the same space before keeps the names centred. */
@Composable
private fun HostsLine(modifier: Modifier) {
    Row(modifier.heightIn(min = 26.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Spacer(Modifier.width(30.dp))
        Text(
            stringResource(R.string.desicrime_hosts).uppercase(Locale.getDefault()),
            style = brandedSansStyle(10.5.sp, 13.sp, weight = 700).copy(letterSpacing = 1.8.sp),
            color = DesiCrimeColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        BrandedCopyrightButton(DesiCrimeColors.Copyright, stringResource(R.string.desicrime_copyright))
    }
}
