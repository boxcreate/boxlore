package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightButton
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedDescriptionExtras
import cx.aswin.boxlore.feature.info.branded.BrandedTag
import cx.aswin.boxlore.feature.info.branded.BrandedTagStyle
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

/**
 * Front page: the official wordmark; a double rule; today's dateline with the Times' masthead and
 * the ©; then the description and the usual chips, set like the paper.
 */
@Composable
internal fun DailyHeader(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val p = DailyColors.current
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Image(
                painter = painterResource(R.drawable.daily_wordmark),
                contentDescription = podcast.title,
                colorFilter = ColorFilter.tint(p.ink),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 193.dp, height = 38.dp).semantics { heading() },
            )
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(p.ink))
            Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.ink))
            Dateline()
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.ink))
            if (description.text.isNotEmpty()) DailyDescription(description)
            BrandedDescriptionExtras(description)
        }
        Spacer(Modifier.height(if (description.text.isNotEmpty()) 16.dp else 14.dp))
        Box(Modifier.fillMaxWidth()) { chips() }
    }
}

/** Today's date, turning over at midnight and checked again whenever the page comes back into view. */
@Composable
private fun rememberToday(): State<LocalDate> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(LocalDate.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = LocalDate.now()
                delay(millisUntilNextDay(ZonedDateTime.now()))
            }
        }
    }
}

/** How long from [now] until the next day starts where [now] is, through clock changes. */
internal fun millisUntilNextDay(now: ZonedDateTime): Long =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1)

/** "SATURDAY, OCTOBER 31, 2026" on the left; the Times' masthead and the © on the right. */
@Composable
private fun Dateline() {
    val p = DailyColors.current
    val locale = LocalConfiguration.current.locales[0]
    val date by rememberToday()
    val today = remember(date, locale) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) }
    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            today.uppercase(locale),
            style = brandedTextStyle(rememberDailyFranklin()[600], 10.sp, 12.sp, letterSpacing = 0.8.sp).copy(fontWeight = FontWeight.SemiBold),
            color = p.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.daily_nyt_masthead),
            contentDescription = stringResource(R.string.daily_masthead_description),
            colorFilter = ColorFilter.tint(p.ink),
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.size(width = 93.dp, height = 13.dp),
        )
        BrandedCopyrightButton(
            p.copyright,
            stringResource(R.string.daily_copyright),
            // The design's © is 26dp wide and reaches 6dp past the page margin: it takes 20dp.
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(20.dp.roundToPx(), placeable.height) { placeable.place(-(placeable.width - 26.dp.roundToPx()) / 2, 0) }
            },
        )
    }
}

/** The description, set in the paper's serif; tapping it opens the full text. */
@Composable
private fun DailyDescription(description: BrandedDescription) {
    Text(
        text = description.text,
        style = brandedTextStyle(rememberDailySerif(400, 15f), 15.sp, 21.sp),
        color = DailyColors.current.ink,
        maxLines = if (description.expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            // 12dp on the board; the serif's line box sits 1dp lower here.
            .padding(top = 11.dp)
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .expressiveClickable(shape = RoundedCornerShape(2.dp)) { description.onToggle() },
    )
}

/** The tag: a sharp ink label, boxlore's mark in a blue from the show's dawn. */
@Composable
internal fun DailyTag(modifier: Modifier) {
    val p = DailyColors.current
    BrandedTag(
        BrandedTagStyle(
            fill = p.ink,
            mark = p.tagMark,
            text = p.page,
            textStyle = brandedSansStyle(13.sp, 16.sp, weight = 600),
            height = 26.dp,
            corner = 2.dp,
            endPadding = 11.dp,
            markHeight = 13.dp,
        ),
        modifier,
    )
}
