package cx.aswin.boxlore.feature.info.branded.rottenmango

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
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

/** The room photo's height for its width: the official art cut to the room above her lap. */
private const val PHOTO_ASPECT = 1800f / 2048f

/** How far down the photo the publisher line starts: 280dp into the board's 324.5dp photo. */
private const val PUBLISHER_AT = 280f / 324.5f

/** On the design the photo starts 7dp above the bottom of the status bar. */
private val PhotoTop = (-7).dp

/** A wider window keeps the photo at a phone's size, centred, its sides fading into the page. */
private val PhotoMaxWidth = 480.dp
private val PhotoSideFade = 48.dp

/**
 * Her room: Apple's official show-page photo of Stephanie Soo in her red room, the show's wordmark
 * on the wall, emerging from the dark under the top bar and fading into the page below her lap.
 * Then her name with the © beside it and the description, centred. The usual chips follow.
 */
@Composable
internal fun RottenMangoHeader(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val room = painterResource(R.drawable.rottenmango_room)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val photoWidth = min(maxWidth, PhotoMaxWidth)
        val photoHeight = photoWidth * PHOTO_ASPECT
        Column(
            Modifier.fillMaxWidth().drawBehind { drawRoom(room, photoWidth, photoHeight) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The wordmark is in the photo, so the space it takes is the page's heading.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PhotoTop + photoHeight * PUBLISHER_AT)
                    .semantics {
                        heading()
                        contentDescription = podcast.title
                    },
            )
            PublisherLine(podcast)
            if (description.text.isNotEmpty()) RottenMangoDescription(description)
            Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
            Spacer(Modifier.height(if (description.text.isNotEmpty()) 13.dp else 16.dp))
            Box(Modifier.fillMaxWidth()) { chips() }
        }
    }
}

/** The photo, faded in from the dark at its top and into the page from just above her lap. */
private fun DrawScope.drawRoom(room: Painter, width: Dp, height: Dp) {
    val page = RottenMangoColors.Page
    val w = width.toPx()
    val h = height.toPx()
    val left = (size.width - w) / 2f
    val top = PhotoTop.toPx()
    translate(left, top) { with(room) { draw(Size(w, h)) } }
    // Board: the page's colour at the photo's top edge, 62% 28dp down, clear by 82dp.
    val topFade = 82.dp.toPx()
    drawRect(
        Brush.verticalGradient(0f to page, 28f / 82f to page.copy(alpha = 0.62f), 1f to page.copy(alpha = 0f), startY = top, endY = top + topFade),
        topLeft = Offset(0f, top),
        size = Size(size.width, topFade),
    )
    // Board: from 204dp into the photo, 74% page by 46% of the way and all page by 86%.
    val fadeTop = top + h * (204f / 324.5f)
    val fadeHeight = h * (122f / 324.5f)
    drawRect(
        Brush.verticalGradient(
            0f to page.copy(alpha = 0f),
            0.46f to page.copy(alpha = 0.74f),
            0.86f to page,
            1f to page,
            startY = fadeTop,
            endY = fadeTop + fadeHeight,
        ),
        topLeft = Offset(0f, fadeTop),
        size = Size(size.width, fadeHeight),
    )
    if (w < size.width) {
        val side = PhotoSideFade.toPx()
        drawRect(Brush.horizontalGradient(0f to page, 1f to page.copy(alpha = 0f), startX = left, endX = left + side), Offset(left, top), Size(side, h))
        drawRect(Brush.horizontalGradient(0f to page.copy(alpha = 0f), 1f to page, startX = left + w - side, endX = left + w), Offset(left + w - side, top), Size(side, h))
    }
}

/** "STEPHANIE SOO" in spaced capitals, the © hanging off its end. */
@Composable
private fun PublisherLine(podcast: Podcast) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            podcast.artist.uppercase(Locale.getDefault()),
            style = brandedSansStyle(11.sp, 14.sp, weight = 600).copy(letterSpacing = 1.8.sp),
            color = RottenMangoColors.Sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // A long name gives way, so the © keeps its place at the end.
            modifier = Modifier.weight(1f, fill = false),
        )
        BrandedCopyrightButton(
            RottenMangoColors.Copyright,
            stringResource(R.string.rottenmango_copyright),
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
private fun RottenMangoDescription(description: BrandedDescription) {
    Text(
        text = description.text,
        style = brandedSansStyle(14.5.sp, 21.sp),
        color = RottenMangoColors.Sub,
        textAlign = TextAlign.Center,
        maxLines = if (description.expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 10.dp)
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .expressiveClickable(shape = RoundedCornerShape(8.dp)) { description.onToggle() },
    )
}

/** The tag: dark with a gold ring, boxlore's mark in the couch's gold. */
@Composable
internal fun RottenMangoTag(modifier: Modifier) {
    BrandedTag(
        BrandedTagStyle(
            fill = RottenMangoColors.Page,
            mark = RottenMangoColors.Gold,
            text = RottenMangoColors.Ink,
            textStyle = brandedSansStyle(13.sp, 16.sp, weight = 600),
            corner = 14.dp,
            ring = RottenMangoColors.TagRing,
        ),
        modifier,
    )
}
