package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.text.UiText

/** What Add to queue did with a selection ([addSelectionToQueue]). */
internal data class QueueSelectionOutcome(
    val added: Int,
    /** Episodes left out because the queue was full. */
    val didNotFit: Int,
)

/**
 * Adds the selected [episodes] to the end of the queue, in selection order, until the queue holds
 * [queueLimit]. Episodes already queued (under any of their IDs, [queuedIds]) are skipped; the rest
 * that don't fit are counted, never dropped silently. [queueSize] is read before each add, so an
 * add that fills the queue stops the run.
 */
internal suspend fun addSelectionToQueue(
    episodes: List<Episode>,
    queuedIds: Set<String>,
    queueLimit: Int,
    queueSize: () -> Int,
    add: suspend (Episode) -> Boolean,
): QueueSelectionOutcome {
    val fresh = episodes.distinctBy(Episode::id).filterNot { it.id in queuedIds }
    var added = 0
    for ((index, episode) in fresh.withIndex()) {
        if (queueSize() >= queueLimit) return QueueSelectionOutcome(added, didNotFit = fresh.size - index)
        if (add(episode)) added++
    }
    return QueueSelectionOutcome(added, didNotFit = 0)
}

/** The show page's message after Add to queue, or null when everything fit. */
internal fun queueSelectionMessage(outcome: QueueSelectionOutcome): UiText? = when {
    outcome.didNotFit == 0 -> null
    outcome.added == 0 -> UiText.res(R.string.podcast_info_queue_full)
    else -> UiText.res(
        R.string.podcast_info_queue_partly_added,
        UiText.plural(R.plurals.podcast_info_queue_added, outcome.added, outcome.added),
        UiText.plural(R.plurals.podcast_info_queue_not_added, outcome.didNotFit, outcome.didNotFit),
    )
}
