package cx.aswin.boxlore.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import cx.aswin.boxlore.feature.history.timeline.TimelineType

/** Test tags of the two tabs' loading placeholders. */
const val HISTORY_INSIGHTS_LOADING_TAG = "history_insights_loading"
const val HISTORY_TIMELINE_LOADING_TAG = "history_timeline_loading"

/**
 * Insights while it loads: the period selector, the time card and two cards, as soft placeholders in
 * their places, with the loaded list's padding so nothing jumps when it arrives.
 */
@Composable
internal fun HistoryInsightsLoading(contentPadding: PaddingValues) {
    HistoryLoadingColumn(HISTORY_INSIGHTS_LOADING_TAG, contentPadding) {
        SkeletonBlock(Modifier.fillMaxWidth().height(44.dp), RoundedCornerShape(22.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(220.dp), RoundedCornerShape(28.dp))
        repeat(2) { SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), RoundedCornerShape(24.dp)) }
    }
}

/** The Timeline while it loads: the search box, the filter chips and four rows of cover and two lines. */
@Composable
internal fun HistoryTimelineLoading(contentPadding: PaddingValues) {
    HistoryLoadingColumn(HISTORY_TIMELINE_LOADING_TAG, contentPadding) {
        SkeletonBlock(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(26.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(24.dp))
        repeat(4) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(60.dp), RoundedCornerShape(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonTextLines(TimelineType.title, lines = 1, widths = listOf(0.85f))
                    SkeletonTextLines(TimelineType.meta, lines = 1, widths = listOf(0.5f))
                }
                SkeletonBlock(Modifier.width(20.dp).height(20.dp), RoundedCornerShape(10.dp))
            }
        }
    }
}

@Composable
private fun HistoryLoadingColumn(tag: String, contentPadding: PaddingValues, content: @Composable () -> Unit) {
    val loading = stringResource(DesignR.string.play_button_loading)
    ShimmerScope(active = true) {
        Column(
            Modifier
                .fillMaxSize()
                .testTag(tag)
                .clearAndSetSemantics {
                    contentDescription = loading
                    liveRegion = LiveRegionMode.Polite
                }
                .padding(contentPadding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}
