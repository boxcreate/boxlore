package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines

/** Test tag on the placeholder list and conversation shown while Feedback loads. */
internal const val FEEDBACK_SKELETON_TAG = "feedback_skeleton"

private const val SKELETON_ROWS = 3

/** Three placeholder rows in the conversation list's grouped shape, so nothing moves when it loads. */
@Composable
internal fun FeedbackListSkeleton(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    ShimmerScope(active = true) {
        // Says "loading" to a screen reader, as the progress indicator it replaced did.
        Column(modifier.fillMaxSize().padding(contentPadding).testTag(FEEDBACK_SKELETON_TAG).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
            SkeletonTextLines(MaterialTheme.typography.titleSmall, lines = 1, widths = listOf(0.4f), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            repeat(SKELETON_ROWS) { index ->
                val first = index == 0
                val last = index == SKELETON_ROWS - 1
                val shape = RoundedCornerShape(
                    topStart = if (first) 20.dp else 0.dp,
                    topEnd = if (first) 20.dp else 0.dp,
                    bottomStart = if (last) 20.dp else 0.dp,
                    bottomEnd = if (last) 20.dp else 0.dp,
                )
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.padding(top = 8.dp)) { SkeletonBlock(Modifier.size(8.dp), shape = CircleShape) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SkeletonTextLines(MaterialTheme.typography.bodyLarge, lines = 1, widths = listOf(0.7f))
                                SkeletonTextLines(MaterialTheme.typography.bodyMedium, lines = 1, widths = listOf(0.45f))
                            }
                        }
                        if (!last) HorizontalDivider(Modifier.padding(start = 34.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/** Two placeholder message cards in the conversation's 20dp card shape. */
@Composable
internal fun FeedbackThreadSkeleton(modifier: Modifier = Modifier) {
    ShimmerScope(active = true) {
        Column(
            modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag(FEEDBACK_SKELETON_TAG)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainerHigh).forEach { color ->
                Surface(shape = RoundedCornerShape(20.dp), color = color, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonTextLines(MaterialTheme.typography.labelLarge, lines = 1, widths = listOf(0.35f))
                        SkeletonTextLines(MaterialTheme.typography.bodyLarge, lines = 3, widths = listOf(0.95f, 0.9f, 0.55f))
                    }
                }
            }
        }
    }
}
