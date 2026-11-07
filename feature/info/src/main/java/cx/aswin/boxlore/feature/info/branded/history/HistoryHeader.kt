package cx.aswin.boxlore.feature.info.branded.history

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import cx.aswin.boxlore.feature.info.branded.LocalBrandedHeaderMotion
import cx.aswin.boxlore.feature.info.branded.brandedEpisodePicture
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedShowPicture

/**
 * The gallery wall: the show's velvet as a museum wall, hung with its seven latest episodes in
 * antique bronze frames (the newest in the big centre frame, the two before it in ovals beside it,
 * four more small at the sides). David and Churchill from the show's cover stand in front, faces
 * whole, cut only by the window's edges and the wall's bottom edge, the show's logo between them.
 * When the page has settled and the frames' pictures are in, the gallery's lights come on one by one
 * ([GalleryWall]). Below the wall: the description, centred, which opens on tap, and the chips.
 */
@Composable
internal fun HistoryHeader(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val palette = historyPalette()
    val pictures = remember(feed, podcast) { feed.take(HistoryWall.MOMENT_FRAMES).map { brandedEpisodePicture(it, podcast) ?: brandedShowPicture(podcast) } }
    val motion = LocalBrandedHeaderMotion.current
    SideEffect { motion?.pictures = pictures }
    Column(modifier) {
        GalleryWall(pictures, brandedShowPicture(podcast))
        if (description.text.isNotEmpty()) {
            Text(
                description.text,
                style = brandedSansStyle(14.5.sp, 21.sp),
                color = palette.textMuted,
                textAlign = TextAlign.Center,
                maxLines = if (description.expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 28.dp, end = 28.dp, top = 12.dp)
                    .fillMaxWidth()
                    .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                    .expressiveClickable(shape = RoundedCornerShape(8.dp)) { description.onToggle() },
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
        Spacer(Modifier.height(if (description.text.isNotEmpty()) 18.dp else 16.dp))
        chips()
    }
}

/** The publisher's logo with the © beside it, at the foot of the wall. */
@Composable
internal fun PublisherLine(modifier: Modifier) {
    Row(modifier.heightIn(min = 26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.trih_goalhanger),
            stringResource(R.string.trih_publisher_description),
            Modifier.size(width = 66.9.dp, height = 17.dp).alpha(0.92f),
            contentScale = ContentScale.FillBounds,
        )
        BrandedCopyrightButton(historyPalette().copyright, stringResource(R.string.trih_copyright))
    }
}
