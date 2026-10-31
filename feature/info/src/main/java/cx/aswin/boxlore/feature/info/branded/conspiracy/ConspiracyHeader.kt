package cx.aswin.boxlore.feature.info.branded.conspiracy

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightButton
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedDescriptionExtras
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import java.util.Locale

private val FileShape = RoundedCornerShape(topStart = 0.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
private val TabShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)

/**
 * A case file on mustard paper: a tab, the cover clipped on like a photo, the stamped wordmark,
 * who it is from with the © beside it, and the show's description typed on a strip that opens on
 * tap. The usual chips follow below the file.
 */
@Composable
internal fun ConspiracyHeader(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val palette = conspiracyPalette()
    val fonts = rememberConspiracyFonts()
    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            PaperTab(stringResource(R.string.conspiracy_case_file), fonts, Modifier.size(width = 132.dp, height = 25.dp))
            // The file overlaps the tab's last 2dp, so the two read as one folder.
            Box(
                Modifier
                    .padding(top = 23.dp)
                    .fillMaxWidth()
                    .heightIn(min = 130.dp)
                    .boxShadow(fill = ConspiracyColors.Mustard, color = palette.fileShadow, offsetY = 10.dp, blur = 24.dp, cornerRadius = 20.dp)
                    .clip(FileShape),
            ) {
                Image(painterResource(R.drawable.conspiracy_paper), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                ClippedCover(podcast, palette)
                Image(
                    painter = painterResource(R.drawable.conspiracy_wordmark),
                    contentDescription = stringResource(R.string.conspiracy_wordmark_description),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.offset(x = 136.dp, y = 16.dp).size(width = 182.dp, height = 57.dp).semantics { heading() },
                )
                FromLine(podcast, palette, fonts, Modifier.padding(start = 140.dp, top = 82.dp, end = 14.dp))
                if (description.text.isNotEmpty()) CaseNotes(description, fonts, Modifier.padding(start = 14.dp, end = 14.dp, top = 130.dp, bottom = 10.dp))
            }
        }
        // The locked-feed notice and recommended shows open below the file, on the page.
        Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
        Spacer(Modifier.height(18.dp))
        chips()
    }
}

/** A paper tab with its label typed in spaced capitals. */
@Composable
internal fun PaperTab(label: String, fonts: ConspiracyFonts, modifier: Modifier, grain: Boolean = false) {
    Box(modifier.clip(TabShape), contentAlignment = Alignment.Center) {
        Image(
            painterResource(if (grain) R.drawable.conspiracy_paper_grain else R.drawable.conspiracy_paper),
            null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Text(
            label.uppercase(Locale.getDefault()),
            style = brandedTextStyle(fonts.courier, 11.sp, 13.sp, letterSpacing = 1.5.sp).copy(fontWeight = FontWeight.Bold),
            color = ConspiracyColors.Ink,
            maxLines = 1,
        )
    }
}

/** The cover as a photo clipped to the file: a white-bordered print, tilted, with a paperclip. */
@Composable
private fun ClippedCover(podcast: Podcast, palette: ConspiracyPalette) {
    Box(
        Modifier
            .offset(x = 18.dp, y = 16.dp)
            .size(104.dp)
            .rotate(-3f)
            .boxShadow(fill = ConspiracyColors.Polaroid, color = palette.polaroidShadow, offsetY = 6.dp, blur = 14.dp, cornerRadius = 0.dp)
            .padding(4.dp),
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = listOf(podcast.fallbackImageUrl, podcast.latestEpisode?.imageUrl),
            proxyWidth = 300,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp),
        )
    }
    Image(
        painter = painterResource(R.drawable.conspiracy_paperclip),
        contentDescription = null,
        modifier = Modifier.offset(x = 34.dp, y = 2.dp).size(width = 18.dp, height = 44.dp).rotate(-3f),
    )
}

/** "From" over the publisher, typed, with the © hanging just below the line. */
@Composable
private fun FromLine(podcast: Podcast, palette: ConspiracyPalette, fonts: ConspiracyFonts, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                stringResource(R.string.conspiracy_from).uppercase(Locale.getDefault()),
                style = brandedTextStyle(fonts.courier, 10.sp, 12.sp, letterSpacing = 1.6.sp).copy(fontWeight = FontWeight.Bold),
                color = ConspiracyColors.FromLabel,
            )
            Text(
                podcast.artist,
                style = brandedTextStyle(fonts.typewriter, 15.sp, 19.sp),
                color = ConspiracyColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(2.dp))
        BrandedCopyrightButton(palette.copyright, stringResource(R.string.conspiracy_copyright))
    }
}

/** The description typed on a lighter strip; tapping it opens the full text. */
@Composable
private fun CaseNotes(description: BrandedDescription, fonts: ConspiracyFonts, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .boxShadow(fill = ConspiracyColors.Strip, color = StripShadow, offsetY = 1.dp, blur = 2.dp, cornerRadius = 2.dp)
            .clip(RoundedCornerShape(2.dp))
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .expressiveClickable(shape = RoundedCornerShape(2.dp)) { description.onToggle() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            description.text,
            style = brandedTextStyle(fonts.courier, 12.5.sp, 17.sp),
            color = ConspiracyColors.StripText,
            maxLines = if (description.expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val StripShadow = Color(0x33000000)

/** The "boxlore pick" tag: a mustard label stuck on at a slight angle. */
@Composable
internal fun ConspiracyTag(modifier: Modifier) {
    val palette = conspiracyPalette()
    Row(
        modifier
            .rotate(-2f)
            .height(28.dp)
            .boxShadow(fill = ConspiracyColors.Mustard, color = palette.tagShadow, offsetY = 3.dp, blur = 8.dp, cornerRadius = 2.dp)
            .padding(start = 9.dp, end = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_branded_pick), null, Modifier.size(17.dp), tint = ConspiracyColors.Ink)
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(R.string.branded_pick_tag),
            style = brandedSansStyle(14.sp, 16.sp, weight = 600),
            color = ConspiracyColors.Ink,
            textAlign = TextAlign.Center,
        )
    }
}
