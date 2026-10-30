package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode

/**
 * Snapshot of a removed queue item, returned so the UI can offer Undo and so the
 * skip signal (analytics + skip memory) can be deferred until the undo window lapses.
 */
data class RemovedQueueItem(
    val episode: Episode,
    val queueIndex: Int,
    val mediaIndex: Int,
    val contextType: String?,
    val contextSourceId: String?,
)
