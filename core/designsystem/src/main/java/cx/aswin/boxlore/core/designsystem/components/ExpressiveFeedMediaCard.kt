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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth

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
    fallbackImageUrls: List<String?>,
    subtitle: String? = null,
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
                            fallbackUrls = fallbackImageUrls,
                            proxyWidth = 400,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                        if (presentation != FeedMediaCardPresentation.ExpressiveFeatured) imageChrome?.invoke(this)
                    }
                },
                title = { titleModifier, leading -> ExpressiveMediaTitle(title, titleModifier, presentation, leading) },
                subtitle = subtitle?.takeIf { it.isNotBlank() }?.let { text -> { ExpressiveMediaSubtitle(text, presentation) } },
                openIndicator = { FeaturedOpenIndicator() },
            )
        }
    }
}

/**
 * Shares the real card's measurement, including the font-scaled three-line title. [showSubtitle]
 * adds the one-line show name slot, for cards that show one.
 */
@Composable
fun FeedMediaCardSkeleton(
    modifier: Modifier = Modifier,
    presentation: FeedMediaCardPresentation = FeedMediaCardPresentation.ExpressivePoster,
    showSubtitle: Boolean = false,
    placeholder: @Composable (Modifier) -> Unit,
) {
    Card(
        modifier = modifier.boxloreCardWidth(
            if (presentation == FeedMediaCardPresentation.ExpressiveFeatured) BoxloreCardFamily.Hero else BoxloreCardFamily.Poster,
        ).clearAndSetSemantics { },
        shape = expressiveCardShape(presentation),
        colors = expressiveCardColors(presentation),
    ) {
        ExpressiveMediaLayout(
            presentation = presentation,
            artwork = placeholder,
            title = { titleModifier, leading ->
                Box(titleModifier) {
                    ExpressiveMediaTitle("", Modifier.fillMaxWidth(), presentation, leading)
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
            subtitle = if (showSubtitle) {
                {
                    Box(contentAlignment = Alignment.CenterStart) {
                        ExpressiveMediaSubtitle("", presentation)
                        val markerHeight = with(LocalDensity.current) { 10.sp.toDp() }
                        placeholder(Modifier.fillMaxWidth(0.5f).height(markerHeight))
                    }
                }
            } else {
                null
            },
            openIndicator = { placeholder(Modifier.size(24.dp)) },
        )
    }
}

@Composable
private fun ExpressiveMediaTitle(title: String, modifier: Modifier, presentation: FeedMediaCardPresentation, leading: Boolean) {
    val style = expressiveMediaTitleStyle(presentation, leading)
    val titleHeight = expressiveMediaTitleHeight(style)
    Box(modifier.height(titleHeight), contentAlignment = if (leading) Alignment.TopStart else Alignment.CenterStart) {
        Text(
            text = title,
            style = style,
            color = LocalContentColor.current,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One line under the title, such as the show an episode comes from. */
@Composable
private fun ExpressiveMediaSubtitle(text: String, presentation: FeedMediaCardPresentation) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        // The featured card sits on tertiary-container, so it keeps the card's own content colour.
        color = if (presentation == FeedMediaCardPresentation.ExpressiveFeatured) {
            LocalContentColor.current
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The card's title style. [leading] is the featured card in a tablet text column of at least
 * [FeaturedLeadingTitleMinWidth]: its title is `titleLarge`, pinned to the top. Everything else keeps
 * the small title.
 */
@Composable
private fun expressiveMediaTitleStyle(presentation: FeedMediaCardPresentation, leading: Boolean = false): TextStyle {
    val featured = presentation == FeedMediaCardPresentation.ExpressiveFeatured
    val base = if (featured && leading) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.titleSmall.copy(
            fontSize = if (featured) 16.sp else 14.sp,
            lineHeight = if (featured) 24.sp else 20.sp,
        )
    }
    return LocalTextStyle.current.merge(
        base.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}

private val FeaturedLeadingTitleMinWidth = 200.dp
private val FeaturedTextPadding = 16.dp
private val FeaturedLeadingTextPadding = 20.dp
private val SubtitleGap = 2.dp

@Composable
private fun expressiveMediaTitleHeight(style: TextStyle): Dp {
    val measured = rememberTextMeasurer().measure(AnnotatedString("Hg\nHg\nHg"), style = style, softWrap = false)
    return with(LocalDensity.current) { measured.size.height.toDp() }
}

/**
 * Artwork plus the same measured three-line foot used by real posters and skeletons, and the show
 * name line when [withSubtitle].
 */
@Composable
fun expressivePosterCardHeight(artworkWidth: Dp, withSubtitle: Boolean = false): Dp {
    val titleHeight = expressiveMediaTitleHeight(expressiveMediaTitleStyle(FeedMediaCardPresentation.ExpressivePoster))
    val measurer = rememberTextMeasurer()
    val subtitleStyle = MaterialTheme.typography.bodySmall
    return with(LocalDensity.current) {
        val subtitlePx = if (withSubtitle) {
            measurer.measure(AnnotatedString(""), style = subtitleStyle, maxLines = 1).size.height + SubtitleGap.roundToPx()
        } else {
            0
        }
        (artworkWidth.roundToPx() + titleHeight.roundToPx() + subtitlePx + 2 * 12.dp.roundToPx()).toDp()
    }
}

@Composable
private fun ExpressiveMediaLayout(
    presentation: FeedMediaCardPresentation,
    artwork: @Composable (Modifier) -> Unit,
    title: @Composable (Modifier, Boolean) -> Unit,
    subtitle: (@Composable () -> Unit)?,
    openIndicator: @Composable () -> Unit,
) {
    if (presentation == FeedMediaCardPresentation.ExpressiveFeatured) {
        val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
        BoxWithConstraints(Modifier.fillMaxWidth().then(if (tablet) Modifier.fillMaxHeight() else Modifier), propagateMinConstraints = tablet) {
            val artworkWidth = (maxWidth * 0.44f).coerceAtMost(176.dp)
            // Phones keep today's card; only tablets with room give the title the lead.
            val leading = tablet && maxWidth - artworkWidth - FeaturedTextPadding * 2 >= FeaturedLeadingTitleMinWidth
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 152.dp),
                verticalAlignment = if (leading) Alignment.Top else Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(if (leading) FeaturedLeadingTextPadding else FeaturedTextPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TitleWithSubtitle(Modifier.fillMaxWidth(), leading, title, subtitle)
                    openIndicator()
                }
                artwork(
                    Modifier.width(artworkWidth).fillMaxHeight()
                        .then(if (tablet) Modifier.testTag("feed_featured_artwork") else Modifier)
                        .clip(RoundedCornerShape(topStart = 72.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 0.dp)),
                )
            }
        }
    } else {
        Column {
            artwork(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)))
            TitleWithSubtitle(Modifier.fillMaxWidth().padding(12.dp), leading = false, title, subtitle)
        }
    }
}

/** Without a subtitle the title takes [modifier] directly, so cards without one keep their height. */
@Composable
private fun TitleWithSubtitle(
    modifier: Modifier,
    leading: Boolean,
    title: @Composable (Modifier, Boolean) -> Unit,
    subtitle: (@Composable () -> Unit)?,
) {
    if (subtitle == null) {
        title(modifier, leading)
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(SubtitleGap)) {
            title(Modifier.fillMaxWidth(), leading)
            subtitle()
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
