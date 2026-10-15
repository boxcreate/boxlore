package cx.aswin.boxlore.core.designsystem.briefing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R

@Composable
internal fun briefingCoverPainter(region: String, editionDate: String): Painter =
    painterResource(briefingCoverResource(region, editionDate))

/** Uncropped landscape artwork; contrast is confined to the masthead and metadata. */
@Composable
@Suppress("LongParameterList")
fun BriefingCover(
    region: String,
    editionDate: String,
    modifier: Modifier = Modifier,
    mastheadTopPadding: Dp = 16.dp,
    mastheadScrim: Boolean = false,
    artworkContentScale: ContentScale = ContentScale.Fit,
    trailing: @Composable BoxScope.() -> Unit = {},
    footer: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier) {
        Image(
            painter = briefingCoverPainter(region, editionDate),
            contentDescription = null,
            contentScale = artworkContentScale,
            modifier = Modifier.fillMaxSize(),
        )
        if (mastheadScrim) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.5f to Color.Black.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            center = Offset(84.dp.toPx(), mastheadTopPadding.toPx() + 32.dp.toPx()),
                            radius = 146.dp.toPx(),
                        ),
                    )
                },
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_boxlore_brief_logo),
            contentDescription = stringResource(R.string.briefing_brand_name),
            colorFilter = ColorFilter.tint(Color.White),
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(AbsoluteAlignment.TopLeft).padding(top = mastheadTopPadding).padding(horizontal = 16.dp)
                .width(136.dp).height(64.dp),
        )
        trailing()
        footer()
    }
}

/** One readable line, leaving the architecture and upper image untouched. */
@Composable
fun BriefingCoverMetadata(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))),
        ).padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
