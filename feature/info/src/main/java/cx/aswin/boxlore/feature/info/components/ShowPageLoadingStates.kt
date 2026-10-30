package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.feature.info.R

internal const val SHOW_PAGE_SKELETON_TAG = "podcast_info_skeleton"
internal const val EPISODE_CARD_SKELETON_TAG = "podcast_info_episode_skeleton"
internal const val SHOW_CAUGHT_UP_TAG = "podcast_info_caught_up"
internal const val SHOW_COMPLETED_BUTTON_TAG = "podcast_info_show_completed"

/** One loading block in the page's own tones; the moving highlight follows the enclosing [ShimmerScope]. */
@Composable
private fun Block(width: Dp?, height: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val sized = if (width == null) modifier.fillMaxWidth() else modifier.width(width)
    Box(sized.height(height).m3Shimmer(colors.surfaceContainerHigh, colors.surfaceContainerLowest, shape = shape))
}

/** A loading episode card in [EpisodeListItem]'s geometry: 76 dp art, three text lines, the action rail. */
@Composable
internal fun EpisodeCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag(EPISODE_CARD_SKELETON_TAG).clearAndSetSemantics {},
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ShimmerScope(active = true) {
            Column(Modifier.padding(14.dp)) {
                Row {
                    Block(76.dp, 76.dp, RoundedCornerShape(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Block(null, 16.dp, RoundedCornerShape(8.dp), Modifier.padding(end = 24.dp))
                        Block(110.dp, 11.dp, RoundedCornerShape(6.dp))
                        Block(null, 11.dp, RoundedCornerShape(6.dp))
                        Block(160.dp, 11.dp, RoundedCornerShape(6.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { Block(48.dp, 56.dp, RoundedCornerShape(14.dp)) }
                    Block(null, 56.dp, RoundedCornerShape(28.dp), Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Opening a show: the page in its own shape (cover, title, chips, description, buttons and
 * episode cards) while it loads, instead of a lone loader in an empty page.
 */
@Composable
internal fun ShowPageSkeleton(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.podcast_info_loading)
    ShimmerScope(active = true) {
        Column(
            modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = false)
                .statusBarsPadding()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                .testTag(SHOW_PAGE_SKELETON_TAG)
                .clearAndSetSemantics { contentDescription = loading },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Block(180.dp, 180.dp, MaterialTheme.shapes.extraLarge)
            Block(240.dp, 28.dp, RoundedCornerShape(10.dp), Modifier.padding(top = 22.dp))
            Block(120.dp, 16.dp, RoundedCornerShape(8.dp), Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Block(128.dp, 32.dp, RoundedCornerShape(16.dp))
                Block(96.dp, 32.dp, RoundedCornerShape(16.dp))
            }
            Block(null, 52.dp, RoundedCornerShape(18.dp), Modifier.padding(top = 18.dp).boxloreCardWidth(BoxloreCardFamily.Utility))
            Row(Modifier.padding(top = 16.dp).boxloreCardWidth(BoxloreCardFamily.Utility), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Block(null, 48.dp, RoundedCornerShape(24.dp), Modifier.weight(1f))
                Block(52.dp, 48.dp, RoundedCornerShape(16.dp))
                Block(52.dp, 48.dp, RoundedCornerShape(24.dp))
            }
            Column(Modifier.padding(top = 16.dp).boxloreCardWidth(BoxloreCardFamily.Reading), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(2) { EpisodeCardSkeleton() }
            }
        }
    }
}

/**
 * Completed episodes are hidden and the show has none left: says so, and offers to show them
 * again, instead of an empty page.
 */
@Composable
internal fun ShowCaughtUpCard(onShowCompleted: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().boxloreCardWidth(BoxloreCardFamily.Utility).padding(horizontal = 16.dp).testTag(SHOW_CAUGHT_UP_TAG),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = RoundedCornerShape(22.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(30.dp))
                }
            }
            Text(
                stringResource(R.string.podcast_info_caught_up_title),
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.podcast_info_caught_up_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onShowCompleted, modifier = Modifier.padding(top = 6.dp).testTag(SHOW_COMPLETED_BUTTON_TAG)) {
                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.podcast_info_show_completed))
            }
        }
    }
}
