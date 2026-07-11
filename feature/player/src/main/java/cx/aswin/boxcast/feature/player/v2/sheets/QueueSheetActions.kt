package cx.aswin.boxcast.feature.player.v2.sheets

import cx.aswin.boxcast.core.model.Episode

data class QueueSheetActions(
    val onPlayEpisode: (Episode) -> Unit,
    val onRemoveEpisode: (Episode) -> Unit,
    val onClose: () -> Unit,
    val onMove: (fromUiIndex: Int, toUiIndex: Int) -> Unit = { _, _ -> },
    val onDragEnd: (episodeId: String, fromUiIndex: Int, toUiIndex: Int) -> Unit = { _, _, _ -> },
)
