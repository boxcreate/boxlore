package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import kotlinx.coroutines.delay

/**
 * A Library page while it loads: a soft outline of the page that is coming ([content]), shown only
 * after [LOADING_SHOW_DELAY_MS] so a quick load never flashes it.
 */
@Composable
internal fun LibraryLoading(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_SHOW_DELAY_MS)
        visible = true
    }
    val loading = stringResourceLoading()
    Box(modifier.fillMaxSize().testTag(LIBRARY_LOADING_TAG).semantics { contentDescription = loading }) {
        if (visible) ShimmerScope(active = true, content = content)
    }
}

/** The hub's five 120dp cards, where the real cards will be. */
@Composable
internal fun LibraryHubSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(HUB_CARDS) { SkeletonBlock(Modifier.fillMaxWidth().boxloreCardWidth(BoxloreCardFamily.Utility).height(120.dp), shape = RoundedCornerShape(24.dp)) }
    }
}

/** Episode or show rows: artwork beside two text lines, as on Liked and Downloads. */
@Composable
internal fun LibraryRowsSkeleton(artwork: Dp = 72.dp, rows: Int = 5, leading: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().boxloreCardWidth(BoxloreCardFamily.Episode).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading?.invoke()
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBlock(Modifier.size(artwork), shape = RoundedCornerShape(12.dp))
                SkeletonTextLines(MaterialTheme.typography.titleSmall, lines = 2, modifier = Modifier.weight(1f).padding(top = 4.dp))
            }
        }
    }
}

/** Covers in a grid of [columns], as on the Shows grid and Playlists. */
@Composable
internal fun LibraryGridSkeleton(columns: Int, cells: Int = columns * 3, top: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().boxloreCardWidth(BoxloreCardFamily.Grid).padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        top?.invoke()
        repeat((cells + columns - 1) / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(columns) { SkeletonBlock(Modifier.weight(1f).aspectRatio(1f), shape = RoundedCornerShape(20.dp)) }
            }
        }
    }
}

/** A block standing in for a page's first card or bar. */
@Composable
internal fun LibraryBlockSkeleton(height: Dp, corner: Dp = 24.dp) {
    SkeletonBlock(Modifier.fillMaxWidth().height(height), shape = RoundedCornerShape(corner))
}

/** The shared "Loading" a screen reader says while the outline shows. */
@Composable
private fun stringResourceLoading(): String = stringResource(DesignR.string.play_button_loading)

private const val HUB_CARDS = 5
private const val LOADING_SHOW_DELAY_MS = 150L

/** Test tag of a loading Library page. */
internal const val LIBRARY_LOADING_TAG = "library_loading"
