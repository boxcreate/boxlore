package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Pull down on New Episodes to check for new episodes now; plain content when there is no refresh. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LatestPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (onRefresh == null) {
        content()
        return
    }
    // boxlore's shape-shifting indicator, as on the show page; it shows under the top bar and tabs.
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).testTag(LATEST_REFRESH_INDICATOR_TAG),
            )
        },
    ) {
        content()
    }
}

/** Lets an empty state take a pull: it scrolls by nothing but still sends the gesture up. */
@Composable
internal fun PullableEmptyState(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewportHeight),
            ) {
                content()
            }
        }
    }
}

/** New Episodes' refresh indicator, for tests. */
internal const val LATEST_REFRESH_INDICATOR_TAG = "latest_refresh_indicator"
