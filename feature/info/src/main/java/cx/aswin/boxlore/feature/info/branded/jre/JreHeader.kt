package cx.aswin.boxlore.feature.info.branded.jre

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import cx.aswin.boxlore.feature.info.branded.LocalBrandedHeaderMotion
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.jre.JreColors.drawCurtain
import cx.aswin.boxlore.feature.info.branded.jre.JreColors.drawStageLight
import java.util.Locale

/** Where the curtain ends below the top of the header; it has faded into the page a little before. */
private val CurtainBottom = 196.dp
private val BadgeSize = 112.dp

/**
 * The studio: the red velvet curtain from the top of the screen, lit warm behind the official round
 * badge, then the host with the © beside it and the description, all centred. The usual chips follow.
 */
@Composable
internal fun JreHeader(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier.fillMaxWidth().drawBehind {
            val top = -(statusBar + JrePage.headerTop).toPx()
            val bottom = CurtainBottom.toPx()
            val height = bottom - top
            drawCurtain(top, height)
            drawStageLight(Offset(size.width / 2f, top + height * 0.4f), radiusX = 214.dp.toPx(), radiusY = 132.dp.toPx(), strength = 0.26f)
            drawRect(
                Brush.verticalGradient(
                    0f to JreColors.Page.copy(alpha = 0.82f),
                    0.26f to JreColors.Page.copy(alpha = 0f),
                    0.52f to JreColors.Page.copy(alpha = 0f),
                    0.96f to JreColors.Page,
                    1f to JreColors.Page,
                    startY = top,
                    endY = bottom,
                ),
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, height),
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Badge()
        Spacer(Modifier.height(10.dp))
        PublisherLine(podcast)
        if (description.text.isNotEmpty()) JreDescription(description)
        Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
        Spacer(Modifier.height(if (description.text.isNotEmpty()) 11.dp else 14.dp))
        Box(Modifier.fillMaxWidth()) { chips() }
    }
}

/** The official badge, its third eye lighting up with the subscribe moment. */
@Composable
private fun Badge() {
    val motion = LocalBrandedHeaderMotion.current
    Box(Modifier.size(BadgeSize)) {
        Image(
            painter = painterResource(R.drawable.jre_badge),
            contentDescription = stringResource(R.string.jre_badge_description),
            modifier = Modifier
                .fillMaxSize()
                .boxShadow(fill = Color.Black, color = BadgeShadow, offsetY = 6.dp, blur = 14.dp, cornerRadius = BadgeSize / 2)
                .semantics { heading() },
        )
        Canvas(Modifier.fillMaxSize()) {
            val millis = motion?.subscribeMomentMillis?.invoke() ?: return@Canvas
            drawBadgeEye(Offset(size.width * JreEye.BADGE_EYE_X, size.height * JreEye.BADGE_EYE_Y), millis)
        }
    }
}

private val BadgeShadow = Color(0x8C000000)

/** "JOE ROGAN" in spaced capitals, the © hanging off its end. */
@Composable
private fun PublisherLine(podcast: Podcast) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            podcast.artist.uppercase(Locale.getDefault()),
            style = brandedSansStyle(11.sp, 14.sp, weight = 600).copy(letterSpacing = 1.8.sp),
            color = JreColors.Sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // A long name gives way, so the © keeps its place at the end.
            modifier = Modifier.weight(1f, fill = false),
        )
        BrandedCopyrightButton(
            JreColors.Copyright,
            stringResource(R.string.jre_copyright),
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
private fun JreDescription(description: BrandedDescription) {
    Text(
        text = description.text,
        style = brandedSansStyle(14.5.sp, 21.sp),
        color = JreColors.Sub,
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

/** The tag: dark with a warm ring, boxlore's mark in the badge's orange. */
@Composable
internal fun JreTag(modifier: Modifier) {
    BrandedTag(
        BrandedTagStyle(
            fill = JreColors.Page,
            mark = JreColors.Orange,
            text = JreColors.Ink,
            textStyle = brandedSansStyle(13.sp, 16.sp, weight = 600),
            corner = 14.dp,
            ring = JreColors.TagRing,
        ),
        modifier,
    )
}
