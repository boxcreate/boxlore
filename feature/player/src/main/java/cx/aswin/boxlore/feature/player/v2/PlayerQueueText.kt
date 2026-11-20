package cx.aswin.boxlore.feature.player.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.player.R

/** Words the queue's callbacks need outside composition, read in the screen's language. */
internal data class PlayerQueueText(val removed: String, val undo: String, val unknownShow: String)

@Composable
internal fun playerQueueText(): PlayerQueueText = PlayerQueueText(
    removed = stringResource(R.string.player_removed_from_queue),
    undo = stringResource(R.string.player_undo),
    unknownShow = stringResource(R.string.player_unknown_show),
)
