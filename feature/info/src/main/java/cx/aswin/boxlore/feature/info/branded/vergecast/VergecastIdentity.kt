package cx.aswin.boxlore.feature.info.branded.vergecast

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightButton
import cx.aswin.boxlore.feature.info.branded.BrandedTag
import cx.aswin.boxlore.feature.info.branded.BrandedTagStyle
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.skewX

/** Cover art beside The Vergecast wordmark, with The Verge's logo and the © button under it. */
@Composable
internal fun VergecastIdentity(podcast: Podcast, modifier: Modifier) {
    val palette = vergecastPalette()
    Row(modifier.padding(bottom = 14.dp)) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = listOf(podcast.fallbackImageUrl, podcast.latestEpisode?.imageUrl),
            proxyWidth = 400,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.padding(top = 2.dp).size(104.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Image(
                painter = painterResource(palette.wordmark),
                contentDescription = stringResource(R.string.vergecast_wordmark_description),
                contentScale = ContentScale.FillBounds,
                // 196 by 83dp, shrinking in proportion when a narrow window leaves less room.
                modifier = Modifier.widthIn(max = 196.dp).fillMaxWidth().aspectRatio(196f / 83f).semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(palette.publisherLogo),
                    contentDescription = stringResource(R.string.vergecast_publisher_description),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(width = 80.dp, height = 19.dp),
                )
                BrandedCopyrightButton(palette.copyright, stringResource(R.string.vergecast_copyright), Modifier.padding(start = 2.dp))
            }
        }
    }
}

/** The tag: a leaning lime label, like The Verge's stickers, with boxlore's mark in ultraviolet. */
@Composable
internal fun VergecastTag(modifier: Modifier) {
    BrandedTag(
        BrandedTagStyle(
            fill = VergecastColors.Lime,
            mark = VergecastColors.Ultraviolet,
            text = VergecastColors.Ink,
            textStyle = brandedSansStyle(14.sp, 16.sp, weight = 600),
            height = 30.dp,
            corner = 2.dp,
            markHeight = 15.dp,
        ),
        modifier.skewX(-8f),
    )
}
