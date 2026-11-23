package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentHeightDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneStatusBarsPadding
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.sections.ShowHeaderForm
import cx.aswin.boxlore.feature.info.sections.ShowHeaderLayout
import cx.aswin.boxlore.feature.info.sections.showHeaderLayout

internal const val SHOW_PAGE_SKELETON_TAG = "podcast_info_skeleton"
internal const val EPISODE_CARD_SKELETON_TAG = "podcast_info_episode_skeleton"
internal const val EPISODE_TITLE_ROW_SKELETON_TAG = "podcast_info_episode_title_row_skeleton"
internal const val SHOW_CAUGHT_UP_TAG = "podcast_info_caught_up"

/** One loading block in the page's own tones; the moving highlight follows the enclosing [ShimmerScope]. */
@Composable
private fun Block(width: Dp?, height: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val sized = if (width == null) modifier.fillMaxWidth() else modifier.width(width)
    Box(sized.height(height).m3Shimmer(colors.surfaceContainerHigh, colors.surfaceContainerLowest, shape = shape))
}

/** Loading lines for text in [style], in the page's own tones and as tall as [lines] lines of it. */
@Composable
private fun TextLines(style: TextStyle, lines: Int, widths: List<Float> = listOf(0.9f, 0.6f)) {
    val colors = MaterialTheme.colorScheme
    SkeletonTextLines(style, lines, widths = widths, baseColor = colors.surfaceContainerHigh, highlightColor = colors.surfaceContainerLowest)
}

/**
 * A loading episode card in [EpisodeListItem]'s geometry at any text size: the picture, a two-line
 * title, the details line and a two-line description, then the connected action row (three 48dp
 * toggles and Play, which stops at the card's cap). Cards 680dp or wider take the one-line form.
 */
@Composable
internal fun EpisodeCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag(EPISODE_CARD_SKELETON_TAG).clearAndSetSemantics {},
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ShimmerScope(active = true) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= WideEpisodeCardMinWidth) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Block(88.dp, 88.dp, MaterialTheme.shapes.medium)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) { CardTextLines() }
                        Spacer(Modifier.width(16.dp))
                        ActionRowSkeleton(Modifier.width(ActionRowWideWidth), fixedPlay = true)
                    }
                } else {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Block(76.dp, 76.dp, MaterialTheme.shapes.medium)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { CardTextLines() }
                        }
                        Spacer(Modifier.height(14.dp))
                        ActionRowSkeleton(Modifier.fillMaxWidth(), fixedPlay = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTextLines() {
    val typography = MaterialTheme.typography
    TextLines(typography.titleMedium, 2)
    Spacer(Modifier.height(4.dp))
    TextLines(typography.labelMedium, 1, listOf(0.45f))
    Spacer(Modifier.height(4.dp))
    TextLines(typography.bodySmall.copy(lineHeight = 16.sp), 2, listOf(1f, 0.7f))
}

/** The card's connected actions: like, download and queue, then Play. */
@Composable
private fun ActionRowSkeleton(modifier: Modifier, fixedPlay: Boolean) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Block(48.dp, 48.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 24.dp))
        Block(48.dp, 48.dp, RoundedCornerShape(8.dp))
        Block(48.dp, 48.dp, RoundedCornerShape(8.dp))
        val playShape = RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
        if (fixedPlay) Block(120.dp, 48.dp, playShape) else Block(null, 48.dp, playShape, Modifier.weight(1f, fill = false).widthIn(max = ListPlayMaxWidth))
    }
}

/** Three 48dp toggles, Play at 120dp and the gaps between them. */
private val ActionRowWideWidth = 48.dp * 3 + 120.dp + 3.dp * 3

/** A loading row in [EpisodeTitleRow]'s geometry: a title line, a details line and the round play button. */
@Composable
internal fun EpisodeTitleRowSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag(EPISODE_TITLE_ROW_SKELETON_TAG).clearAndSetSemantics {},
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ShimmerScope(active = true) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Block(null, 16.dp, RoundedCornerShape(8.dp), Modifier.padding(end = 24.dp))
                    Block(120.dp, 11.dp, RoundedCornerShape(6.dp))
                }
                Spacer(Modifier.width(8.dp))
                Block(42.dp, 42.dp, RoundedCornerShape(21.dp), Modifier.padding(3.dp))
            }
        }
    }
}

/** Loading rows in the shape the list will take: title rows for Episode titles only, else episode cards. */
@Composable
internal fun EpisodeListSkeleton(titlesOnly: Boolean, modifier: Modifier = Modifier) {
    if (titlesOnly) EpisodeTitleRowSkeleton(modifier) else EpisodeCardSkeleton(modifier)
}

/**
 * Opening a show: the page in its own shape (cover, title, chips, description, buttons and
 * episode cards) while it loads, instead of a lone loader in an empty page. The header takes the
 * loaded page's form ([showHeaderLayout]) for this pane, so nothing jumps when the show arrives.
 */
@Composable
internal fun ShowPageSkeleton(modifier: Modifier = Modifier, titlesOnly: Boolean = false) {
    val loading = stringResource(R.string.podcast_info_loading)
    val pageHeight = boxloreContentHeightDp()
    val side = infoReadingSidePadding(boxloreContentWidthDp().dp)
    ShimmerScope(active = true) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .boxlorePaneStatusBarsPadding()
                .boxloreHorizontalSafePadding()
                .padding(horizontal = side),
        ) {
            val header = showHeaderLayout(widthDp = maxWidth.value, pageHeightDp = pageHeight)
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = false)
                    .padding(top = 80.dp)
                    .testTag(SHOW_PAGE_SKELETON_TAG)
                    .clearAndSetSemantics { contentDescription = loading },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (header.form == ShowHeaderForm.Stacked) StackedHeaderSkeleton(header.coverSize) else HorizontalHeaderSkeleton(header)
                ToolbarSkeleton()
                Column(Modifier.padding(top = 14.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (titlesOnly) repeat(TITLE_ROW_SKELETONS) { EpisodeTitleRowSkeleton() } else repeat(2) { EpisodeCardSkeleton() }
                }
            }
        }
    }
}

@Composable
private fun StackedHeaderSkeleton(coverSize: androidx.compose.ui.unit.Dp) {
    Block(coverSize, coverSize, MaterialTheme.shapes.extraLarge, Modifier.testTag(SHOW_PAGE_SKELETON_COVER_TAG))
    Block(240.dp, 28.dp, RoundedCornerShape(10.dp), Modifier.padding(top = 22.dp))
    Block(120.dp, 16.dp, RoundedCornerShape(8.dp), Modifier.padding(top = 12.dp))
    ChipSkeletons(Modifier.padding(top = 16.dp))
    DescriptionSkeleton(Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp))
}

@Composable
private fun HorizontalHeaderSkeleton(header: ShowHeaderLayout) {
    val wide = header.form == ShowHeaderForm.Wide
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = if (wide) Alignment.Top else Alignment.CenterVertically) {
            Block(header.coverSize, header.coverSize, MaterialTheme.shapes.extraLarge, Modifier.testTag(SHOW_PAGE_SKELETON_COVER_TAG))
            Spacer(Modifier.width(if (wide) 24.dp else 16.dp))
            Column(Modifier.weight(1f)) {
                Block(null, 28.dp, RoundedCornerShape(10.dp), Modifier.padding(end = 24.dp))
                Block(120.dp, 16.dp, RoundedCornerShape(8.dp), Modifier.padding(top = 12.dp))
                ChipSkeletons(Modifier.padding(top = 16.dp))
                if (wide) DescriptionSkeleton(Modifier.padding(top = 16.dp), lines = 3)
            }
        }
        if (!wide) DescriptionSkeleton(Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ChipSkeletons(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Block(128.dp, 36.dp, RoundedCornerShape(18.dp))
        Block(96.dp, 36.dp, RoundedCornerShape(18.dp))
    }
}

/** The description card: its lines of text and the card's padding. */
@Composable
private fun DescriptionSkeleton(modifier: Modifier, lines: Int = 2) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.padding(16.dp)) { TextLines(MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), lines, listOf(1f, 0.7f)) }
    }
}

/** Subscribe (stopping at its cap) and the sort and search buttons. */
@Composable
private fun ToolbarSkeleton() {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 16.dp, end = 16.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Block(null, 48.dp, RoundedCornerShape(24.dp), Modifier.weight(1f, fill = false).widthIn(max = SubscribeMaxWidth).testTag(SHOW_PAGE_SKELETON_TOOLBAR_TAG))
        Block(48.dp, 48.dp, RoundedCornerShape(8.dp))
        Block(48.dp, 48.dp, RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp))
    }
}

internal const val SHOW_PAGE_SKELETON_COVER_TAG = "podcast_info_skeleton_cover"
internal const val SHOW_PAGE_SKELETON_TOOLBAR_TAG = "podcast_info_skeleton_toolbar"

/** Title rows are about a third of a card's height, so the opening page shows more of them. */
private const val TITLE_ROW_SKELETONS = 4

/**
 * Completed episodes are hidden and the show has none left: says so, and offers to show them
 * again, instead of an empty page.
 */
@Composable
internal fun ShowCaughtUpCard(onShowCompleted: () -> Unit, modifier: Modifier = Modifier) {
    InfoStatusCard(
        icon = Icons.Rounded.Check,
        title = stringResource(R.string.podcast_info_caught_up_title),
        body = stringResource(R.string.podcast_info_caught_up_body),
        action = InfoStatusAction(stringResource(R.string.podcast_info_show_completed), onShowCompleted),
        actionIcon = Icons.Rounded.Visibility,
        modifier = modifier.testTag(SHOW_CAUGHT_UP_TAG),
    )
}
