package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.feature.library.R

/** What the New Episodes sort sheet changes: the order, and whether played episodes are hidden. */
internal data class LatestSortActions(
    val onUseSmartRankChange: (Boolean) -> Unit,
    val onHideCompletedChange: (Boolean) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * New Episodes' sort: the order as pills, like Shows' Sort and organize, and Hide played as a switch
 * row of its own rather than another choice among the orders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LatestSortSheet(useSmartRank: Boolean, hideCompleted: Boolean, actions: LatestSortActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            LatestSortSheetContent(
                useSmartRank = useSmartRank,
                hideCompleted = hideCompleted,
                actions = actions,
                modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding(),
            )
        }
    }
}

@Composable
internal fun LatestSortSheetContent(useSmartRank: Boolean, hideCompleted: Boolean, actions: LatestSortActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_sort),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = actions.onDismiss) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(R.string.library_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 12.dp)) {
            SortChip(label = stringResource(R.string.library_sort_smart), selected = useSmartRank, onClick = { actions.onUseSmartRankChange(true) })
            SortChip(label = stringResource(R.string.library_sort_chronological), selected = !useSmartRank, onClick = { actions.onUseSmartRankChange(false) })
        }
        HidePlayedRow(hideCompleted, actions.onHideCompletedChange)
    }
}

/** One switch row: the whole row toggles, and TalkBack reads it as one switch. */
@Composable
private fun HidePlayedRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(end = 12.dp).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.library_hide_played_episodes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
