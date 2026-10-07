package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoveryExpressiveTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
internal fun ExpressiveFeedMediaCard(
    imageUrl: String,
    title: String,
    onClick: () -> Unit,
    presentation: FeedMediaCardPresentation,
    modifier: Modifier,
    imageChrome: @Composable (BoxScope.() -> Unit)?,
) {
    DiscoveryExpressiveTheme {
        Card(
            onClick = onClick,
            shape = expressiveCardShape(presentation),
            colors = expressiveCardColors(presentation),
            modifier = modifier.semantics { role = Role.Button },
        ) {
            ExpressiveMediaLayout(
                presentation = presentation,
                artwork = { artworkModifier ->
                    Box(artworkModifier) {
                        OptimizedImage(
                            url = imageUrl,
                            proxyWidth = 400,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                        if (presentation != FeedMediaCardPresentation.ExpressiveFeatured) imageChrome?.invoke(this)
                    }
                },
                title = { titleModifier -> ExpressiveMediaTitle(title, titleModifier, presentation) },
                openIndicator = { FeaturedOpenIndicator() },
            )
        }
    }
}

/** Shares the real card's measurement, including the font-scaled three-line title. */
@Composable
fun FeedMediaCardSkeleton(
    modifier: Modifier = Modifier,
    presentation: FeedMediaCardPresentation = FeedMediaCardPresentation.ExpressivePoster,
    placeholder: @Composable (Modifier) -> Unit,
) {
    Card(
        modifier = modifier.clearAndSetSemantics { },
        shape = expressiveCardShape(presentation),
        colors = expressiveCardColors(presentation),
    ) {
        ExpressiveMediaLayout(
            presentation = presentation,
            artwork = placeholder,
            title = { titleModifier ->
                Box(titleModifier) {
                    ExpressiveMediaTitle("", Modifier.fillMaxWidth(), presentation)
                    val markerHeight = with(LocalDensity.current) { 12.sp.toDp() }
                    Column(Modifier.matchParentSize()) {
                        listOf(0.9f, 0.75f, 0.55f).forEach { fraction ->
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                                placeholder(Modifier.fillMaxWidth(fraction).height(markerHeight))
                            }
                        }
                    }
                }
            },
            openIndicator = { placeholder(Modifier.size(24.dp)) },
        )
    }
}

@Composable
private fun ExpressiveMediaTitle(title: String, modifier: Modifier, presentation: FeedMediaCardPresentation) {
    val featured = presentation == FeedMediaCardPresentation.ExpressiveFeatured
    val style = LocalTextStyle.current.merge(
        MaterialTheme.typography.titleSmall.copy(
            fontSize = if (featured) 16.sp else 14.sp,
            lineHeight = if (featured) 24.sp else 20.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
    // Measure actual paragraphs: nonlinear font scaling and empty-string metrics
    // make a dp/sp multiplication or Text(minLines) insufficient for matching feet.
    val measuredTitle = rememberTextMeasurer().measure(AnnotatedString("Hg\nHg\nHg"), style = style, softWrap = false)
    val titleHeight = with(LocalDensity.current) { measuredTitle.size.height.toDp() }
    Box(modifier.height(titleHeight), contentAlignment = Alignment.CenterStart) {
        Text(
            text = title,
            style = style,
            color = LocalContentColor.current,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExpressiveMediaLayout(
    presentation: FeedMediaCardPresentation,
    artwork: @Composable (Modifier) -> Unit,
    title: @Composable (Modifier) -> Unit,
    openIndicator: @Composable () -> Unit,
) {
    if (presentation == FeedMediaCardPresentation.ExpressiveFeatured) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val artworkWidth = (maxWidth * 0.44f).coerceAtMost(176.dp)
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 152.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    title(Modifier.fillMaxWidth())
                    openIndicator()
                }
                artwork(
                    Modifier.width(artworkWidth).fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 72.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 0.dp)),
                )
            }
        }
    } else {
        Column {
            artwork(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)))
            title(Modifier.fillMaxWidth().padding(12.dp))
        }
    }
}

private fun expressiveCardShape(presentation: FeedMediaCardPresentation) =
    if (presentation == FeedMediaCardPresentation.ExpressiveFeatured) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp)
    } else {
        RoundedCornerShape(24.dp)
    }

@Composable
private fun expressiveCardColors(presentation: FeedMediaCardPresentation) =
    if (presentation == FeedMediaCardPresentation.ExpressiveFeatured) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

@Composable
private fun FeaturedOpenIndicator() {
    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(24.dp))
}
