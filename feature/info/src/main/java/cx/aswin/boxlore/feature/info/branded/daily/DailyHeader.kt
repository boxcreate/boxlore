package cx.aswin.boxlore.feature.info.branded.daily

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneTopInset
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightButton
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedDescriptionExtras
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.daily.DailyHorizon.drawFirstLight

/** The © button's width; an empty space as wide sits before the masthead so the masthead stays centred. */
private val CopyrightWidth = 26.dp

/**
 * First light: the cover's dawn sky from the top of the screen, with the Times' masthead (© beside
 * it) and the official wordmark in white, centred under the seal. The sun peeks over the middle of
 * the horizon, and the page rises into the sky as the curve of a planet ([DailyHorizon]). On the page:
 * the description, centred, which opens on tap, then the usual chips.
 */
@Composable
internal fun DailyHeader(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val p = DailyColors.current
    val statusBar = boxlorePaneTopInset()
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind { drawFirstLight(skyTop = -(statusBar + DailyPage.headerTop).toPx(), page = p.page) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(CopyrightWidth))
            Image(
                painter = painterResource(R.drawable.daily_nyt_masthead),
                contentDescription = stringResource(R.string.daily_masthead_description),
                colorFilter = ColorFilter.tint(Color.White),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(width = 100.dp, height = 14.dp),
            )
            BrandedCopyrightButton(p.copyright.copy(icon = Color.White), stringResource(R.string.daily_copyright))
        }
        Image(
            painter = painterResource(R.drawable.daily_wordmark),
            contentDescription = podcast.title,
            colorFilter = ColorFilter.tint(Color.White),
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.padding(top = 4.dp).size(width = 214.dp, height = 42.dp).semantics { heading() },
        )
        // The sky, the sun on the horizon and a little page, from the wordmark's foot to the description.
        Spacer(Modifier.height(DailyHorizon.PageTop - DailyHorizon.LockupHeight))
        if (description.text.isNotEmpty()) DailyDescription(description)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
        Spacer(Modifier.height(16.dp))
        chips()
    }
}

/** The description, centred in the paper's serif; tapping it opens the full text. */
@Composable
private fun DailyDescription(description: BrandedDescription) {
    Text(
        text = description.text,
        style = brandedTextStyle(rememberDailySerif(400, 15f), 15.sp, 22.sp),
        color = DailyColors.current.sub,
        textAlign = TextAlign.Center,
        maxLines = if (description.expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .expressiveClickable(shape = RoundedCornerShape(8.dp)) { description.onToggle() },
    )
}
